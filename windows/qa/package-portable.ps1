# Cria e testa uma distribuicao PORTATIL com runtime Java incluido.
# Nao instala, nao escreve no registro e nao acessa arquivos de clientes.
# Uso exclusivo no runner de QA, a pasta distribuida e temporaria.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'Empacotamento portatil permitido somente no runner de QA.'
}
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$base = Join-Path $project 'build\compose\binaries\main\app'
$launchers = @(Get-ChildItem -LiteralPath $base -Recurse -File -Filter 'Brother Matrizes.exe')
if ($launchers.Count -ne 1) {
    throw "App-image deve conter um unico Brother Matrizes.exe; encontrados $($launchers.Count)."
}
$sourceFolder = $launchers[0].Directory.FullName
if (!(Test-Path -LiteralPath (Join-Path $sourceFolder 'runtime') -PathType Container) -or
    !(Test-Path -LiteralPath (Join-Path $sourceFolder 'app') -PathType Container)) {
    throw 'Runtime e bibliotecas exigidos para uso portatil nao encontrados.'
}
$sourceFiles = @(Get-ChildItem -LiteralPath $sourceFolder -Recurse -Force)
if (@($sourceFiles | Where-Object {
    ($_.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0
}).Count -ne 0) {
    throw 'App-image contem links simbolicos; pacote portatil recusado.'
}
$destination = Join-Path $project 'build\qa\portable'
if (Test-Path -LiteralPath $destination) {
    throw 'Diretorio de saida portatil ja existe; recusa sobrescrita.'
}
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$staging = Join-Path $env:RUNNER_TEMP 'bm-portable-stage'
$testDir = Join-Path $env:RUNNER_TEMP 'bm-portable-test'
if ((Test-Path -LiteralPath $staging) -or
    (Test-Path -LiteralPath $testDir)) {
    throw 'Diretorio de trabalho portatil preexistente; recusa sobrescrita.'
}
try {
    $application = Join-Path $staging 'Brother Matrizes'
    New-Item -ItemType Directory -Path $application -Force | Out-Null
    foreach ($item in @(Get-ChildItem -LiteralPath $sourceFolder -Force)) {
        Copy-Item -LiteralPath $item.FullName -Destination $application -Recurse -Force -ErrorAction Stop
    }
    $readme = Join-Path $staging 'LEIA-ME-PORTATIL.txt'
    [System.IO.File]::WriteAllText($readme, @'
BROTHER MATRIZES - WINDOWS PORTATIL 0.1.0 (QA)
T.I. Machado Solucoes em Tecnologia

1. Extraia TODO este ZIP para uma pasta local confiavel.
2. Abra a pasta "Brother Matrizes".
3. Execute "Brother Matrizes.exe".
4. NAO mova somente o EXE; a subpasta runtime e a subpasta app sao obrigatorias.
5. Esta copia nao precisa de instalador nem privilegios administrativos de instalacao.
6. Nao e uma versao comercial, nao possui assinatura digital e nao usa licenciamento.
7. Nao desative SmartScreen, antivirus ou controles do Windows para executa-la.
8. Nao use para projetos comerciais nem matrizes de clientes.
9. Caso o Windows bloqueie o programa, registre a mensagem e contate a T.I. Machado.

IMPORTANTE: use somente em um computador de testes autorizado.
'@, [System.Text.Encoding]::UTF8)
    $archive = Join-Path $destination 'Brother-Matrizes-Windows-Portable-0.1.0-QA.zip'
    Compress-Archive -LiteralPath @($application, $readme) -DestinationPath $archive -CompressionLevel Optimal -ErrorAction Stop
    if ((Get-Item -LiteralPath $archive).Length -lt 2000000L) {
        throw 'ZIP portatil anormalmente pequeno: runtime ausente.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $testDir -ErrorAction Stop
    $exe = Join-Path $testDir 'Brother Matrizes\Brother Matrizes.exe'
    if (!(Test-Path -LiteralPath $exe -PathType Leaf)) {
        throw 'Launcher portatil nao existe depois da extracao.'
    }
    $report = Join-Path $env:RUNNER_TEMP 'brother-portable-qa-smoke.txt'
    if (Test-Path -LiteralPath $report) {
        throw 'Relatorio de QA portatil preexistente.'
    }
    $args = '--smoke-test "' + $report + '"'
    $process = Start-Process -FilePath $exe -ArgumentList $args -WorkingDirectory (Split-Path $exe -Parent) -PassThru
    if (-not $process.WaitForExit(90000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw 'Launcher portatil nao concluiu smoke em 90 segundos.'
    }
    $process.Refresh()
    if ($process.ExitCode -ne 0 -or
        !(Test-Path -LiteralPath $report -PathType Leaf)) {
        throw "Falha ao abrir launcher portatil extraido (codigo $($process.ExitCode))."
    }
    $text = Get-Content -LiteralPath $report -Raw -Encoding UTF8
    if (!$text.StartsWith('BROTHER_WINDOWS_QA_SMOKE_OK')) {
        throw 'Relatorio do launcher portatil invalido.'
    }
    $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Value (
        $hash + '  ' + (Split-Path $archive -Leaf)
    ) -Encoding ASCII
    Write-Host "BROTHER_WINDOWS_PORTABLE_LAUNCH_OK"
    Write-Host "BROTHER_WINDOWS_PORTABLE_SHA256=$hash"
} finally {
    if (Test-Path -LiteralPath $staging) {
        Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $testDir) {
        Remove-Item -LiteralPath $testDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
