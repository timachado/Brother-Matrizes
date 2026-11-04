# Brother Matrizes Windows QA — manifesto de integridade de instaladores.
# Cria ou verifica hashes SHA-256 do EXE/MSI em arvore LOCAL. Nao assina
# executaveis e nao garante autenticidade fora do repositorio confiavel.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Create', 'Verify')][string]$Mode,
    [string]$Root = '',
    [string]$SourceCommit = $env:GITHUB_SHA,
    [string]$RunId = $env:GITHUB_RUN_ID
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Join-Path $PSScriptRoot '..\build\compose\binaries\main'
}
$Root = [System.IO.Path]::GetFullPath($Root)
$manifestDirectory = Join-Path $Root 'qa'
$manifestFile = Join-Path $manifestDirectory 'installer-manifest.json'
$checksumFile = Join-Path $manifestDirectory 'SHA256SUMS.txt'

function Get-InstallerRecords {
    param([string]$Base)
    $records = @(
        foreach ($type in @('exe', 'msi')) {
            $directory = Join-Path $Base $type
            if (!(Test-Path -LiteralPath $directory -PathType Container)) {
                throw "Pasta de instalador ausente: $type"
            }
            $installers = @(Get-ChildItem -LiteralPath $directory -File -Recurse |
                Where-Object { $_.Extension -ieq ".$type" })
            if ($installers.Count -ne 1) {
                throw "Esperado 1 instalador .$type; encontrados $($installers.Count)."
            }
            $item = $installers[0]
            $inside = [System.IO.Path]::GetRelativePath($directory, $item.FullName)
                .Replace([char]92, [char]47)
            if ($inside -match '(^|/)\.\.(/|$)' -or [System.IO.Path]::IsPathRooted($inside)) {
                throw "Nome de instalador fora da arvore esperada."
            }
            [pscustomobject][ordered]@{
                relativePath = "$type/$inside"
                bytes = [long]$item.Length
                sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            }
        }
    )
    return @($records | Sort-Object relativePath)
}

$actual = @(Get-InstallerRecords -Base $Root)
if ($Mode -eq 'Create') {
    if (Test-Path -LiteralPath $manifestFile -PathType Leaf) {
        throw "Manifesto existente; recusada sobrescrita."
    }
    if (Test-Path -LiteralPath $checksumFile -PathType Leaf) {
        throw "SHA256SUMS existente; recusada sobrescrita."
    }
    [System.IO.Directory]::CreateDirectory($manifestDirectory) | Out-Null
    $manifest = [ordered]@{
        schemaVersion = 1
        purpose = 'WINDOWS_QA_NOT_COMMERCIAL'
        signingStatus = 'UNSIGNED_QA'
        sourceCommit = [string]$SourceCommit
        workflowRunId = [string]$RunId
        installers = $actual
    }
    $json = ConvertTo-Json -InputObject $manifest -Depth 6
    $hashList = ($actual | ForEach-Object { "$($_.sha256)  $($_.relativePath)" }) -join "`n"
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($manifestFile, $json + "`n", $utf8)
    [System.IO.File]::WriteAllText($checksumFile, $hashList + "`n", $utf8)
    Write-Host "BROTHER_WINDOWS_QA_SHA256_MANIFEST_CREATED"
} else {
    if (!(Test-Path -LiteralPath $manifestFile -PathType Leaf) -or
        !(Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
        throw 'Arquivos de manifesto QA ausentes.'
    }
    $manifest = Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.schemaVersion -ne 1 -or
        $manifest.purpose -cne 'WINDOWS_QA_NOT_COMMERCIAL' -or
        $manifest.signingStatus -cne 'UNSIGNED_QA') {
        throw 'Metadados invalidos de manifesto QA.'
    }
    if (![string]::IsNullOrWhiteSpace($SourceCommit) -and
        $manifest.sourceCommit -cne $SourceCommit) {
        throw 'O commit do manifesto nao corresponde ao commit em verificacao.'
    }
    if (![string]::IsNullOrWhiteSpace($RunId) -and
        $manifest.workflowRunId -cne $RunId) {
        throw 'O run ID do manifesto nao corresponde ao CI.'
    }
    $saved = @($manifest.installers)
    if ($saved.Count -ne 2 -or $actual.Count -ne 2) {
        throw 'Manifesto ou arvore de instaladores incompletos.'
    }
    for ($i = 0; $i -lt 2; $i++) {
        $expected = $saved[$i]
        $observed = $actual[$i]
        if ($expected.relativePath -cne $observed.relativePath -or
            [long]$expected.bytes -ne [long]$observed.bytes -or
            [string]$expected.sha256 -cne [string]$observed.sha256) {
            throw "Hash, tamanho ou caminho divergente: $($observed.relativePath)"
        }
    }
    $lines = @(Get-Content -LiteralPath $checksumFile -Encoding UTF8)
    $expectedLines = @($saved | ForEach-Object {
        "$($_.sha256)  $($_.relativePath)"
    })
    if ($lines.Count -ne $expectedLines.Count) {
        throw 'SHA256SUMS com numero incorreto de linhas.'
    }
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -cne $expectedLines[$i]) {
            throw 'SHA256SUMS nao corresponde ao manifesto JSON.'
        }
    }
    Write-Host 'BROTHER_WINDOWS_QA_SHA256_VERIFIED'
}
