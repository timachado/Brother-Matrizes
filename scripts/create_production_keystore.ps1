param(
    [string]$OutputDir = (Join-Path $HOME "Brother-Matrizes-Production-Key")
)

$ErrorActionPreference = "Stop"
$Alias = "brother-matrizes-production"
$Keystore = Join-Path $OutputDir "brother-matrizes-production.jks"
$Base64File = Join-Path $OutputDir "brother-matrizes-production.base64.txt"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "Java keytool não encontrado. Instale um JDK 17+ e tente novamente."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

if (Test-Path $Keystore) {
    throw "A keystore já existe em '$Keystore'. Nada foi sobrescrito."
}

Write-Host "A chave será criada FORA do repositório:"
Write-Host "  $Keystore"
Write-Host ""
Write-Host "O keytool pedirá as senhas de forma interativa."
Write-Host "Guarde essas senhas em um gerenciador seguro."
Write-Host ""

$keytoolArgs = @(
    "-genkeypair",
    "-v",
    "-keystore", $Keystore,
    "-alias", $Alias,
    "-keyalg", "RSA",
    "-keysize", "4096",
    "-validity", "10000"
)

& keytool @keytoolArgs

if ($LASTEXITCODE -ne 0) {
    throw "O keytool não conseguiu criar a keystore."
}

Write-Host ""
Write-Host "Certificado criado. Dados do certificado:"

$listArgs = @(
    "-list",
    "-v",
    "-keystore", $Keystore,
    "-alias", $Alias
)

& keytool @listArgs

if ($LASTEXITCODE -ne 0) {
    throw "Não foi possível verificar a keystore criada."
}

$Bytes = [IO.File]::ReadAllBytes($Keystore)
[Convert]::ToBase64String($Bytes) | Set-Content -NoNewline -Encoding ascii $Base64File

Write-Host ""
Write-Host "Cópia Base64 criada em:"
Write-Host "  $Base64File"
Write-Host "Use-a apenas para cadastrar BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64 no GitHub."
Write-Host "Depois de cadastrar o secret, mantenha-a protegida ou apague-a."
Write-Host ""
Write-Host "Alias:"
Write-Host "  $Alias"
Write-Host ""
Write-Host "IMPORTANTE:"
Write-Host "- faça duas cópias seguras da keystore;"
Write-Host "- não envie a keystore por chat/e-mail;"
Write-Host "- não coloque a keystore dentro do projeto Brother Matrizes;"
Write-Host "- perder essa chave pode impedir atualizações compatíveis futuras."
