#!/usr/bin/env bash
set -euo pipefail

echo "Checking production source for forbidden secrets and cleartext URLs..."


echo "Checking repository for tracked signing material..."

TRACKED_SIGNING_MATERIAL="$(
  git ls-files \
    | grep -Ei '(\.(jks|keystore|p12|pfx)$|(^|/).*(keystore|signing).*(base64|b64))' \
    || true
)"

if [ -n "$TRACKED_SIGNING_MATERIAL" ]; then
  echo "$TRACKED_SIGNING_MATERIAL"
  echo "Tracked production signing material found. Remove it from Git history before continuing."
  exit 1
fi

if grep -RInE   --include='*.kt'   --include='*.kts'   '(service[_-]?role|SUPABASE_SERVICE_ROLE|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY)'   app/src/main app/build.gradle.kts; then
  echo "Forbidden production secret marker found."
  exit 1
fi

HTTP_MATCHES="$(
  grep -RInE --include='*.kt' --include='*.kts' '"http://' app/src/main app/build.gradle.kts \
    | grep -vE 'http://apache\.org/xml/|http://xml\.org/sax/' \
    | grep -vF 'xmlns=\"http://www.w3.org/2000/svg' \
    || true
)"

if [ -n "$HTTP_MATCHES" ]; then
  echo "$HTTP_MATCHES"
  echo "Cleartext HTTP endpoint found in production Kotlin/Gradle source."
  exit 1
fi

echo "Static pre-release safety checks passed."
