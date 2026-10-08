#!/usr/bin/env bash
set -euo pipefail
APK="$1"
PKG="br.com.timachado.pitchstudio.stems"
OUT="$RUNNER_TEMP/pitchstudio-vocals-smoke"
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
  for overlay_attempt in 1 2 3; do
    adb shell uiautomator dump /sdcard/view.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/view.xml > "$OUT/current.xml" || true
    if grep -qiE 'Quickstep (isn.t responding|keeps stopping)|System UI isn.t responding' "$OUT/current.xml"; then
      echo "Aviso de ANR do launcher do EMULADOR; fechando apenas a janela do sistema."
      adb shell input tap 530 1180 || true
      sleep 2
      adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity" >/dev/null 2>&1 || true
      sleep 2
      continue
    fi
    break
  done
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
visible "Comparar voz e música"
visible "Analisar trecho selecionado"
visible "Isolar voz com IA"
tap "Isolar voz com IA"
visible "Importe uma música"
tap "Analisar trecho selecionado"
visible "Importe uma música"
# A permissão fica reservada ao uso do microfone. Negar não pode encerrar a tela.
tap "Analisar minha voz"
# No emulador a captura termina sozinha após 12 segundos. Não exigir que
# "Concluir análise" ainda esteja visível depois da coleta.
sleep 14
visible "Analisar novamente"
# Após a introdução do seletor, os botões ficam abaixo da dobra.
# tap() localiza o controle e rola a tela antes de tocá-lo.
tap "Mais grave"
sleep 2
visible "PitchStudio"
# A indicação de semitons fica fora da primeira dobra do editor Expressive.
for attempt in 1 2 3 4 5 6 7; do
  readui
  if grep -q -- '-2 semitons' "$OUT/current.xml"; then
    echo "PASSOU: a seleção -2 semitons voltou ao editor."
    break
  fi
  if [ "$attempt" = 7 ]; then
    echo "ERRO: alteração de -2 semitons não localizada no editor" >&2
    exit 1
  fi
  adb shell input swipe 500 1500 500 500 280
  sleep 1
done
echo "PASSOU: retorno do Tom Ideal ao editor."
for back_to_top in 1 2 3 4 5 6; do
  adb shell input swipe 500 450 500 1550 230
done
sleep 1
tap "YouTube"
visible "EXPLORAR MÚSICAS"
echo "PASSOU: fluxo YouTube permanece funcional."
# Teste da inferência real com arquivo PCM interno de 7s, sem YouTube ou rede.
echo "Preparando faixa sintética estéreo para inferência ONNX…"
python3 - "$OUT" <<'PY'
from pathlib import Path
import json, math, struct, sys, time
dir=Path(sys.argv[1]); rate=44100; duration=7; frames=rate*duration
notes=(261.63,293.66,329.63,392.00)
with (dir/"stems_test.f32").open("wb") as out:
    for i in range(frames):
        freq=notes[min(3,int(i/rate)%4)]
        lead=0.35*math.sin(2*math.pi*freq*i/rate)
        bass=0.13*math.sin(2*math.pi*110*i/rate)
        mixed=float(lead+bass)
        out.write(struct.pack('<ff',mixed,mixed))
meta={
"schema":1,"title":"Teste IA Musical","updatedAt":int(time.time()*1000),
"sampleRate":rate,"channels":2,"frames":frames,"peak":0.48,
"waveform":[0.48]*90,"semitones":0,"cents":0,"speed":1.0,
"quality":"ALTA","position":0.0
}
(dir/"stems_test.json").write_text(json.dumps(meta),encoding="utf-8")
PY
ID="223e4567-e89b-12d3-a456-426614174111"
adb push "$OUT/stems_test.f32" /data/local/tmp/pitchstudio-vocal-test.f32 >/dev/null
adb push "$OUT/stems_test.json" /data/local/tmp/pitchstudio-vocal-test.json >/dev/null
adb shell run-as "$PKG" mkdir -p "files/saved_audio_projects/$ID"
adb shell run-as "$PKG" cp /data/local/tmp/pitchstudio-vocal-test.f32 "files/saved_audio_projects/$ID/original.f32"
adb shell run-as "$PKG" cp /data/local/tmp/pitchstudio-vocal-test.json "files/saved_audio_projects/$ID/project.json"

adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity"
sleep 4
tap "Meus projetos"
visible "Teste IA Musical"
tap "Teste IA Musical"
tap "Abrir e continuar"
sleep 6
tap "Tom Ideal"
visible "Isolar voz com IA"
adb logcat -c || true
tap "Isolar voz com IA"
echo "Executando separação neural com arquivo real de modelo no Android…"
SUCCESS=0
for attempt in $(seq 1 40); do
  readui
  if grep -qE 'Voz estimada por separação neural|A IA separou a faixa vocal' "$OUT/current.xml"; then
    echo "PASSOU: modelo ONNX executado e arquivo vocal temporário processado."
    SUCCESS=1
    break
  fi
  if grep -q 'Separação vocal indisponível' "$OUT/current.xml"; then
    echo "FALHA: erro ao executar o modelo neural." >&2
    grep -o 'Separação vocal indisponível[^<]*' "$OUT/current.xml" | head -c 800 || true
    exit 1
  fi
  sleep 3
done
if [ "$SUCCESS" -ne 1 ]; then
  echo "FALHA: tempo de inferência excedido, ou resultado inacessível." >&2
  exit 1
fi
echo "PASSOU: teste de inferência do modelo ONNX em emulador Android."

if adb logcat -d -b crash -t 1500 | grep -E 'FATAL EXCEPTION|Process: br.com.timachado.pitchstudio.stems'; then
 echo "FALHA: crash identificado no logcat" >&2
 exit 1
fi
