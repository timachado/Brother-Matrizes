#!/usr/bin/env bash
# CI ONLY: disposable PostgreSQL service. NEVER connect to Supabase production.
set -euo pipefail
: "${PGHOST:?Missing disposable PGHOST}"
: "${PGDATABASE:?Missing disposable PGDATABASE}"
if [[ "$PGHOST" != "127.0.0.1" || "$PGDATABASE" != "brother_v2_qa" ]]; then
  echo "Refusing to run outside the disposable QA Postgres service" >&2
  exit 10
fi

psql -X -v ON_ERROR_STOP=1 <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;
CREATE TABLE public.brother_matrizes_import_usage (
 request_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL,
 operation text NOT NULL,
 period_start date NOT NULL,
 state text NOT NULL DEFAULT 'reserved',
 created_at timestamptz NOT NULL DEFAULT now(),
 finalized_at timestamptz
);
CREATE INDEX brother_matrizes_import_usage_user_period_idx
  ON public.brother_matrizes_import_usage(user_id, operation, period_start);
-- Isolated stub representing the EXISTING Android V1 finalization signature.
-- The QA here tests only routine presence / privileges, never grants quotas.
CREATE FUNCTION public.brother_matrizes_finalize_import(
  p_user_id uuid, p_operation text, p_request_key text, p_success boolean
) RETURNS boolean LANGUAGE sql SECURITY INVOKER SET search_path TO ''
AS $body$ SELECT FALSE $body$;
REVOKE ALL ON FUNCTION public.brother_matrizes_finalize_import(
  uuid,text,text,boolean
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_finalize_import(
  uuid,text,text,boolean
) TO service_role;
SQL

python3 - <<'PY'
from pathlib import Path
doc=Path("docs/BROTHER_WINDOWS_RESERVA_IDEMPOTENTE_V2_QA.md").read_text()
assert "NÃO EXECUTAR" in doc
body=doc.split("```sql\n",1)[1].split("\n```",1)[0]
assert body.count("CREATE OR REPLACE FUNCTION")==1
assert "GRANT EXECUTE" in body and "TO service_role;" in body
sql_without_comments="\n".join(line for line in body.splitlines()
                               if not line.lstrip().startswith("--"))
import re
assert not re.search(r"\b(DROP\s+TABLE|TRUNCATE|DELETE\s+FROM)\b",
                     sql_without_comments, re.I)
Path("/tmp/brother_v2_sql_review_qa.sql").write_text(body)
PY
psql -X -v ON_ERROR_STOP=1 -f /tmp/brother_v2_sql_review_qa.sql

psql -X -v ON_ERROR_STOP=1 <<'SQL'
DO $assert$
DECLARE
 v jsonb;
 v_request uuid := '12345678-1234-4234-8234-123456789abc';
 v_owner uuid := '11111111-2222-4333-8444-555555555555';
 v_rows integer;
BEGIN
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_matrix',v_request);
 IF v->>'request_key' <> 'usage_' || v_request::text OR
    v->>'state' <> 'reserved' OR
    v->>'replayed' <> 'false' OR
    v->>'remaining' <> '4' OR
    v->>'allowed' <> 'true' THEN
   RAISE EXCEPTION 'Initial UUID reservation invalid: %', v;
 END IF;
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_matrix',v_request);
 IF v->>'request_key' <> 'usage_' || v_request::text OR
    v->>'replayed' <> 'true' OR v->>'allowed' <> 'true' THEN
   RAISE EXCEPTION 'Replay did not return original reservation: %', v;
 END IF;
 SELECT count(*) INTO v_rows FROM public.brother_matrizes_import_usage
 WHERE user_id=v_owner AND operation='import_matrix';
 IF v_rows <> 1 THEN
   RAISE EXCEPTION 'Repeated UUID inserted % rows; expected 1', v_rows;
 END IF;
 UPDATE public.brother_matrizes_import_usage SET state='completed',
   finalized_at=now() WHERE request_id=v_request;
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_matrix',v_request);
 IF v->>'allowed' <> 'false' OR v->>'state' <> 'completed' OR
    v->>'replayed' <> 'true' THEN
   RAISE EXCEPTION 'Completed UUID was incorrectly reauthorized: %', v;
 END IF;
END
$assert$;

DO $assert$
DECLARE
 v jsonb;
 v_owner uuid := '22222222-2222-4222-8222-222222222222';
 v_request uuid := '87654321-4321-4321-8321-123456789abc';
BEGIN
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_font',v_request);
 UPDATE public.brother_matrizes_import_usage
 SET created_at = now() - interval '40 minutes'
 WHERE request_id=v_request;
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_font',v_request);
 IF v->>'allowed' <> 'false' OR v->>'state' <> 'reserved' THEN
   RAISE EXCEPTION 'Expired UUID was reauthorized: %', v;
 END IF;
END
$assert$;

DO $assert$
DECLARE
 v jsonb;
 v_owner uuid := '33333333-3333-4333-8333-333333333333';
 v_period date := date_trunc('month',now() at time zone 'UTC')::date;
BEGIN
 -- Emulate a legacy Android V1 reserved font in this disposable database.
 INSERT INTO public.brother_matrizes_import_usage
  (request_id,user_id,operation,period_start,state)
 VALUES ('00000000-0000-4000-8000-000000000001', v_owner,
  'import_font', v_period, 'reserved');
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_font',
  '00000000-0000-4000-8000-000000000002');
 IF v->>'allowed' <> 'true' OR v->>'remaining' <> '1' THEN
   RAISE EXCEPTION 'V2 did not count legacy V1 reservation: %', v;
 END IF;
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_font',
  '00000000-0000-4000-8000-000000000003');
 IF v->>'allowed' <> 'true' OR v->>'remaining' <> '0' THEN
   RAISE EXCEPTION 'V2 second font reservation wrong: %', v;
 END IF;
 v := public.brother_matrizes_reserve_import_v2(v_owner,'import_font',
  '00000000-0000-4000-8000-000000000004');
 IF v->>'allowed' <> 'false' THEN
   RAISE EXCEPTION 'Monthly font quota exceeded: %', v;
 END IF;
