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

1. **Protocolo de reconciliação de reserva antes do primeiro ACK**:
   sem `reservationKey` o journal legado não especifica se a
   tentativa original era Free/Pro V1/V2. Não inferir uma chave;
   adicionar tipo de protocolo e intenção ao journal antes do envio,
   com migração compatível dos registros antigos.
2. Persistir intenção e identidade do arquivo local criado antes
   de finalizar, para recuperação segura de ownership após crash.
3. Homologação completa em staging separado com autenticação Google,
   autorização por conta e audit trail no servidor. Ainda não
   autorizado deploy para produção.
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
- [Portable QA #42412612723](https://github.com/timachado/Brother-Matrizes/actions/runs/42412612723)
  executa regressão prioritária e APP-IMAGE. O resultado final
  da imagem Windows deve ser confirmado separadamente.
