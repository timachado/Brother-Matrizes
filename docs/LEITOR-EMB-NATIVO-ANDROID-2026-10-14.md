# Brother Matrizes — Abrir arquivos EMB nativos (implementação incremental)

**Estado:** código integrado ao aplicativo Android em `main`. O workflow
`Brother Matrizes - QA Leitor EMB Nativo` valida compilação e testes sem
assinar ou publicar um APK comercial.

## O que foi implementado
- O seletor de arquivos atual reconhece **EMB**, incluindo amostras com
  extensão adicional `.emb .doc`, e encaminha para a tela nativa de inspeção.
- O arquivo é processado **offline no Android**, sem upload ao servidor.
- Motor autoral Kotlin de contêiner **MS-CFB (OLE)**, FAT/MiniFAT e propriedades
  genéricas MS-OLEPS com limite de 10 MB por EMB.
- Confirma a presença dos streams `DesignDocument` e
  `WilcomDesignInformationDDD`, e apresenta dados declarados: pontos,
  cores, cortes, objetos, paradas, mudanças de cor, etapas de fios.
- Extrai a prévia JPEG `TRUEVIEW_ICON` incluída no arquivo, com limite de
  descompactação e assinatura JPEG validada.
- A tela EMB é separada de `EmbroideryDesign` (que contém pontos
  efetivamente decodificados), portanto não permite exportação simulada.

## Limite muito importante

A compatibilidade disponível é **ABRIR E INSPECIONAR EMB**, **não** converter
EMB em formatos de máquina. O stream binário de objetos e pontos
`DesignDocument` ainda carece de decodificação e QA real. Metadados e
miniaturas não são uma trilha de bordado. O leitor não pode oferecer
`EMB -> PES/JEF/DST` até verificar coordenadas, comandos e cores do desenho.

A conversão anterior entre `PES, JEF, DST` do app segue inalterada.

## Verificação e distribuição
1. Abrir `quiltin puma.EMB` ou `coração. EMB .doc` no **seletor do app**.
2. Verificar prévia, contagens e nome de arquivo sem internet.
3. Voltar ao início, abrir PES/JEF/DST e executar conversões já suportadas,
   validando regressão.
4. Verificar mensagens e segurança com EMB corrompido/arquivo grande.
5. **Não** publicar APK comercial até QA de compilação Android e teste real no
   aparelho. O código no GitHub não atualiza a instalação dos usuários.

## Pesquisa para ativar conversão no futuro
- Obter, para pelo menos duas amostras de versões diferentes, arquivos
  `EMB` e `PES/DST` exportados legitimamente **do mesmo design**.
- Verificar trilha completa de pontos: posição inicial, escala, orientação,
  comando STITCH/JUMP/TRIM/STOP, mudanças de cores, END, contagens,
  bounding box e fidelidade visual/bordado.
- Só habilitar um writer quando o resultado passar por QA e testes de
  bordadeira; não converter imagem em matriz fingindo ser o EMB original.
- Sem SDK, API, código ou licença proprietária Wilcom.

## Privacidade e operação
As amostras originais do proprietário permanecem fora do GitHub público.
O aplicativo comercial de produção não deve mudar até homologação.
Referências de contêiner: especificações genéricas MS-CFB/MS-OLEPS da Microsoft.
