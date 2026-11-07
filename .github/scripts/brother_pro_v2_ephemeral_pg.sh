#!/usr/bin/env bash
# STRICTLY a GitHub Actions disposable PostgreSQL service QA.
# NEVER use against Supabase production or staging.
set -euo pipefail
: "${PGHOST:?}" "${PGDATABASE:?}" "${PGPORT:?}" "${PGUSER:?}"
if [[ "$PGHOST" != "127.0.0.1" || "$PGPORT" != "5432" ||
      "$PGDATABASE" != "brother_pro_v2_qa" || "$PGUSER" != "postgres" ]]; then
  echo "ABORT: only the disposable, localhost brother_pro_v2_qa is allowed" >&2
  exit 10
fi
test "$(psql -X -t -A -c 'select current_database()')" = "brother_pro_v2_qa"
test "$(psql -X -t -A -c 'show server_version_num')" -ge 160000

psql -X -v ON_ERROR_STOP=1 <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;
-- This pre-existing Free V1 table is a minimal QA stub ONLY:
-- the SQL proposal never modifies its schema or quotas.
CREATE TABLE public.brother_matrizes_import_usage (
  request_id uuid PRIMARY KEY
);
GRANT SELECT ON public.brother_matrizes_import_usage TO service_role;
SQL

# Only the QA proposal, never a Supabase CLI migration, can be run here.
python3 - <<'PY'
from pathlib import Path
import re
file = Path("qa/sql/brother_pro_v2_receipts_ephemeral.sql")
text = file.read_text()
assert text.startswith("-- QA / disposable PostgreSQL ONLY")
assert text.count("CREATE FUNCTION public.brother_matrizes_") == 2
assert "ENABLE ROW LEVEL SECURITY" in text
assert "SECURITY INVOKER" in text
assert "TO service_role" in text
assert "REVOKE ALL" in text
no_comments = "\n".join(
    line for line in text.splitlines()
    if not line.lstrip().startswith("--")
)
assert not re.search(r"\b(DROP|TRUNCATE|DELETE\s+FROM|ALTER\s+ROLE)\b",
                     no_comments, re.I)
assert "SUPABASE_SERVICE_ROLE_KEY" not in no_comments
assert "CREATE TABLE public.brother_matrizes_import_usage" not in no_comments
print("BROTHER_PRO_V2_EPHEMERAL_SQL_SAFETY_GUARD_OK")
PY

psql -X -v ON_ERROR_STOP=1 -f qa/sql/brother_pro_v2_receipts_ephemeral.sql

# Assertions as real service_role, with PostgreSQL RLS and restricted grants.
psql -X -v ON_ERROR_STOP=1 <<'SQL'
DO $assert$
DECLARE
 v boolean;
BEGIN
 SELECT relrowsecurity INTO v
 FROM pg_catalog.pg_class
 WHERE oid = 'public.brother_matrizes_pro_import_receipts'::regclass;
 IF NOT v THEN RAISE EXCEPTION 'Pro receipts RLS is disabled'; END IF;

 IF pg_catalog.has_table_privilege('anon',
     'public.brother_matrizes_pro_import_receipts', 'SELECT') OR
    pg_catalog.has_table_privilege('authenticated',
     'public.brother_matrizes_pro_import_receipts', 'INSERT') THEN
    RAISE EXCEPTION 'Client role can access Pro receipt data';
 END IF;
 IF NOT pg_catalog.has_table_privilege('service_role',
     'public.brother_matrizes_pro_import_receipts', 'INSERT') THEN
    RAISE EXCEPTION 'Server-side insert privilege missing';
 END IF;

 IF pg_catalog.has_function_privilege('anon',
      'public.brother_matrizes_reserve_pro_import_v2(uuid,text,uuid,text)',
      'EXECUTE') OR
    pg_catalog.has_function_privilege('authenticated',
      'public.brother_matrizes_finalize_pro_import_v2(uuid,text,uuid,boolean)',
      'EXECUTE') OR
    NOT pg_catalog.has_function_privilege('service_role',
      'public.brother_matrizes_finalize_pro_import_v2(uuid,text,uuid,boolean)',
      'EXECUTE') THEN
    RAISE EXCEPTION 'RPC execution grants violate server-only policy';
 END IF;
 IF EXISTS (
   SELECT 1 FROM pg_catalog.pg_proc
   WHERE oid IN (
     'public.brother_matrizes_reserve_pro_import_v2(uuid,text,uuid,text)'::regprocedure,
     'public.brother_matrizes_finalize_pro_import_v2(uuid,text,uuid,boolean)'::regprocedure
   ) AND prosecdef
 ) THEN RAISE EXCEPTION 'SECURITY DEFINER was introduced'; END IF;
