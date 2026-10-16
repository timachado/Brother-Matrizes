# Brother Matrizes QA — imagem aberta diretamente no bastidor original

Vídeo de referência: `1000375944.mp4` (16/10/2026). O usuário mostrou logo PANOBIANCO, troca de bastidor que não alterava o tamanho, barra de escala e um pseudo-bordado vertical na simulação com banner de "PRÉVIA ILUSTRATIVA". Essa animação não representava Satin profissional e precisava sair do fluxo.

## Implementação
1. **Primeira tela é o próprio bastidor.** Ferramentas > Imagem para Bordado continua acionando seletor nativo Android diretamente. Após escolher uma foto, `RasterArtworkAutoCrop.detect` localiza limites úteis de um desenho por alfa ou contraste com bordas; `RasterPhotoPreparation.prepare` remove fundo conectado à borda quando apropriado. A arte resultante aparece automaticamente no bastidor sem pedir "Preparar imagem".
2. A tela reutiliza o componente **`EmbroideryCanvas` original** com parâmetros *opcionais* `rasterArtwork`/`rasterWidthMm`, permanecendo intacto o desenho de ponto, hoop, grid e motor original de matrizes PES. Na rota de imagem, o objeto passado ao canvas é uma `EmbroideryDesign` **sem nenhum ponto**; o logo é **bitmap sobre bastidor**, não uma matriz sintética. A lógica de posicionamento reproduz a mesma orientação automática do bastidor (retrato/paisagem) do componente original.
3. **Trocar bastidor reajusta o desenho** para 92% da área útil (porcentagem regulável pelo Slider), tanto ao aumentar quanto diminuir. `ImageHoopSizing` considera rotação de referência e proporção da arte; barra controla porcentagem, não widthMm absoluto fixo; canvas recompõe a cada alteração.
4. **Recorte opcional** abre editor por gesto diretamente na própria tela; ao arrastar ou selecionar uma região candidata, preparação e renderização são atualizadas automaticamente. Não existe prévia duplicada quadriculada em outra tela.
5. **Simular pontos Satin** chama o SimulatorScreen original *somente se* `MultiColorRasterDigitizer` gerou agulhadas Satin retas válidas. A antiga simulação de **linhas verticais ilustrativas** foi removida desta navegação: ela pode confundir usuários e não é uma matriz. Logo completo, letras complexas e símbolos incompatíveis receberão informação clara e poderão ser recortados em partes; não há promessa de reconstrução automática de glifos.
6. A rota continua protegida (não usa Viewer/exportação/Recentes ou envio para máquina) até um gerador Satin profissional, por objetos e trilhos, passar em ensaio físico com uma Brother. O original `SimulatorScreen.kt` e seu motor não são alterados.
7. Este APK é separado como `com.timachado.brothermatrizes.qanativeimage` / `Brother Matrizes QA Visual`. Não substitui oficial comercial 1.10.8 (184), assinatura nem QAs antigas.

## Critérios de validação
- [ ] `:app:testDebugUnitTest`, com detecção de logo e ajuste automático retrato/paisagem
- [ ] `:app:assembleDebug` + artefato QA instalável
- [ ] Teste manual Android: abrir imagem -> logo aparece automaticamente no bastidor; troca entre 100×100 e 130×180 altera dimensões da arte e o desenho visual na mesma tela; arrastar Slider muda escala imediatamente; abrir Ajustar recorte arrasta seleção e a prévia reflete ajuste; Exibição original continua acessível.
- [ ] Teste de simulação Satin em elemento isolado geométrico adequado; Panobianco inteira pode continuar incompatível e não deverá mostrar falsa simulação.
- [ ] Futuro: converter partes com Satin vetorial profissional, comparar com matrizes de referência e bordar fisicamente em retalho antes de liberar PES.
