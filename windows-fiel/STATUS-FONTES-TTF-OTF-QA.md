# Brother Matrizes Windows fiel — Fontes TTF/OTF (parcial, QA)

Última revisão: 06/11/2026. Somente na branch `feature/windows-fiel-android-1-11-8-20261106` / PR #226.

## Auditoria do Android oficial

O Android 1.11.8 usa `font/ImportedFontStore.kt` para importar e guardar
fontes, com **máximo 12 MB**, verificação da assinatura `0x00010000`,
`true` ou `OTTO`, validação `Typeface.createFromFile`, identificação
por primeiros **10 hex SHA-256** e armazenamento local isolado.

O motor real de contornos, satin, zig-zag, underlay e sequenciamento em
`font/ImportedFontMatrixGenerator.kt` tem mais de **200 KB** e depende
de primitivas gráficas Android (`Bitmap`, `Canvas`, `Paint`,
`Path`, `PathMeasure`, `Matrix`, `RectF`). **Não** pode ser
substituído por texto sobreposto à prévia, imagens ou fontes do sistema
sem transformar geometria em pontadas equivalentes. Não copiar
mecanicamente essas APIs Android para JVM.

A tela Android `FontLibraryScreen.kt` também exige autorização de
conta Google e validação de cota de importação na versão comercial.
**A existência da biblioteca local NÃO autoriza contornar esse requisito
no Windows.**

## Implementado nesta etapa, somente backend QA

Arquivo: `desktop/platform/DesktopImportedFontLibrary.kt`.

- Importação de arquivos locais TTF/OTF com extensão e assinatura
  coerentes; recusa diretórios, links simbólicos e arquivos inválidos.
- Limite rígido de 12 MB e validação usando `FontMgr.default.makeFromFile`
  do Skia (mesmo renderizador do Compose Desktop).
- Nome sanitizado e ID com SHA-256 de 10 caracteres hexadecimais.
- Biblioteca local parametrizável por pasta, sem compartilhamento com
  armazenamento Android, dados de clientes ou produção.
- Deduplicação sem sobrescrita; listagem e exclusão por ID validado.
- Testes `DesktopImportedFontLibraryTest` com fontes TTF do próprio
  runner Windows, alteração de extensão, dados falsificados, arquivo
  grande, deduplicação e proteção de originais.

**Não disponibilizar o importador na interface** até implementar e
homologar tanto o gerador de matrizes quanto autorização/cotas reais.
`desktop/platform/DesktopCreatorPlatform.kt` continua retornando
lista vazia e recusa a geração TTF/OTF; isso é proposital e
explicitamente bloqueado.

## Geometria dos glifos — segundo estágio (ainda em QA)

`DesktopSkiaGlyphGeometry.kt` implementa a etapa correspondente à
`buildReferenceGlyphGeometry` / `polygonizeReferencePath` do motor
Android, **sem reescrever as regras de satin**:

- Define escala pela altura do contorno de `H`, como o Android.
- Compõe caracteres por avanços individuais e espaçamento vetorial.
- Preserva a ordem dos glifos e seus múltiplos contornos,
  inclusive os vazados de letras como `O`.
- Normaliza o desenho pelo centro da união das caixas vetoriais.
- Discretiza curvas quadráticas, cúbicas e cônicas do Skia,
  reamostrando cada contorno fechado com
  `max(8, ceil(perimeter/2))` pontos, conforme o parâmetro
  original Android. Diferenças de tesselação Skia/Android
  ainda precisam de comparação numérica em fonte idêntica.
- Rejeita Unicode inválido, glifos ausentes, números não finitos,
  arquivos inadequados e contornos com complexidade excessiva.
- `DesktopSkiaGlyphGeometryTest.kt` verifica `Maria`, os vazados
  de `OO`, espaçamento, dimensões e entradas inválidas.

**Sem pretensão de matrizes prontas:** os polígonos ainda NÃO foram
passados ao gerador `appendReferenceSatinGlyph` do Android.
`CreateNameScreen` não exibe fontes externas até que o cálculo de
pontadas reais, a ordenação e a autorização comercial sejam equivalentes.
A QA da revisão de geometria precisa passar antes de declarar esta
etapa aprovada.

