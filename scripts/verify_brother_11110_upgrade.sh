#!/usr/bin/env bash
# QA comercial: atualizar 1.11.9/196 para 1.11.10/197 sem reinstalacao destrutiva.
set -euo pipefail
package="com.timachado.brothermatrizes"
old="baseline/Brother-Matrizes-1.11.9-OFICIAL-COMERCIAL-ASSINADO.apk"
new="dist/Brother-Matrizes-1.11.10-OFICIAL-COMERCIAL-ASSINADO.apk"

first_install_time() {
  adb shell dumpsys package "$package" |
    sed -n 's/.*firstInstallTime=//p' |
    sed -n '1p' | tr -d '\r'
}

adb wait-for-device
test -s "$old" && test -s "$new"
adb install -r "$old"
adb shell dumpsys package "$package" | grep -Eq 'versionCode=196([[:space:]]|$)'
before="$(first_install_time)"
test -n "$before" || { echo "::error::Faltou firstInstallTime no APK original"; exit 1; }

# Upgrade no mesmo package/certificado. Nunca usar pm clear ou uninstall.
adb install -r "$new"
adb shell dumpsys package "$package" | grep -Eq 'versionCode=197([[:space:]]|$)'
after="$(first_install_time)"
test "$before" = "$after" || {
  echo "::error::Instalacao substituida em vez de atualizar no mesmo lugar"; exit 1;
}
adb shell am start -W -n "$package/.MainActivity"
sleep 8
adb shell pidof "$package"
echo "QA OK: upgrade assinado 196 -> 197, pacote e firstInstallTime mantidos, app iniciou."
