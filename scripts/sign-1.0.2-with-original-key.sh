#!/usr/bin/env bash
# Assinatura comercial Brother Matrizes 1.0.2.
# Requer EXCLUSIVAMENTE a chave original usada na versão 1.0.1.
# Não executa automaticamente nas builds de PR.
set -euo pipefail
umask 077

test -n "$ANDROID_HOME" || { echo "ANDROID_HOME ausente"; exit 1; }
test -n "$BROTHER_RELEASE_KEYSTORE_BASE64" || { echo "Keystore original ausente"; exit 1; }
test -n "$BROTHER_RELEASE_STORE_PASSWORD" || { echo "Senha do keystore ausente"; exit 1; }
test -n "$BROTHER_RELEASE_KEY_ALIAS" || { echo "Alias ausente"; exit 1; }
test -n "$BROTHER_RELEASE_KEY_PASSWORD" || { echo "Senha da chave ausente"; exit 1; }

TOOLS="$ANDROID_HOME/build-tools/36.0.0"
UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
OUTPUT_DIR="app/build/outputs/apk/brother-release"
SIGNED="$OUTPUT_DIR/Brother-Matrizes-v1.0.2-ESTAVEL-ASSINADO.apk"
EXPECTED_CERT_SHA256="fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a"

test -s "$UNSIGNED"
mkdir -p "$OUTPUT_DIR"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
printf '%s' "$BROTHER_RELEASE_KEYSTORE_BASE64" | base64 --decode > "$WORK_DIR/release.jks"
"$TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$WORK_DIR/aligned.apk"

"$TOOLS/apksigner" sign \
  --ks "$WORK_DIR/release.jks" \
  --ks-pass env:BROTHER_RELEASE_STORE_PASSWORD \
  --ks-key-alias "$BROTHER_RELEASE_KEY_ALIAS" \
  --key-pass env:BROTHER_RELEASE_KEY_PASSWORD \
  --out "$SIGNED" "$WORK_DIR/aligned.apk"

VERIFY_OUTPUT="$("$TOOLS/apksigner" verify --verbose --print-certs "$SIGNED")"
printf '%s\n' "$VERIFY_OUTPUT" | grep -F "Verified using v2 scheme (APK Signature Scheme v2): true" >/dev/null
REAL_CERT="$(printf '%s\n' "$VERIFY_OUTPUT" \
  | sed -n -E 's/^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})$/\1/p' \
  | head -n 1 | tr '[:upper:]' '[:lower:]')"
if [[ "$REAL_CERT" != "$EXPECTED_CERT_SHA256" ]]; then
  echo "ERRO: certificado novo não coincide com o APK estável 1.0.1."
  rm -f "$SIGNED"
  exit 1
fi
INFO="$("$TOOLS/aapt" dump badging "$SIGNED" | head -n 1)"
[[ "$INFO" == *"name='com.timachado.brothermatrizes'"* ]]
[[ "$INFO" == *"versionCode='168'"* ]]
[[ "$INFO" == *"versionName='1.0.2'"* ]]
echo "APK comercial assinado com o certificado original."
sha256sum "$SIGNED" | tee "$OUTPUT_DIR/Brother-Matrizes-v1.0.2-ESTAVEL-ASSINADO.sha256"
