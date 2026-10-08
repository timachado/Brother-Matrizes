#!/usr/bin/env bash
set -euo pipefail
APK="$1"
OUT="$RUNNER_TEMP/pitchstudio-ui-smoke"
mkdir -p "$OUT"
cleanup() {
  adb shell screencap -p /sdcard/pitchstudio-smoke.png >/dev/null 2>&1 || true
  adb pull /sdcard/pitchstudio-smoke.png "$OUT/screen.png" >/dev/null 2>&1 || true
  adb logcat -d -b main -b crash -t 2000 > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
}
trap cleanup EXIT
adb wait-for-device
adb shell input keyevent 82 || true
adb install -r "$APK"
PKG="br.com.timachado.pitchstudio.expressivefix"
adb shell am start -W -n "$PKG/br.com.timachado.pitchstudio.MainActivity"
sleep 6
adb shell uiautomator dump /sdcard/before.xml >/dev/null
adb shell cat /sdcard/before.xml > "$OUT/before.xml"
POSITION=$(python3 - "$OUT/before.xml" <<'PY'
import sys, re
from xml.etree import ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
candidates=[]
for node in root.iter("node"):
    name=(node.attrib.get("text","")+" "+node.attrib.get("content-desc","")).strip()
    if name=="YouTube" and node.attrib.get("bounds"):
        nums=list(map(int,re.findall(r"\d+",node.attrib["bounds"])))
        if len(nums)==4:
            candidates.append(((nums[0]+nums[2])//2,(nums[1]+nums[3])//2))
if not candidates:
    raise SystemExit("Botão YouTube não localizado no editor.")
print(*candidates[0])
PY
)
read -r X Y <<< "$POSITION"
echo "Abrindo YouTube no Android: toque ($X,$Y)"
adb shell input tap "$X" "$Y"
sleep 6
adb shell uiautomator dump /sdcard/after.xml >/dev/null
adb shell cat /sdcard/after.xml > "$OUT/after.xml"
python3 - "$OUT/after.xml" <<'PY'
import sys
from xml.etree import ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts=[(n.attrib.get("text") or "") for n in root.iter("node")]
print("Visíveis:", [v for v in texts if v.strip()][:18])
if not any("EXPLORAR MÚSICAS" in t for t in texts):
    raise SystemExit("A tela do YouTube não abriu; conferir logcat.")
print("PASSOU: YouTubeBrowserActivity iniciou e renderizou o cabeçalho no Android.")
PY
if adb logcat -d -b crash -t 1000 | grep -E 'FATAL EXCEPTION|Process: br.com.timachado.pitchstudio.expressivefix'; then
  echo "Falha fatal identificada no processo." >&2
  exit 1
fi
