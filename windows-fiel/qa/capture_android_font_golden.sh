#!/usr/bin/env bash
# Golden TTF do motor Satin ATIVO, apenas Android emulador de QA.
set -Eeuo pipefail
gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p windows-fiel/build/qa/font-parity/android
adb shell am instrument -w \
  -e class com.timachado.brothermatrizes.font.ActiveSatinFontGoldenTest \
  com.timachado.brothermatrizes.test/androidx.test.runner.AndroidJUnitRunner \
  | tee windows-fiel/build/qa/font-parity/android/instrumentation.log
grep -Fq 'OK (1 test)' windows-fiel/build/qa/font-parity/android/instrumentation.log
if grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -2' \
    windows-fiel/build/qa/font-parity/android/instrumentation.log; then
  echo 'Teste Android não aprovado: cancelar referência.' >&2
  exit 1
fi
for name in bm-parity-font.ttf bm-parity-android-outline.tsv bm-parity-android-outline-maria.tsv bm-parity-android-kernel-o.tsv bm-parity-android-coverage.tsv bm-parity-android-coverage-real-o.tsv bm-parity-android-status.txt; do
  adb exec-out run-as com.timachado.brothermatrizes cat "files/$name" \
    > "windows-fiel/build/qa/font-parity/android/$name"
done
# A geração da matriz pode falhar pelo guard Android; isso é diagnóstico.
adb exec-out run-as com.timachado.brothermatrizes \
  cat files/bm-parity-android-stitches.tsv \
  > windows-fiel/build/qa/font-parity/android/bm-parity-android-stitches.tsv \
  2>/dev/null || rm -f windows-fiel/build/qa/font-parity/android/bm-parity-android-stitches.tsv
python3 - <<'PY'
from pathlib import Path
from hashlib import sha256
root = Path("windows-fiel/build/qa/font-parity/android")
font = (root / "bm-parity-font.ttf").read_bytes()
outline = (root / "bm-parity-android-outline.tsv").read_text()
sha = sha256(font).hexdigest()
assert len(font) > 1000 and len(font) <= 12 * 1024 * 1024
assert outline.startswith("FONT\t" + sha + "\t"), "Hash da fonte divergente."
assert sum(line.startswith("P\t") for line in outline.splitlines()) > 20
assert "ACTIVE_CLEANROOM=" in (root / "bm-parity-android-status.txt").read_text()
maria = (root / "bm-parity-android-outline-maria.tsv").read_text()
assert maria.startswith("FONT\t" + sha + "\t")
assert maria.splitlines()[0].endswith("\tMaria"), "Fixture Maria não corresponde ao Android."
assert sum(line.startswith("P\t") for line in maria.splitlines()) > 50
kernel = (root / "bm-parity-android-kernel-o.tsv").read_text().splitlines()
assert kernel[0].startswith("STITCHES\t")
assert len(kernel) == int(kernel[0].split("\t")[1]) + 1
assert sum("\tSTITCH\t" in line for line in kernel[1:]) > 20
coverage = (root / "bm-parity-android-coverage.tsv").read_text().splitlines()
assert len(coverage) == 2 and coverage[0].startswith("SCORE\t")
assert coverage[1] == "BLOCKED\ttrue"
for x in coverage[0].split("\t")[1:]:
    assert 0.0 <= float(x) <= 1.0
real = (root / "bm-parity-android-coverage-real-o.tsv").read_text().strip()
assert real == "BLOCKED" or (
    real.startswith("PASS\t") and len(real.split("\t")) == 3
)
print("BM_ACTIVE_SATIN_ANDROID_FONT_GOLDEN_OK sha256=" + sha)
PY
