# Brother Matrizes Windows — inventário ZIP, RAR/RAR5 e 7Z (QA)

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

- Menu Windows: **Biblioteca → Analisar ZIP / RAR / 7Z (somente leitura)**.
- Seleção explícita do pacote ZIP/RAR/7Z pelo JFileChooser; nenhuma pasta do cliente
  é escaneada automaticamente.
- `DesktopArchiveInventory.inspect` lê apenas os metadados de cada
  pacote local (ZIP, RAR/RAR5 ou 7Z); não extrai os
  arquivos internos, não grava em pasta temporária e não usa rede.
- Tipos reconhecidos conforme Android:
  **PES, DST, JEF** (matrizes) e **TTF, OTF** (fontes).
- Limites espelhados do `SafeArchiveExtractor`: pacote de até
  **128 MB**, até **300 entradas**, **24 MB** por arquivo,
  **256 MB** de conteúdo descompactado anunciado, profundidade
  menor que 8, relação de compressão máxima 1000:1 para candidatas.
- Filtragem de nomes inseguros para Windows (caminhos absolutos,
  `..`, nomes reservados, caracteres de controle e Unicode bidi),
  tipos não suportados e entradas duplicadas sem abrir conteúdo.
- Tela `DesktopArchivePreviewScreen` apresenta o formato detectado,
  totais, nomes, tipos e tamanhos; sem controles falsos de extração.
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

**Não está homologado:** leitura de RAR real em todos os formatos, arquivos com senha, importação
com licença/cota, validação CRC/conteúdo das matrizes internas,
exclusão/renomeação, extração, análise da integridade de cada arquivo
interno e interoperabilidade física com bordadeira.

O inventário mostra **metadados declarados pelo pacote**, não certificação de
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
  **Resultado confirmado:** `completed/success` na revisão
  `c226168`. A imagem portátil foi construída, os testes prioritários
  passaram, e o launcher executou as capturas Windows com a nova tela
  de análise ZIP. Artefatos temporários privados:
  [Portable QA #13578400425](https://github.com/timachado/Brother-Matrizes/actions/runs/42381808212/artifacts/13578400425)
  (ZIP com launcher e runtime, ~95 MB) e
  [capturas #13578385395](https://github.com/timachado/Brother-Matrizes/actions/runs/42381808212/artifacts/13578385395).
  A retenção é temporária; não houve publicação comercial.

## Situação de liberação

Somente QA. Não fazer merge na `main`, upload no site, envio em grupos,
associação automática de arquivos ou liberação de `.exe` comercial.

## Evolução RAR / RAR5 / 7Z — QA 07/11/2026

- O inventário Windows agora reconhece as assinaturas reais de
  **ZIP, RAR/RAR5 e 7Z** e recusa discrepância entre extensão e bytes.
  Aceita somente arquivos locais regulares, até 128 MB, selecionados
  explicitamente pelo usuário; symlinks não são analisados.
- Integra as **mesmas versões do Android oficial**:
  `com.github.junrar:junrar:8.1.1`,
  `org.apache.commons:commons-compress:1.28.0`,
  `org.tukaani:xz:1.10`. O leitor 7Z utiliza limite de
  memória de 32 MiB, e o RAR limita dicionário a 32 MiB.
- Em todos os formatos, utiliza apenas listagem de cabeçalhos:
  **sem descompactação, leitura individual de matriz, importação,
  escrita, pedido de senha, cobrança ou alteração em arquivos originais**.
  Arquivos protegidos por senha podem ser rejeitados na leitura.
- O teste `DesktopArchiveRar7zInventoryTest` gera **um 7Z sintético
  real**, identifica matrizes e fontes, rejeita caminhos inseguros
  e confirma que nenhum arquivo foi extraído. Também rejeita
  assinatura falsa e cabeçalhos RAR/RAR5/7Z inválidos.
- **RAR/RAR5 real permanece com homologação manual pendente**:
  o teste automatizado não contém um RAR autêntico e
  autorizado. Não alegar compatibilidade universal sem esse ensaio.
- **Licença/redistribuição:** antes de distribuição comercial,
  revisar formalmente os termos e avisos de licença do `junrar`
  (componentes UnRAR), e também gerar/inspecionar o relatório de
  dependências, segurança, SBOM e notices. O QA privado não é
  um release nem autoriza essa distribuição.

Workflows de validação:
[Arquivos compactados](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-archive-inventory-qa.yml)
e
[Windows portátil QA](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-portable-qa.yml).

A autorização comercial **por item** e o registro/finalização da cota
permanecem bloqueios obrigatórios para qualquer importação futura.

## Evidência CI de ZIP/RAR/7Z — 07/11/2026

- [Inventário Windows #42385223806](https://github.com/timachado/Brother-Matrizes/actions/runs/42385223806):
  `completed/success`, após corrigir a validação do tamanho comprimido
  não informado no leitor 7Z/RAR, preservando a regra do Android. Os
  testes de 7Z sintético real e os testes anteriores de ZIP passaram.
  Captura de QA (somente dados sintéticos):
  [#13579092825](https://github.com/timachado/Brother-Matrizes/actions/runs/42385223806/artifacts/13579092825).
- [Portable Windows #42385223821](https://github.com/timachado/Brother-Matrizes/actions/runs/42385223821):
  `completed/success`; testes prioritários, geração de APP-IMAGE,
  execução do launcher e capturas de tela realizados no Windows runner.
  Artefatos privados temporários:
  [pacote portátil QA #13579775101](https://github.com/timachado/Brother-Matrizes/actions/runs/42385223821/artifacts/13579775101)
  (~98 MB, launcher e runtime completos) e
  [capturas #13579860000](https://github.com/timachado/Brother-Matrizes/actions/runs/42385223821/artifacts/13579860000).
- O QA não contém arquivo RAR autêntico: a biblioteca de listagem RAR
  compilou, mas a leitura positiva em RAR/RAR5 real ainda é pendente.
  Também não houve teste em equipamento de bordado, conta comercial ou
  documento de cliente. Sem distribuição, merge na main ou publicação
  no site da T.I. Machado.
