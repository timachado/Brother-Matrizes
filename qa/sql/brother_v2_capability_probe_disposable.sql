-- QA/DISPOSABLE POSTGRES ONLY. Not a production migration.
-- An authorized staging project must install/review this separately.
-- SECURITY INVOKER, read-only system catalog checks, not quota writes.
CREATE OR REPLACE FUNCTION public.brother_matrizes_v2_capability_probe()
RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path TO ''
AS $probe$
  SELECT pg_catalog.jsonb_build_object(
    'free_v2',
      pg_catalog.to_regclass('public.brother_matrizes_import_usage') IS NOT NULL
      AND pg_catalog.to_regprocedure(
        'public.brother_matrizes_reserve_import_v2(uuid,text,uuid)'
      ) IS NOT NULL
      AND NOT COALESCE(pg_catalog.has_function_privilege(
        'anon',
        pg_catalog.to_regprocedure(
          'public.brother_matrizes_reserve_import_v2(uuid,text,uuid)'
        ), 'EXECUTE'), TRUE)
      AND NOT COALESCE(pg_catalog.has_function_privilege(
        'authenticated',
        pg_catalog.to_regprocedure(
          'public.brother_matrizes_reserve_import_v2(uuid,text,uuid)'
        ), 'EXECUTE'), TRUE),
    'pro_v2',
      pg_catalog.to_regclass(
        'public.brother_matrizes_pro_import_receipts'
      ) IS NOT NULL
      AND COALESCE((
        SELECT c.relrowsecurity
        FROM pg_catalog.pg_class c
        WHERE c.oid = pg_catalog.to_regclass(
          'public.brother_matrizes_pro_import_receipts'
        )
      ), FALSE)
      AND pg_catalog.to_regprocedure(
        'public.brother_matrizes_reserve_pro_import_v2(uuid,text,uuid,text)'
      ) IS NOT NULL
      AND pg_catalog.to_regprocedure(
        'public.brother_matrizes_finalize_pro_import_v2(uuid,text,uuid,boolean)'
      ) IS NOT NULL
      AND NOT COALESCE(pg_catalog.has_function_privilege(
        'anon',
        pg_catalog.to_regprocedure(
          'public.brother_matrizes_reserve_pro_import_v2(uuid,text,uuid,text)'
        ), 'EXECUTE'), TRUE)
      AND NOT COALESCE(pg_catalog.has_function_privilege(
        'authenticated',
        pg_catalog.to_regprocedure(
          'public.brother_matrizes_reserve_pro_import_v2(uuid,text,uuid,text)'
        ), 'EXECUTE'), TRUE)
  );
$probe$;
REVOKE ALL ON FUNCTION public.brother_matrizes_v2_capability_probe()
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_v2_capability_probe()
TO service_role;
