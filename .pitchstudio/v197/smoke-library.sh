#!/usr/bin/env bash
set -euo pipefail
APK="$1"
OUT="$RUNNER_TEMP/pitchstudio-library-smoke"
mkdir -p "$OUT"
PKG="br.com.timachado.pitchstudio.library"
cleanup() {
  adb logcat -d -b main -b crash > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/library-last.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/library-last.xml > "$OUT/screen.xml" 2>/dev/null || true
}
trap cleanup EXIT
adb wait-for-device
adb install -r "$APK"
adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity"
sleep 5
test_visible() {
  for attempt in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/screen.xml >/dev/null 2>&1
    adb shell cat /sdcard/screen.xml > "$OUT/current.xml"
    if python3 - "$OUT/current.xml" "$1" <<'PY'
import sys
from xml.etree import ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
target=sys.argv[2]
if not any(target in (n.get("text","")+" "+n.get("content-desc","")) for n in nodes):
    raise SystemExit(1)
print("Encontrado:",target)
PY
    then return 0; fi
    adb shell input swipe 525 1450 525 650 300
    sleep 1
  done
  echo "Não encontrou texto: $1" >&2
  return 1
}
tap_text() {
  for attempt in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/screen.xml >/dev/null 2>&1
    adb shell cat /sdcard/screen.xml > "$OUT/current.xml"
    if coords=$(python3 - "$OUT/current.xml" "$1" <<'PY'
import re,sys
from xml.etree import ElementTree as ET
target=sys.argv[2]
for node in ET.parse(sys.argv[1]).getroot().iter("node"):
    label=(node.get("text","")+" "+node.get("content-desc","")).strip()
    if target in label:
        m=re.findall(r"\d+",node.get("bounds",""))
        if len(m)==4:
            a,b,c,d=map(int,m)
            print((a+c)//2,(b+d)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
); then
      read -r X Y <<< "$coords"
      echo "Tocando $1: $X $Y"
      adb shell input tap "$X" "$Y"
      sleep 2
      return 0
    fi
    adb shell input swipe 525 1450 525 650 300
    sleep 1
  done
  echo "Não encontrou botão: $1" >&2
  return 1
}
test_visible "PitchStudio"
# Um pequeno projeto PCM é injetado na área privada para testar restauração
# completa sem dependência de arquivos externos ou do YouTube.
python3 - "$OUT" <<'PY'
from pathlib import Path
import json,sys,math,struct,time
path=Path(sys.argv[1])
with (path/"fixture.f32").open("wb") as f:
    for i in range(44100):
        f.write(struct.pack("<f",0.2*math.sin(i*2*math.pi*440/44100)))
meta={
"schema":1,"title":"Teste Biblioteca 197","updatedAt":int(time.time()*1000),
"sampleRate":44100,"channels":1,"frames":44100,"peak":0.2,
"waveform":[0.2]*90,
"semitones":-2,"cents":-5,"speed":0.9,"quality":"ALTA",
"position":0.25,"loopA":0.1,"loopB":0.7
}
(path/"fixture.json").write_text(json.dumps(meta),encoding="utf-8")
PY
ID="123e4567-e89b-12d3-a456-426614174000"
adb push "$OUT/fixture.f32" /data/local/tmp/pitchstudio-fixture.f32 >/dev/null
adb push "$OUT/fixture.json" /data/local/tmp/pitchstudio-fixture.json >/dev/null
adb shell run-as "$PKG" mkdir -p "files/saved_audio_projects/$ID"
adb shell run-as "$PKG" cp /data/local/tmp/pitchstudio-fixture.f32 "files/saved_audio_projects/$ID/original.f32"
adb shell run-as "$PKG" cp /data/local/tmp/pitchstudio-fixture.json "files/saved_audio_projects/$ID/project.json"
tap_text "Meus projetos"
test_visible "Teste Biblioteca 197"
tap_text "Teste Biblioteca 197"
tap_text "Abrir e continuar"
sleep 5
test_visible "Teste Biblioteca 197"
test_visible "-2 semitons"
test_visible "Ajuste fino: -5 cents"
echo "PASSOU: áudio e ajustes foram restaurados no Android."
for back_to_top in 1 2 3; do adb shell input swipe 525 520 525 1550 200; done
sleep 1
tap_text "Salvar projeto"
tap_text "Salvar"
sleep 5
tap_text "Meus projetos"
test_visible "Meus projetos (2)"
adb shell input keyevent 4
echo "PASSOU: salvamento criado pelo aplicativo e listado como segundo projeto."
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity"
sleep 3
tap_text "Meus projetos"
test_visible "Meus projetos (2)"
echo "PASSOU: os dois projetos permaneceram salvos após reiniciar o aplicativo."
adb shell input keyevent 4
sleep 1
adb shell input swipe 525 650 525 1500 260
tap_text "YouTube"
test_visible "EXPLORAR MÚSICAS"
echo "PASSOU: a tela do YouTube também abre no mesmo APK."
