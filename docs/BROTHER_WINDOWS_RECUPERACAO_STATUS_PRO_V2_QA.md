# Brother Matrizes Windows — diagnóstico de recibos Pro V2 (somente QA)

**Data:** 07/11/2026. **PR:** [#226](https://github.com/timachado/Brother-Matrizes/pull/226).

## Entrega deste ciclo

A Edge Function `brother-usage/index.ts` da **branch de QA**
agora aceita a ação aditiva **`status_pro_v2`**, com
identidade comprovada por `admin.auth.getUser(token)`, e-mail
confirmado e flag server-side `BROTHER_PRO_V2_ENABLED === "true"`.
Sem esse sinalizador, **503 sem consulta**. A função **não foi
implantada**; não há tabela Pro V2 nem RPC Pro V2 em produção.

A operação recebe somente:

```json
{
  "action": "status_pro_v2",
  "operation": "import_matrix",
  "request_key": "pro_12345678-1234-4234-8234-123456789abc"
}
```

O helper `read-pro-status-v2.ts` verifica `pro_UUIDv4` e usa o
contexto autenticado do servidor para buscar **apenas `state`**,
filtrando por `request_id`, `user_id` e `operation` na
`brother_matrizes_pro_import_receipts`. Não expõe email, plano,
transação financeira nem dados de matriz.

Estados de leitura: `reserved`, `completed`, `failed`;
`found:false` quando não encontrado. Erros de banco, estados
inesperados e respostas falsas falham fechados. **`found:false` não
prova que uma reserva inexistente foi reembolsada ou finalizada.**

A consulta de um recibo já existente não exige assinatura Pro
atualmente ativa; isso permite diagnosticar uma importação
iniciada antes do vencimento sem criar novo direito de acesso.
Jamais realiza `reserve`, `finalize`, `insert`, `update`
ou `delete`.

## Windows

`DesktopBrotherArchiveUsageGateway.lookupKnown` diferencia
as chaves por tipo: `usage_` chama `status`; `pro_UUIDv4`
chama `status_pro_v2`. O retorno é validado estritamente por
chave, categoria, estado e JSON booleano real.

`DesktopArchiveQuotaRecoveryAssessment.inspect` tem uma
garantia adicional **fundamental**:

> Um recibo `pro_UUIDv4` só pode ser classificado como potencial
> Pro V2 se o UUID remoto for **idêntico** ao UUID do `Pending.localId`
> gravado pelo journal **antes da reserva**.

O protocolo antigo Pro V1 também utilizava UUIDv4, porém gerado
pelo servidor **independentemente do journal**. UUIDv4 válido
isoladamente não é prova de V2. Recibos Pro antigos ou
divergentes permanecem `NO_SERVER_KEY`/bloqueados.

Todos os diagnósticos continuam com
`blocksNewImports=true`, `permitsAutomaticFinalize=false` e
`permitsAutomaticCleanup=false`. Nenhum arquivo local ou journal
é apagado e nenhuma nova importação é autorizada.

## Evidências de QA

- Testes Deno `read-pro-status-v2_test.ts` abrangem ambos os
  tipos de operação, três estados, usuário divergente, item não
  encontrado, erro de SQL, UUID legado e chave injetada, sem conexão
  externa.
- Testes Kotlin `DesktopBrotherArchiveUsageGatewayTest` verificam
  URL fixada, ação correta, categorias, chave idêntica e
  negação de respostas fraudulentas.
- Testes `DesktopArchiveQuotaRecoveryAssessmentTest` comprovam
  journal preservado, novas reservas bloqueadas e rejeição da
  associação indevida a recibo Pro V1.
- [CI de status](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-status-qa.yml).
- [CI portátil Windows](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-portable-qa.yml).

## O que ainda falta

1. **Protocolo de reserva antes do primeiro ACK — implementado em QA, aguardando CI:** 
   o journal V2 grava `LEGACY_UNKNOWN`, `FREE_V2` ou `PRO_V2`
   **antes** da chamada remota. Marcadores JSON V1 são lidos como
   `LEGACY_UNKNOWN`, sem promoção automática; a chave de consulta
   Free/Pro V2 só é derivada se a intenção V2 já estava persistida.
   O servidor ainda precisa ser consultado explicitamente em um
   fluxo de diagnóstico autorizado. Nenhum status remoto autoriza
   finalizar, excluir o journal ou liberar outra importação.
2. Persistir intenção e identidade do arquivo local criado antes
   de finalizar, para recuperação segura de ownership após crash.
3. Homologação Windows de autenticação Google com **o mesmo Supabase
   oficial do Android**, utilizando chamadas simuladas na CI e conta
   de teste autorizada para o ensaio manual. Não criar outro projeto,
   não executar reserva/cobrança real em produção e não implantar
   migrações no Supabase existente sem revisão específica.
4. Verificação de queda de energia, ACL e bloqueio entre processos
   reais no dispositivo Windows de teste.

**Situação comercial:** etapa #3 avança em QA, mas permanece
ABERTA. Contagem total: **9 portões comerciais abertos / 0
homologados**. `main`, Android e Supabase produção inalterados.

## Resultados executados no GitHub Actions

- [CI de status Pro V2 #42412652537](https://github.com/timachado/Brother-Matrizes/actions/runs/42412652537):
  **dois jobs `completed/success`**. Deno compilou a Edge
  Function completa e passou os testes com base simulada.
  Windows passou os testes de HTTP falso, Pro V2 e journal, sem
  acesso a Supabase real.
- [Relatórios XML Windows #13589947316](https://github.com/timachado/Brother-Matrizes/actions/runs/42412652537/artifacts/13589947316):
  arquivo sintético de testes, sem tokens nem matrizes de clientes.
- [Portable QA #42412612723](https://github.com/timachado/Brother-Matrizes/actions/runs/42412612723):
  **completed/success**, com regressão prioritária, APP-IMAGE,
  execução do launcher e captura de tela no runner Windows.
  [Executável portátil QA #13590541211](https://github.com/timachado/Brother-Matrizes/actions/runs/42412612723/artifacts/13590541211)
  (98.267.343 bytes; pasta com launcher e runtime) e
  [capturas QA #13589164810](https://github.com/timachado/Brother-Matrizes/actions/runs/42412612723/artifacts/13589164810)
  (237.365 bytes). Artefatos privados e temporários, sem liberação comercial.

## Protocolo local V2 — continuação técnica (07/11/2026)

- `DesktopArchiveQuotaJournal` grava `version=2` e o campo
  `protocol`, incluindo o tipo `FREE_V2` definido pelo adaptador
  Windows atualmente implementado, **antes** de reservar a cota.
  Futuro adaptador Pro V2 deverá declarar seu tipo expressamente;
  o código atual não habilita reservas Pro nem consulta contas reais.
- Leitura de marcadores `version=1` preserva
  `LEGACY_UNKNOWN`: nem um UUIDv4 com prefixo `pro_` igual ao
  identificador local passa a ser tratado como comprovante Pro V2.
- `DesktopArchiveQuotaRecoveryAssessment.diagnosticRequestKey`
  permite reconstruir somente uma **chave candidata de consulta
  read-only** se o protocolo V2 tiver sido gravado antes do primeiro
  envio. Chave candidata não é autorização nem prova de reserva.
  Estado remoto `reserved/completed/failed/not_found` continua
  mantendo `blocksNewImports=true`, sem finalização nem limpeza.
- Cenários de teste acrescentados: ACK de reserva perdida Free/Pro V2,
  UUID remoto divergente, legado Pro V1, proteção contra repetição e
  compatibilidade de leitura de journal V1.
- **Status desta revisão:** alterações enviadas ao PR #226 apenas
  para QA. A aprovação de compilação e testes corresponde ao workflow
  do commit mais recente, a verificar. Nenhum EXE comercial,
  merge, migração, instalação ou alteração no Supabase de produção.

## Diagnóstico local de recuperação sem mutação — 07/11/2026

Foi acrescentado `DesktopArchiveQuotaReadOnlyDiagnostic` à branch Windows
(QA somente; **não integrado à UI**). A implementação:

- mantém um `FileLock` durante a leitura do journal e da eventual
  consulta de estado, evitando modificação concorrente da evidência;
- não tenta autenticação, HTTP ou consulta de cota quando não há
  registro pendente ou quando o recibo Pro V1 não tem identidade
  remotamente comprovável;
- consulta apenas a chave `FREE_V2`/`PRO_V2` derivada do protocolo
  persistido ou um recibo `usage_` legado comprovado; toda consulta
  depende de um token injetado explicitamente para QA;
- trata falha de rede, token inválido ou sessão revogada como
  `SERVER_STATUS_UNAVAILABLE`, conservando o marcador e o bloqueio;
- propaga cancelamento, rejeita servidor de chave/categoria divergente
  e nunca invoca `reserve`, `finalize`, exclusão, rollback ou
  qualquer resolução automática de conta/benefício.

Foram acrescentados quatro testes sintéticos em
`DesktopArchiveQuotaReadOnlyDiagnosticTest` e sua execução no
workflow de recuperação e no workflow de status. [CI de recuperação
Windows](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-recovery-qa.yml).

**Evidência anterior confirmada:** a revisão de journal e autenticação
[#42416830438](https://github.com/timachado/Brother-Matrizes/actions/runs/42416830438)
teve conclusão `success`. O novo código de diagnóstico só poderá ser
descrito como testado depois da conclusão de seu próprio CI.

**Portão comercial permanece aberto:** ainda são necessários Auth Redirect
URLs Windows verificados para o Supabase oficial já utilizado no Android,
conta Google de testes autorizada, compatibilidade com o contrato SQL/Edge
existente, leitura de arquivos locais com ownership conferido após
desligamento brusco e cobertura física/funcional Windows. Nenhum
novo Supabase faz parte do escopo aprovado.
Nenhum desses requisitos pode ser substituído por status remoto ou
resultado de teste automatizado. 0 de 9 portões homologados.
