# QA em runner Windows DESCARTÁVEL. Nunca execute em ambiente do cliente.
# Instala somente o MSI produzido nesta execução do CI, testa o launcher
# da instalação real e desinstala via MSI. Falha fechada; não distribui release.
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$packageName = "Brother Matrizes"
$msiFolder = Join-Path $PSScriptRoot "..\build\compose\binaries\main\msi"
$packages = @(Get-ChildItem -LiteralPath $msiFolder -Filter "*.msi" -File)
if ($packages.Count -ne 1) {
    throw "Esperado exatamente um pacote MSI no build; encontrados: $($packages.Count)"
}
$msiPath = $packages[0].FullName
$logRoot = $env:RUNNER_TEMP
if ([string]::IsNullOrWhiteSpace($logRoot)) {
    throw "Este teste so pode executar num runner QA com RUNNER_TEMP."
}
$installLog = Join-Path $logRoot "brother-windows-msi-install.log"
$uninstallLog = Join-Path $logRoot "brother-windows-msi-uninstall.log"
$report = Join-Path $logRoot "brother-windows-installed-msi-smoke.txt"

function Get-InstalledBrotherMsi {
    $registryPaths = @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )
    @(
        foreach ($path in $registryPaths) {
            Get-ItemProperty -Path $path -ErrorAction SilentlyContinue |
                Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -ieq $packageName }
        }
    )
}

