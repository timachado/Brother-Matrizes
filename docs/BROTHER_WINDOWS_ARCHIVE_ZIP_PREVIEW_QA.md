# Brother Matrizes Windows — inventário ZIP somente leitura (QA)

**Escopo:** PR #226, branch `feature/windows-fiel-android-1-11-8-20261106`.
**Fonte Android:** `ArchiveExtractorScreen.kt` e
`core/archive/SafeArchiveExtractor.kt` da versão comercial 1.11.8.

## Motivação e limite comercial

O extrator Android usa **autorização individual de importação** por
matriz/fonte e finaliza a reserva de cota conforme o resultado. O
Windows QA não possui ainda autenticação Google e concessão de cotas
comercialmente homologadas. Por isso, **não** foi disponibilizada
extração, gravação de matrizes importadas, importação de fontes,
cobrança fictícia ou botão de importar inoperante.

## Implementação efetuada

- Menu Windows: **Biblioteca → Analisar pacote ZIP (somente leitura)**.
- Seleção explícita do ZIP pelo JFileChooser; nenhuma pasta do cliente
  é escaneada automaticamente.
- `DesktopArchiveInventory.inspect` lê apenas o diretório de
  metadados do ZIP local; não abre individualmente nem extrai os
  arquivos internos, não grava em pasta temporária e não usa rede.
- Tipos reconhecidos conforme Android:
  **PES, DST, JEF** (matrizes) e **TTF, OTF** (fontes).
- Limites espelhados do `SafeArchiveExtractor`: ZIP de até
  **128 MB**, até **300 entradas**, **24 MB** por arquivo,
  **256 MB** de conteúdo descompactado anunciado, profundidade
  menor que 8, relação de compressão máxima 1000:1 para candidatas.
- Filtragem de nomes inseguros para Windows (caminhos absolutos,
  `..`, nomes reservados, caracteres de controle e Unicode bidi),
  tipos não suportados e entradas duplicadas sem abrir conteúdo.
- Tela `DesktopArchivePreviewScreen` apresenta totais, nomes,
  formatos e tamanhos; não oferece controles falsos de extração.
- `--capture-archive-qa` mostra somente inventário sintético,
  sem ler arquivos de usuários.
- Nenhum dado de produção Supabase, senha, token ou arquivo comercial
  é lido, modificado ou publicado.

## Testes e riscos residuais

`DesktopArchiveInventoryTest` cobre classificação, preservação dos
bytes, ausência de extração, nomes inseguros, duplicatas,
ZIP inválido, extensão incorreta, symlink e mais de 300 entradas.

[Workflow de QA Windows ZIP](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-inventory-qa.yml)
realiza compilação, testes em Windows e captura visual, além de
verificar os limites e formatos contra a fonte Android.

**Não está homologado:** RAR/RAR5/7Z, arquivos com senha, importação
com licença/cota, validação CRC/conteúdo das matrizes internas,
exclusão/renomeação, extração, análise da integridade de cada arquivo
interno e interoperabilidade física com bordadeira.

O inventário é **metadados declarados pelo ZIP**, não certificação de
que a matriz interna está íntegra ou é segura para bordar. A
homologação comercial deverá preservar a autorização individual e
as regras de cobrança do Android, além de testes com documentos
explicitamente autorizados pelo cliente.

## Resultado de QA verificado — 07/11/2026

- [Windows ZIP #42381695195](https://github.com/timachado/Brother-Matrizes/actions/runs/42381695195):
  `completed/success` na revisão `eb97467`. Inclui validação do
  contrato de limites Android, compilação da tela, testes de
  inventário sem extração e captura no Windows runner.
- [Captura QA #13577594303](https://github.com/timachado/Brother-Matrizes/actions/runs/42381695195/artifacts/13577594303),
  arquivo `brother-windows-archive-inventory-qa-screenshot`
  (18.556 bytes). O conteúdo é sintético e não usa documentos de clientes.
- Os primeiros jobs de inventário falharam na compilação por uma
  referência a `archivePreview` e `archiveError` antes de sua
  declaração em `FielMain.kt`. A ordem foi corrigida na revisão
  `eb97467` e o pipeline final passou. Nenhuma falha foi ignorada.
- A criação da imagem portátil com o novo inventário é verificada
  separadamente por
  [Windows portátil #42381808212](https://github.com/timachado/Brother-Matrizes/actions/runs/42381808212).
  Sua conclusão e artefato devem ser conferidos antes de comunicar
  um novo ZIP de QA.

## Situação de liberação

Somente QA. Não fazer merge na `main`, upload no site, envio em grupos,
associação automática de arquivos ou liberação de `.exe` comercial.
