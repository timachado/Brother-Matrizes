# Brother Matrizes Windows — Criar Desenho/SVG (QA isolada)

**Fonte da verdade:** Android comercial 1.11.8, `CreateDrawingScreen.kt`,
`SimpleSvgParser` e `VectorMatrixGenerator`. Branch experimental:
`feature/windows-fiel-android-1-11-8-20261106`; PR #226.

## Entrega implementada

- A tela **Criar Desenho** do Android é extraída para Compose Desktop,
  mantendo controles de largura, estilo de pontos, paleta, bastidor,
  formato PES/DST/JEF, preview, gestos de desenho livre, **Gerar matriz**
  e **Simular desenho**. Não é uma reprodução visual aproximada.
- O adaptador de plataforma substitui **somente** o antigo
  `ActivityResultContracts.OpenDocument`/`ContentResolver` pelo
  `JFileChooser` nativo Windows. O aplicativo não grava o SVG original.
- `DesktopSvgFilePicker` aceita exclusivamente `.svg` local regular,
  sem symlinks de arquivo ou da pasta imediata e no máximo 2.000.000
  bytes. Somente o documento explicitamente selecionado é lido.
- O SVG escolhido é interpretado pelo **mesmo**
  `SimpleSvgParser.parse` do Android; a geração de pontos continua em
  `VectorMatrixGenerator.generate` original, sem motor paralelo.
- No menu do Windows aparece **Criar → Desenho / SVG**, separado da
  criação de nomes e monogramas, preservando todas as rotas anteriores.
- A tela utiliza diretamente o mouse nos gestos Compose existentes.
  Ainda não se declarou equivalência em tablets de desenho nem suporte
  a todo o padrão SVG. O parser original aceita apenas um subconjunto.
- A captura `--capture-create-drawing-qa` abre a tela sem dados
  do cliente, sem arquivo importado e sem gravar matrizes.
- O código do Android comercial, a autenticação, as licenças,
  os arquivos FLP e o site oficial não foram modificados.

## Aceite QA

1. Compilar a tela Android transformada e impedir imports Android no
   projeto JVM Windows.
2. Testar `DesktopSvgFilePickerTest`: SVG sintético,
   `SimpleSvgParser`, motor vetorial, preservação dos bytes, arquivo
   ausente, extensão incorreta, vazio, acima de 2 MB e symlink.
3. Capturar a interface original no runner Windows, e executá-la pelo
   launcher portátil com runtime embutido.
4. Fazer ensaio manual com mouse, SVG autorizado e
   `Salvar projeto FLP` antes de afirmar paridade real com Windows.

Os resultados deverão ser consultados em:
[QA Criar Desenho](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-drawing-qa.yml)
e
[QA Portátil](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-portable-qa.yml).

**Bloqueios da versão comercial:** autenticação Google e cotas TTF/OTF,
rotas ainda incompletas, aparelho de bordado real, homologação de
arquivos de clientes autorizados, assinatura e distribuição.

## Resultado verificado — 07/11/2026

- [Workflow Windows Criar Desenho #42380552922](https://github.com/timachado/Brother-Matrizes/actions/runs/42380552922):
  `completed/success` no commit `cc0493b`. Compilação da UI oficial
  Android adaptada, testes `DesktopSvgFilePickerTest` com SVG válido,
  motor de pontos, extensão/tamanho, symlinks, documentos malformados,
  DOCTYPE/entidades externas; tela real capturada pelo runner Windows.
- [Captura de QA #13577133921](https://github.com/timachado/Brother-Matrizes/actions/runs/42380552922/artifacts/13577133921):
  `brother-windows-create-drawing-qa-screenshot`, 14.291 bytes.
- Os cenários não homologam suporte integral a SVG arbitrário,
  desenho com mesa digitalizadora, compatibilidade em PC de usuário
  ou abertura física de uma matriz pela bordadeira.
- A geração do novo launcher portátil (APP-IMAGE) possui verificação
  independente em [workflow Portátil QA #42380552935](https://github.com/timachado/Brother-Matrizes/actions/runs/42380552935);
  **Resultado verificado:** `completed/success` na revisão `cc0493b`.
  O launcher portátil `BrotherMatrizesQA.exe` foi executado no
  Windows runner e passou nas telas Home, Conta, Fontes, Enviar e
  Criar Desenho. Artefatos privados temporários:
  [pacote QA #13577308082](https://github.com/timachado/Brother-Matrizes/actions/runs/42380552935/artifacts/13577308082)
  (ZIP com launcher e runtime, ~95 MB) e
  [capturas #13577920939](https://github.com/timachado/Brother-Matrizes/actions/runs/42380552935/artifacts/13577920939).
  Nenhum instalador comercial, release público ou upload no site foi feito.
