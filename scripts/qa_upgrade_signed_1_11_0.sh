#!/usr/bin/env bash
# Ensaio real de atualizacao 1.10.10 -> 1.11.0 em Android emulado.
# Nao envia notificacoes nem publica nada.
set -euo pipefail

baseline="baseline/Brother-Matrizes-1.10.10-OFICIAL-COMERCIAL-ASSINADO.apk"
candidate="dist/Brother-Matrizes-1.11.0-OFICIAL-COMERCIAL-ASSINADO.apk"
test -s "$baseline" && test -s "$candidate"

adb wait-for-device
adb install -r "$baseline"
adb shell pm path com.timachado.brothermatrizes | grep -F 'package:'
adb shell am force-stop com.timachado.brothermatrizes || true

# Esta instalacao -r so tem sucesso se a identidade de pacote e a assinatura
# forem compativeis. Nao desinstala nem limpa dados do aplicativo anterior.
adb install -r "$candidate"
info="$(adb shell dumpsys package com.timachado.brothermatrizes)"
[[ "$info" == *"versionCode=187"* ]] || {
  echo "ERRO: Android nao reconheceu versionCode 187 apos upgrade"
  exit 1
}
adb shell am start -W -n com.timachado.brothermatrizes/.MainActivity
sleep 6
adb shell pidof com.timachado.brothermatrizes >/dev/null || {
  echo "ERRO: processo do Brother Matrizes nao permaneceu aberto"
  adb logcat -d -s AndroidRuntime:E | tail -80
  exit 1
}
echo "UPGRADE Android 1.10.10 -> 1.11.0 aprovado sem desinstalacao."
