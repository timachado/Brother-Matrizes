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
    & $validator -Mode Create -Root $fixture -SourceCommit ('1' * 40) -RunId '12345'
    & $validator -Mode Verify -Root $fixture -SourceCommit ('1' * 40) -RunId '12345'

    # O mesmo validador usado na beta deve funcionar offline no Windows.
    & (Join-Path $PSScriptRoot 'verify-beta-package.ps1') -PackageRoot $fixture -ExpectedCommit ('1' * 40) -ExpectedRunId '12345'
    $wrongCommitRejected = $false
    try {
        & (Join-Path $PSScriptRoot 'verify-beta-package.ps1') -PackageRoot $fixture -ExpectedCommit ('2' * 40) -ExpectedRunId '12345'
    } catch {
        $wrongCommitRejected = $true
    }
    if (-not $wrongCommitRejected) {
        throw 'Verificador beta aceitou commit diferente do autorizado.'
    }
    $wrongRunRejected = $false
    try {
        & (Join-Path $PSScriptRoot 'verify-beta-package.ps1') -PackageRoot $fixture -ExpectedCommit ('1' * 40) -ExpectedRunId '67890'
    } catch {
        $wrongRunRejected = $true
    }
    if (-not $wrongRunRejected) {
        throw 'Verificador beta aceitou workflow diferente do autorizado.'
    }

    # Mesmo tamanho, dados diferentes: so o hash detecta substituicao.
    [System.IO.File]::WriteAllBytes($exe, [byte[]]@(1, 2, 3, 4, 99))
    $rejected = $false
    try {
        & $validator -Mode Verify -Root $fixture -SourceCommit ('1' * 40) -RunId '12345'
    } catch {
        $rejected = $true
        Write-Host 'Alteracao de bytes detectada conforme esperado.'
    }
    if (!$rejected) {
        throw 'Falha: pacote alterado deveria ser rejeitado pelo SHA256.'
    }
    $betaRejected = $false
    try {
        & (Join-Path $PSScriptRoot 'verify-beta-package.ps1') -PackageRoot $fixture -ExpectedCommit ('1' * 40) -ExpectedRunId '12345'
    } catch {
        $betaRejected = $true
    }
    if (!$betaRejected) {
        throw 'Falha: verificador beta aceitou EXE modificado.'
    }
    Write-Host 'BROTHER_WINDOWS_BETA_VERIFIER_TAMPER_REJECTED'
    Write-Host 'BROTHER_WINDOWS_QA_TAMPER_DETECTED'
} finally {
    if (Test-Path -LiteralPath $fixture) {
        Remove-Item -LiteralPath $fixture -Recurse -Force
    }
}
