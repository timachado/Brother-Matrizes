#!/usr/bin/env bash
set -euo pipefail
APK="$1"
PKG="br.com.timachado.pitchstudio.stemexport"
OUT="$RUNNER_TEMP/pitchstudio-stem-wav-smoke"
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
# O novo botão pode ficar fora da dobra; tap() rola antes de tocar.
tap "Isolar voz com IA"
visible "Importe uma música"
tap "Analisar trecho selecionado"
visible "Importe uma música"
# O novo card de IA desloca o microfone para cima na tela:
# retornar ao topo antes de tentar tocar no botão.
for up in 1 2 3 4 5; do adb shell input swipe 520 500 520 1550 230; done
sleep 1
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
echo "Obtendo áudio vocal real de referência do sherpa-onnx..."
curl --fail --location --retry 3 --connect-timeout 30 --max-time 150 \
  'https://github.com/k2-fsa/sherpa-onnx/releases/download/source-separation-models/qi-feng-le-zh.wav' \
  --output "$OUT/qi-feng-le-zh.wav"
python3 - "$OUT" <<'PY'
from pathlib import Path
import array, json, math, sys, struct, time, wave
directory=Path(sys.argv[1])
# Referência pública oficial do mesmo modelo, cantada, em lugar de uma
# senoide sintética sem características espectrais de uma voz humana.
with wave.open(str(directory/"qi-feng-le-zh.wav"),"rb") as wav:
    rate=wav.getframerate()
    channels=wav.getnchannels()
    width=wav.getsampwidth()
    total=wav.getnframes()
    assert rate==44100 and channels==2 and width==2, (rate,channels,width)
    frames=min(rate*7,total)
    start=max(0, min(rate*9, total-frames))
    wav.setpos(start)
    data=wav.readframes(frames)
samples=array.array("h"); samples.frombytes(data)
if sys.byteorder!="little": samples.byteswap()
assert len(samples)==frames*2
energy=0.0; peak=0.0
with (directory/"stems_test.f32").open("wb") as out:
    for value in samples:
        floating=value/32768.0
        energy+=floating*floating
        peak=max(peak,abs(floating))
        out.write(struct.pack("<f",floating))
rms=math.sqrt(energy/len(samples))
assert rms>0.003,(rms,frames)
meta={
    "schema":1,"title":"Teste IA Musical","updatedAt":int(time.time()*1000),
    "sampleRate":rate,"channels":2,"frames":frames,"peak":peak,
    "waveform":[peak]*90,"semitones":0,"cents":0,"speed":1.0,
    "quality":"ALTA","position":0.0
}
(directory/"stems_test.json").write_text(json.dumps(meta),encoding="utf-8")
print(f"Faixa real: {frames/rate:.2f}s a {rate} Hz, RMS original {rms:.5f}")
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
# O novo botão pode ficar fora da dobra; tap() rola antes de tocar.
adb logcat -c || true
tap "Isolar voz com IA"
echo "Executando separação neural no Android sobre gravação vocal de referência…"
SUCCESS=0
for attempt in $(seq 1 40); do
  readui
  if grep -qE 'Voz estimada por separação neural|A voz foi extraída, mas' "$OUT/current.xml"; then
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
# O trecho isolado dura ~5,8 s. Pausar imediatamente, pois
# uiautomator dump + scroll pode consumir toda a reprodução.
tap "Ouvir voz isolada"
tap "Pausar prévia vocal"
visible "Continuar prévia vocal"
echo "PASSOU: prévia vocal temporária reproduziu e pausou via AudioTrack."
# O áudio temporário deve ser transformado em WAV ANTES de abrir o
# seletor Android. O onStop da Activity limpa a prévia, mas não o WAV.
tap "Salvar voz isolada"
sleep 3
DOCUMENT_ACTIVITY="$(adb shell dumpsys activity activities | grep -Ei 'mResumedActivity|topResumedActivity|Resumed: ActivityRecord' | tail -n 3 || true)"
echo "Tela do seletor de documentos: $DOCUMENT_ACTIVITY"
if ! echo "$DOCUMENT_ACTIVITY" | grep -Eiq 'documentsui|files|documents'; then
  readui
  if ! grep -Eiq 'save|salvar|downloads|recent|documents' "$OUT/current.xml"; then
    echo "FALHA: seletor de arquivos Android não abriu." >&2
    exit 1
  fi
fi
STAGE=$(adb shell run-as "$PKG" ls cache | tr -d '\r' | grep -E '^pitch_voice_export_.*\.wav

if adb logcat -d -b crash -t 1500 | grep -E 'FATAL EXCEPTION|Process: br.com.timachado.pitchstudio.stemexport'; then
 echo "FALHA: crash identificado no logcat" >&2
 exit 1
fi
 | head -n1)
if [ -z "$STAGE" ]; then
  echo "FALHA: exportação não preparou WAV antes do seletor." >&2
  exit 1
fi
adb exec-out run-as "$PKG" cat "cache/$STAGE" > "$OUT/staged_voice.wav"
python3 - "$OUT/staged_voice.wav" <<'PY'
import sys,wave,os
path=sys.argv[1]
with wave.open(path,'rb') as w:
    rate=w.getframerate()
    channels=w.getnchannels()
    frames=w.getnframes()
    bits=w.getsampwidth()*8
    assert rate==44100 and channels==1 and bits==16,(rate,channels,bits)
    assert 3.0<frames/rate<7.0,frames/rate
    raw=w.readframes(frames)
    assert any(b!=0 for b in raw),"WAV vocal silencioso"
print("PASSOU: WAV gerado é PCM16 mono",rate,"Hz,",frames,"frames,",
      os.path.getsize(path),"bytes")
PY
# Cancelar o SAF deve limpar WAV transitório; não mantém arquivo privado.
adb shell input keyevent 4
sleep 3
if adb shell run-as "$PKG" ls cache | grep -q "$STAGE"; then
  echo "FALHA: WAV não foi apagado após cancelar exportação." >&2
  exit 1
fi
echo "PASSOU: seletor SAF abriu e cancelamento removeu arquivo temporário."


if adb logcat -d -b crash -t 1500 | grep -E 'FATAL EXCEPTION|Process: br.com.timachado.pitchstudio.stemexport'; then
 echo "FALHA: crash identificado no logcat" >&2
 exit 1
fi
