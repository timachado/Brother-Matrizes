#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${1:-$HOME/Brother-Matrizes-Production-Key}"
KEYSTORE="$OUTPUT_DIR/brother-matrizes-production.jks"
BASE64_FILE="$OUTPUT_DIR/brother-matrizes-production.base64.txt"
ALIAS="brother-matrizes-production"

if ! command -v keytool >/dev/null 2>&1; then
  echo "Java keytool não encontrado. Instale um JDK 17+ e tente novamente."
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR" 2>/dev/null || true

if [ -e "$KEYSTORE" ]; then
  echo "A keystore já existe em: $KEYSTORE"
  echo "Nada foi sobrescrito."
  exit 1
fi

echo "A chave será criada FORA do repositório:"
echo "  $KEYSTORE"
echo
echo "O keytool pedirá as senhas de forma interativa."
echo "Guarde essas senhas em um gerenciador seguro."
echo

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

chmod 600 "$KEYSTORE" 2>/dev/null || true

echo
echo "Certificado criado. Fingerprint SHA-256:"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  | grep -E 'SHA256:|SHA-256:' || true

if command -v base64 >/dev/null 2>&1; then
  base64 < "$KEYSTORE" | tr -d '\n' > "$BASE64_FILE"
  chmod 600 "$BASE64_FILE" 2>/dev/null || true
  echo
  echo "Cópia Base64 criada em:"
  echo "  $BASE64_FILE"
  echo "Use-a apenas para cadastrar BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64 no GitHub."
  echo "Depois de cadastrar o secret, mantenha-a protegida ou apague-a."
fi

echo
echo "Alias:"
echo "  $ALIAS"
echo
echo "IMPORTANTE:"
echo "- faça duas cópias seguras da keystore;"
echo "- não envie a keystore por chat/e-mail;"
echo "- não coloque a keystore dentro do projeto Brother Matrizes;"
echo "- perder essa chave pode impedir atualizações compatíveis futuras."
