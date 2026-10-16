# Brother Matrizes — Imagem para Bordado: abertura simples

## Referência do usuário
Vídeo 1000375783.mp4: a opção **Abrir Matrizes** já abre o seletor Android (Recentes, Imagens, Drive, Arquivos), e após selecionar um PES exibe a matriz em uma tela limpa. A nova entrada Imagem para Bordado deve seguir o mesmo conceito de simplicidade para clientes leigas, com pouca navegação.

## Implementado
- A entrada **Início > Ferramentas > Imagem para Bordado** chama uma NOVA tela enxuta `SimpleImageToEmbroideryScreen`.
- Ao entrar, dispara diretamente o **seletor nativo de arquivos/imagens Android**, através de `ActivityResultContracts.OpenDocument()` com filtro `image/*`. O seletor permite imagens locais recentes e provedores compatíveis; a escolha exata de app de fotos depende do aparelho. Não importa PES como fotografia.
- Se cancelar, a tela mostra somente **Abrir imagem** e **Voltar**.
- Ao escolher a imagem: imagem grande com gesto fora da lista rolável; sugestões curtas **Imagem inteira / Parte 1 / Parte 2** baseadas no detector heurístico existente; uma ação **Preparar imagem**, uma **Escolher outra imagem**, prévia da transparência se o fundo pôde ser isolado.
- Não há sliders, detalhes técnicos de densidade, listas de camadas, backup nem alternância de formatos ocupando a tela de boas-vindas.
- A função comercial existente **Abrir Matrizes** permanece intacta com picker de PES/JEF/DST e suas funcionalidades de visualização, simulação e transferência.
- A **geração/exportação PES por foto continua temporariamente bloqueada** na nova entrada. O usuário pediu apenas fluxo simples, mas as trajetórias raster horizontais ainda não são aprovadas como Satin vertical; não expor gerador antigo que pode produzir arquivos inadequados à Brother.
- A fotografia original é mantida em memória durante a sessão da tela; não é salva no dispositivo pela tela simples.
- O APK de QA usa pacote distinto `com.timachado.brothermatrizes.qasimple`, nome **Brother Matrizes QA Simples**. Não substitui app comercial nem QAs anteriores.

## Validação necessária
- [ ] Gradle `:app:testDebugUnitTest`
- [ ] Gradle `:app:assembleDebug`
- [ ] Android físico: tocar no card, seletor abre sem tela intermediária; cancelar sem crash; reabrir arquivos locais
- [ ] Importar PNG/JPEG/WEBP e conferir arte
- [ ] Selecionar um elemento sugerido, ou ajustar recorte por gesto, sem deslocar toda a tela
- [ ] Preparar/visualizar resultado sem exportar PES inseguro

## Próxima etapa técnica independente
O Satin vertical precisa de uma implementação específica e validação de pontos conforme coluna; apenas trocar o simulador não altera a sequência de agulhadas. Não desbloquear exportação até que haja testes físicos.

Base de produção 1.10.8/184, assinatura comercial e motor SimulatorScreen.kt permanecem sem alterações.
