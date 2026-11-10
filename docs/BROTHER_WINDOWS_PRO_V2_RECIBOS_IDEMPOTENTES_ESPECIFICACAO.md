# Brother Matrizes Windows — especificação dos recibos Pro V2

**Situação:** arquitetura proposta, sem implementação comercial, sem
migração, sem ativação de plano e sem alterações na produção.  
**Vinculado ao portão comercial #2:** [Relatório de prontidão 07/11/2026](BROTHER_WINDOWS_RELATORIO_PRONTIDAO_COMERCIAL_2026-11-07.md).  
**Código fonte:** PR #226, branch Windows QA.

## Contrato atual observado

A Edge Function `brother-entitlement` responde ao usuário autenticado
com o plano efetivo confirmado pelo servidor. O acesso Pro inclui:
`trial` (não financeiro), `pro_monthly`, `pro_yearly`,
`pro_lifetime` e `pro_lifetime_launch`. A decisão comercial
continua pertencendo à autoridade de assinaturas, incluindo
eventos WooCommerce/Efí, concessões do proprietário e trial
com horário do banco. **Não reutilizar detalhes de pagamento**
como identificadores de importação.

No protocolo antigo `brother-usage`, a reserva Pro retorna
`pro_<UUID>` sem gravar recibo durável. A mesma tentativa refeita
após perda de resposta pode produzir outra chave sem rastreabilidade;
isso impede a reconciliação segura após crash.

No protocolo Windows `reserve_v2`, a reserva Free continua separada e Pro retorna indisponibilidade. Na branch foi adicionado `reserve_pro_v2` com configuração server-side própria, **desativada por padrão**. A produção permanece inalterada.

## Modelo proposto (não executado)

Uma tabela **dedicada de recibos operacionais Pro**, sem
colunas de cobrança ou autorização de compra:

- `request_id UUID PRIMARY KEY`: UUIDv4 aleatório, persistido
  no Windows antes do primeiro envio;
- `user_id UUID NOT NULL` e
  `operation TEXT NOT NULL` com valores
  `import_font` / `import_matrix`;
- `state`: `reserved`, `completed`, `failed`;
- `entitlement_type` com o código de plano confirmado na
  solicitação pelo `brother-entitlement`, somente para auditoria;
- `created_at`, `expires_at`, `finalized_at` gerados
  e verificados **pelo servidor**;
- `finalize_success` nullable boolean persistindo o resultado
  decidido pela transação, sem permitir mudança posterior;
- registro de ID da tentativa e versão do protocolo, sem
  access token, refresh token, e-mail, nome, pedido, valores,
  credenciais ou dados de matriz.

**O recibo Pro não concede acesso Pro**. A autoridade de
assinatura precisa validar identidade e direito ativo antes
de criar nova reserva. Nenhum dado enviado pelo Windows
pode alterar o plano da conta ou criar trial.

## Regras de segurança obrigatórias

1. Autenticar o bearer e vincular `user_id` a
   `getUser`. Exigir e-mail confirmado/Google conforme
   contrato oficial.
2. Verificar o plano Pro ativo e a identidade do resultado
   do `brother-entitlement` **antes de criar um novo recibo**.
   Tratamento da revogação/trial expirado falha fechado.
3. Inserir recibo no PostgreSQL sob lock transacional e
   constraint única do `request_id`; mesma UUID, mesmo
   usuário e operação retorna mesma chave, sem segunda linha.
4. UUID já usado com outra conta/operação não revela dados
   nem permite importação.
5. Replay de `completed`, `failed` ou `reserved`
   expirado não autoriza nova gravação nem reabre o recibo.
6. `finalize` com resultado igual previamente confirmado
   pode devolver confirmação idempotente. Resultado oposto
   não altera o recibo nem autoriza apagar arquivo.
7. Usar um endpoint autenticado com credenciais apenas
   server-side e RPC com privilégios mínimos; negar execução
   direta a `anon`/`authenticated` quando a RPC aceita
   `p_user_id`. Auditar RLS/grants.
8. O fluxo Android V1 permanece inalterado até que o
   contrato Pro V2 seja homologado e implantado de forma
   separada.