END
$assert$;

BEGIN;
SET LOCAL ROLE service_role;
DO $assert$
DECLARE
 v jsonb;
 v_owner uuid := '11111111-2222-4333-8444-555555555555';
 v_key uuid := '12345678-1234-4234-8234-123456789abc';
 v_count int;
BEGIN
 v := public.brother_matrizes_reserve_pro_import_v2(
   v_owner, 'import_matrix', v_key, 'pro_monthly');
 IF v->>'allowed' <> 'true' OR v->>'replayed' <> 'false'
    OR v->>'request_key' <> 'pro_' || v_key::text
    OR v->>'state' <> 'reserved'
    OR v->>'remaining' IS NOT NULL THEN
   RAISE EXCEPTION 'Initial Pro receipt response invalid: %', v;
 END IF;
 v := public.brother_matrizes_reserve_pro_import_v2(
   v_owner, 'import_matrix', v_key, 'pro_monthly');
 IF v->>'allowed' <> 'true' OR v->>'replayed' <> 'true'
    OR v->>'request_key' <> 'pro_' || v_key::text THEN
   RAISE EXCEPTION 'Pro replay did not reuse original receipt: %', v;
 END IF;
 SELECT count(*) INTO v_count
 FROM public.brother_matrizes_pro_import_receipts
 WHERE request_id = v_key;
 IF v_count <> 1 THEN RAISE EXCEPTION 'Duplicate Pro receipt'; END IF;

 v := public.brother_matrizes_finalize_pro_import_v2(
   v_owner, 'import_matrix', v_key, true);
 IF v->>'ok' <> 'true' OR v->>'replayed' <> 'false'
    OR v->>'state' <> 'completed' THEN
   RAISE EXCEPTION 'First finalize invalid: %', v;
 END IF;
 v := public.brother_matrizes_finalize_pro_import_v2(
   v_owner, 'import_matrix', v_key, true);
 IF v->>'ok' <> 'true' OR v->>'replayed' <> 'true'
    OR v->>'state' <> 'completed' THEN
   RAISE EXCEPTION 'Repeated same finalize not idempotent: %', v;
 END IF;
 v := public.brother_matrizes_finalize_pro_import_v2(
   v_owner, 'import_matrix', v_key, false);
 IF v->>'ok' <> 'false' OR v->>'conflict' <> 'true'
    OR v->>'state' <> 'completed' THEN
   RAISE EXCEPTION 'Conflicting outcome altered receipt: %', v;
 END IF;
 v := public.brother_matrizes_reserve_pro_import_v2(
   v_owner, 'import_matrix', v_key, 'pro_monthly');
 IF v->>'allowed' <> 'false' OR v->>'state' <> 'completed' THEN
   RAISE EXCEPTION 'Finished receipt was reopened: %', v;
 END IF;
END
$assert$;
COMMIT;

BEGIN;
SET LOCAL ROLE service_role;
DO $assert$
DECLARE
 v jsonb;
 v_owner uuid := '22222222-2222-4222-8222-222222222222';
 v_key uuid := '87654321-4321-4321-8321-123456789abc';
BEGIN
 v := public.brother_matrizes_reserve_pro_import_v2(
   v_owner, 'import_font', v_key, 'trial');
 UPDATE public.brother_matrizes_pro_import_receipts
 SET expires_at = now() - interval '1 minute'
 WHERE request_id=v_key;
 v := public.brother_matrizes_reserve_pro_import_v2(
   v_owner, 'import_font', v_key, 'trial');
 IF v->>'allowed' <> 'false' OR v->>'state' <> 'reserved' THEN
   RAISE EXCEPTION 'Expired receipt was authorized: %', v;
 END IF;
 v := public.brother_matrizes_finalize_pro_import_v2(
   v_owner, 'import_font', v_key, true);
 IF v->>'ok' <> 'false' OR v->>'state' <> 'failed'
    OR v->>'conflict' <> 'true' THEN
   RAISE EXCEPTION 'Expired receipt finalized as success: %', v;
 END IF;
 v := public.brother_matrizes_finalize_pro_import_v2(
   v_owner, 'import_font', v_key, false);
 IF v->>'ok' <> 'true' OR v->>'replayed' <> 'true'
    OR v->>'state' <> 'failed' THEN
   RAISE EXCEPTION 'Expired failed result cannot be confirmed: %', v;
 END IF;
END
$assert$;
COMMIT;
SQL

