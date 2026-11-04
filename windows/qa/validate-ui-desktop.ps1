# Validação gráfica da tela inicial Windows Compose no runner efêmero.
# A captura só contém a tela inicial vazia de QA, sem dados de clientes.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$appImage = Join-Path $PSScriptRoot '..\build\compose\binaries\main\app'
$launchers = @(Get-ChildItem -LiteralPath $appImage -File -Recurse -Filter 'BrotherMatrizesDesktop.exe')
if ($launchers.Count -ne 1) {
    throw "Esperado um launcher EXE no app-image; encontrados $($launchers.Count)."
}
if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'A validação gráfica exige RUNNER_TEMP de um runner descartável.'
}
if (-not [Environment]::UserInteractive) {
    throw 'Runner não possui sessão gráfica interativa; teste visual indisponível.'
}

$folder = Join-Path $PSScriptRoot '..\build\qa\desktop-ui'
[System.IO.Directory]::CreateDirectory($folder) | Out-Null
$report = Join-Path $folder 'brother-ui-smoke.txt'
$picture = Join-Path $folder 'brother-ui-smoke.png'
if ((Test-Path -LiteralPath $report) -or (Test-Path -LiteralPath $picture)) {
    throw 'Relatório gráfico já existe: execução não pode sobrescrever resultados anteriores.'
}
$env:BROTHER_WINDOWS_QA_UI = '1'
$launcher = $launchers[0]
$arguments = '--ui-smoke-test "' + ([System.IO.Path]::GetFullPath($report)) + '"'
Write-Host 'Executando GUI Compose no runner Windows, sem abrir documentos privados.'
$process = Start-Process -FilePath $launcher.FullName -WorkingDirectory $launcher.DirectoryName -ArgumentList $arguments -PassThru
try {
    if (-not $process.WaitForExit(90000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw 'Janela Compose não concluiu seu diagnóstico em 90 segundos.'
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0) {
        throw "Aplicativo não concluiu diagnóstico gráfico (exit code $($process.ExitCode))."
    }
    if (!(Test-Path -LiteralPath $report -PathType Leaf) -or
        !(Test-Path -LiteralPath $picture -PathType Leaf)) {
        throw 'Relatório ou captura PNG da janela Compose ausentes.'
    }
    $contents = Get-Content -LiteralPath $report -Encoding UTF8 -Raw
    if (-not $contents.StartsWith('BROTHER_WINDOWS_UI_SMOKE_OK')) {
        throw 'Diagnóstico da interface não confirmou janela renderizada.'
    }
    $bytes = [System.IO.File]::ReadAllBytes($picture)
    if ($bytes.Length -lt 4000 -or
        $bytes[0] -ne 137 -or $bytes[1] -ne 80 -or
        $bytes[2] -ne 78 -or $bytes[3] -ne 71) {
        throw 'Captura PNG inválida ou vazia.'
    }
    Write-Host $contents
    Write-Host 'BROTHER_WINDOWS_UI_SCREENSHOT_VERIFIED'
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
