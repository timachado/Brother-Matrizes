# Brother Matrizes Windows — diagnóstico autenticado de reservas (QA)

**Revisão:** [PR #226](https://github.com/timachado/Brother-Matrizes/pull/226),
branch `feature/windows-fiel-android-1-11-8-20261106`.

**Política:** implementação em GitHub **não implantada** no
`brother-usage` de produção. `main`, Android comercial,
WooCommerce/Efí, assinatura Pro, reservas existentes, site e dados de
clientes permanecem inalterados.

## Diagnóstico read-only do serviço atual

O serviço comercial `brother-usage` de produção foi verificado por
consultas **exclusivamente de metadados e definição** no Supabase
`dwpcddiramxlhavdmmyn` (nenhum dado de usuário lido/modificado).

- A tabela `public.brother_matrizes_import_usage` possui
  `request_id` UUID gerado pelo **servidor**,
  `user_id`, `operation`, `period_start`, `state`,
  `created_at` e `finalized_at`. RLS está habilitado.
- A função SQL `brother_matrizes_reserve_import` gera
  `gen_random_uuid()` e retorna `usage_<UUID>` **somente como
  resposta**, sem aceitar identificador da tentativa do cliente.
- A função `brother_matrizes_finalize_import` recebe
  `user_id`, `operation`, `request_key` e `success`;
  estados de quota são `reserved`, `completed`, `failed`.
- Assim, **perder a resposta do reserve não é recuperável com
  segurança** pelo contrato atual: não se sabe qual UUID recebeu o
  pedido. Repetir `reserve` pode criar outra reserva. Isto não é
  resolvido pela consulta de status deste ciclo.
- As reservas `pro_<UUID>` são efêmeras no endpoint legado, não
  persistidas na tabela de quotas. Não é seguro inventar estado Pro.

## Protocolo aditivo proposto (somente branch)

`supabase/functions/brother-usage/index.ts` passa a aceitar,
**após validar o token Supabase e o usuário**, um novo corpo JSON:

```json
{"action":"status","operation":"import_matrix","request_key":"usage_12345678-1234-1234-1234-123456789abc"}
```

O código lê apenas `state`, filtrando simultaneamente:
`request_id`, `user_id` **autenticado** e `operation`.
O resultado pode ser:

- `found:true, state:reserved/completed/failed`
- `found:false`, sem afirmar que a cota foi liberada
- HTTP 400/401/503 para entrada/identidade/serviço inválidos

`pro_` não é consultável. Sem `INSERT`, `UPDATE`,
`DELETE`, tentativa automática de reserve/finalize ou exposição
de dados de outro usuário.

**Restrições importantes:** o endpoint novo ainda não foi implantado;
o código e o token autenticado continuam sendo requeridos antes de
qualquer consulta real. O fluxo de quotas do Android oficial
(`reserve/finalize`) foi preservado e não recebeu novas exigências.

### Lado Windows

`DesktopBrotherArchiveUsageGateway.lookupKnown` espelha o protocolo,
sem ser conectado a `FielMain.kt`. Garante apenas o endpoint HTTPS
oficial, `usage_` válido, categoria, chave e campos da resposta,
rejeitando HTTP não 200, redirects, resposta inválida ou estado
desconhecido.

`DesktopArchiveQuotaRecoveryAssessment.inspect` recebe o registro
durável anterior e a leitura opcional do servidor. A função é **pura**
e retorna um diagnóstico; em todos os estados:

- `blocksNewImports = true`
- `permitsAutomaticFinalize = false`
- `permitsAutomaticCleanup = false`

Mesmo quando o servidor responde `completed` ou `failed`,
a aplicação ainda não pode remover o registro: falta confirmar a
propriedade e integridade da gravação local após interrupção.

## Testes isolados

- `supabase/functions/brother-usage/read-status_test.ts` em Deno,
  com função de consulta simulada, verifica identidade, operação,
  reservas encontradas/não encontradas, bloqueio de chaves Pro,
  respostas inválidas e falhas do banco. **Nenhuma conexão ao Supabase**.
- `DesktopBrotherArchiveUsageGatewayTest`: HTTP simulado,
  parâmetros, estados, `found:false`, HTTP 503, payload falso,
  chaves/ operações divergentes, sem rede ou credenciais.
- `DesktopArchiveQuotaRecoveryAssessmentTest`: confirma
  que pendências de `reserve` sem chave, `finalize` sem confirmação,
  falhas remotas e estados desconhecidos não liquidam quotas nem
  apagam o journal, mesmo após reinício.

[GitHub Actions — contrato de consulta de quotas](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-status-qa.yml).

## Etapa obrigatória antes de reativar uma cota desconhecida

É necessária uma versão de reserva **idempotente e autenticada**,
mantendo compatibilidade total com o Android atual:

1. Definir um `client_request_id` UUID aleatório por tentativa,
   criado e persistido **antes** do primeiro envio.
2. Criar um índice único (usuário, operação, `client_request_id`)
   dentro do mesmo banco, com RPC transacional que devolva
   **a mesma reserva** para a mesma tentativa. Verificar locks,
   concorrência, conta e mês de referência antes de gravar;
   não alterar contadores financeiros.
3. Criar consulta autorizada também por identificador do cliente,
   para o caso em que a primeira resposta nunca chegou.
4. Persistir a decisão de `finalize` de forma idempotente.
   Resolver também recibos Pro na autoridade de licenciamento,
   sem gerar acesso de assinatura pelo cliente.
5. Somente após homologação isolada, auditoria de privilégios/RLS,
   testes de reconciliação do servidor e aceite explícito para
   implantação, ativar uma versão comercial Windows.

**Não foram aplicadas migrações nem executadas RPCs com efeito.**
As assinaturas SQL da produção foram somente consultadas.
Não usar esta revisão para cobrar/consumir cotas de clientes.

## Evidência de CI — 07/11/2026

- [Contrato de status #42403888861](https://github.com/timachado/Brother-Matrizes/actions/runs/42403888861):
  `completed/success` no commit `a345b48`, com dois jobs
  aprovados: Deno com base em memória/sem permissões externas,
  e Kotlin/Windows com HTTP simulado e journal imutável.
- [Resultados XML Windows #13586169484](https://github.com/timachado/Brother-Matrizes/actions/runs/42403888861/artifacts/13586169484).
- [Regressão portátil #42404005897](https://github.com/timachado/Brother-Matrizes/actions/runs/42404005897):
  `completed/success` no commit `5451e7b`, incluindo o
  diagnóstico de status de quotas nos testes prioritários, montagem
  da imagem APP-IMAGE e execução/captura das telas no runner Windows.
  Artefatos QA temporários privados:
  [Brother Matrizes Windows Portable QA #13585839410](https://github.com/timachado/Brother-Matrizes/actions/runs/42404005897/artifacts/13585839410)
  (98.263.701 bytes, launcher e runtime completos) e
  [capturas Windows #13585714916](https://github.com/timachado/Brother-Matrizes/actions/runs/42404005897/artifacts/13585714916)
  (237.365 bytes). Podem exigir login GitHub e expiram.
  Nenhuma chamada ao endpoint novo ocorre na UI QA.
- Nada foi implantado no serviço de produção, Android oficial
  ou servidor de licenciamento: estes resultados se referem
  exclusivamente ao código da branch e à QA simulada.
