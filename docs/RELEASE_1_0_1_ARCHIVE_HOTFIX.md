# Brother Matrizes 1.0.1 — Correção de extração ZIP

Reportado por gravação de tela de 10/10/2026: ZIP com 9 matrizes nos formatos JEF, PES e DST, reconhecidas pelo Extrator Inteligente, mas importação falhava ao tentar abrir /cache/archive-<id>.bin após os itens serem exibidos.

## Causa técnica
Na `ArchiveExtractorScreen`, `DisposableEffect(inventory)` incluía `onDispose { inventory?.sourceFile?.delete() }`. O callback lia a variável de estado mutável `inventory`, que já apontava para o inventário recém-preparado quando o efeito anterior era descartado após a mudança de estado. Assim, o primeiro efeito apagava o ZIP de cache da nova leitura.

## Correção
Capturar `stagedSourceFile = inventory?.sourceFile` imutavelmente e usá-lo como chave do `DisposableEffect`. O antigo ciclo descarta somente seu arquivo, sem alcançar o novo. O arquivo temporário continua privado e é removido quando a tela é realmente destruída, mantendo os limites de descompactação, validações ZIP/RAR/7Z e proteção contra traversal.

Foi adicionada uma mensagem segura caso um arquivo temporário desapareça por limpeza externa do Android. Teste instrumentado `ArchiveStageLifecycleTest` abre um ZIP com múltiplos arquivos e confirma a existência após a recomposição e a limpeza ao sair da tela.

## Versão
`versionName=1.0.1`, `versionCode=167`. Assinar com o MESMO certificado de produção 1.0.0, nunca gerar identidade nova. Nenhuma alteração em autenticação, cotas, assinaturas, pagamentos, WordPress/WooCommerce/Efí ou dados do usuário.
