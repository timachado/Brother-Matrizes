# Brother Matrizes Windows — reserva idempotente V2 (pré-homologação)

**Status: apenas proposta e código na branch. NÃO EXECUTAR esta SQL na produção.**

PR: https://github.com/timachado/Brother-Matrizes/pull/226

## Resultado do diagnóstico de produção (somente leitura)

O schema existente `public.brother_matrizes_import_usage` contém
`request_id uuid` como chave primária, `user_id`, `operation`,
`period_start`, `state`, `created_at` e `finalized_at`.
As RPCs `brother_matrizes_reserve_import` e
`brother_matrizes_finalize_import` são usadas pelo Android comercial.

A versão anterior gera `request_id` aleatório no servidor. Se
a primeira resposta se perder, o cliente não conhece a chave remota.
Este desenho V2 usa o UUIDv4 gerado pelo aplicativo e persistido **antes**
do envio como `request_id` do registro, sem criar uma segunda tabela
de contadores nem alterar assinaturas já homologadas do Android.

**Nenhuma migração foi aplicada.** Este documento contém SQL proposta
para revisão, não um arquivo de migração do Supabase CLI. Antes de criar
migração oficial, executar `supabase migration new` num checkout com
CLI instalado, homologar em banco descartável com pgTAP e revisar
advisors, RLS, concorrência e permissões. A aprovação para implantar
precisa ser uma etapa separada.

## Porta de ativação Free V2 independente (09/11/2026)

**Proteção incluída somente no código da PR, ainda sem deploy:** o roteador `brother-usage/index.ts` aceita `reserve_v2` apenas quando a variável **de servidor** `BROTHER_FREE_V2_ENABLED` é exatamente `true`. Ausente, `false`, `1` ou qualquer outra configuração, responde HTTP 503 antes de acessar a autoridade de licença ou o RPC. A operação **não** é ativada por login Google, chave publicável, flag Windows ou pela mera existência da função SQL. O arquivo `free-v2-route.ts` reforça a negação antes de chamar o RPC, mesmo se o roteador for alterado depois.

O bloqueio Free V2 é **independente** de `BROTHER_PRO_V2_ENABLED`: não habilita Pro, não modifica `reserve` e `finalize` legados do Android e não cria licença. Testes sem rede `free-v2-route_test.ts` verificam ausência de acesso ao banco com rollout desligado, UUID idêntico no replay e rejeição de resposta Pro para reserva Free.

Para homologação futura, revisar as RPCs SQL e RLS num **PostgreSQL descartável** primeiro; depois exigir projeto autorizado, conta Google de QA, evidência do allowlist OAuth e auditoria de recuperação. Não configurar a flag no Supabase de produção até aprovação específica da mudança comercial. A revisão do ambiente real e o deploy não foram executados aqui.

## Contrato aditivo

Nova ação HTTP:

```json
{"action":"reserve_v2","operation":"import_matrix","client_request_id":"12345678-1234-4234-8234-123456789abc"}
```

- O servidor autentica o usuário Supabase Google como na V1.
- **Somente plano Gratuito** nessa fase: para Pro a V2 falha fechada
  (HTTP 503), pois `pro_UUID` ainda é efêmero. A versão Android
  continua usando o mesmo endpoint V1, sem qualquer alteração.
- Para nova reserva Gratuita: `usage_<client_request_id>`,
  `allowed:true`, `state:reserved`, `replayed:false`,
  `remaining` coerente com a quota mensal.
- Para a MESMA conta + operação + UUID: devolver **a mesma chave**;
  `replayed:true`, sem inserir outro registro. Somente uma reserva
  `reserved`, dentro do mês UTC atual e dos 30 minutos de validade
  pode retornar `allowed:true`.
- Se já houve `completed`, `failed` ou expirou, retorno
  `allowed:false` e nenhuma nova quota.
- UUID reutilizado por outra conta ou operação: falha fechada;
  jamais devolver dados de outra conta.
- A verificação de estado existente é **antes** da contagem mensal
  e da criação de nova reserva. A operação `finalize` V1 recebe
  a chave `usage_UUID` sem alteração.
- A chave do cliente é um identificador de tentativa, **não concede
  assinatura nem quota**. Somente o backend autenticado decide.

## SQL proposta para banco de homologação DESCARTÁVEL

