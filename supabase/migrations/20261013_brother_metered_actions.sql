-- Additive only: no changes to licensing, billing, trials or existing
-- import_* monthly counters. New daily quotas start ONLY with the new client.
-- Managed by service_role after Google Auth and entitlement checks in the Edge Function.
CREATE TABLE IF NOT EXISTS public.brother_matrizes_daily_limits (
    operation text PRIMARY KEY CHECK (operation IN (
        'open_matrix', 'rar_receive', 'telegram_search',
        'telegram_import', 'dafont_search', 'dafont_import')),
    limit_per_day integer NOT NULL CHECK (limit_per_day BETWEEN 1 AND 100000),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.brother_matrizes_daily_limits(operation, limit_per_day)
VALUES ('open_matrix',20), ('rar_receive',10), ('telegram_search',20),
       ('telegram_import',10), ('dafont_search',20), ('dafont_import',10)
ON CONFLICT (operation) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.brother_matrizes_action_usage (
    request_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation text NOT NULL REFERENCES public.brother_matrizes_daily_limits(operation),
    local_day date NOT NULL,
    state text NOT NULL DEFAULT 'reserved'
        CHECK (state IN ('reserved', 'completed', 'failed')),
    fingerprint text,
    created_at timestamptz NOT NULL DEFAULT now(),
    finalized_at timestamptz
);
CREATE INDEX IF NOT EXISTS brother_action_usage_lookup_idx
ON public.brother_matrizes_action_usage(user_id, operation, local_day, state, created_at);
-- A content hash can prevent double billing across the supported matrix import paths.
CREATE UNIQUE INDEX IF NOT EXISTS brother_action_usage_dedupe_idx
ON public.brother_matrizes_action_usage(user_id, local_day, fingerprint)
WHERE fingerprint IS NOT NULL AND state = 'completed';

ALTER TABLE public.brother_matrizes_daily_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.brother_matrizes_action_usage ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.brother_matrizes_daily_limits FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.brother_matrizes_action_usage FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.brother_matrizes_daily_limits TO service_role;
GRANT SELECT, INSERT, UPDATE ON public.brother_matrizes_action_usage TO service_role;

CREATE OR REPLACE FUNCTION public.brother_matrizes_reserve_action(
    p_user_id uuid, p_operation text
) RETURNS jsonb LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE
    v_day date := (now() AT TIME ZONE 'America/Sao_Paulo')::date;
    v_limit integer;
    v_count bigint;
    v_token uuid := gen_random_uuid();
    v_next timestamptz :=
      ((v_day + 1)::timestamp AT TIME ZONE 'America/Sao_Paulo');
BEGIN
    IF p_user_id IS NULL THEN RAISE EXCEPTION 'Identidade inválida.'; END IF;
    SELECT limit_per_day INTO v_limit FROM public.brother_matrizes_daily_limits
    WHERE operation = p_operation;
    IF NOT FOUND THEN RAISE EXCEPTION 'Operação inválida.'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(p_user_id::text),
        pg_catalog.hashtext(p_operation || '/' || v_day::text));
    SELECT count(*) INTO v_count FROM public.brother_matrizes_action_usage
    WHERE user_id = p_user_id AND operation = p_operation AND local_day = v_day AND
      (state = 'completed' OR
       (state = 'reserved' AND created_at > now() - interval '30 minutes'));
    IF v_count >= v_limit THEN
        RETURN jsonb_build_object('allowed', false, 'operation', p_operation,
          'used', v_count, 'remaining', 0, 'limit', v_limit,
          'renews_at', v_next);
    END IF;
    INSERT INTO public.brother_matrizes_action_usage
        (request_id, user_id, operation, local_day)
    VALUES (v_token, p_user_id, p_operation, v_day);
    RETURN jsonb_build_object('allowed', true, 'operation', p_operation,
        'request_key', 'usage_' || v_token::text,
        'used', v_count, 'remaining', v_limit-v_count-1,
        'limit', v_limit, 'renews_at', v_next);
END $$;