## Kernel de pontadas Satin extraído sem fork (QA de integração)

O Gradle `copyAndroidReferenceSatin` lê diretamente
`app/src/main/java/com/timachado/brothermatrizes/font/ImportedFontMatrixGenerator.kt`
e gera, durante a compilação, `DesktopReferenceSatinKernel.kt`.
Não altera a fonte Android. **Atenção: trata-se do algoritmo de fallback**
`buildReferenceSatinText`, usado quando o fluxo principal
`buildStrokeFlowSatinText` não produz pontadas; não é ainda a
implementação dominante do Android 1.11.8. O trecho extraído contém:

- `appendReferenceSatinGlyph` (colunas de satin e escolha de eixo);
- `scanReferenceSpans`, `buildReferenceColumns` e divisão de colunas;
- `emitReferenceColumn` e `emitReferenceCenterUnderlay`;
- `emitReferenceLock`, `emitReferenceTravel`, `emitReferenceStitchTo`.

Não se copia a função de rasterização `densifyReferenceContours`,
específica de `SampledContour` Android. O novo amostrador Skia entrega
polígonos vetoriais ao kernel e os cálculos de costura aproveitam
**o mesmo código matemático de FALLBACK Android**, sem reimplementar
as pontadas.

O wrapper valida estilo `SATIN`, densidade 0,3–1,2 mm,
compensação 0–1 mm (limites do motor Android), finitude,
número de glifos, pontos de contorno e quantidade final de pontos.

`DesktopReferenceSatinKernelTest` valida emissão de pontos
`STITCH` / `JUMP` para **Maria**, o efeito de underlay no mesmo
contorno, determinismo e igualdade exata do código-fonte gerado
com o trecho oficial Android.

**Limitações fundamentais, verificadas no código Android:**

1. Para texto Satin, o Android chama primeiro
   `buildStrokeFlowSatinText`, cujo `buildSatinByAxis` desenha máscara
   EVEN_ODD com `Bitmap/Canvas`, calcula o esqueleto
   (`thinMask`, `traceSkeleton`) e distribui colunas de satin
   seguindo a topologia do glifo. O código puro de fallback só é
   acionado quando esse fluxo não emite pontadas. **Essa rota principal
   ainda não está portada.**
2. A criação de polígonos no Skia ainda não foi demonstrada
   numericamente idêntica ao `buildReferenceGlyphGeometry` Android
   com a **mesma fonte TTF/OTF**.
3. Mesmo o mesmo kernel de fallback pode produzir saída diferente
   por diferenças de contorno, posicionamento, tesselação e
   arredondamento.

Portanto o kernel permanece **experimental, isolado de UI, clientes
e exportação comercial** até portar o stroke-flow principal, completar
comparação binária de pontadas e validar direitos/licenciamento.

## Stroke-flow: raster e esqueleto Android (etapa isolada)

Nesta revisão, o pipeline **de investigação**, ainda fora de
`CreateNameScreen`, recebeu:

1. `DesktopStrokeRasterizer`: transforma os polígonos dos glifos
   em máscara com preenchimento EVEN_ODD e anti-alias Skia.
   Usa a fórmula original de resolução `max(0,75, maxSpan/1500)`,
   margem de 8 pixels e limiar de opacidade **96/255**, como
   `buildSatinByAxis` Android. Rejeita máscaras excessivamente
   grandes e entradas inválidas. Preserva os vazados internos.
2. `copyAndroidStrokeSkeleton`: Gradle extrai **literalmente**
   `thinMask` e `traceSkeleton` de
   `ImportedFontMatrixGenerator.kt` Android, sem reimplementar
   os critérios de afinamento, ordenação de pixels ou ligação
   de segmentos. `DesktopStrokeSkeleton.analyze` valida
   dimensões e bordas, e devolve as linhas do esqueleto.
3. `DesktopStrokeSkeletonTest`: compara o código extraído à
   fonte original, confere duas colunas desconectadas, contornos
   com vazado, determinismo e máscaras inválidas.