expect_denied() {
  local sql="$1"
  # SQL errors are expected, and may not expose user-specific data.
  if psql -X -v ON_ERROR_STOP=1 -c "SET ROLE service_role; $sql" \
       > /tmp/brother-pro-denied.log 2>&1; then
    echo "Unexpectedly accepted a forbidden Pro operation" >&2
    exit 1
  fi
}

owner="11111111-2222-4333-8444-555555555555"
key="12345678-1234-4234-8234-123456789abc"
expect_denied "select public.brother_matrizes_reserve_pro_import_v2(
 '99999999-2222-4333-8444-555555555555','import_matrix','$key','pro_monthly')"
expect_denied "select public.brother_matrizes_reserve_pro_import_v2(
 '$owner','import_font','$key','pro_monthly')"
expect_denied "select public.brother_matrizes_reserve_pro_import_v2(
 '$owner','import_matrix','$key','pro_yearly')"
expect_denied "select public.brother_matrizes_finalize_pro_import_v2(
 '$owner','import_font','$key',true)"
expect_denied "select public.brother_matrizes_finalize_pro_import_v2(
 '99999999-2222-4333-8444-555555555555','import_matrix','$key',true)"
expect_denied "select public.brother_matrizes_reserve_pro_import_v2(
 '$owner','import_matrix','12345678-1234-1234-8234-123456789abc','pro_monthly')"
expect_denied "select public.brother_matrizes_reserve_pro_import_v2(
 '$owner','import_matrix','99999999-9999-4999-8999-999999999999','free')"
expect_denied "select public.brother_matrizes_finalize_pro_import_v2(
 '$owner','import_matrix','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',true)"
# Unlike service_role, neither public client role may read any receipt.
for client_role in anon authenticated; do
  if psql -X -v ON_ERROR_STOP=1 -c \
      "SET ROLE $client_role; SELECT * FROM public.brother_matrizes_pro_import_receipts" \
      > /tmp/brother-pro-client-denied.log 2>&1; then
    echo "Client role $client_role unexpectedly read Pro receipts" >&2
    exit 1
  fi
done

# A client UUID already used in the existing Free table must be denied.
free_uuid="abcd1234-abcd-4bcd-8bcd-123456789abc"
psql -X -v ON_ERROR_STOP=1 -c \
  "insert into public.brother_matrizes_import_usage(request_id) values ('$free_uuid')"
expect_denied "select public.brother_matrizes_reserve_pro_import_v2(
 '$owner','import_matrix','$free_uuid','pro_monthly')"

# Two REAL independent psql connections with the SAME UUID must create
# ONE row, under the database advisory lock, not only a Deno fake-DB test.
parallel_owner="33333333-3333-4333-8333-333333333333"
parallel_uuid="87654321-1111-4333-8111-111111111111"
pids=()
for i in 1 2 3 4; do
 psql -X -v ON_ERROR_STOP=1 -c \
   "SET ROLE service_role; SELECT public.brother_matrizes_reserve_pro_import_v2(
     '$parallel_owner','import_matrix','$parallel_uuid','pro_lifetime')" \
   >"/tmp/brother-pro-concurrent-$i.log" 2>&1 &
 pids+=("$!")
done
for pid in "${pids[@]}"; do wait "$pid"; done
actual="$(psql -X -t -A -c "
  SELECT count(*) FROM public.brother_matrizes_pro_import_receipts
  WHERE request_id='$parallel_uuid' AND user_id='$parallel_owner'")"
[[ "$actual" == "1" ]] || {
  echo "Expected one Pro receipt, observed $actual" >&2; exit 1;
}

# Two distinct outcomes racing to finalize the same receipt must
# serialize and never change the first stored decision.
pids=()
for status in true false; do
 psql -X -v ON_ERROR_STOP=1 -c \
   "SET ROLE service_role; SELECT public.brother_matrizes_finalize_pro_import_v2(
     '$parallel_owner','import_matrix','$parallel_uuid',$status)" \
   >"/tmp/brother-pro-finalize-$status.log" 2>&1 &
 pids+=("$!")
done
for pid in "${pids[@]}"; do wait "$pid"; done
actual="$(psql -X -t -A -c "
  SELECT CASE WHEN state='completed' AND finalize_success IS TRUE THEN 'ok'
              WHEN state='failed' AND finalize_success IS FALSE THEN 'ok'
              ELSE 'bad' END
  FROM public.brother_matrizes_pro_import_receipts
  WHERE request_id='$parallel_uuid'")"
[[ "$actual" == "ok" ]] || {
  echo "Concurrent opposite finalization broke immutable outcome" >&2; exit 1;
}

echo "BROTHER_PRO_V2_DISPOSABLE_POSTGRES_LOCK_RLS_REPLAY_FINALIZE_OK"