CREATE OR REPLACE FUNCTION public.brother_matrizes_finalize_action(
    p_user_id uuid, p_operation text, p_request_key text, p_success boolean,
    p_fingerprint text DEFAULT NULL
) RETURNS boolean LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE
    v_id uuid;
    v_state text;
    v_day date;
    v_limit integer;
    v_count bigint;
    v_duplicate boolean;
BEGIN
    IF p_user_id IS NULL OR p_success IS NULL
       OR p_request_key IS NULL OR p_request_key !~
          '^usage_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR (p_fingerprint IS NOT NULL AND
         p_fingerprint !~ '^[0-9a-f]{64}$')
    THEN RAISE EXCEPTION 'Reserva inválida.'; END IF;
    SELECT limit_per_day INTO v_limit FROM public.brother_matrizes_daily_limits
    WHERE operation = p_operation;
    IF NOT FOUND THEN RAISE EXCEPTION 'Operação inválida.'; END IF;
    v_id := substring(p_request_key FROM 7)::uuid;
    SELECT state, local_day INTO v_state, v_day
    FROM public.brother_matrizes_action_usage
    WHERE request_id = v_id AND user_id = p_user_id AND operation = p_operation
    FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Reserva inexistente.'; END IF;
    IF v_state = 'completed' THEN RETURN true; END IF;
    IF v_state = 'failed' THEN RETURN false; END IF;
    IF p_success THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtext(p_user_id::text),
            pg_catalog.hashtext(p_operation || '/' || v_day::text));
        IF p_fingerprint IS NOT NULL THEN
            SELECT EXISTS(SELECT 1 FROM public.brother_matrizes_action_usage
            WHERE user_id=p_user_id AND local_day=v_day
             AND fingerprint=p_fingerprint AND state='completed')
            INTO v_duplicate;
            IF v_duplicate THEN
                UPDATE public.brother_matrizes_action_usage
                SET state='failed', finalized_at=now() WHERE request_id=v_id;
                RETURN false;
            END IF;
        END IF;
        SELECT count(*) INTO v_count FROM public.brother_matrizes_action_usage
        WHERE user_id = p_user_id AND operation = p_operation
           AND local_day = v_day AND state = 'completed';
        IF v_count >= v_limit THEN RAISE EXCEPTION 'Limite diário atingido.'; END IF;
    END IF;
    UPDATE public.brother_matrizes_action_usage
    SET state = CASE WHEN p_success THEN 'completed' ELSE 'failed' END,
        fingerprint = CASE WHEN p_success THEN p_fingerprint ELSE NULL END,
        finalized_at = now() WHERE request_id = v_id;
    RETURN p_success;
END $$;

CREATE OR REPLACE FUNCTION public.brother_matrizes_action_status(
    p_user_id uuid
) RETURNS jsonb LANGUAGE sql STABLE SET search_path = '' AS $$
  SELECT jsonb_build_object(
    'timezone', 'America/Sao_Paulo',
    'renews_at', (((now() AT TIME ZONE 'America/Sao_Paulo')::date + 1)::timestamp
                AT TIME ZONE 'America/Sao_Paulo'),
    'items', coalesce(jsonb_agg(jsonb_build_object(
        'operation', l.operation, 'used', coalesce(u.used,0),
        'limit', l.limit_per_day, 'remaining',
        greatest(0,l.limit_per_day-coalesce(u.used,0))) ORDER BY l.operation),'[]'::jsonb)
  )
  FROM public.brother_matrizes_daily_limits l
  LEFT JOIN LATERAL (
    SELECT count(*)::int AS used FROM public.brother_matrizes_action_usage a
    WHERE a.user_id=p_user_id AND a.operation=l.operation
      AND a.local_day=(now() AT TIME ZONE 'America/Sao_Paulo')::date
      AND a.state='completed'
  ) u ON true
$$;

REVOKE ALL ON FUNCTION public.brother_matrizes_reserve_action(uuid,text) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.brother_matrizes_finalize_action(uuid,text,text,boolean,text) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.brother_matrizes_action_status(uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_reserve_action(uuid,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_finalize_action(uuid,text,text,boolean,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_action_status(uuid) TO service_role;
