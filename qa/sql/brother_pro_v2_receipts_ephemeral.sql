-- QA / disposable PostgreSQL ONLY. NOT a Supabase migration.
-- Never apply this file to production; an authorized staging migration must
-- be separately generated and reviewed by the Supabase CLI.
-- No payment, subscription or entitlement-granting columns exist here.
CREATE TABLE public.brother_matrizes_pro_import_receipts (
    request_id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    operation text NOT NULL
      CHECK (operation IN ('import_font', 'import_matrix')),
    entitlement_type text NOT NULL
      CHECK (entitlement_type IN (
        'trial', 'pro_monthly', 'pro_yearly',
        'pro_lifetime', 'pro_lifetime_launch'
      )),
    state text NOT NULL DEFAULT 'reserved'
      CHECK (state IN ('reserved', 'completed', 'failed')),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '30 minutes'),
    finalized_at timestamptz,
    finalize_success boolean,
    protocol_version smallint NOT NULL DEFAULT 2
      CHECK (protocol_version = 2),
    CONSTRAINT brother_pro_receipt_outcome_consistency CHECK (
      (state = 'reserved' AND finalized_at IS NULL AND finalize_success IS NULL)
      OR
      (state = 'completed' AND finalized_at IS NOT NULL AND finalize_success IS TRUE)
      OR
      (state = 'failed' AND finalized_at IS NOT NULL AND finalize_success IS FALSE)
    )
);

CREATE INDEX brother_pro_receipts_user_state_idx
 ON public.brother_matrizes_pro_import_receipts(user_id, operation, state);

ALTER TABLE public.brother_matrizes_pro_import_receipts
 ENABLE ROW LEVEL SECURITY;
-- RLS is defence in depth. API/client roles must not have table grants.
REVOKE ALL ON public.brother_matrizes_pro_import_receipts
 FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE
 ON public.brother_matrizes_pro_import_receipts TO service_role;

CREATE POLICY brother_pro_receipts_service_operations
 ON public.brother_matrizes_pro_import_receipts
 FOR ALL TO service_role USING (true) WITH CHECK (true);

-- Caller must be the verified Edge Function using service_role and
-- p_user_id originating ONLY from the authenticated Supabase getUser response.
-- This function does not authorize Pro: the Edge Function MUST check
-- the trusted brother-entitlement authority before invoking it.
CREATE FUNCTION public.brother_matrizes_reserve_pro_import_v2(
 p_user_id uuid, p_operation text, p_client_request_id uuid, p_plan_code text
) RETURNS jsonb
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO ''
AS $brother_pro_reserve$
DECLARE
 v_existing public.brother_matrizes_pro_import_receipts%ROWTYPE;
 v_key text := 'pro_' || p_client_request_id::text;
BEGIN
 IF p_user_id IS NULL OR p_client_request_id IS NULL
   OR p_operation IS NULL OR
     p_operation NOT IN ('import_font', 'import_matrix')
   OR p_plan_code IS NULL OR p_plan_code NOT IN (
      'trial', 'pro_monthly', 'pro_yearly',
      'pro_lifetime', 'pro_lifetime_launch'
   )
   OR p_client_request_id::text !~
       '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
 THEN
   RAISE EXCEPTION 'Recibo Pro inválido.';
 END IF;

 -- Serialize even a malicious cross-account UUID reuse.
 PERFORM pg_catalog.pg_advisory_xact_lock(
   pg_catalog.hashtext('brother-pro-v2'),
   pg_catalog.hashtext(p_client_request_id::text)
 );

 -- Do not allow one UUID to claim both a Free and a Pro receipt.
 -- The existing Free import-usage table is part of the established schema.
 IF EXISTS (
    SELECT 1 FROM public.brother_matrizes_import_usage
    WHERE request_id = p_client_request_id
 ) THEN
    RAISE EXCEPTION 'Identificador já utilizado em outra modalidade.';
 END IF;

 SELECT * INTO v_existing
 FROM public.brother_matrizes_pro_import_receipts
 WHERE request_id = p_client_request_id
 FOR UPDATE;

 IF FOUND THEN
    IF v_existing.user_id <> p_user_id OR
       v_existing.operation <> p_operation OR
       v_existing.entitlement_type <> p_plan_code THEN
      RAISE EXCEPTION 'Identificador de recibo pertence a outra operação.';
    END IF;
    IF v_existing.state = 'reserved' AND
       v_existing.expires_at > pg_catalog.now() THEN
      RETURN pg_catalog.jsonb_build_object(
        'allowed', true, 'operation', p_operation,
        'request_key', v_key, 'state', 'reserved',
        'replayed', true, 'pro', true, 'remaining', null
      );
    END IF;
    RETURN pg_catalog.jsonb_build_object(
      'allowed', false, 'operation', p_operation,
      'request_key', v_key, 'state', v_existing.state,
      'replayed', true
    );
 END IF;

 INSERT INTO public.brother_matrizes_pro_import_receipts(
   request_id, user_id, operation, entitlement_type, state
 ) VALUES(
   p_client_request_id, p_user_id, p_operation, p_plan_code, 'reserved'
 );

 RETURN pg_catalog.jsonb_build_object(
   'allowed', true, 'operation', p_operation,
   'request_key', v_key, 'state', 'reserved',
   'replayed', false, 'pro', true, 'remaining', null
 );