4. `DesktopStrokeRasterizerTest`: testa a máscara EVEN_ODD com
   furo central, o cálculo de esqueleto de `O` a partir de uma
   fonte TTF real e a preservação dos bytes de máscara de entrada.

**A existência de máscaras e esqueletos NÃO produz por si uma matriz
Satin final.** Ainda falta portar exatamente a segmentação,
topologia e distribuição de linhas `buildStrokeFlowRows`,
`planStrokeFlowTraversal`, `emitStrokeFlowCenterRun` e
`emitStrokeFlowCoverage`. Também falta comparar a máscara e a
lista de pontos com o **mesmo arquivo TTF/OTF no Android e no
Windows**. Todas essas rotinas continuam isoladas das telas comerciais.

O runner Windows compila e testa as alterações, mas CI só
é considerada concluída depois do job do commit correspondente
retornar `success`. A branch `main` não foi modificada.

## Planejamento do percurso e linhas Satin — extração original (QA)

**Novos componentes, sem alterar o Android comercial:**

- `copyAndroidStrokePlanner` reproduz literalmente
  `splitStrokeFlowSegments`, `rebaseLeadingClosedSkeletonLine`,
  `rebaseLeadingClosedStrokeFlow`, `preferredOpenSwashEntry`,
  `preferredStrokeFlowEntry`, `planStrokeFlowTraversal` e
  seus auxiliares do `ImportedFontMatrixGenerator.kt` oficial.
  Para cada segmento define **CENTER_RUN → SATIN_RETURN**, de forma
  determinística. O wrapper `DesktopStrokeFlowPlanner` valida
  coordenadas/complexidade e não grava arquivos.
- `copyAndroidStrokeRows` copia literalmente o construtor de linhas
  `buildStrokeFlowRows`, suavização/reamostragem de esqueleto,
  `boundaryDistance` e conversão de pixels em unidades de
  bordado. O wrapper `DesktopStrokeFlowRows` usa a **máscara
  Skia** e aplica os intervalos oficiais de densidade e repuxo.
- `DesktopStrokeFlowPlannerTest` testa as duas fases por trecho,
  percurso em loop, entrada inválida e igualdade exata do código.
- `DesktopStrokeFlowRowsTest` testa o encadeamento de máscara,
  thinning, planejamento e linhas satin em barras reais; repuxo,
  determinismo e não mutação da máscara.

**Estado de homologação:** o planner passou pela compilação da QA
[42204139644](https://github.com/timachado/Brother-Matrizes/actions/runs/42204139644).
As linhas satin foram adicionadas depois e exigem uma nova aprovação da QA
[42204525932](https://github.com/timachado/Brother-Matrizes/actions/runs/42204525932).
Não confundir código implementado com teste aprovado.

**Ainda não conectado:** a emissão original de pontos
`moveStrokeFlowTo`, `emitStrokeFlowCenterRun` e
`emitStrokeFlowCoverage`, a criação de uma `EmbroideryDesign`
completa, equivalência numérica da mesma fonte Android/Skia,
licenciamento e autorização de conta. Nenhuma matriz de fonte
importada sai desse caminho experimental para o cliente.

## Portões seguintes

1. **Concluir a paridade numérica** entre contornos Android e Skia
   com o mesmo arquivo TTF/OTF, e comparar pontadas de nomes sintéticos
   por estilo, tamanho, underlay, fonte e orientação. Os extratores
   de contorno e kernel Satin estão implementados em QA, ainda sem
   aprovação de fidelidade de saída do motor de fonte.
2. Porta de autorização: login, cotas e direitos da conta comercial
   equivalentes ao Android; nenhuma importação deve ser aberta ao
   usuário sem autorização.
3. Portar a verdadeira tela `FontLibraryScreen.kt`, incluindo
   seleção local, importação e ZIP de fontes, preservando navegação,
   visual, mensagens e estados originais.
4. Aprovar captura de telas e testes Windows em PC físico, sem
   substituir a experiência aprovada do Android.

**CI verde de importador não equivale a geração de bordado TTF/OTF.**
Nenhum aplicativo Windows comercial foi publicado.
