# Brother Matrizes Windows — importar matriz do Telegram / Arquivos (QA interno)

Data: 09/11/2026. PR #226, ramo `feature/windows-fiel-android-1-11-8-20261106`. **Não é liberação comercial.**

## Paridade implementada em código

A busca no Telegram segue o comportamento original do Android: a pesquisa abre o aplicativo oficial e **não** lê mensagens privadas. A ação existente de **Abrir arquivo do Telegram / Arquivos** continua permitindo visualização sem consumo de cota. Foi adicionada uma ação separada `Importar matriz PES/DST/JEF com conta e cota`, visível apenas no modo de QA estritamente autorizado.

Após o usuário selecionar um arquivo local (não lê o banco do Telegram), confirmar direito de uso e possível desconto de uma cota, `FielMain` invoca `DesktopWindowsAccountQaOptIn.telegramImportForQa`. Ela usa **a mesma sessão Google, gateway Free V2, diretórios e journal idempotente** do Extrator original, sem criar um serviço ou controle de cotas paralelo.

`DesktopWindowsStandaloneMatrixAuthorizedImport`:
- Aceita só arquivo regular PES/DST/JEF de até 24 MiB, não segue links simbólicos e confere SHA-256 da seleção.
- Cria identificador pseudônimo com hash, sem guardar caminho ou nome de usuário no journal.
- Registra reserva Free V2 com UUIDv4 **antes** do primeiro HTTP, empregando `DesktopArchiveCommercialImportGate`.
- Copia bytes após a reserva para arquivo temporário aleatório e verifica tamanho + hash da cópia e do original antes de autorizar publicação.
- Reutiliza `DesktopArchiveValidatedLibrarySinkFactory` (motor PES/DST/JEF e codificador FLP originais do Android), grava FLP apenas com `CREATE_NEW`, comprova ownership no journal e confirma finalização no servidor.
- Em queda de rede ou resposta ambígua, preserva o marcador pendente e impede qualquer nova reserva até revisão manual. Duplicatas não sobrescrevem FLP nem recebem finalização positiva.
- Nunca baixa arquivos do Telegram, autentica na TDLib, autoriza acesso Pro local, altera cotas na UI ou lê mensagens privadas.

## Controles obrigatórios

No executável padrão o botão de importação comercial não aparece. Para exercício isolado são exigidos TODOS:

`BROTHER_WINDOWS_QA_GOOGLE_AUTH=enabled` com chave pública válida, `BROTHER_WINDOWS_QA_REDIRECT_REVIEWED=enabled`, `BROTHER_WINDOWS_QA_ARCHIVE_V2_BACKEND_REVIEWED=enabled`, `BROTHER_WINDOWS_QA_ARCHIVE_IMPORT=enabled` e o opt-in separado `BROTHER_WINDOWS_QA_TELEGRAM_IMPORT=enabled`. Além disso, Google OAuth e cotas devem ser verificados no servidor. As flags expressam revisão do operador, **não** são evidência de que o backend esteja pronto.

O projeto de produção continua com a Edge Function `brother-usage` V1 (sem `reserve_v2`). **Não habilitar esse fluxo na produção.** Os testes atuais usam gateway Free V2 simulado; não é permitida publicação do EXE comercial.

## Verificação

`DesktopWindowsStandaloneMatrixAuthorizedImportTest` valida matriz DST gerada pelo motor original e persistida como FLP, ausência de alteração do arquivo selecionado, deduplicação, rejeição de entrada inválida, journal Free V2 após resposta perdida e nenhuma reserva dupla após reinício. `DesktopWindowsFontImportOptInTest` verifica que nenhuma opção é exibida sem todas as aprovações e que Telegram compartilha a origem de journal do Extrator.

Pendências reais: autenticação Google/PKCE com conta dedicada no staging autorizado, implantação/testes de `reserve_v2` e `finalize_v2` no servidor seguro, cotas Free e Pro, reconciliação, teste manual do seletor Windows e assinatura do EXE. APK Android oficial, Supabase comercial e site permanecem inalterados.
