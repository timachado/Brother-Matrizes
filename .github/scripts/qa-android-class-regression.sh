#!/usr/bin/env bash
# Run every instrumented class independently in the GitHub Android emulator.
# Passing comma-separated classes produced only one executed class in earlier CI.
set -uo pipefail

classes=(
  com.timachado.brothermatrizes.ImageToEmbroideryScreenSmokeTest
  com.timachado.brothermatrizes.ImageToEmbroideryInstrumentedQaTest
  com.timachado.brothermatrizes.ImportedFontSystemSmokeTest
  com.timachado.brothermatrizes.GreatVibesStartDiagnosticTest
  com.timachado.brothermatrizes.ImportedFontSatinSequenceTest
)
folder="app/build/qa-regressions"
mkdir -p "$folder"
failed=0
for klass in "${classes[@]}"; do
  label="${klass##*.}"
  echo "::group::Android QA class: $label"
  # Run connectedDebugAndroidTest once per requested class. Do not consider
  # a zero-test result to be a pass.
  if gradle :app:connectedDebugAndroidTest \
      "-Pandroid.testInstrumentationRunnerArguments.class=$klass" \
      --no-daemon --stacktrace; then
    echo "Gradle finished: $label"
  else
    echo "::error::Failed Android QA class: $label"
    failed=1
  fi
  xml_dir="app/build/outputs/androidTest-results/connected"
  python3 - "$xml_dir" "$folder/$label" <<'PY'
import pathlib, shutil, sys
source=pathlib.Path(sys.argv[1])
dest=pathlib.Path(sys.argv[2])
dest.mkdir(parents=True,exist_ok=True)
files=list(source.rglob("TEST-*.xml")) if source.is_dir() else []
for idx,src in enumerate(files):
    shutil.copy2(src,dest/("%03d-"%idx+src.name))
print("Archived",len(files),"JUnit XML results for",dest.name)
PY
  echo "::endgroup::"
done

# Verify coverage from recorded JUnit XML, not just Gradle's exit status.
if python3 - "$folder" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
classes=(
  "ImageToEmbroideryScreenSmokeTest",
  "ImageToEmbroideryInstrumentedQaTest",
  "ImportedFontSystemSmokeTest",
  "GreatVibesStartDiagnosticTest",
  "ImportedFontSatinSequenceTest",
)
failed=0
for klass in classes:
    cases=[]
    for file in (root/klass).glob("*.xml"):
        try: tests=ET.parse(file).getroot()
        except Exception as e:
            print("INVALID JUnit XML",file,e); failed+=1; continue
        cases.extend(t for t in tests.iter("testcase")
                     if t.attrib.get("classname","").endswith("."+klass))
    total=len(cases)
    skipped=sum(c.find("skipped") is not None for c in cases)
    errors=sum(c.find("failure") is not None or c.find("error") is not None
               for c in cases)
    print(f"{klass}: total={total}, skipped={skipped}, failed={errors}")
    for case in cases:
        for kind in ("failure","error"):
            for err in case.findall(kind):
                print("  FAIL",case.attrib.get("name",""),
                      err.attrib.get("message","")[:500])
                print("  ",(err.text or "")[:800])
    if total==0:
        print("  ERROR: no testcases executed. Invalid filter or missing tests.")
        failed+=1
    if errors: failed+=errors
    if klass!="GreatVibesStartDiagnosticTest" and skipped==total:
        print("  ERROR: required class did not execute a test.")
        failed+=1
    if klass=="GreatVibesStartDiagnosticTest" and skipped:
        print("  NOTE: missing reference font fixture: SKIPPED, NOT PASSED.")
print("Instrumented class report gate failures:",failed)
sys.exit(1 if failed else 0)
PY
then
  echo "Android JUnit coverage and assertions verified."
else
  failed=1
fi
exit "$failed"