END
$assert$;

DO $assert$
DECLARE
 v boolean;
BEGIN
 SELECT has_function_privilege('anon',
  'public.brother_matrizes_reserve_import_v2(uuid,text,uuid)', 'EXECUTE')
 INTO v;
 IF v THEN RAISE EXCEPTION 'anon can invoke client-owned ID RPC'; END IF;
 SELECT has_function_privilege('authenticated',
  'public.brother_matrizes_reserve_import_v2(uuid,text,uuid)', 'EXECUTE')
 INTO v;
 IF v THEN RAISE EXCEPTION 'authenticated can spoof arbitrary user_id'; END IF;
 SELECT has_function_privilege('service_role',
  'public.brother_matrizes_reserve_import_v2(uuid,text,uuid)', 'EXECUTE')
 INTO v;
 IF NOT v THEN RAISE EXCEPTION 'Server-only execution missing'; END IF;
END
$assert$;
SQL

# Foreign ownership and operation reuse must NOT succeed or return data.
for q in \
  "SELECT public.brother_matrizes_reserve_import_v2('ffffffff-ffff-4fff-8fff-ffffffffffff','import_matrix','12345678-1234-4234-8234-123456789abc')" \
  "SELECT public.brother_matrizes_reserve_import_v2('11111111-2222-4333-8444-555555555555','import_font','12345678-1234-4234-8234-123456789abc')"; do
  if psql -X -v ON_ERROR_STOP=1 -c "$q" >/dev/null 2>&1; then
    echo "Cross-user or cross-operation UUID reuse unexpectedly succeeded" >&2
    exit 1
  fi
done

# Two concurrent, independent psql clients using one UUID must create one row.
concurrent_owner="44444444-4444-4444-8444-444444444444"
concurrent_uuid="76543210-1234-4234-8234-111111111111"
pids=()
for i in 1 2; do
  psql -X -v ON_ERROR_STOP=1 -c \
    "SELECT public.brother_matrizes_reserve_import_v2('$concurrent_owner','import_matrix','$concurrent_uuid')" \
    >"/tmp/brother-v2-concurrency-$i.log" 2>&1 &
  pids+=("$!")
done
for pid in "${pids[@]}"; do wait "$pid"; done
actual="$(psql -X -t -A -c "SELECT count(*) FROM public.brother_matrizes_import_usage WHERE user_id='$concurrent_owner' AND request_id='$concurrent_uuid'")"
[[ "$actual" == "1" ]] || { echo "Concurrent identical UUID created $actual rows" >&2; exit 1; }

# Concurrent distinct UUIDs must still respect the shared free 5-matrix limit.
quota_owner="55555555-5555-4555-8555-555555555555"
pids=()
for i in $(seq 1 10); do
  id="$(printf '76543210-1234-4234-8234-%012d' "$i")"
  psql -X -v ON_ERROR_STOP=1 -c \
    "SELECT public.brother_matrizes_reserve_import_v2('$quota_owner','import_matrix','$id')" \
    >"/tmp/brother-v2-quota-$i.log" 2>&1 &
  pids+=("$!")
done
for pid in "${pids[@]}"; do wait "$pid"; done
actual="$(psql -X -t -A -c "SELECT count(*) FROM public.brother_matrizes_import_usage WHERE user_id='$quota_owner' AND operation='import_matrix' AND state='reserved'")"
[[ "$actual" == "5" ]] || { echo "Concurrent monthly matrix quota created $actual rows; expected 5" >&2; exit 1; }

echo "BROTHER_V2_DISPOSABLE_POSTGRES_CONCURRENT_AND_QUOTA_QA_OK"

# Additional schema-readiness handshake, read-only and service_role-only.
# This fixture is executed only after the Free V2 QA RPC was installed.
psql -X -v ON_ERROR_STOP=1 -f qa/sql/brother_v2_capability_probe_disposable.sql
psql -X -v ON_ERROR_STOP=1 <<'SQL'
SET ROLE service_role;
DO $assert$
DECLARE v jsonb;
BEGIN
  SELECT public.brother_matrizes_v2_capability_probe() INTO v;
  IF v->>'free_v2' <> 'true' OR v->>'pro_v2' <> 'false' THEN
    RAISE EXCEPTION 'Free V2 schema readiness incorrect: %', v;
  END IF;
END
$assert$;
RESET ROLE;
SQL
for role in anon authenticated; do
  if psql -X -v ON_ERROR_STOP=1 -c "SET ROLE $role; SELECT public.brother_matrizes_v2_capability_probe();" >/dev/null 2>&1; then
    echo "Untrusted $role could call capability probe" >&2
    exit 1
  fi
done
# A missing finalization RPC must turn the read-only readiness OFF.
psql -X -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
DROP FUNCTION public.brother_matrizes_finalize_import(uuid,text,text,boolean);
SET ROLE service_role;
DO $assert$
DECLARE v jsonb;
BEGIN
  SELECT public.brother_matrizes_v2_capability_probe() INTO v;
  IF v->>'free_v2' <> 'false' THEN
    RAISE EXCEPTION 'Free V2 accepted missing finalization RPC: %', v;
  END IF;
END
$assert$;
RESET ROLE;
ROLLBACK;
SQL
echo "BROTHER_V2_SQL_CAPABILITY_FREE_ONLY_READONLY_OK"
