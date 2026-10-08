#!/usr/bin/env bash
set -euo pipefail
APK="$1"
PKG="br.com.timachado.pitchstudio.vocalqa"
OUT="$RUNNER_TEMP/pitchstudio-18s-smoke"
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
echo "PASSOU: tela YouTube abre sem crash."
# Confere retorno de navegação sem deixar o player preso em segundo plano.
adb shell input keyevent 4
sleep 2
visible "PitchStudio"
echo "PASSOU: navegação volta do YouTube ao editor."
# Teste da inferência real com faixa longa, sem depender da busca YouTube.
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
    frames=min(rate*21,total)
    start=max(0, min(rate*2, total-frames))
    assert frames >= rate*18, "A referência vocal é curta demais para validar 18 segundos."
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
# Exercitar duração máxima (18s), com múltiplas inferências e transições.
tap "Duração de isolamento: 6 s"
tap "Duração de isolamento: 12 s"
visible "Duração de isolamento: 18 s"
visible "Cancelar isolamento"
adb logcat -c || true
tap "Isolar voz com IA"
echo "Executando separação neural no Android sobre gravação vocal de referência…"
SUCCESS=0
for attempt in $(seq 1 75); do
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
# O trecho isolado dura 18 s. Pausar imediatamente, pois
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
STAGE=$(adb shell run-as "$PKG" ls cache | tr -d '\r' | grep -E '^pitch_voice_export_.*\.wav$' | head -n1)
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
    assert 17.9<frames/rate<=18.0,frames/rate
    raw=w.readframes(frames)
    assert any(b!=0 for b in raw),"WAV vocal silencioso"
print("PASSOU: WAV gerado é PCM16 mono",rate,"Hz,",frames,"frames,",
      os.path.getsize(path),"bytes")
PY
# Validar o caminho positivo de gravação, não apenas a geração e cancelamento.
# O Android DocumentsUI costuma iniciar em Recentes, onde não se pode salvar.
readui
if grep -qE '(text|content-desc)="Downloads"' "$OUT/current.xml"; then
  tap "Downloads"