function Invoke-Msiexec {
    param(
        [Parameter(Mandatory=$true)][ValidateSet("install", "uninstall")]
        [string]$Mode,
        [Parameter(Mandatory=$true)][string]$Target,
        [Parameter(Mandatory=$true)][string]$Log
    )
    $operation = if ($Mode -eq "install") { "/i" } else { "/x" }
    $arguments = $operation + ' "' + $Target + '" /qn /norestart /l*v "' + $Log + '"'
    Write-Host "Iniciando msiexec $Mode para pacote QA (sem interface)."
    $process = Start-Process -FilePath "msiexec.exe" -ArgumentList $arguments -PassThru
    if (-not $process.WaitForExit(180000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "msiexec $Mode excedeu limite de 180 segundos."
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0) {
        if (Test-Path -LiteralPath $Log) {
            Get-Content -LiteralPath $Log -Tail 40 | ForEach-Object { Write-Host $_ }
        }
        throw "msiexec $Mode falhou (codigo $($process.ExitCode))."
    }
}

$existing = @(Get-InstalledBrotherMsi)
if ($existing.Count -ne 0) {
    throw "Runner ja possui $packageName instalado; proibido modificar software preexistente."
}

$productCode = $null
$installedByThisJob = $false
try {
    Invoke-Msiexec -Mode install -Target $msiPath -Log $installLog
    $installedByThisJob = $true

    $matches = @(Get-InstalledBrotherMsi)
    if ($matches.Count -ne 1) {
        throw "Registro MSI nao encontrado ou ambiguo apos instalar a QA ($($matches.Count))."
    }
    $entry = $matches[0]
    $productCode = [string]$entry.PSChildName
    if ($productCode -notmatch '^\{[0-9a-fA-F-]{36}\}$') {
        throw "ProductCode de desinstalacao nao e GUID MSI valido."
    }

    # InstallLocation e opcional em registros MSI; StrictMode nao deve
    # interromper a localizacao quando o instalador nao publica esse campo.
    $registeredPath = [string]($entry | Select-Object -ExpandProperty InstallLocation -ErrorAction SilentlyContinue)
    $candidateFolders = @(
        $registeredPath,
        (Join-Path $env:ProgramFiles $packageName),
        (Join-Path $env:LOCALAPPDATA $packageName),
        (Join-Path (Join-Path $env:LOCALAPPDATA 'Programs') $packageName)
    )
    if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('ProgramFiles(x86)'))) {
        $candidateFolders += (Join-Path ([Environment]::GetEnvironmentVariable('ProgramFiles(x86)')) $packageName)
    }
    $launchers = @(
        foreach ($folder in ($candidateFolders | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
            $launcher = Join-Path $folder "$packageName.exe"
            if (Test-Path -LiteralPath $launcher -PathType Leaf) {
                (Get-Item -LiteralPath $launcher).FullName
            }
        }
    ) | Select-Object -Unique
    if (@($launchers).Count -ne 1) {
        throw "Executavel instalado nao encontrado em pasta padrao do MSI ($(@($launchers).Count))."
    }

    if (Test-Path -LiteralPath $report) {
        Remove-Item -LiteralPath $report -Force
    }
    $app = [string]@($launchers)[0]
    Write-Host "Executando launcher realmente instalado pelo MSI em runner virtual."
    $args = '--smoke-test "' + $report + '"'
    $runner = Start-Process -FilePath $app -ArgumentList $args -WorkingDirectory (Split-Path $app -Parent) -PassThru
    if (-not $runner.WaitForExit(90000)) {
        Stop-Process -Id $runner.Id -Force -ErrorAction SilentlyContinue
        throw "Launcher instalado nao terminou smoke test em 90 segundos."
    }
    $runner.Refresh()
    if ($runner.ExitCode -ne 0) {
        throw "Launcher instalado retornou codigo $($runner.ExitCode)."
    }
    if (-not (Test-Path -LiteralPath $report -PathType Leaf)) {
        throw "Launcher instalado nao gerou relatorio de diagnostico."
    }
    $result = Get-Content -LiteralPath $report -Raw -Encoding UTF8
    foreach ($required in @(
        'BROTHER_WINDOWS_QA_SMOKE_OK',
        'DST: validado',
        'FLP: validado',
        'Sobrescrita: bloqueada',
        'Desfazer/refazer: validado',
        'Expansão H/V: validado',
        'Bastidores: pré-checagem validada',
        'Cores de linha: edição reversível validada',
        'Letras independentes: edição reversível validada',
        'Camadas FLP: associação SHA256 e reabertura validadas'
    )) {
        if (-not $result.Contains($required)) {
            throw "Relatorio do MSI nao inclui evidencia esperada: $required"
        }
    }
    Write-Host "BROTHER_WINDOWS_MSI_INSTALLED_SMOKE_OK"
    Write-Host "Instalacao MSI e launcher empacotado validados em runner virtual."

    # Verifica a causa do relato em um runner descartavel:
    # um MSI nao esta aprovado se nao houver atalhos funcionais.
    function Find-BrotherShortcut {
        param([string[]]$Roots)
        @(
            foreach ($root in ($Roots | Where-Object {
                -not [string]::IsNullOrWhiteSpace($_) -and
                (Test-Path -LiteralPath $_ -PathType Container)
            } | Select-Object -Unique)) {
                Get-ChildItem -LiteralPath $root -Filter "$packageName.lnk" -Recurse -File -ErrorAction Stop |
                    Select-Object -ExpandProperty FullName
            }
        )
    }
    $startRoots = @(
        [Environment]::GetFolderPath('CommonPrograms'),
        [Environment]::GetFolderPath('Programs')
    )
    $desktopRoots = @(
        [Environment]::GetFolderPath('CommonDesktopDirectory'),
        [Environment]::GetFolderPath('DesktopDirectory')
    )
    $startLinks = @(Find-BrotherShortcut -Roots $startRoots)
    $desktopLinks = @(Find-BrotherShortcut -Roots $desktopRoots)
    if ($startLinks.Count -lt 1 -or $desktopLinks.Count -lt 1) {
        throw "MSI sem atalhos: menu Iniciar=$($startLinks.Count), Area de Trabalho=$($desktopLinks.Count)."
    }
    $shell = New-Object -ComObject WScript.Shell
    foreach ($shortcut in ($startLinks + $desktopLinks)) {
        $target = [string]$shell.CreateShortcut($shortcut).TargetPath
        if ([string]::IsNullOrWhiteSpace($target) -or
            !(Test-Path -LiteralPath $target -PathType Leaf) -or
            !([System.IO.Path]::GetFullPath($target) -ieq
                [System.IO.Path]::GetFullPath($app))) {
            throw "Atalho nao aponta ao executavel instalado: $shortcut."
        }
    }
    Write-Host "BROTHER_WINDOWS_MSI_START_DESKTOP_SHORTCUTS_OK"

    # Abre a janela Compose do MSI realmente instalado, com tela vazia.
    # Nao toca dados do usuario nem depende do editor de matrizes.
    $uiReport = Join-Path $logRoot "brother-windows-installed-msi-ui.txt"
    $uiPicture = Join-Path $logRoot "brother-windows-installed-msi-ui.png"
    if ((Test-Path -LiteralPath $uiReport) -or
        (Test-Path -LiteralPath $uiPicture)) {
        throw "Evidencia grafica do MSI ja existe; nao sobrescrever."
    }
    $env:BROTHER_WINDOWS_QA_UI = '1'
    try {
        $uiArgs = '--ui-smoke-test "' + $uiReport + '"'
        $uiProcess = Start-Process -FilePath $app -ArgumentList $uiArgs -WorkingDirectory (Split-Path $app -Parent) -PassThru
        if (-not $uiProcess.WaitForExit(90000)) {
            Stop-Process -Id $uiProcess.Id -Force -ErrorAction SilentlyContinue
            throw "Interface do MSI instalado nao abriu em 90 segundos."
        }
        $uiProcess.Refresh()
        if ($uiProcess.ExitCode -ne 0) {
            throw "Aplicativo instalado nao iniciou a interface (codigo $($uiProcess.ExitCode))."
        }
        if (!(Test-Path -LiteralPath $uiPicture -PathType Leaf) -or
            !(Test-Path -LiteralPath $uiReport -PathType Leaf)) {
            throw "Aplicativo instalado nao gerou evidencia da janela."
        }
        $uiText = Get-Content -LiteralPath $uiReport -Encoding UTF8 -Raw
        if (!$uiText.StartsWith('BROTHER_WINDOWS_UI_SMOKE_OK')) {
            throw "Relatorio da interface instalada nao foi aprovado."
        }
        Write-Host "BROTHER_WINDOWS_MSI_INSTALLED_GUI_OK"
    } finally {
        Remove-Item Env:BROTHER_WINDOWS_QA_UI -ErrorAction SilentlyContinue
    }

    # Confirma abertura NORMAL sem flags no launcher instalado. Consultar
    # User32 evita falso negativo da propriedade .MainWindowHandle no wrapper.
    Add-Type -Path (Join-Path $PSScriptRoot 'BrotherWindowProbe.cs') -ErrorAction Stop
    $interactive = Start-Process -FilePath $app -WorkingDirectory (Split-Path $app -Parent) -PassThru
    try {
        $seenWindow = $false
        $ownerId = 0
        for ($attempt = 0; $attempt -lt 120; $attempt++) {
            $interactive.Refresh()
            if ($interactive.HasExited) {
                throw "Aplicativo encerrou antes de mostrar a janela normal."
            }
            $ownerId = [BrotherWindowProbe]::FindVisibleWindowOwner("Brother Matrizes Desktop")
            if ($ownerId -gt 0) {
                $owner = Get-Process -Id $ownerId -ErrorAction SilentlyContinue
                $ownerPath = [string]($owner | Select-Object -ExpandProperty Path -ErrorAction SilentlyContinue)
                $installedFolder = ([System.IO.Path]::GetFullPath(
                    (Split-Path $app -Parent)).TrimEnd('\') + '\')
                if ([string]::IsNullOrWhiteSpace($ownerPath) -or
                    !([System.IO.Path]::GetFullPath($ownerPath).StartsWith(
                        $installedFolder, [StringComparison]::OrdinalIgnoreCase))) {
                    throw "Janela detectada fora da instalacao QA esperada (PID $ownerId)."
                }
                $seenWindow = $true
                Write-Host "Janela visivel encontrada pelo Windows: PID $ownerId."
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if (-not $seenWindow) {
            $interactive.Refresh()
            Write-Host "Launcher normal PID=$($interactive.Id), Handle=$($interactive.MainWindowHandle), Title=$($interactive.MainWindowTitle)"
            throw "Aplicativo instalado nao mostrou janela visivel em 30 segundos."
        }
        Write-Host "BROTHER_WINDOWS_MSI_NORMAL_LAUNCH_OK"
    } finally {
        $interactive.Refresh()
        if (-not $interactive.HasExited) {
            Stop-Process -Id $interactive.Id -Force -ErrorAction SilentlyContinue
        }
    }
} finally {
    if ($installedByThisJob) {
        if ($null -eq $productCode) {
            $found = @(Get-InstalledBrotherMsi)
            if ($found.Count -eq 1) {
                $productCode = [string]$found[0].PSChildName
            }
        }
        if ($null -ne $productCode -and $productCode -match '^\{[0-9a-fA-F-]{36}\}$') {
            Invoke-Msiexec -Mode uninstall -Target $productCode -Log $uninstallLog
            if (@(Get-InstalledBrotherMsi).Count -ne 0) {
                throw "MSI QA ainda aparece instalado depois de desinstalar."
            }
            Write-Host "BROTHER_WINDOWS_MSI_UNINSTALL_OK"
        } else {
            throw "Falha de limpeza: ProductCode MSI nao identificado. Runner nao deve ser reutilizado."
        }
    }
}
