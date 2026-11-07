# Brother Matrizes Windows — extração seletiva (QA isolada)

**Fonte de verdade:** Android comercial 1.11.8,
`core/archive/SafeArchiveExtractor.kt` (método `extract`),
`ArchiveExtractorScreen.kt` (`canImport` / `onImportFinished`) e
`core/account/BrotherUsageClient.kt`.

**Destino:** branch `feature/windows-fiel-android-1-11-8-20261106`,
[PR #226](https://github.com/timachado/Brother-Matrizes/pull/226).
Não publicar esta revisão como release Windows.

## Implementado

### Inventário associado ao arquivo real

- `DesktopArchiveInventory.inspect` calcula SHA-256 do arquivo local
  selecionado, além de identificar formato ZIP, 7Z ou RAR/RAR5.
- `DesktopArchiveInventory.Preview.archiveSha256` vincula a
  prévia ao conteúdo integral do pacote, não apenas ao nome/tamanho.
  Capturas sintéticas sem hash não podem ser extraídas.

### Estágio privado de um único arquivo

`DesktopArchiveSelectiveStager.extract` recebe exclusivamente a
entrada presente no inventário. Antes de extrair, revalida assinatura,
metadados, SHA-256 e a identidade exata do índice selecionado.

- Reaproveita os três mecanismos da implementação Android:
  `ZipFile`, `SevenZFile` com limite de 32 MiB de memória e `junrar`
  com limite de dicionário de 32 MiB.
- Extrai somente uma entrada para arquivo temporário privado com nome
  aleatório. **Nunca usa o caminho interno do pacote como destino**.
- Recusa extração acima de 24 MiB, tamanhos inconsistentes, pacotes
  alterados, itens ausentes e operações canceladas.
- Para ZIP, calcula e confere CRC-32 ao terminar o fluxo.
- Confere SHA-256 do pacote novamente após copiar os bytes.
- Exige `Staged.close()` e apaga o arquivo/pasta temporários
  mesmo em falha ou cancelamento; o ZIP/RAR/7Z original permanece
  intocado.

### Ponte com o bloqueio comercial

`DesktopArchiveSelectiveImportFactory` implementa o contrato
`DesktopArchiveImportFactory` do gate já testado:

1. Sessão Google verificada (ainda indisponível na UI Windows).
2. Reserva remota individual `import_matrix` / `import_font`.
3. Somente após a reserva, a fábrica extrai para o estágio privado.
4. Um **validador/integrador explicitamente fornecido**
   (`DesktopArchiveValidatedSink`) verifica o arquivo e salva,
   retornando `created` ou duplicado.
5. Finalização server-side `success=true` somente para criação
   confirmada; erro, cancelamento ou resposta inválida acionam
   reversão de conteúdo criado pela transação.

**Nenhum sink comercial foi instalado.** O validador real de
PES/DST/JEF e de fontes TTF/OTF ainda não foi homologado no fluxo.
As gravações dos testes usam exclusivamente arquivos sintéticos
em pasta descartável.

## Critérios de QA

`DesktopArchiveSelectiveStagerTest` e
`DesktopArchiveSelectiveImportFactoryTest` exercitam:

- ZIP/PES e 7Z/OTF de referência gerados no teste.
- Extração de entrada única e destino que não revela nomes internos.
- Nome e tamanho iguais com conteúdo diferente → recusa via SHA-256.
- CRC ZIP incorreto → recusa mesmo com prévia nova e hash coerente.
- Prévia sintética sem hash e item não pertencente → recusa.
- Interrupção durante leitura → remoção do arquivo parcialmente criado.
- Ausência de sessão → nenhuma reserva/extração.
- Cota simulada autorizada → ordem correta antes da extração.
- Finalização de cota falhou → rollback sem apagar arquivos anteriores.
- Importação duplicada → `success=false`, preservando o existente.

Workflow de teste:
[Brother Windows — extração seletiva QA](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-selective-qa.yml).

## Bloqueios de liberação

**Ainda não são funcionalidades comerciais ativas:**
importação seletiva na interface, autenticação Google Windows aprovada,
validação de matrizes/fontes extraídas pelo motor original, persistência
definitiva idempotente, recuperação de transações após corte de energia
e compatibilidade com bordadeira física.

O processo de rollback é atualmente **in-process**; isso não substitui
reconciliação server-side de reservas desconhecidas após falha/corte
de energia. A versão 0.0.1 Windows permanece QA. A `main`, o Android
oficial, as quotas de produção e o site não sofreram alterações.

Este documento registra o código implementado e os critérios de
homologação; somente um resultado CI `completed/success` confirma
a execução dos testes.
