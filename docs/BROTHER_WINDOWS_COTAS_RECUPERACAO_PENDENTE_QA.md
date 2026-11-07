# Brother Matrizes Windows — registro durável e reconciliação de cotas (QA isolada)

**Origem oficial:** Android comercial 1.11.8 — contratos
`BrotherMatrizesAccountService.authorizeImport/finalizeImport` e
`BrotherUsageClient.reserve/finalize`.

**Código:** `windows-fiel/`, branch
`feature/windows-fiel-android-1-11-8-20261106`,
[PR #226](https://github.com/timachado/Brother-Matrizes/pull/226).
Não modificar `main`, aplicativo Android, servidor Supabase nem site.

## Problema real identificado

Não é possível garantir que um `reserve` foi rejeitado quando a
conexão é interrompida antes da resposta. O servidor pode ter
registrado a reserva mesmo que o Windows receba timeout. O mesmo
ocorre depois de `finalize(success=true/false)`: uma resposta
perdida não prova que a operação falhou.

**Repetir automaticamente `reserve`/`finalize` seria inseguro**
sem um contrato comprovado de idempotência e uma rota server-side
de consulta do status de cada tentativa. O fluxo Windows deve
bloquear novas importações nesse caso até reconciliar a situação.

## Implementação neste ciclo

### `DesktopArchiveQuotaJournal`

- Exige um diretório local **existente** reservado à aplicação e
  protegido pelo usuário atual; o código não cria automaticamente
  pastas globais nem altera permissões do cliente. A implantação
  comercial ainda precisa comprovar ACL privada no Windows.
- Arquivos `pending-UUID.json` com estados:
  - `RESERVE_UNKNOWN`: gravado **antes** da primeira chamada `reserve`;
    qualquer timeout deixa a tentativa pendente, mesmo sem chave remota;
  - `RESERVED`: resposta recebida com identificador server-side;
  - `FINALIZE_UNKNOWN`: gravado **antes** de chamar `finalize`;
  - `CLEANUP_UNKNOWN`: confirmação recebida para falha/duplicata,
    mas reversão local ainda não concluída.
- Usa arquivo temporário na mesma pasta, `FileChannel.force(true)`
  e `ATOMIC_MOVE` para publicar cada atualização. Não promete
  persistência absoluta de metadados do diretório em queda de energia:
  isso ainda requer auditoria no volume/Windows alvo.
- `FileLock` entre processos e `Mutex` no processo para impedir
  novas reservas simultâneas na mesma pasta de controle. Arquivos
  temporários abandonados ou registros corrompidos bloqueiam a
  próxima reserva — nunca são descartados silenciosamente.
- O registro contém apenas UUID local, categoria, hash SHA-256
  da identificação da entrada, chave opaca de reserva, estado e
  horário local. Não armazena access token, refresh token, segredo,
  nome de cliente, matriz, conteúdo do pacote ou caminho original.
- Exibe os pendentes por `listPending()`, sem consultar o servidor,
  não reexecuta quotas nem exclui arquivos automaticamente.
- A remoção do registro ocorre apenas depois de confirmação da
  finalização remota e conclusão da reversão necessária.

### Integração no gate comercial

`DesktopArchiveCommercialImportGate` foi adaptado para usar
o journal quando explicitamente injetado:

1. Valida a seleção, sessão verificada e importador disponível.
2. Recusa um adaptador HTTP real
   `DesktopBrotherArchiveUsageGateway` **sem journal**.
3. Adquire lock, verifica ausência de registros anteriores e grava
   `RESERVE_UNKNOWN` antes de solicitar `reserve`.
4. Marca `RESERVED` após a resposta e somente então executa a
   extração seletiva e validação original Android.
5. Grava `FINALIZE_UNKNOWN` antes de enviar a finalização.
6. Após confirmação de sucesso e reversão, quando necessária,
   retira o registro. Em situação ambígua mantém o item pendente
   e bloqueia novas reservas com mensagem de reconciliação manual.

O restante do programa Windows permanece **sem sessão Google
comercial e sem importação pela interface**. Esta infraestrutura
foi testada com servidor falso e arquivos de QA, sem dados de cliente.

## Critérios de aceitação automatizados

`DesktopArchiveQuotaJournalTest` cobre:

- Escrita anterior ao envio da primeira reserva e ausência de
  credenciais/caminhos originais no arquivo local.
- Reinício simulado com registro `RESERVED` ou `FINALIZE_UNKNOWN`,
  sem tentativas automáticas de nova cota.
- Tempo de resposta perdido após `reserve`, ainda sem chave remota:
  nenhuma segunda tentativa é permitida.
- Resposta perdida após `finalize`: rollback da nova gravação,
  preservação do registro para reconciliação e bloqueio de nova cota.
- Falha local com resposta remota `success=false` confirmada:
  rollback e retirada do registro.
- Registro malformado, arquivo temporário órfão e lock concorrente:
  falha fechada.
- Recusa de `DesktopBrotherArchiveUsageGateway` real sem journal.

[CI Windows — recuperação de cotas](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-recovery-qa.yml)
executa estes cenários sem rede, chaves de produção ou autenticação
real, além da regressão dos contratos anteriores.

## Bloqueios comerciais não resolvidos

1. **Endpoint servidor idempotente e de consulta/reconciliação**,
   inclusive para `reserve` cujo identificador não chegou ao
   cliente. Sem isso, o journal só detecta a dúvida e bloqueia
   novas operações; **não resolve automaticamente a quota**.
2. **OAuth Google Windows/PKCE** homologado, com configuração
   autorizada e sem armazenar refresh tokens em texto simples.
3. **ACL, durabilidade e testes com encerramento forçado** em PC
   Windows real; mutex e lock file não substituem teste de queda
   de energia.
4. **Propriedade durável do arquivo criado**: ainda existe janela
   entre a gravação local da matriz/fonte e o registro de ownership;
   o rollback atual é in-process e não permite excluir órfãos após
   crash com segurança. Implementar diário transacional por arquivo.
5. Revisão jurídica das dependências de extração RAR/UnRAR,
   homologação com máquinas e arquivos autorizados e testes
   de concorrência entre instalações/usuários.

**Status:** implementação experimental somente QA. Não habilitar
importação comercial, não consumir cotas reais, não publicar EXE,
não fazer merge na `main` nem upload ao site da T.I. Machado.

## Resultado de CI verificado — 07/11/2026

- A primeira execução
  [#42392827084](https://github.com/timachado/Brother-Matrizes/actions/runs/42392827084)
  encontrou uma falha de regressão na mensagem de erro esperada pelo
  gate comercial anterior. A proteção funcionou, mas o teste
  `failedFinalizationRollsBackOnlyNewContent` detectou a diferença.
- A mensagem pública antiga `A cota não foi sincronizada` foi
  preservada e complementada com instrução para reconciliação.
- A revisão corrigida
  [#42393125582](https://github.com/timachado/Brother-Matrizes/actions/runs/42393125582)
  terminou `completed/success` no commit `e83166c`.
  Compilou o journal e passou os testes de interrupção, bloqueio de
  reservas ambíguas, reinício, concorrência entre instâncias,
  protocolo HTTPS simulado e regressão do gate comercial.
- [Artefato de resultados #13582680060](https://github.com/timachado/Brother-Matrizes/actions/runs/42393125582/artifacts/13582680060)
  contém exclusivamente XML de testes, sem tokens ou arquivos
  do usuário.
- A regressão portátil
  [#42393169200](https://github.com/timachado/Brother-Matrizes/actions/runs/42393169200)
  terminou `completed/success` no commit `365ff6f`, com testes
  prioritários aprovados, APP-IMAGE Windows gerada e launcher
  executado no runner. Artefatos de QA, sem publicação comercial:
  [Portable QA #13582008393](https://github.com/timachado/Brother-Matrizes/actions/runs/42393169200/artifacts/13582008393)
  (98.251.671 bytes, ~98 MB, launcher e runtime completos) e
  [capturas reais #13582192905](https://github.com/timachado/Brother-Matrizes/actions/runs/42393169200/artifacts/13582192905)
  (237.365 bytes). Os artefatos são temporários e podem exigir
  autenticação no GitHub.
- A primeira suíte exercitou duas instâncias no **mesmo processo JVM**.
  A suíte adicional `DesktopArchiveQuotaMultiprocessTest` agora
  inicia **processos Java 17 independentes** no Windows runner,
  testa a exclusão entre processos e provoca `destroyForcibly`
  e `Runtime.halt(61)`. Após a interrupção, outro processo
  deve encontrar a reserva pendente e bloquear novas tentativas.
  Não há conexão de rede, cobrança de quotas ou arquivo de cliente.
  Este teste **não equivale a queda de energia**, validação das ACLs
  ou homologação em computadores dos clientes.


## Teste de múltiplos processos e encerramento forçado

Os novos arquivos `DesktopArchiveQuotaProcessProbe.kt` e
`DesktopArchiveQuotaMultiprocessTest.kt` executam JVMs independentes
via `ProcessBuilder`, usando o classpath de teste fornecido pelo
Gradle e sempre uma pasta temporária isolada.

1. Um processo adquire `FileLock` e escreve `RESERVE_UNKNOWN`.
2. Uma segunda JVM tenta adquirir o mesmo lock e deve ser rejeitada.
3. O primeiro processo é encerrado com `destroyForcibly`,
   sem executar os blocos `finally` ou fechar voluntariamente
   a transação. A terceira JVM verifica que o bloqueio do SO foi
   liberado, mas a reserva **continua pendente** e `begin` falha.
4. Outro cenário escreve `FINALIZE_UNKNOWN` e encerra imediatamente
   com `Runtime.halt(61)`. Uma JVM recém-iniciada encontra a chave
   de reserva intacta, sem tentar outra cobrança ou finalização.

Executados em:
[workflow de recuperação](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-recovery-qa.yml)
e
[QA portátil Windows](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-portable-qa.yml).

**Limites remanescentes:** consulta de status de reserva no servidor
com idempotência verificável, testes com encerramento do SO, teste de
ACLs Windows por usuário e diário de ownership local dos arquivos
criados. Nenhuma recuperação automática destrutiva foi ativada.