fi
readui
# Evita selecionar sugestões de nome ou tocar em botões fora da janela SAF.
SAVE_POS=$(python3 - "$OUT/current.xml" <<'PY'
import sys,re
from xml.etree import ElementTree as ET
nodes=list(ET.parse(sys.argv[1]).getroot().iter("node"))
for n in nodes:
    value=(n.get("text","") or n.get("content-desc","")).strip().casefold()
    if value not in ("save","salvar"):
        continue
    if n.get("enabled")=="false":
        continue
    bounds=list(map(int,re.findall(r"\d+",n.get("bounds",""))))
    if len(bounds)==4:
        a,b,c,d=bounds
        print((a+c)//2,(b+d)//2)
        raise SystemExit(0)
raise SystemExit("FALHA: botão Salvar do seletor SAF não está habilitado")
PY
)
read -r SAVE_X SAVE_Y <<<"$SAVE_POS"
echo "Confirmando salvamento SAF em $SAVE_X,$SAVE_Y"
adb shell input tap "$SAVE_X" "$SAVE_Y"
sleep 5
readui
if ! grep -q 'Voz isolada salva em WAV no local escolhido' "$OUT/current.xml"; then
  echo "FALHA: o aplicativo não confirmou a gravação do WAV pelo ContentResolver." >&2
  grep -Eo 'text="[^"]{0,140}"' "$OUT/current.xml" | tail -n 25 || true
  exit 1
fi
if adb shell run-as "$PKG" ls cache | grep -q "$STAGE"; then
  echo "FALHA: arquivo WAV de staging não foi apagado após salvar." >&2
  exit 1
fi
echo "PASSOU: salvamento externo SAF confirmado e WAV temporário descartado."
# No provedor de documentos local do emulador, validar também os bytes do arquivo.
SAVED_REMOTE=$(adb shell find /sdcard/Download /sdcard/Documents /sdcard/Music -maxdepth 3 -type f -name 'PitchStudio_Voz_Isolada*.wav' 2>/dev/null | tr -d '\r' | head -n1 || true)
if [ -n "$SAVED_REMOTE" ]; then
  adb pull "$SAVED_REMOTE" "$OUT/confirmed_voice.wav" >/dev/null
  python3 - "$OUT/staged_voice.wav" "$OUT/confirmed_voice.wav" <<'PY'
from pathlib import Path
import hashlib,sys,wave
prepared,final=map(Path,sys.argv[1:])
assert final.read_bytes()==prepared.read_bytes(),"Arquivo salvo difere do WAV preparado"
with wave.open(str(final),"rb") as audio:
    assert audio.getframerate()==44100 and audio.getnchannels()==1
print("PASSOU: WAV salvo no destino SAF é byte a byte igual ao original temporário.")
PY
else
  echo "AVISO: provedor SAF confirmou gravação, mas não expôs caminho direto ao adb para comparação."
fi


# Regressão end-to-end: MP3 no editor a partir do projeto carregado,
# selecionando destino real via SAF e decodificando o arquivo final.
adb shell input keyevent 4
sleep 3
visible "PitchStudio"
# O cartão FINALIZAR fica no fim do editor Material Expressive:
# a rolagem deve atingir seu conteúdo antes de buscar o botão MP3.
for down in 1 2 3 4 5 6; do
  adb shell input swipe 520 1800 520 550 220
done
visible "MP3 (até 320 kbps)"
tap "MP3 (até 320 kbps)"
sleep 3
readui
if grep -qE '(text|content-desc)="Downloads"' "$OUT/current.xml"; then
  tap "Downloads"
fi
readui
MP3_SAVE_POS=$(python3 - "$OUT/current.xml" <<'PY'
import re,sys
from xml.etree import ElementTree as ET
for node in ET.parse(sys.argv[1]).getroot().iter("node"):
    label=(node.get("text","") or node.get("content-desc","")).strip().casefold()
    if label not in ("save","salvar") or node.get("enabled")=="false":
        continue
    coords=list(map(int,re.findall(r"\d+",node.get("bounds",""))))
    if len(coords)==4:
        a,b,c,d=coords
        print((a+c)//2,(b+d)//2)
        raise SystemExit(0)
raise SystemExit("FALHA: seletor MP3 não mostrou Salvar habilitado")
PY
)
read -r MP3_X MP3_Y <<<"$MP3_SAVE_POS"
adb shell input tap "$MP3_X" "$MP3_Y"
MP3_DONE=0
for attempt in $(seq 1 45); do
  readui
  if grep -q 'Exportação concluída' "$OUT/current.xml"; then
    MP3_DONE=1; break
  fi
  if grep -q 'Falha na exportação' "$OUT/current.xml"; then
    echo "FALHA: exportador MP3 apresentou erro." >&2
    grep -o 'Falha na exportação[^<]*' "$OUT/current.xml" | head -c 500 || true
    exit 1
  fi
  sleep 2
done
test "$MP3_DONE" = 1 || { echo "FALHA: MP3 não concluiu." >&2; exit 1; }
MP3_REMOTE=$(adb shell find /sdcard/Download /sdcard/Documents /sdcard/Music \
  -maxdepth 3 -type f -name '*_processado.mp3' 2>/dev/null | tr -d '\r' | head -n1 || true)
test -n "$MP3_REMOTE" || { echo "FALHA: MP3 exportado não localizado no Android." >&2; exit 1; }
adb pull "$MP3_REMOTE" "$OUT/confirmed_export.mp3" >/dev/null
python3 - "$OUT/confirmed_export.mp3" <<'PY'
import sys,shutil,subprocess,json
from pathlib import Path
raw=Path(sys.argv[1]).read_bytes()
assert len(raw)>100000, "MP3 exportado está vazio ou truncado"
pos=0
if raw.startswith(b"ID3"):
    assert len(raw)>=10
    tag=(raw[6]<<21)|(raw[7]<<14)|(raw[8]<<7)|raw[9]
    pos=10+tag
mpeg_frames=0
br_set=set()
rates=set()
modes=set()
duration=0.0
bitrates=[0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0]
while pos+4<=len(raw):
    h=int.from_bytes(raw[pos:pos+4],"big")
    assert (h>>21)&2047==2047, f"Frame MP3 inválido no byte {pos}"
    version=(h>>19)&3
    layer=(h>>17)&3
    br_idx=(h>>12)&15
    sr_idx=(h>>10)&3
    assert version==3 and layer==1 and 1<=br_idx<=14 and sr_idx<=2,hex(h)
    rate=(44100,48000,32000)[sr_idx]
    bitrate=bitrates[br_idx]
    channels=1 if ((h>>6)&3)==3 else 2
    length=144000*bitrate//rate+((h>>9)&1)
    assert pos+length<=len(raw), "MP3 termina no meio de um frame"
    br_set.add(bitrate);rates.add(rate);modes.add(channels)
    duration+=1152.0/rate
    mpeg_frames+=1
    pos+=length
assert pos==len(raw) or (len(raw)-pos==128 and raw[pos:pos+3]==b"TAG"),"Resíduo inválido no MP3"
assert mpeg_frames>=200,mpeg_frames
assert rates=={44100} and modes=={2} and br_set=={320},(rates,modes,br_set)
assert 20.7<=duration<=21.8,duration
print(f"PASSOU: MP3 SAF salvo, {mpeg_frames} quadros MPEG íntegros, "
      f"44,1 kHz, estéreo, 320kbps, duração {duration:.3f}s.")
# FFmpeg é opcional: nem todas as imagens Ubuntu do Actions o incluem.
# Quando disponível, decodificar todo o arquivo também.
if shutil.which("ffprobe") and shutil.which("ffmpeg"):
    probe=subprocess.run(["ffprobe","-v","error","-show_entries",
        "stream=codec_name,sample_rate,channels,bit_rate:format=duration",
        "-of","json",sys.argv[1]],capture_output=True,text=True,check=True)
    meta=json.loads(probe.stdout)
    audio=[s for s in meta.get("streams",[]) if s.get("codec_name")=="mp3"]
    assert len(audio)==1,meta
    assert int(audio[0]["sample_rate"])==44100
    subprocess.run(["ffmpeg","-v","error","-i",sys.argv[1],"-f","null","-"],
        check=True,capture_output=True)
    print("PASSOU: decodificação completa do MP3 pelo FFmpeg.")
else:
    print("AVISO: FFmpeg ausente no runner; quadros validados integralmente sem dependência externa.")
PY

# Provar que o cancelamento da separação longa interrompe o trabalho,
# libera botões e exclui os arquivos PCM temporários.
echo "Exercitando cancelamento explícito da separação vocal longa…"
for to_top in 1 2 3 4 5 6; do
  adb shell input swipe 520 500 520 1650 230
done
tap "Tom Ideal"
tap "Duração de isolamento: 6 s"
tap "Duração de isolamento: 12 s"
visible "Duração de isolamento: 18 s"
tap "Isolar voz com IA"
tap "Cancelar isolamento"
CANCEL_OK=0
for attempt in $(seq 1 40); do
  readui
  if grep -q 'Isolamento vocal cancelado' "$OUT/current.xml"; then
    CANCEL_OK=1
    break
  fi
  sleep 2
done
test "$CANCEL_OK" = 1 || {
  echo "FALHA: cancelamento vocal não finalizou." >&2
  exit 1
}
if adb shell run-as "$PKG" ls cache | grep -qE '^pitch_vocal_stem_.*\.f32
 echo "FALHA: crash identificado no logcat" >&2
 exit 1
fi
; then
  echo "FALHA: áudio PCM temporário permaneceu após cancelar isolamento." >&2
  exit 1
fi
echo "PASSOU: cancelamento de IA 18s sem áudio temporário residual."
adb shell dumpsys meminfo "$PKG" | grep -Ei 'TOTAL PSS|TOTAL RSS|TOTAL SWAP' | tail -n 3 || true

if adb logcat -d -b crash -t 1500 | grep -E 'FATAL EXCEPTION|Process: br.com.timachado.pitchstudio.vocalqa'; then
 echo "FALHA: crash identificado no logcat" >&2
 exit 1
fi
