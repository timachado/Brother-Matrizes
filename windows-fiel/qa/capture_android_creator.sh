#!/usr/bin/env bash
# Android original 1.11.8, SOMENTE emulador. Não instala versão comercial.
set -Eeuo pipefail
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.timachado.brothermatrizes.BrotherCreateNameScreenshotTest \
  com.timachado.brothermatrizes.test/androidx.test.runner.AndroidJUnitRunner \
  | tee android-creator-original-test.log
grep -Eq 'OK [(]1 test[)]|INSTRUMENTATION_CODE: -1' android-creator-original-test.log
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -2' android-creator-original-test.log; then
  echo "Instrumentação Android Criar Nome falhou." >&2
  exit 1
fi
adb exec-out run-as com.timachado.brothermatrizes \
  cat files/android-original-create-name-1118.png \
  > android-original-create-name-1118.png
python3 - <<'PY'
from pathlib import Path
from struct import unpack
p = Path("android-original-create-name-1118.png")
b = p.read_bytes()
assert len(b) > 10_000 and b[:8] == bytes.fromhex("89504e470d0a1a0a")
assert b[12:16] == b"IHDR"
w, h = unpack(">II", b[16:24])
assert w >= 320 and h >= 560
print(f"BROTHER_ANDROID_CREATOR_REFERENCE_OK {w}x{h}")
PY
