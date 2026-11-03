#!/usr/bin/env bash
# QA protegida: update in-place do APK comercial 1.11.7/194 para a candidata assinada 1.11.8/195.
# Android-emulator-runner deve chamar este arquivo com um único Bash.
set -euo pipefail

package="com.timachado.brothermatrizes"
old_apk="baseline/Brother-Matrizes-1.11.7-OFICIAL-COMERCIAL-ASSINADO.apk"
new_apk="dist/Brother-Matrizes-1.11.8-CANDIDATE-ASSINADO-NAO-PUBLICADO.apk"

first_install_time() {
  adb shell dumpsys package "$package" |
    sed -n 's/.*firstInstallTime=//p' |
    sed -n '1p' | tr -d '\r'
}

installed_version() {
  adb shell dumpsys package "$package" |
    grep -F "versionCode=$1"
}

adb wait-for-device
test -s "$old_apk"
test -s "$new_apk"
adb install -r "$old_apk"
installed_version 194
before="$(first_install_time)"
test -n "$before" || { echo "::error::firstInstallTime ausente na 1.11.7"; exit 1; }

# Sem uninstall, sem pm clear e sem reconfigurar dados do usuario.
adb install -r "$new_apk"
installed_version 195
after="$(first_install_time)"
test -n "$after" && test "$before" = "$after" || {
  echo "::error::Atualizacao 194->195 nao preservou firstInstallTime"; exit 1;
}
adb shell am start -W -n "$package/.MainActivity"
sleep 8
adb shell pidof "$package"
echo "QA Android 35: upgrade 194->195 assinado, pacote mantido e inicializacao OK."
