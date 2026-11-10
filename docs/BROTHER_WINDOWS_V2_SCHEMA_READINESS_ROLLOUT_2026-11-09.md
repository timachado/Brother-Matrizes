# Brother Matrizes — configuração V2 segura e verificada antes de reservar

**Estado:** código/QA na PR #226. Não foi feito deploy de schema, Edge Function,
flag de produção, autenticação real, publicação nem mudança de cotas.

## Lacuna resolvida na branch

Antes, `preflight_v2` marcava `ready=true` apenas se a flag de ambiente
Free/Pro correspondente estivesse ligada. Isso NÃO demonstra que a RPC SQL
está implantada. O Windows poderia então criar `RESERVE_UNKNOWN` no
journal e receber erro de API `reserve_v2`/Pro ausente.

Agora o servidor, APÓS validar o JWT com Supabase Auth e obter o entitlement
seguro, exige dois sinais independentes:

1. `BROTHER_FREE_V2_ENABLED` ou `BROTHER_PRO_V2_ENABLED` explicitamente
   `true` na Edge Function. Ausente = OFF, sem consulta adicional.
2. `admin.rpc("brother_matrizes_v2_capability_probe")` feita com service_role.
   A RPC somente leitura é `SECURITY INVOKER` e confere no catálogo PostgreSQL
   que as funções/tabelas V2 existem com assinaturas corretas, RLS nos
   recibos Pro e ausência de EXECUTE para clientes nos métodos de reserva.

Qualquer erro de RPC, snapshot ausente, strings "true" ou banco incompleto
gera `ready=false`, SEM reserva, arquivo, journal, privilégio ou bypass
de quota. Essa prova é um diagnóstico estrutural, **não** uma aprovação
completa de reserva/finalização, identidade, concorrência e cobranças.

## Evidências isoladas

- Testes Deno do pure helper exercitam Free e Pro, flags ON/OFF, banco
  ausente, valores falsificados e seleções de protocolo incorretas.
- `qa/sql/brother_v2_capability_probe_disposable.sql` é apenas fixture.
  Os workflows PostgreSQL Free e Pro criam bancos Docker diferentes,
  instalam a RPC após seu respectivo schema e verificam exatamente
  `free_v2=true,pro_v2=false` ou `free_v2=false,pro_v2=true`.
- Ambos os bancos recusam execução do probe a `anon`/`authenticated`;
  `service_role` obtém apenas JSON booleana e não escreve nada.

## Etapas ainda necessárias fora desta branch

- **Staging exclusivo Brother Matrizes:** a conexão Supabase disponível
  aponta para produção `dwpcddiramxlhavdmmyn`; o projeto Matrix Engine
  não pode ser reutilizado como ambiente de testes Brother. Não alterar
  produção V1, tabelas, Redirect URLs, assinaturas, licenças ou outras apps.
  Em plano gratuito, criar outro projeto requer verificar limites da conta.
- Executar a migração de staging pelo Supabase CLI após revisão, instalar
  ambas RPCs Free/Pro e a RPC de leitura (criar arquivo de migração pelo
  CLI, nunca executar este fixture QA por engano na produção), validar
  RLS, grants, pagamento/entitlement, OAuth Google e recibos.
- Fazer deploy somente em staging e ativar flags do estágio individualmente.
  Provar idempotência e compatibilidade V1 de Android + Windows no
  mesmo banco descartável antes de qualquer avaliação de produção.
- O pipeline comercial de assinatura requer certificado OV real e ambiente
  HSM/SignTool protegido. Não assinar QA nem publicar o EXE sem release.

Nenhum certificado, chave, senha de usuário ou service_role é necessário
para rodar a suíte isolada.

### Reforço de finalização (mesma revisão)

A leitura de readiness agora também exige:
- Free V2: finalizador legado Android `brother_matrizes_finalize_import(uuid,text,text,boolean)`
  presente e sem `EXECUTE` para `anon`/`authenticated`. A assinatura foi
  conferida **apenas por SELECT em `pg_catalog.pg_proc` na produção**, sem
  consultar ou alterar qualquer dado comercial.
- Pro V2: `brother_matrizes_finalize_pro_import_v2` presente,
  execução negada a clientes, tabela de recibos Pro com RLS habilitado
  e leitura da tabela negada a `anon`/`authenticated`.
- Os testes PostgreSQL Docker removem cada finalizador em transação
  `ROLLBACK` e exigem `ready=false`. Nunca são executados no Supabase.
