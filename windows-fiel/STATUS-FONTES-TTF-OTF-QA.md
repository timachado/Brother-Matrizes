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
Não altera a fonte Android. O trecho extraído contém exatamente:

- `appendReferenceSatinGlyph` (colunas de satin e escolha de eixo);
- `scanReferenceSpans`, `buildReferenceColumns` e divisão de colunas;
- `emitReferenceColumn` e `emitReferenceCenterUnderlay`;
- `emitReferenceLock`, `emitReferenceTravel`, `emitReferenceStitchTo`.

Não se copia a função de rasterização `densifyReferenceContours`,
específica de `SampledContour` Android. O novo amostrador Skia entrega
polígonos vetoriais ao kernel e os cálculos de costura aproveitam
**o mesmo código matemático Android**, sem reimplementar as pontadas.

O wrapper valida estilo `SATIN`, densidade 0,3–1,2 mm,
compensação 0–1 mm (limites do motor Android), finitude,
número de glifos, pontos de contorno e quantidade final de pontos.

`DesktopReferenceSatinKernelTest` valida emissão de pontos
`STITCH` / `JUMP` para **Maria**, o efeito de underlay no mesmo
contorno, determinismo e igualdade exata do código-fonte gerado
com o trecho oficial Android.

**Limitação fundamental:** a criação de polígonos no Skia não
foi demonstrada numericamente idêntica à função
`buildReferenceGlyphGeometry` do Android para a **mesma fonte TTF/OTF**.
Mesmo com algoritmo Satin compartilhado, diferenças de contorno,
posicionamento, resolução e arredondamento podem produzir matrizes
diferentes. Portanto o kernel permanece **experimental, isolado de UI,
clientes e exportação comercial** até completar comparação binária
de pontadas, validação física e autorização/licenciamento.

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
