-- Brother Matrizes | monthly limits for navigation and external handoffs.
-- Additive migration: preserves the existing 3-font/5-matrix monthly counters,
-- payments, license grants and the unused legacy daily tables untouched.
-- All monthly cycles here match the existing import cycle: UTC calendar month.
-- Public API access is denied; only the Google-verified service_role edge can call RPCs.
CREATE TABLE IF NOT EXISTS public.brother_matrizes_monthly_action_limits (
    operation text PRIMARY KEY CHECK (operation IN (
        'rar_receive', 'telegram_search', 'dafont_search'
    )),
    limit_per_month integer NOT NULL CHECK (limit_per_month BETWEEN 1 AND 100000),
    updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO public.brother_matrizes_monthly_action_limits(operation, limit_per_month)
VALUES ('rar_receive', 30), ('telegram_search', 60), ('dafont_search', 60)
ON CONFLICT (operation) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.brother_matrizes_monthly_action_usage (
    request_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation text NOT NULL
        REFERENCES public.brother_matrizes_monthly_action_limits(operation),
    month_start date NOT NULL,
    state text NOT NULL DEFAULT 'reserved'
        CHECK (state IN ('reserved', 'completed', 'failed')),
    created_at timestamptz NOT NULL DEFAULT now(),
    finalized_at timestamptz
);
CREATE INDEX IF NOT EXISTS brother_monthly_action_count_idx
    ON public.brother_matrizes_monthly_action_usage
    (user_id, operation, month_start, state, created_at);

ALTER TABLE public.brother_matrizes_monthly_action_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.brother_matrizes_monthly_action_usage ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.brother_matrizes_monthly_action_limits FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.brother_matrizes_monthly_action_usage FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.brother_matrizes_monthly_action_limits TO service_role;
GRANT SELECT, INSERT, UPDATE ON public.brother_matrizes_monthly_action_usage TO service_role;

CREATE OR REPLACE FUNCTION public.brother_matrizes_reserve_monthly_action(
    p_user_id uuid, p_operation text
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE
    v_month date := date_trunc('month', now() AT TIME ZONE 'UTC')::date;
    v_next timestamptz := (date_trunc('month', now() AT TIME ZONE 'UTC')
        + interval '1 month') AT TIME ZONE 'UTC';
    v_limit integer;
    v_count bigint;
    v_token uuid := gen_random_uuid();
BEGIN
    IF p_user_id IS NULL THEN RAISE EXCEPTION 'Identidade inválida.'; END IF;
    SELECT limit_per_month INTO v_limit
      FROM public.brother_matrizes_monthly_action_limits
     WHERE operation = p_operation;
    IF NOT FOUND THEN RAISE EXCEPTION 'Operação inválida.'; END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(p_user_id::text),
        pg_catalog.hashtext(p_operation || '/' || v_month::text)
    );
    SELECT count(*) INTO v_count
      FROM public.brother_matrizes_monthly_action_usage
     WHERE user_id = p_user_id AND operation = p_operation
       AND month_start = v_month
       AND (state = 'completed'
         OR (state = 'reserved' AND created_at > now() - interval '30 minutes'));

    IF v_count >= v_limit THEN
        RETURN jsonb_build_object(
            'allowed', false, 'operation', p_operation, 'used', v_count,
            'remaining', 0, 'limit', v_limit, 'period', 'monthly',
            'renews_at', v_next
        );
    END IF;

    INSERT INTO public.brother_matrizes_monthly_action_usage
        (request_id, user_id, operation, month_start)
    VALUES (v_token, p_user_id, p_operation, v_month);

    RETURN jsonb_build_object(
        'allowed', true, 'operation', p_operation, 'request_key',
        'usage_' || v_token::text, 'used', v_count, 'remaining',
        v_limit - v_count - 1, 'limit', v_limit, 'period', 'monthly',
        'renews_at', v_next
    );
END $$;

CREATE OR REPLACE FUNCTION public.brother_matrizes_finalize_monthly_action(
    p_user_id uuid, p_operation text, p_request_key text, p_success boolean
) RETURNS boolean LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE
    v_id uuid;
    v_state text;
    v_month date;
    v_limit integer;
    v_count bigint;
BEGIN
    IF p_user_id IS NULL OR p_operation IS NULL OR p_success IS NULL OR
       p_request_key IS NULL OR p_request_key !~
       '^usage_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    THEN RAISE EXCEPTION 'Reserva inválida.'; END IF;

    SELECT limit_per_month INTO v_limit
      FROM public.brother_matrizes_monthly_action_limits
     WHERE operation = p_operation;
    IF NOT FOUND THEN RAISE EXCEPTION 'Operação inválida.'; END IF;

    v_id := substring(p_request_key FROM 7)::uuid;
    SELECT state, month_start INTO v_state, v_month
      FROM public.brother_matrizes_monthly_action_usage
     WHERE request_id = v_id AND user_id = p_user_id
       AND operation = p_operation FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Reserva inexistente.'; END IF;
    IF v_state = 'completed' THEN RETURN true; END IF;
    IF v_state = 'failed' THEN RETURN false; END IF;

    IF p_success THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtext(p_user_id::text),
            pg_catalog.hashtext(p_operation || '/' || v_month::text)
        );
        SELECT count(*) INTO v_count
          FROM public.brother_matrizes_monthly_action_usage
         WHERE user_id = p_user_id AND operation = p_operation
           AND month_start = v_month AND state = 'completed';
        IF v_count >= v_limit THEN
            RAISE EXCEPTION 'Limite mensal atingido.';
        END IF;
    END IF;

    UPDATE public.brother_matrizes_monthly_action_usage
       SET state = CASE WHEN p_success THEN 'completed' ELSE 'failed' END,
           finalized_at = now()
     WHERE request_id = v_id;
    RETURN p_success;
END $$;

CREATE OR REPLACE FUNCTION public.brother_matrizes_monthly_action_status(
    p_user_id uuid
) RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $$
    SELECT jsonb_build_object(
        'period', 'monthly', 'timezone', 'UTC',
        'renews_at', (date_trunc('month', now() AT TIME ZONE 'UTC')
                     + interval '1 month') AT TIME ZONE 'UTC',
        'items', coalesce(jsonb_agg(jsonb_build_object(
            'operation', l.operation, 'used', coalesce(u.used, 0),
            'limit', l.limit_per_month,
            'remaining', greatest(0, l.limit_per_month - coalesce(u.used, 0))
        ) ORDER BY l.operation), '[]'::jsonb)
    )
    FROM public.brother_matrizes_monthly_action_limits l
    LEFT JOIN LATERAL (
        SELECT count(*)::integer AS used
          FROM public.brother_matrizes_monthly_action_usage a
         WHERE a.user_id = p_user_id AND a.operation = l.operation
           AND a.month_start = date_trunc('month', now() AT TIME ZONE 'UTC')::date
           AND a.state = 'completed'
    ) u ON true
$$;

REVOKE ALL ON FUNCTION public.brother_matrizes_reserve_monthly_action(uuid,text)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.brother_matrizes_finalize_monthly_action(uuid,text,text,boolean)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.brother_matrizes_monthly_action_status(uuid)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_reserve_monthly_action(uuid,text)
    TO service_role;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_finalize_monthly_action(uuid,text,text,boolean)
    TO service_role;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_monthly_action_status(uuid)
    TO service_role;
