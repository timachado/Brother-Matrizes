#!/usr/bin/env bash
# QA visual: mantém o aplicativo instalado até extrair a captura PNG real.
# NUNCA publica ou assina artefato comercial.
set -Eeuo pipefail

gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm path com.timachado.brothermatrizes | grep -q 'package:'

adb shell am instrument -w \
  -e class com.timachado.brothermatrizes.PremiumHomeScreenshotTest \
  com.timachado.brothermatrizes.test/androidx.test.runner.AndroidJUnitRunner \
  | tee premium-home-test.log

if ! grep -Eq 'OK [(]1 test[)]|INSTRUMENTATION_CODE: -1' premium-home-test.log; then
    echo 'FALHA: a instrumentacao Android nao confirmou teste aprovado.' >&2
    exit 1
fi
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -2' premium-home-test.log; then
    echo 'FALHA: o teste Android reportou excecao ou falha.' >&2
    exit 1
fi

adb shell pm path com.timachado.brothermatrizes | grep -q 'package:'
adb exec-out run-as com.timachado.brothermatrizes cat files/premium-home-screenshot.png > premium-home-screenshot.png
adb exec-out run-as com.timachado.brothermatrizes cat files/premium-launcher-icon.png > premium-launcher-icon.png

python3 - <<'PY'
from pathlib import Path
import struct
for filename, minimum_size, minimum_width, minimum_height in [
    ("premium-home-screenshot.png", 10_000, 320, 560),
    ("premium-launcher-icon.png", 5_000, 432, 432),
]:
    p = Path(filename)
    data = p.read_bytes()
    assert len(data) >= minimum_size, f"{filename}: tamanho invalido ({len(data)})"
    assert data[:8] == bytes.fromhex("89504e470d0a1a0a"), f"{filename}: nao e PNG"
    assert data[12:16] == b'IHDR', f"{filename}: IHDR ausente"
    width, height = struct.unpack(">II", data[16:24])
    assert width >= minimum_width and height >= minimum_height, f"{filename}: resolucao invalida"
    print(f"QA imagem validada: {filename}, {width}x{height}, {len(data)} bytes")
PY
