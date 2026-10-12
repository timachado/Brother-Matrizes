#!/usr/bin/env bash
# QA somente para APKs DEBUG construidos no mesmo runner.
# Verifica upgrade in-place 1.0.6 -> 1.0.7 com dados persistidos.
# NAO substitui o teste de certificados comerciais nem instala em celulares reais.
set -euo pipefail

OLD_APK="${1:?Informe o APK debug da 1.0.6}"
NEW_APK="${2:?Informe o APK debug da 1.0.7}"
PKG="com.timachado.brothermatrizes"
ACTIVITY="${PKG}/.MainActivity"
AAPT="${ANDROID_HOME:?ANDROID_HOME ausente}/build-tools/36.0.0/aapt"
SENTINEL="files/brother_qa_upgrade_sentinel"

test -s "$OLD_APK"
test -s "$NEW_APK"
test -x "$AAPT"

package_line() {
  "$AAPT" dump badging "$1" | sed -n '1p'
}
OLD_LINE="$(package_line "$OLD_APK")"
NEW_LINE="$(package_line "$NEW_APK")"
[[ "$OLD_LINE" == *"name='$PKG'"* && "$OLD_LINE" == *"versionCode='172'"* && "$OLD_LINE" == *"versionName='1.0.6'"* ]] || {
  echo "Falha: APK base nao e Brother Matrizes 1.0.6"; exit 1;
}
[[ "$NEW_LINE" == *"name='$PKG'"* && "$NEW_LINE" == *"versionCode='173'"* && "$NEW_LINE" == *"versionName='1.0.7'"* ]] || {
  echo "Falha: APK candidato nao e Brother Matrizes 1.0.7"; exit 1;
}

assert_running() {
  local tries
  for tries in {1..15}; do
    if adb shell pidof "$PKG" | grep -Eq '[0-9]'; then return 0; fi
    sleep 2
  done
  echo "Falha: aplicativo encerrou ou nao iniciou apos abertura"
  exit 1
}

adb wait-for-device
echo "Instalando 1.0.6 debug no emulador descartavel"
adb install -r "$OLD_APK"
adb shell am start -W -n "$ACTIVITY" > /dev/null
assert_running

# Arquivo de controle dentro do sandbox do aplicativo. Nada do usuario e acessado.
adb shell run-as "$PKG" mkdir -p files
adb shell run-as "$PKG" touch "$SENTINEL"
adb shell run-as "$PKG" ls "$SENTINEL" > /dev/null

echo "Atualizando in-place para 1.0.7 debug, sem uninstall ou clear"
adb install -r "$NEW_APK"
adb shell run-as "$PKG" ls "$SENTINEL" > /dev/null || {
  echo "Falha: dados internos foram perdidos na atualizacao"; exit 1;
}

adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY" > /dev/null
assert_running

echo "Verificando abertura da Central de Atualizacoes apos upgrade"
adb shell am start -W -a com.timachado.brothermatrizes.OPEN_UPDATES -n "$ACTIVITY" > /dev/null
assert_running
adb shell run-as "$PKG" ls "$SENTINEL" > /dev/null

INSTALLED_INFO="$(adb shell dumpsys package "$PKG")"
if [[ "$INSTALLED_INFO" != *"versionCode=173"* ]]; then
  echo "Falha: versao instalada nao e 173"; exit 1
fi

echo "QA DEBUG aprovado: upgrade 172->173, dados internos mantidos, inicializacao e Central acessiveis."
echo "Atenção: teste de APKs comerciais assinados em dispositivo fisico ainda e obrigatorio."
