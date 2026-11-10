#!/usr/bin/env bash
set -Eeuo pipefail
# QA Android 1.11.8: não utiliza fontes/dados de clientes e não publica APK.
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm path com.timachado.brothermatrizes | grep -q 'package:'
adb shell am instrument -w \
  -e class com.timachado.brothermatrizes.BrotherNameEditorScreenshotTest \
  com.timachado.brothermatrizes.test/androidx.test.runner.AndroidJUnitRunner \
  | tee android-name-editor-original-test.log
grep -Eq 'OK [(]1 test[)]|INSTRUMENTATION_CODE: -1' android-name-editor-original-test.log
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -2' android-name-editor-original-test.log; then
  echo 'A captura Android não passou na instrumentação.' >&2
  exit 1
fi
adb exec-out run-as com.timachado.brothermatrizes \
  cat files/android-original-name-editor-1118.png > android-original-name-editor-1118.png
python3 - <<'PY'
from pathlib import Path
from struct import unpack
f = Path("android-original-name-editor-1118.png")
b = f.read_bytes()
assert len(b) > 10_000 and b[:8] == bytes.fromhex("89504e470d0a1a0a")
assert b[12:16] == b"IHDR"
w, h = unpack(">II", b[16:24])
assert w >= 320 and h >= 560, (w, h)
print(f"BROTHER_ANDROID_NAME_EDITOR_REFERENCE_OK {w}x{h}")
PY