9. Nenhuma recuperação automática exclui arquivos após
   resultado remoto sem journal de ownership local (#4).

## Testes obrigatórios antes da implementação comercial

- Queda de resposta após inserção e repetição da mesma UUID;
  **exatamente uma linha de recibo**.
- Duas JVMs e dois requests HTTP simultâneos de mesma conta/UUID;
  mesma chave, nenhum direito duplicado.
- Assinatura ativa, cancelada, revogada, trial expirado e
  entitlement temporariamente indisponível.
- Conta diferente/ operação diferente tentando reutilizar a UUID;
  resposta bloqueada sem vazamento de dados.
- Finalizar sucesso, falha, repetição igual, repetição contrária
  e resposta perdida após persistência.
- Falha do banco, perda de internet, dupla instância e
  encerramento forçado durante a transação.
- Preservação do acesso Android V1, do contador gratuito e
  da lógica de planos; sem compra, renovação ou upgrade na QA.

## Política de execução e critérios de aceite

Até que todos os critérios sejam comprovados em banco PostgreSQL
descartável e posteriormente em staging autorizado, o portão #2
permanece **ABERTO**. Toda futura migração deverá vir do
Supabase CLI, ser revisada por segurança e possuir plano de
rollback testado. O ambiente de produção não pode ser modificado
automaticamente para cumprir este documento.

**Implementação técnica em QA:** helper `pro-receipt-v2.ts`, roteador puro `pro-v2-route.ts` e integração opt-in na `brother-usage/index.ts`. A configuração `BROTHER_PRO_V2_ENABLED` não foi habilitada: **sem deploy, sem tabela no Supabase e sem chamadas reais**. O Android V1 mantém o fluxo anterior.

[CI Pro V2 #42408075964](https://github.com/timachado/Brother-Matrizes/actions/runs/42408075964):
`completed/success`, verificação de tipos Deno e **10 testes
aprovados, zero falhas**. Casos: plano Pro mensal, trial,
vitalício, identidade divergente, conta sem plano, assinatura
vencida, indisponibilidade da autoridade, recibo falso,
resposta perdida, replay e finalização com resultado imutável.
A primeira tentativa #42408011040 falhou somente na tipagem
de valores literais no teste, corrigida na execução final.

**Próxima implementação técnica recomendada:** tabela de recibos
Pro operacionais com RLS/permissões corretas e RPCs transacionais
de reserva e finalização, sempre em **PostgreSQL descartável**
antes de staging. Depois integrar somente após homologação.
Nenhuma instalação, deploy, cobrança, notificação ou release
é autorizada por esta especificação.

## Homologação SQL isolada — 07/11/2026

- Implementadas [tabela de recibos e RPCs SQL Pro V2 em QA](../../qa/sql/brother_pro_v2_receipts_ephemeral.sql), com RLS, grants para o serviço, reserva UUIDv4 e finalização idempotente. **Este arquivo não é uma migração oficial.**
- [Script de PostgreSQL 16 descartável](../../.github/scripts/brother_pro_v2_ephemeral_pg.sh) verifica transações reais, bloqueios entre quatro conexões simultâneas, finalizações opostas, reserva expirada, identidade divergente e ausência de acesso direto dos papéis públicos.
- [CI #42408650490](https://github.com/timachado/Brother-Matrizes/actions/runs/42408650490): **dois jobs concluídos com sucesso**, incluindo dez testes Deno (zero falhas) e execução SQL exclusivamente no PostgreSQL descartável da CI.
- Não houve alteração na Edge Function comercial, no projeto Supabase, na conta Pro de qualquer cliente ou no Android oficial. O endpoint Pro V2 permanece desativado.
- **Ainda pendente para concluir o portão #2:** gerar migração com Supabase CLI, auditar RLS e compatibilidade de retornos reais de `brother-entitlement`, integrar RPCs no servidor autenticado, testar staging com planos ativos e expirados e obter autorização explícita de implantação.

## Integração do endpoint sob configuração desativada — 07/11/2026

A Edge Function `brother-usage` da **branch GitHub** aceita os formatos:

```json
{"action":"reserve_pro_v2","operation":"import_matrix","client_request_id":"12345678-1234-4234-8234-123456789abc"}
```

```json
{"action":"finalize_pro_v2","operation":"import_matrix","request_key":"pro_12345678-1234-4234-8234-123456789abc","success":true}
```

Ela valida a sessão pelo Supabase `getUser`, exige e-mail confirmado e verifica a variável **`BROTHER_PRO_V2_ENABLED === "true"`** antes de chamar qualquer RPC nova. O valor ausente ou diferente de `true` devolve **503**; não existe fallback que ative Pro V2 automaticamente. **Nenhuma variável foi configurada em produção.**

Para `reserve_pro_v2`, os dados de licença são obtidos pelo próprio servidor mediante GET autenticado ao `brother-entitlement`. O helper confere identidade, e-mail, estado ativo, plano e vencimento usando o relógio do servidor. A conta não fornece `planCode` no corpo da requisição. A resposta dessa autoridade é compatível com os campos previstos em `brother-entitlement/index.ts`, conforme inspeção do **código-fonte** (não é uma homologação com conta Google real).

Para `finalize_pro_v2`, apenas a identidade `getUser` e a propriedade da reserva no RPC são necessárias. Isso permite confirmar o **resultado de uma reserva já existente** após o vencimento do plano, sem criar outra licença ou novo recibo.

**Evidências:** [CI #42411506522](https://github.com/timachado/Brother-Matrizes/actions/runs/42411506522) aprovada com compilação de toda a Edge Function (`deno check`), **7 testes do roteador + 10 testes Pro** e testes SQL sobre PostgreSQL 16 descartável; [guarda Pro #42411509845](https://github.com/timachado/Brother-Matrizes/actions/runs/42411509845) aprovada.

**Bloqueadores remanescentes do portão comercial #2:** migração oficial Supabase CLI, staging independente, grants/RLS com usuários reais de QA, validação de redirect Google Windows, revisão de segredo e variável do ambiente e aprovação separada para publicação. Nenhum destes bloqueadores foi encerrado pelos testes offline. Nenhuma versão EXE nova foi gerada neste ciclo.


## Transporte Windows Pro V2 restrito a staging — 09/11/2026

Foi acrescentado `DesktopBrotherProV2QaGateway` **somente ao código de QA**. Ele implementa `DesktopArchiveIdempotentUsageGateway` com protocolo persistido `PRO_V2`, enviando os formatos `reserve_pro_v2`, `finalize_pro_v2` e `status_pro_v2` já previstos no backend de branch. Em nenhum momento este adaptador cria uma assinatura, ativa Pro, processa pagamento ou autoriza a importação por uma configuração local.

**Bloqueio técnico contra produção:** seu construtor exige `stagingProjectRef` explicitamente e **rejeita** o ID oficial `dwpcddiramxlhavdmmyn`. Não há referência default a projeto staging nem conexão à interface Windows. Logo, nenhum usuário do pacote QA pode ativar o fluxo comercial com um botão existente ou apenas com flags do ambiente. A primeira integração real exige um projeto staging autorizado, mais aprovação separada antes de alterar o bloqueio do ID oficial.

**Garantias de recibo:** reserva recebe exclusivamente UUIDv4 já persistido, exige `pro_<UUID>`, `allowed:true`, `pro:true`, `state:reserved`, operação exata e `replayed` booleano JSON. A finalização recusa respostas legadas `{"ok":true}`; exige chave, operação, estado `completed`/`failed` de acordo com o resultado e booleano `replayed`. A consulta `status_pro_v2` é somente leitura, não apaga journal nem resolve reserva. O método `reserve` V1 **sempre** falha em vez de emitir recibos efêmeros.

**Reforço do portão:** `DesktopArchiveCommercialImportGate` passou a exigir journal durável e importador com identidade/ownership persistidos de **qualquer** `DesktopArchiveIdempotentUsageGateway` (Free ou Pro), e não somente da classe original Free. Sem essas duas garantias não há requisição ao servidor. No Pro V2 o journal grava `PRO_V2` e UUID antes da primeira reserva; bloqueia reenvio automático após perda de resposta.

**Testes:** `DesktopBrotherProV2QaGatewayTest` utiliza exclusivamente `HttpURLConnection` simulada, sem navegador, Supabase real, pagamento, assinatura, cliente ou fonte de terceiros. Abrange impossibilidade de selecionar produção, recibos de fonte/matriz, replay, finalização, consulta de estado, negação de resposta legada, UUID incorreto e importação sem journal. Incluído na compilação Windows portátil. O sucesso do CI não é homologação no staging real.

**Pendências:** criar/homologar ambiente de staging autorizado, aplicar migração sob revisão, validar RLS/grants/recibos, reproduzir Google/PKCE com conta de QA e realizar testes físicos Windows. A Edge Function de produção versão 7 continua sem essas rotas; o Android oficial não foi alterado.
