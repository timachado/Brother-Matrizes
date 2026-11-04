# Teste deterministico de manifesto SHA256 com pacotes EXE/MSI ficticios.
# Nunca acessa os instaladores oficiais ou arquivos de clientes.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    throw 'Teste exige RUNNER_TEMP de uma VM efemera.'
}
$fixture = Join-Path $env:RUNNER_TEMP ("brother-windows-hash-fixture-" + [guid]::NewGuid().ToString("N"))
$exe = Join-Path $fixture 'exe\fake-installer.exe'
$msi = Join-Path $fixture 'msi\fake-installer.msi'
$validator = Join-Path $PSScriptRoot 'checksum-installers.ps1'
try {
    [System.IO.Directory]::CreateDirectory((Split-Path $exe -Parent)) | Out-Null
    [System.IO.Directory]::CreateDirectory((Split-Path $msi -Parent)) | Out-Null
    [System.IO.File]::WriteAllBytes($exe, [byte[]]@(1, 2, 3, 4, 5))
    [System.IO.File]::WriteAllBytes($msi, [byte[]]@(6, 7, 8, 9))
    & $validator -Mode Create -Root $fixture -SourceCommit 'synthetic-test-commit' -RunId 'synthetic-test-run'
    & $validator -Mode Verify -Root $fixture -SourceCommit 'synthetic-test-commit' -RunId 'synthetic-test-run'

    # Mesmo tamanho, dados diferentes: so o hash detecta substituicao.
    [System.IO.File]::WriteAllBytes($exe, [byte[]]@(1, 2, 3, 4, 99))
    $rejected = $false
    try {
        & $validator -Mode Verify -Root $fixture -SourceCommit 'synthetic-test-commit' -RunId 'synthetic-test-run'
    } catch {
        $rejected = $true
        Write-Host 'Alteracao de bytes detectada conforme esperado.'
    }
    if (!$rejected) {
        throw 'Falha: pacote alterado deveria ser rejeitado pelo SHA256.'
    }
    Write-Host 'BROTHER_WINDOWS_QA_TAMPER_DETECTED'
} finally {
    if (Test-Path -LiteralPath $fixture) {
        Remove-Item -LiteralPath $fixture -Recurse -Force
    }
}
