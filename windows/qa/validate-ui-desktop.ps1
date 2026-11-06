# Validação gráfica da interface Compose no runner Windows efêmero.
# Os diagnósticos usam tela vazia ou matriz sintética, sem dados de clientes.
[CmdletBinding()]
param(
    [switch]$SyntheticPreview,
    [switch]$WheelZoom,
    [switch]$Drawing,
    [switch]$VectorSvg
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$appImage = Join-Path $PSScriptRoot '..\build\compose\binaries\main\app'
$launchers = @(Get-ChildItem -LiteralPath $appImage -File -Recurse -Filter 'Brother Matrizes.exe')
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
$selectedModes = @()
if ($SyntheticPreview) { $selectedModes += 'synthetic' }
if ($WheelZoom) { $selectedModes += 'wheel' }
if ($Drawing) { $selectedModes += 'drawing' }
if ($VectorSvg) { $selectedModes += 'svg' }
if ($selectedModes.Count -gt 1) {
    throw 'Escolha apenas um modo de diagnóstico de interface.'
}
$stem = if ($WheelZoom) {
    'brother-ui-wheel-smoke'
} elseif ($SyntheticPreview) {
    'brother-ui-synthetic-smoke'
} elseif ($Drawing) {
    'brother-ui-drawing-smoke'
} elseif ($VectorSvg) {
    'brother-ui-svg-smoke'
} else {
    'brother-ui-smoke'
}
$report = Join-Path $folder ($stem + '.txt')
$picture = Join-Path $folder ($stem + '.png')
if ((Test-Path -LiteralPath $report) -or (Test-Path -LiteralPath $picture)) {
    throw 'Relatório gráfico já existe: execução não pode sobrescrever resultados anteriores.'
}
$env:BROTHER_WINDOWS_QA_UI = '1'
$launcher = $launchers[0]
$command = if ($WheelZoom) {
    '--ui-wheel-smoke-test'
} elseif ($SyntheticPreview) {
    '--ui-synthetic-smoke-test'
} elseif ($Drawing) {
    '--ui-drawing-smoke-test'
} elseif ($VectorSvg) {
    '--ui-svg-smoke-test'
} else {
    '--ui-smoke-test'
}
$arguments = $command + ' "' + ([System.IO.Path]::GetFullPath($report)) + '"'
Write-Host 'Executando GUI Compose no runner Windows, sem abrir documentos privados.'
$process = Start-Process -FilePath $launcher.FullName -WorkingDirectory $launcher.DirectoryName -ArgumentList $arguments -PassThru
try {
    if (-not $process.WaitForExit(90000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw 'Janela Compose não concluiu seu diagnóstico em 90 segundos.'
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0) {
        # O launcher Windows é javaw e não entrega stderr ao GitHub Actions.
        # Ler a medição persistida antes de reportar a falha para a QA.
        if (Test-Path -LiteralPath $report -PathType Leaf) {
            Write-Warning ('Diagnóstico gráfico reprovado:' +
                [Environment]::NewLine + (Get-Content -LiteralPath $report -Raw -Encoding UTF8))
        }
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
    if ($SyntheticPreview -or $WheelZoom) {
        if (!$contents.Contains('BROTHER_WINDOWS_SYNTHETIC_UI_OK')) {
            throw 'A captura não confirmou a matriz sintética de QA.'
        }
        # A tela com matriz deve ser visualmente diferente da janela vazia,
        # sem depender de arquivos de clientes nem de inspeção humana no CI.
        $emptyPicture = Join-Path $folder 'brother-ui-smoke.png'
        if (!(Test-Path -LiteralPath $emptyPicture -PathType Leaf)) {
            throw 'Captura da interface vazia necessária para comparação.'
        }
        $emptySha = (Get-FileHash -LiteralPath $emptyPicture -Algorithm SHA256).Hash
        $syntheticSha = (Get-FileHash -LiteralPath $picture -Algorithm SHA256).Hash
        if ($emptySha -ceq $syntheticSha) {
            throw 'A janela com matriz sintética é idêntica à janela vazia.'
        }
        Write-Host 'BROTHER_WINDOWS_UI_SYNTHETIC_PREVIEW_VERIFIED'
    }
    if ($WheelZoom) {
        if (!$contents.Contains('BROTHER_WINDOWS_UI_WHEEL_ZOOM_OK')) {
            throw 'A roda do mouse não ampliou a matriz sintética na interface.'
        }
        $zoomPicture = Join-Path $folder ($stem + '-zoom.png')
        if (!(Test-Path -LiteralPath $zoomPicture -PathType Leaf)) {
            throw 'Captura ampliada ausente.'
        }
        $zoomBytes = [System.IO.File]::ReadAllBytes($zoomPicture)
        if ($zoomBytes.Length -lt 4000 -or
            $zoomBytes[0] -ne 137 -or $zoomBytes[1] -ne 80 -or
            $zoomBytes[2] -ne 78 -or $zoomBytes[3] -ne 71) {
            throw 'Captura ampliada inválida ou vazia.'
        }
        Write-Host 'BROTHER_WINDOWS_UI_MOUSE_WHEEL_VERIFIED'
    }
    if ($Drawing -or $VectorSvg) {
        if (!$contents.Contains('BROTHER_WINDOWS_UI_DRAWING_VERIFIED')) {
            throw 'Não foram detectados traços coloridos no canvas Desenhar.'
        }
        if ($Drawing) {
            if (!$contents.Contains('BROTHER_WINDOWS_UI_MOUSE_DRAW_OK')) {
                throw 'O movimento real do mouse não gerou desenho visível.'
            }
            $drawPicture = Join-Path $folder ($stem + '-after.png')
            if (!(Test-Path -LiteralPath $drawPicture -PathType Leaf)) {
                throw 'Captura após o movimento do mouse ausente.'
            }
        }
        Write-Host 'BROTHER_WINDOWS_DRAWING_CANVAS_VERIFIED'
    }
    Write-Host $contents
    Write-Host 'BROTHER_WINDOWS_UI_SCREENSHOT_VERIFIED'
} finally {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
