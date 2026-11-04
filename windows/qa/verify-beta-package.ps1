# Brother Matrizes Desktop — verificador OFFLINE para beta interna Windows.
# Compatível com Windows PowerShell 5.1 e PowerShell 7.
# Nunca instala nem executa EXE/MSI, não acessa internet, não coleta dados.
[CmdletBinding()]
param(
    [string]$PackageRoot = '',
    [string]$ExpectedCommit = '',
    [string]$ExpectedRunId = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($PackageRoot)) {
    $PackageRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
}
$PackageRoot = [System.IO.Path]::GetFullPath($PackageRoot)
$manifestPath = Join-Path $PackageRoot 'qa\installer-manifest.json'
$listPath = Join-Path $PackageRoot 'qa\SHA256SUMS.txt'
if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf) -or
    !(Test-Path -LiteralPath $listPath -PathType Leaf)) {
    throw 'Manifesto e SHA256SUMS.txt obrigatórios. Extraia o pacote completo.'
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or
    $manifest.purpose -cne 'WINDOWS_QA_NOT_COMMERCIAL' -or
    $manifest.signingStatus -cne 'UNSIGNED_QA') {
    throw 'Metadados de QA inválidos; pacote recusado.'
}
$commit = [string]$manifest.sourceCommit
$runId = [string]$manifest.workflowRunId
if ($commit -cnotmatch '^[0-9a-fA-F]{40}$' -or $runId -cnotmatch '^[0-9]+$') {
    throw 'Identificadores de origem ausentes ou inválidos.'
}
if (![string]::IsNullOrWhiteSpace($ExpectedCommit) -and $commit -cne $ExpectedCommit) {
    throw 'SHA do commit do pacote difere do commit de origem autorizado.'
}
if (![string]::IsNullOrWhiteSpace($ExpectedRunId) -and $runId -cne $ExpectedRunId) {
    throw 'ID de workflow não corresponde à execução de origem autorizada.'
}

$installers = @($manifest.installers)
if ($installers.Count -ne 2) {
    throw 'Manifesto deve possuir exatamente dois instaladores: EXE e MSI.'
}
$expectedLines = New-Object 'System.Collections.Generic.List[string]'
foreach ($kind in @('exe', 'msi')) {
    $matches = @($installers | Where-Object { ([string]$_.relativePath).StartsWith("$kind/", [System.StringComparison]::Ordinal) })
    if ($matches.Count -ne 1) {
        throw "Manifesto deve registrar exatamente um instalador $kind."
    }
    $record = $matches[0]
    $relative = [string]$record.relativePath
    if ($relative -cnotmatch ('^' + $kind + '/[a-zA-Z0-9_.-]+\.' + $kind + '$') -or
        $relative.Contains('..') -or $relative.Contains('\')) {
        throw "Caminho do instalador $kind rejeitado (fora da estrutura autorizada)."
    }
    $file = Join-Path $PackageRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Instalador $kind ausente: $relative."
    }
    $info = Get-Item -LiteralPath $file
    if (($info.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Instalador $kind não pode ser link simbólico."
    }
    $hash = [string]$record.sha256
    if ($hash -cnotmatch '^[0-9a-fA-F]{64}$' -or [long]$record.bytes -ne [long]$info.Length) {
        throw "Tamanho ou hash informado inválido para $kind."
    }
    $actualHash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -cne $hash.ToLowerInvariant()) {
        throw "SHA256 divergente para $kind; não instale este arquivo."
    }
    $expectedLines.Add(($hash + '  ' + $relative))
}
$lines = @(Get-Content -LiteralPath $listPath -Encoding UTF8)
if ($lines.Count -ne 2) {
    throw 'SHA256SUMS.txt deve conter exatamente dois hashes.'
}
for ($i = 0; $i -lt 2; $i++) {
    if ($lines[$i] -cne $expectedLines[$i]) {
        throw 'SHA256SUMS.txt não corresponde ao manifesto verificado.'
    }
}

Write-Host "Origem declarada: commit $commit / workflow $runId"
Write-Host 'BROTHER_WINDOWS_BETA_PACKAGE_SHA256_OK'
Write-Host 'Integridade local verificada. Isto NÃO autentica o autor nem assina o EXE/MSI.'
Write-Host 'Não instale se a origem GitHub e o commit não forem confiáveis.'
