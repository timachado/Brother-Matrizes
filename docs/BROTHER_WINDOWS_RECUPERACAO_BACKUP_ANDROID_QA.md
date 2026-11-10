# Brother Matrizes Windows — recuperação de matrizes de backup Android (QA)

**Estado:** funcionalidade em branch de homologação, **não distribuível**. Não alterar o Android oficial nem publicar um EXE comercial a partir desta etapa.

## Formatos aceitos

| Arquivo de entrada | O que é feito |
|---|---|
| `.zip` de backup FLP local do Windows | Restaura projetos FLP em **nova pasta**. |
| `.brother_matrizes-backup` original Android | Valida e recupera **somente** as matrizes FLP. |
| `.brother_matrizes-fullbackup` original Android | Valida manifesto, backup de projetos e fontes; recupera **somente** as matrizes FLP. |

A tela da Biblioteca mantém o botão **Restaurar FLP**. Ao selecionar um backup Android, exibe primeiro as contagens de matrizes e fontes detectadas. É necessária uma **confirmação explícita** para recuperar somente FLP. O usuário escolhe uma pasta pai; o programa cria um subdiretório exclusivo para a recuperação.

## Proteções obrigatórias

- O ZIP completo/histórico é verificado por `DesktopAndroidBackupInspector`, que exige diretório central íntegro, extensões e entradas oficiais, limite de tamanho, rejeição de caminhos suspeitos e decodificação pelos **codecs originais do Android**.
- `DesktopAndroidProjectRecovery` reutiliza `CompleteLibraryBackupCodec`, `ProjectBackupCodec` e `ProjectCodec`; o Windows aceita **1 a 200 matrizes FLP**, no máximo **64 MiB por FLP** e **128 MiB de FLP recuperados por operação**.
- Cada projeto é gravado em uma pasta temporária **nova** com `CREATE_NEW` e reaberto pelo `DesktopFlpProjectFiles.open` do port do codec Android. O catálogo é verificado antes de mover a pasta para seu destino definitivo.
- Não sobrescreve ZIP de origem, projetos da Biblioteca, arquivos preexistentes, pastas de cliente ou fontes. Repetir a operação cria outra pasta. Em falha, apenas a pasta temporária desta tentativa é descartada.
- Os documentos recuperados usam nomes `Android-00001.flp`, `Android-00002.flp` etc. Os bytes FLP permanecem idênticos aos armazenados no backup, preservando os metadados internos.
- **Nenhuma fonte TTF/OTF é restaurada ou ativada**. Não cria nem altera `imported_fonts`, conta Google, armazenamento de tokens, assinatura, licença, cotas, pagamento ou dados de produção.

## Como homologar

1. Executar a workflow `Brother Matrizes - Windows ler backup completo Android (QA segura)`.
2. Confirmar sucesso em `DesktopAndroidBackupInspectorTest`, `DesktopAndroidProjectRecoveryTest`, `DesktopFlpBackupRestorerTest` e `AndroidProjectLibraryParityTest`.
3. Confirmar a compilação geral `Brother Matrizes - Windows fiel Home Android (compilacao QA)` para a **mesma revisão**.
4. Em Windows de QA e com arquivos sintéticos: recuperar dois FLP de um backup Android completo com duas fontes; abrir os dois no Viewer; verificar que as fontes não foram instaladas, o ZIP não mudou e um FLP preexistente com mesmo nome permaneceu intacto.
5. Repetir com backup histórico e com um ZIP intencionalmente truncado; este último não pode publicar nenhuma pasta.

## Bloqueios ainda presentes

A recuperação de matrizes não equivale à **restauração completa** do Android, que inclui fontes e outros estados. Ainda faltam homologação do login Google real, permissões de importação TTF/OTF e paridade completa dos recursos Android antes de empacotamento/distribuição comercial.
