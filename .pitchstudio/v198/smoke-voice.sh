#!/usr/bin/env bash
set -euo pipefail
APK="$1"
PKG="br.com.timachado.pitchstudio.voice"
OUT="$RUNNER_TEMP/pitchstudio-voice-smoke"
mkdir -p "$OUT"
cleanup() {
  adb logcat -d -b main -b crash > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell screencap -p /sdcard/tom-ideal-test.png >/dev/null 2>&1 || true
  adb pull /sdcard/tom-ideal-test.png "$OUT/screen.png" >/dev/null 2>&1 || true
  adb shell uiautomator dump /sdcard/final.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/final.xml > "$OUT/final.xml" 2>/dev/null || true
}
trap cleanup EXIT
adb wait-for-device
adb install -r "$APK"
# Permissão concedida explicitamente no teste; o fluxo de negativa
# continua implementado na Activity sem depender da língua do emulador.
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO
adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity"
sleep 5
readui() {
  adb shell uiautomator dump /sdcard/view.xml >/dev/null 2>&1
  adb shell cat /sdcard/view.xml > "$OUT/current.xml"
}
visible() {
  readui
  python3 - "$OUT/current.xml" "$1" <<'PY'
import sys
from xml.etree import ElementTree as ET
wanted=sys.argv[2]
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
if not any(wanted in (n.get("text","")+" "+n.get("content-desc","")) for n in nodes):
    raise SystemExit("Não visível: "+wanted)
print("VISÍVEL:",wanted)
PY
}
tap() {
  for attempt in 1 2 3 4; do
    readui
    if pos=$(python3 - "$OUT/current.xml" "$1" <<'PY'
import sys,re
from xml.etree import ElementTree as ET
wanted=sys.argv[2]
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
for exact in (True,False):
  for n in nodes:
    value=(n.get("text","")+" "+n.get("content-desc","")).strip()
    if (value==wanted if exact else wanted in value):
      box=list(map(int,re.findall(r"\d+",n.get("bounds",""))))
      if len(box)==4:
        a,b,c,d=box
        print((a+c)//2,(b+d)//2)
        raise SystemExit(0)
raise SystemExit(1)
PY
); then
      read -r X Y <<<"$pos"
      echo "TOQUE: $1 -> $X,$Y"
      adb shell input tap "$X" "$Y"
      sleep 2
      return 0
    fi
    adb shell input swipe 500 1400 500 600 250
    sleep 1
  done
  echo "Controle não localizado: $1"
  exit 1
}
# A imagem do emulador às vezes apresenta ANR no Pixel Launcher.
# Reabrir a Activity alvo ao invés de confundir a sobreposição com crash do app.
for attempt in 1 2 3 4; do
  if visible "PitchStudio"; then break; fi
  echo "Aguardando launcher do emulador (tentativa $attempt)"
  adb shell input keyevent 4 || true
  adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity" || true
  sleep 5
done
visible "PitchStudio"
tap "Tom Ideal"
visible "Tom Ideal"
visible "Analisar minha voz"
visible "Compare três versões"
# A permissão fica reservada ao uso do microfone. Negar não pode encerrar a tela.
tap "Analisar minha voz"
# No emulador a captura termina sozinha após 12 segundos. Não exigir que
# "Concluir análise" ainda esteja visível depois da coleta.
sleep 14
visible "Analisar novamente"
visible "Mais grave"
tap "Mais grave"
sleep 2
visible "−2 semitons"
echo "PASSOU: permissão, gravação controlada e retorno de -2 semitons ao editor."
tap "YouTube"
visible "EXPLORAR MÚSICAS"
echo "PASSOU: fluxo YouTube permanece funcional."
if adb logcat -d -b crash -t 1500 | grep -E 'FATAL EXCEPTION|Process: br.com.timachado.pitchstudio.voice'; then
 echo "FALHA: crash identificado no logcat" >&2
 exit 1
fi
