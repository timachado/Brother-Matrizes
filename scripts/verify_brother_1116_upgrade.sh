#!/usr/bin/env bash
# Teste de upgrade comercial 1.11.5 (192) -> 1.11.6 (193).
# Executar como UM processo bash: android-emulator-runner executa cada linha
# do campo 'script' em um novo shell, perdendo variaveis entre comandos.
set -euo pipefail

package="com.timachado.brothermatrizes"
old_apk="baseline/Brother-Matrizes-1.11.5-OFICIAL-COMERCIAL-ASSINADO.apk"
new_apk="dist/Brother-Matrizes-1.11.6-OFICIAL-COMERCIAL-ASSINADO.apk"

first_install_time() {
  adb shell dumpsys package "$package" |
    sed -n 's/.*firstInstallTime=//p' |
    sed -n '1p' |
    tr -d '\r'
}

assert_version() {
  local expected="$1"
  adb shell dumpsys package "$package" | grep -F "versionCode=$expected"
}

adb wait-for-device
test -s "$old_apk"
test -s "$new_apk"

echo "Instalando baseline comercial 1.11.5 (192)"
adb install -r "$old_apk"
assert_version 192
first_install="$(first_install_time)"
if [[ -z "$first_install" ]]; then
  echo "::error::firstInstallTime ausente apos instalar baseline 1.11.5; nao e possivel comprovar upgrade sem reinstalacao."
  exit 1
fi

echo "Atualizando por cima para 1.11.6 (193), sem remover dados"
adb install -r "$new_apk"
assert_version 193
after_install="$(first_install_time)"
if [[ -z "$after_install" || "$first_install" != "$after_install" ]]; then
  echo "::error::firstInstallTime alterado ou ausente apos upgrade; atualizacao nao homologada."
  exit 1
fi

adb shell am start -W -n "$package/.MainActivity"
sleep 8
adb shell pidof "$package"

echo "Upgrade instalado 192 -> 193 aprovado, mesma primeira instalacao e app em execucao."
