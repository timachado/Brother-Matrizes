# Brother Matrizes Windows — autorização por item de arquivos compactados (QA)

**Escopo:** PR #226 na branch
`feature/windows-fiel-android-1-11-8-20261106`.
Android 1.11.8 é a fonte da verdade; produção não foi alterada.

## Contrato comercial original

Na `ArchiveExtractorScreen.kt` do Android:

1. A prévia e a seleção são locais, sem cobrar cota.
2. Antes de cada extração/importação o app chama
   `BrotherMatrizesAccountService.authorizeImport(context, isFont)`.
3. O servidor `brother-usage` recebe
   `action=reserve` e `operation=import_matrix` ou `import_font`.
4. Somente uma reserva aceita libera a tentativa de importação.
5. A finalização informa `request_key` e `success`, inclusive em falhas,
   para consolidar ou devolver a cota.
6. A versão final não confia em `pro=true` local ou texto de perfil:
   autenticação Google verificada, estado da assinatura e permissões
   comerciais pertencem ao servidor.

## Implementação Windows deste ciclo

`DesktopArchiveCommercialImportGate.kt` contém o contrato de transação
**por item**, com estas propriedades:

- Recusa por padrão: usa `WindowsFontSessionUnavailable` até
  homologação do login Google Windows (OAuth PKCE e redirect allowlist).
  Não existem credenciais ou servidor de cotas configurados no gate.
- A categoria da entrada diferencia `import_matrix` de `import_font`.
  A chave de reserva possui o mesmo formato `usage_UUID`/`pro_UUID`
  aceito por `BrotherUsageClient` oficial.
- Um `Mutex` compartilhado entre instâncias serializa a operação dentro
  do processo, evitando duplicações e reversões conflitantes.
- Cada item só pode ser processado após `reserve` remoto.
- O resultado `created=true` só é considerado concluído após o
  `finalize(success=true)` bem-sucedido.
- Uma importação duplicada conclui a reserva como
  `finalize(success=false)` e preserva arquivos anteriores.
- Em falha na importação, cancelamento ou indisponibilidade na
  finalização, a transação tenta rollback do item criado. Tentativas de
  finalização e rollback são feitas mesmo sob cancelamento de coroutine.
- O importador padrão `WindowsArchiveImporterUnavailable` **não extrai
  nem grava nada**. A interface `DesktopArchiveImportFactory` é
  implementável somente após homologar extração seletiva, validadores
  reais de PES/DST/JEF/TTF/OTF, destino e rollback.

## QA isolada e limites

`DesktopArchiveCommercialImportGateTest` executa as reservas em memória,
sem Supabase, OAuth, rede, APIs de produção nem arquivos de clientes:

- Sem sessão Google → nenhuma reserva e nenhum importador.
- Metadado de item inválido → não autoriza.
- Matrizes e fontes → reservas/finalizações individuais.
- Duplicata → `success=false` e preservação do original.
- Cota negada e chave inválida → nenhuma escrita.
- Falha parcial → rollback + `success=false`.
- Falha de confirmação no servidor → rollback e bloqueio.
- Cancelamento → tentativa de finalizar `false` e rollback.
- Duas instâncias concorrentes → transações serializadas.

Workflow:
[Brother Windows — cotas por item](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-commercial-gate-qa.yml).

**Não habilitado ao usuário:** botão de importar do Extrator Inteligente,
extração RAR/7Z/ZIP, reserva real, lançamento de contadores, login comercial
Windows ou distribuição da versão comercial.

**Bloqueadores de release:** além de homologação Google real, a recuperação
de reservas pendentes em falhas/corte de energia (idempotência no servidor),
extração segura e transação durável no disco exigem implementação e QA
antes de qualquer ativação. O mutex protege apenas um processo e não garante
isolamento entre múltiplas instâncias Windows.

Nenhuma alteração em `main`, Android oficial, Supabase ou site.