```sql
-- NÃO EXECUTAR NA PRODUÇÃO. Proposta para migração criada pelo CLI.
-- Sem ALTER TABLE, TRUNCATE, DELETE ou alterações no Android V1.
CREATE OR REPLACE FUNCTION public.brother_matrizes_reserve_import_v2(
  p_user_id uuid,
  p_operation text,
  p_client_request_id uuid
) RETURNS jsonb
LANGUAGE plpgsql
SET search_path TO ''
AS $function$
DECLARE
  v_period date := date_trunc('month', now() AT TIME ZONE 'UTC')::date;
  v_limit integer;
  v_used bigint;
  v_existing public.brother_matrizes_import_usage%ROWTYPE;
BEGIN
  IF p_user_id IS NULL
     OR p_client_request_id IS NULL
     OR p_operation NOT IN ('import_font','import_matrix')
     -- UUIDv4 only, matching the persisted DesktopArchiveQuotaJournal.localId.
     OR p_client_request_id::text !~
       '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
  THEN
    RAISE EXCEPTION 'Tentativa de importação inválida.';
  END IF;

  -- Serialize identical retries for the SAME verified user.
  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtext(p_user_id::text),
    pg_catalog.hashtext('brother-v2/' || p_client_request_id::text)
  );

  -- IMPORTANT: global PK can match a legacy server-minted UUID (very
  -- unlikely). A foreign owner/op must be rejected, never disclosed.
  SELECT * INTO v_existing
  FROM public.brother_matrizes_import_usage
  WHERE request_id = p_client_request_id
  FOR UPDATE;

  IF FOUND THEN
    IF v_existing.user_id <> p_user_id
       OR v_existing.operation <> p_operation THEN
      RAISE EXCEPTION 'Tentativa pertence a outra operação.';
    END IF;
    -- Never resurrect expired, finished, failed or prior-month quota.
    IF v_existing.state = 'reserved'
       AND v_existing.period_start = v_period
       AND v_existing.created_at > now() - INTERVAL '30 minutes' THEN
      RETURN pg_catalog.jsonb_build_object(
        'allowed', true,
        'operation', p_operation,
        'request_key', 'usage_' || p_client_request_id::text,
        'state', 'reserved', 'replayed', true, 'remaining', null
      );
    END IF;
    RETURN pg_catalog.jsonb_build_object(
      'allowed', false,
      'operation', p_operation,
      'request_key', 'usage_' || p_client_request_id::text,
      'state', v_existing.state,
      'replayed', true
    );
  END IF;

  v_limit := CASE WHEN p_operation = 'import_font' THEN 3 ELSE 5 END;

  -- Identical quota lock to the V1 RPC; preserves mixed Android/Windows
  -- serializable monthly counter behavior without altering V1 clients.
  PERFORM pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtext(p_user_id::text),
    pg_catalog.hashtext(p_operation || '/' || v_period::text)
  );

  SELECT count(*) INTO v_used
  FROM public.brother_matrizes_import_usage
  WHERE user_id = p_user_id
    AND operation = p_operation
    AND period_start = v_period
    AND (state = 'completed'
      OR (state = 'reserved' AND created_at > now() - INTERVAL '30 minutes'));

  IF v_used >= v_limit THEN
    RETURN pg_catalog.jsonb_build_object(
      'allowed', false, 'operation', p_operation, 'remaining', 0
    );
  END IF;

  -- Global request_id primary key prevents a duplicated row even
  -- across installations; an unexpected PK conflict fails the RPC.
  INSERT INTO public.brother_matrizes_import_usage
    (request_id, user_id, operation, period_start, state)
  VALUES
    (p_client_request_id, p_user_id, p_operation, v_period, 'reserved');

  RETURN pg_catalog.jsonb_build_object(
    'allowed', true, 'operation', p_operation,
    'request_key', 'usage_' || p_client_request_id::text,
    'state', 'reserved', 'replayed', false,
    'remaining', (v_limit - v_used - 1)
  );
END
$function$;

-- The Edge Function invokes this via service_role after verifying getUser.
-- Never let anon/authenticated call a function accepting p_user_id directly.
REVOKE ALL ON FUNCTION public.brother_matrizes_reserve_import_v2(
  uuid, text, uuid
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.brother_matrizes_reserve_import_v2(
  uuid, text, uuid
) TO service_role;
```

### Critérios obrigatórios em banco descartável antes de aprovação

1. Duas tentativas concorrentes com o mesmo UUIDv4, usuário e
   operação deixam **uma linha** e a mesma chave.
2. Quota 3 fontes/5 matrizes do mês UTC é preservada quando Android
   V1 e Windows V2 fazem reservas simultâneas.
3. Repetir UUID após `finalize` ou prazo de 30 minutos não reabre
   autorização.
4. Usuário A tentando o UUID de B ou mudando a operação é rejeitado.
5. Nenhum grant de execução às funções V2 por `anon` ou
   `authenticated`; validação com `getUser` obrigatória na Edge
   Function. Service-role nunca é enviada ao Windows.
6. Requisição repetida durante falha de internet mantém
   `RESERVE_UNKNOWN` no journal Windows até reconciliação explícita,
   sem gravar arquivo de cliente.
7. Assinatura e testes da V1 do Android inalterados.

**Bloqueios restantes:** reserva idempotente Pro com recibo durável,
journal de propriedade local de arquivos, servidor de
`finalize` V2 com decisão persistida, OAuth Google Windows
homologado e auditoria de queda de energia/ACL do PC.

**Estado atual:** implementação incremental na branch GitHub com
servidor/banco falsos; nenhuma migração, RPC real ou deploy.
