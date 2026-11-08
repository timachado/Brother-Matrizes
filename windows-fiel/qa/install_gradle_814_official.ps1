# Brother Matrizes Windows: Gradle 8.14 toolchain bootstrap (CI only).
# Avoids intermittent GitHub Actions setup-gradle "Provision Gradle" HTTP 504.
# Official ZIP is SHA-256 pinned to gradle.org/release-checksums (8.14 -bin).
# Never invokes code from an unverified download.
$ErrorActionPreference = 'Stop'
$version = '8.14'
$sha256 = '61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa'
$archive = Join-Path $env:RUNNER_TEMP "brother-gradle-$version-bin.zip"
$root = Join-Path $env:RUNNER_TEMP 'brother-verified-gradle'
$exe = Join-Path $root "gradle-$version/bin/gradle.bat"
$urls = @(
    "https://downloads.gradle.org/distributions/gradle-$version-bin.zip",
    "https://services.gradle.org/distributions/gradle-$version-bin.zip"
)
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    $verified = $false
    foreach ($url in $urls) {
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            try {
                if (Test-Path -LiteralPath $archive) {
                    Remove-Item -LiteralPath $archive -Force
                }
                Write-Host "Downloading official Gradle $version, mirror attempt $attempt"
                Invoke-WebRequest -Uri $url -OutFile $archive -TimeoutSec 240 -MaximumRetryCount 1 -RetryIntervalSec 5
                $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($actual -ne $sha256) {
                    throw "Downloaded Gradle checksum mismatch; binary rejected."
                }
                $verified = $true
                break
            } catch {
                Write-Warning ("Gradle download failed, retrying if possible: " + $_.Exception.Message)
            }
        }
        if ($verified) { break }
    }
    if (-not $verified) {
        throw "Cannot acquire SHA-256 verified Gradle $version from official distributors."
    }
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $root -Force
}
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw "Verified Gradle archive did not contain gradle.bat."
}
$bin = Split-Path -Parent $exe
Add-Content -LiteralPath $env:GITHUB_PATH -Value $bin
& $exe --version
if ($LASTEXITCODE -ne 0) { throw "Gradle $version failed its bootstrap check." }
Write-Host 'BROTHER_WINDOWS_VERIFIED_GRADLE_8_14_READY'
