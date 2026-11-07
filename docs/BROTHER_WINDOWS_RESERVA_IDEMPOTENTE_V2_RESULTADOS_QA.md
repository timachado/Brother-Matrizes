# Brother Matrizes Windows — evidências V2 de reserva idempotente

**Data:** 07/11/2026.
**Branch:** `feature/windows-fiel-android-1-11-8-20261106`.
**PR:** https://github.com/timachado/Brother-Matrizes/pull/226

## Estado comprovado

[CI reserva idempotente V2 #42405719472](https://github.com/timachado/Brother-Matrizes/actions/runs/42405719472):
`completed/success`, três jobs aprovados no commit
`c78c5fd2a997d52d7e5e3316c14bb98b47e124d7`.

1. **Servidor Deno**: verificação de tipos TypeScript e testes offline
   de repetição de UUIDv4, perda da primeira resposta, cota negada,
   reserva já finalizada, chave falsa, usuário/operação divergente e
   estados inesperados. Os handlers V1 do Android permanecem presentes.
2. **PostgreSQL 16 descartável**: a SQL extraída da
   [proposta V2](BROTHER_WINDOWS_RESERVA_IDEMPOTENTE_V2_QA.md)
   foi criada **somente em banco Docker de CI**. Passaram:
   - Primeira reserva e replay do mesmo `client_request_id`;
   - Mesma UUID enviada simultaneamente por dois processos `psql`,
     gerando somente uma linha;
   - Dez reservas simultâneas de matrizes distintas: limite final de
     **cinco** entradas reservadas;
   - Contagem V2 compatível com reserva V1 pré-existente de fonte,
     preservando limite de **três**;
   - Tentativa finalizada/expirada não ganha nova autorização;
   - Reutilização de UUID por outro usuário ou operação é recusada;
   - RPC nova sem privilégio de execução para `anon` e
     `authenticated`; somente `service_role` na base descartável.
3. **Windows/Kotlin**: respostas HTTP simuladas para `reserve_v2`,
   validação estrita da mesma UUIDv4, recusa de booleanos falsos,
   gate exigindo journal local gravado antes da rede, testes de
   recuperação e travamento entre JVMs independentes.
4. [Relatórios XML Windows #13586918734](https://github.com/timachado/Brother-Matrizes/actions/runs/42405719472/artifacts/13586918734),
   somente dados sintéticos.

### Pacote portátil Windows

[Regressão Portable QA #42405263745](https://github.com/timachado/Brother-Matrizes/actions/runs/42405263745):
`completed/success` no commit
`73ca8a62b712658f3d1f53f93b34adc128443a81` com
compilação, testes Kotlin atualizados, geração da APP-IMAGE,
launcher executado no runner Windows e capturas de tela.

- [Portable QA #13587032923](https://github.com/timachado/Brother-Matrizes/actions/runs/42405263745/artifacts/13587032923)
  — 98.266.587 bytes, pasta do launcher e JRE embutido.
- [Screenshots #13587137717](https://github.com/timachado/Brother-Matrizes/actions/runs/42405263745/artifacts/13587137717)
  — 237.365 bytes.

Os artefatos são privados, temporários e podem exigir login GitHub.
Não são releases comerciais; não há importação autenticada habilitada
na interface Windows.

## Verificação de não implantação

- Nenhuma migração foi aplicada ao projeto
  `dwpcddiramxlhavdmmyn`; o único acesso Supabase desta etapa
  foi **consulta de metadados e definições SQL**.
- O texto SQL é um **rascunho**, não uma migração oficial do CLI.
  A função `brother_matrizes_reserve_import_v2` não existe em
  produção por causa deste trabalho.
- A Edge Function `brother-usage` da produção não foi atualizada.
- Nenhum token Google, chave secreta, reserva ou cota real foi
  utilizado nos testes.
- A `main`, a versão Android oficial, assinatura financeira
  WooCommerce/Efí e o site da T.I. Machado não foram alterados.

## Bloqueadores comerciais restantes

1. Gerar migração oficial com Supabase CLI e homologar em staging,
   auditar RLS e grants; deploy somente após aprovação separada.
2. Persistir recibos **Pro V2** de modo idempotente sem criar
   direitos de assinatura no cliente. Pro V2 falha fechada no código.
3. Concluir a reconciliação de `finalize` com intenção
   server-side, inclusive respostas perdidas e recuperação de
   ownership de arquivos locais após crash.
4. Homologar OAuth Google Windows, permissões locais e execução
   após queda de energia em hardware autorizado.

**Aprovação de QA técnica não é autorização para deploy ou distribuição comercial.**
