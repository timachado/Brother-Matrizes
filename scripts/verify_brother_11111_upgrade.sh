#!/usr/bin/env bash
# QA comercial: validar upgrade assinado 1.11.10/197 -> 1.11.11/198.
# Emulador descartável. Não usar adb uninstall, pm clear, nem limpar dados.
set -euo pipefail

package="com.timachado.brothermatrizes"
old="baseline/Brother-Matrizes-1.11.10-OFICIAL-COMERCIAL-ASSINADO.apk"
new="dist/Brother-Matrizes-1.11.11-OFICIAL-COMERCIAL-ASSINADO.apk"

first_install_time() {
  adb shell dumpsys package "$package" |
    sed -n 's/.*firstInstallTime=//p' |
    sed -n '1p' | tr -d '\r'
}
check_install() {
  local code="$1"
  adb shell dumpsys package "$package" | grep -Eq "versionCode=\${code}([[:space:]]|$)"
  adb shell pm path "$package" | grep -q '^package:'
}
start_app() {
  adb shell am start -W -n "$package/.MainActivity" > /tmp/brother-launch.log
  sleep 5
  adb shell pidof "$package"
}

adb wait-for-device
test -s "$old" && test -s "$new"

echo "Instalando a versão oficial 1.11.10 como baseline..."
adb install -r "$old"
check_install 197
start_app
before="$(first_install_time)"
test -n "$before" || { echo "::error::firstInstallTime não encontrado"; exit 1; }

echo "Atualizando em cima da instalação oficial sem remover dados..."
adb install -r "$new"
check_install 198
after="$(first_install_time)"
test "$before" = "$after" || {
  echo "::error::A instalação original foi substituída em vez de atualizada"
  exit 1
}
start_app
echo "PASSOU: upgrade comercial 197 -> 198, pacote e firstInstallTime preservados."
