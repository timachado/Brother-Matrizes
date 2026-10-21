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
    if [[ "$label" == "GreatVibesStartDiagnosticTest" ]]; then
      echo "::warning::Great Vibes instrumented execution returned nonzero. The XML gate below must distinguish a missing licensed fixture from a genuine assertion failure."
    else
      echo "::error::Failed Android QA class: $label"
      failed=1
    fi
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
    errors=0
    fixture_missing=0
    for case in cases:
        for kind in ("failure","error"):
            for err in case.findall(kind):
                combined=(err.attrib.get("message","") or "")+"\n"+(err.text or "")
                # AndroidX's instrumentation JUnit report may serialize an
                # AssumptionViolatedException as <failure> instead of <skipped>.
                # Accept ONLY this exact optional font-fixture case. An
                # unrelated assumption or real test assertion still fails.
                if (klass=="GreatVibesStartDiagnosticTest"
                    and "org.junit.AssumptionViolatedException" in combined
                    and "REFERÊNCIA INDISPONÍVEL:" in combined
                    and "GreatVibes-Regular.ttf" in combined):
                    fixture_missing+=1
                    print("  NOT EXECUTED (source font unavailable):",
                          case.attrib.get("name",""))
                else:
                    errors+=1
                    print("  FAIL",case.attrib.get("name",""),
                          err.attrib.get("message","")[:500])
                    print("  ",(err.text or "")[:800])
    print(f"{klass}: total={total}, skipped={skipped}, unavailable_source={fixture_missing}, failed={errors}")
    if total==0:
        print("  ERROR: no testcases executed. Invalid filter or missing tests.")
        failed+=1
    if errors: failed+=errors
    if klass!="GreatVibesStartDiagnosticTest" and skipped==total:
        print("  ERROR: required class did not execute a test.")
        failed+=1
    if klass=="GreatVibesStartDiagnosticTest" and (skipped or fixture_missing):
        print("  NOTE: comparison requires licensed font fixture: NOT EXECUTED, NOT PASSED.")
print("Instrumented class report gate failures:",failed)
sys.exit(1 if failed else 0)
PY
then
  echo "Android JUnit coverage and assertions verified."
else
  failed=1
fi
exit "$failed"
