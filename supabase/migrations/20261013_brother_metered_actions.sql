-- Brother Matrizes only. Additive migration: no change to billing, trial,
-- existing import quota, Bible EBD tables or subscription data.
-- Apply in Supabase before deploying the matching brother-usage Edge Function.
CREATE TABLE IF NOT EXISTS public.brother_matrizes_action_usage (
    request_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation text NOT NULL CHECK (operation IN
        ('rar_open', 'rar_send', 'telegram_search', 'dafont_search')),
    period_start date NOT NULL,
    state text NOT NULL DEFAULT 'reserved'
        CHECK (state IN ('reserved', 'completed', 'failed')),
    created_at timestamptz NOT NULL DEFAULT now(),
    finalized_at timestamptz
);

CREATE INDEX IF NOT EXISTS brother_action_usage_lookup_idx
ON public.brother_matrizes_action_usage
(user_id, operation, period_start, state, created_at);

ALTER TABLE public.brother_matrizes_action_usage ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.brother_matrizes_action_usage FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.brother_matrizes_action_usage TO service_role;

-- Reservation is atomic per Google user/action/month and counts pending slots
-- for no longer than 30 minutes. Clock, identity and totals are server owned.
CREATE OR REPLACE FUNCTION public.brother_matrizes_reserve_action(
    p_user_id uuid, p_operation text
) RETURNS jsonb LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE
    v_month date := date_trunc('month', now() AT TIME ZONE 'UTC')::date;
    v_limit integer;
    v_count bigint;
    v_token uuid := gen_random_uuid();
BEGIN
    IF p_user_id IS NULL THEN RAISE EXCEPTION 'Identidade inválida.'; END IF;
    v_limit := CASE p_operation
        WHEN 'rar_open' THEN 5
        WHEN 'rar_send' THEN 5
        WHEN 'telegram_search' THEN 5
        WHEN 'dafont_search' THEN 10
        ELSE NULL END;
    IF v_limit IS NULL THEN RAISE EXCEPTION 'Operação inválida.'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(p_user_id::text),
        pg_catalog.hashtext(p_operation || '/' || v_month::text));
    SELECT count(*) INTO v_count
    FROM public.brother_matrizes_action_usage
    WHERE user_id = p_user_id AND operation = p_operation
        AND period_start = v_month AND
        (state = 'completed' OR
            (state = 'reserved' AND created_at > now() - interval '30 minutes'));
    IF v_count >= v_limit THEN
        RETURN jsonb_build_object('allowed', false, 'operation', p_operation,
            'remaining', 0, 'limit', v_limit,
            'renewal_on', (v_month + interval '1 month')::date);
    END IF;
    INSERT INTO public.brother_matrizes_action_usage
        (request_id, user_id, operation, period_start)
    VALUES (v_token, p_user_id, p_operation, v_month);
    RETURN jsonb_build_object('allowed', true, 'operation', p_operation,
        'request_key', 'usage_' || v_token::text,
        'remaining', v_limit - v_count - 1, 'limit', v_limit,
        'renewal_on', (v_month + interval '1 month')::date);
END $$;

CREATE OR REPLACE FUNCTION public.brother_matrizes_finalize_action(
    p_user_id uuid, p_operation text, p_request_key text, p_success boolean
) RETURNS boolean LANGUAGE plpgsql SET search_path = '' AS $$
DECLARE
    v_id uuid;
    v_state text;
    v_month date;
    v_limit integer;
    v_count bigint;
BEGIN
    IF p_user_id IS NULL OR p_success IS NULL
       OR p_request_key IS NULL OR p_request_key !~
          '^usage_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    THEN RAISE EXCEPTION 'Reserva inválida.'; END IF;
    v_limit := CASE p_operation
        WHEN 'rar_open' THEN 5
        WHEN 'rar_send' THEN 5
        WHEN 'telegram_search' THEN 5
        WHEN 'dafont_search' THEN 10
        ELSE NULL END;
    IF v_limit IS NULL THEN RAISE EXCEPTION 'Operação inválida.'; END IF;
    v_id := substring(p_request_key FROM 7)::uuid;
    SELECT state, period_start INTO v_state, v_month
    FROM public.brother_matrizes_action_usage
    WHERE request_id = v_id AND user_id = p_user_id AND operation = p_operation
    FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Reserva inexistente.'; END IF;
    IF v_state = 'completed' THEN RETURN true; END IF;
    IF v_state = 'failed' THEN RETURN false; END IF;
    IF p_success THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtext(p_user_id::text),
            pg_catalog.hashtext(p_operation || '/' || v_month::text));
        SELECT count(*) INTO v_count
        FROM public.brother_matrizes_action_usage
        WHERE user_id = p_user_id AND operation = p_operation
            AND period_start = v_month AND state = 'completed';
        IF v_count >= v_limit THEN
            RAISE EXCEPTION 'Limite mensal atingido.'; END IF;
    END IF;
    UPDATE public.brother_matrizes_action_usage
    SET state = CASE WHEN p_success THEN 'completed' ELSE 'failed' END,
        finalized_at = now()
    WHERE request_id = v_id;
    RETURN p_success;
END $$;

REVOKE ALL ON FUNCTION public.brother_matrizes_reserve_action(uuid, text)
FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.brother_matrizes_finalize_action(uuid, text, text, boolean)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_reserve_action(uuid, text)
TO service_role;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_finalize_action(uuid, text, text, boolean)
TO service_role;