END
$brother_pro_reserve$;

-- Once a receipt exists, finalization must work even if the subscription
-- has expired: the result is an import outcome, not a new entitlement.
CREATE FUNCTION public.brother_matrizes_finalize_pro_import_v2(
 p_user_id uuid, p_operation text, p_client_request_id uuid, p_success boolean
) RETURNS jsonb
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO ''
AS $brother_pro_finalize$
DECLARE
 v_existing public.brother_matrizes_pro_import_receipts%ROWTYPE;
 v_key text := 'pro_' || p_client_request_id::text;
 v_expected_state text := CASE WHEN p_success THEN 'completed' ELSE 'failed' END;
BEGIN
 IF p_user_id IS NULL OR p_client_request_id IS NULL OR
    p_operation IS NULL OR p_operation NOT IN ('import_font', 'import_matrix') OR
    p_success IS NULL OR
    p_client_request_id::text !~
      '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
 THEN
   RAISE EXCEPTION 'Finalização Pro inválida.';
 END IF;

 -- Row lock serializes concurrent same/outcome or opposite/outcome calls.
 SELECT * INTO v_existing
 FROM public.brother_matrizes_pro_import_receipts
 WHERE request_id = p_client_request_id
 FOR UPDATE;
 IF NOT FOUND OR v_existing.user_id <> p_user_id
   OR v_existing.operation <> p_operation THEN
   RAISE EXCEPTION 'Recibo Pro não corresponde ao usuário e operação.';
 END IF;

 IF v_existing.state <> 'reserved' THEN
    IF v_existing.state = v_expected_state AND
       v_existing.finalize_success IS NOT DISTINCT FROM p_success THEN
      RETURN pg_catalog.jsonb_build_object(
        'ok', true, 'operation', p_operation,
        'request_key', v_key, 'state', v_existing.state,
        'replayed', true
      );
    END IF;
    RETURN pg_catalog.jsonb_build_object(
      'ok', false, 'operation', p_operation,
      'request_key', v_key, 'state', v_existing.state,
      'conflict', true
    );
 END IF;

 -- An expired reservation cannot be successfully committed after expiry.
 -- It may still be explicitly closed as failed, never reactivated.
 IF v_existing.expires_at <= pg_catalog.now() THEN
    UPDATE public.brother_matrizes_pro_import_receipts
    SET state = 'failed',
        finalize_success = false,
        finalized_at = pg_catalog.now()
    WHERE request_id = p_client_request_id;

    IF p_success THEN
      RETURN pg_catalog.jsonb_build_object(
        'ok', false, 'operation', p_operation,
        'request_key', v_key, 'state', 'failed', 'conflict', true
      );
    END IF;
    RETURN pg_catalog.jsonb_build_object(
      'ok', true, 'operation', p_operation,
      'request_key', v_key, 'state', 'failed', 'replayed', false
    );
 END IF;

 UPDATE public.brother_matrizes_pro_import_receipts
 SET state = v_expected_state,
     finalize_success = p_success,
     finalized_at = pg_catalog.now()
 WHERE request_id = p_client_request_id;

 RETURN pg_catalog.jsonb_build_object(
    'ok', true, 'operation', p_operation,
    'request_key', v_key, 'state', v_expected_state,
    'replayed', false
 );
END
$brother_pro_finalize$;

-- PostgreSQL by default grants FUNCTION EXECUTE to PUBLIC.
-- These are server-only APIs, never directly client-callable with p_user_id.
REVOKE ALL ON FUNCTION public.brother_matrizes_reserve_pro_import_v2(
 uuid, text, uuid, text
) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.brother_matrizes_finalize_pro_import_v2(
 uuid, text, uuid, boolean
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_reserve_pro_import_v2(
 uuid, text, uuid, text
) TO service_role;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_finalize_pro_import_v2(
 uuid, text, uuid, boolean
) TO service_role;
