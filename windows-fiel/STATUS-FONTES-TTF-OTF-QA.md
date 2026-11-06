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

## Portões seguintes

1. Portar a **geometria** de `ImportedFontMatrixGenerator` para
   Skia/Desktop ou isolar algoritmo compartilhável, sem alterar
   resultados oficiais do Android. Comparar pontadas de nomes de teste
   por estilo, tamanho, underlay, fonte e orientação.
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
