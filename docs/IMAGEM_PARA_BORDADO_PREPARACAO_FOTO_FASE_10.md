# Brother Matrizes — Etapa 10: preparar logo a partir de uma foto

## Base e escopo
Branch isolada baseada na PR #94 (Ferramentas > Imagem para Bordado) e seus módulos anteriores (#83, #85, #87, #88, #89, #91, #92, #93). Produto comercial original v1.10.8 (versionCode 184) **não foi modificado**.

## Entrega
Na nova tela **Ferramentas > Imagem para Bordado**, ao importar PNG, JPEG ou WebP aparece o bloco **Preparar a logo**:
1. **Arraste um retângulo** na fotografia para selecionar apenas a marca/nome, preservando uma pequena margem de tecido.
2. **Remover fundo conectado às bordas** ligado por padrão. É opcional, portanto PNGs com transparência existente podem continuar sem nova remoção.
3. Slider de **tolerância de cor (12–110)** para roupas de fundo aproximadamente uniforme.
4. **Aplicar recorte e preparar matriz** atualiza a fonte da digitalização existente e zera apenas as edições de camadas da imagem anterior. Mostra as dimensões e quantos pixels de fundo foram eliminados.
5. **Restaurar imagem original** reverte a aplicação antes do próximo salvamento. Os comandos de matriz continuam em PES/JEF/DST, com PES como destaque principal.

## Implementação real
- `RasterPhotoPreparation.prepare` opera em pixels ARGB originais (máximo 512 × 512 após o decoder seguro), com retângulo normalizado para independência da tela.
- Remoção é feita com **flood-fill 4-vizinhos a partir da borda do recorte**, usando RGB mediano da borda como amostra. Apenas áreas conectadas a essa borda e dentro de tolerância recebem alfa 0. **Áreas internas totalmente fechadas não são apagadas indiscriminadamente**.
- Verificações de recorte, ocupação final, pixels mínimos e proporção removida recusam resultados perigosos.
- O raster preparado alimenta o MultiColorRasterDigitizer já existente; os testes reabrem o PES gerado.
- Foto original fica em memória para desfazer o recorte. Ao salvar projeto .bmrail armazena-se **a fonte atualmente preparada**, não o original da fotografia.

## Limitações importantes
**Não é remoção inteligente de fundo em qualquer foto.** Dobras, texturas de tecido, sombra, fundo multicor e marca sem contraste suficiente continuam podendo confundir a separação. A aproximação RGB por borda é conservadora e não equivale a segmentação semântica. Foto do roupão PANOBIANCO ACADEMIAS requer teste com a fotografia específica e ajuste de recorte; a caligrafia Sabrina Freitas exige Satin manual quando necessário. O funcionamento da ferramenta precisa ser validado em Android real e com amostra física em máquina Brother.

## Critérios de QA
- [ ] Testes JVM para recorte, alfa PNG, logo laranja/branca em fundo preto, furo interno preservado e falhas seguras
- [ ] Pipeline: fonte recortada + remoção de fundo → separação multicor → geração → reabertura PES
- [ ] Android Gradle assembleDebug, sem mudanças no simulador
- [ ] Android real: gesto de recorte, fundo liga/desliga, tolerância, aplicar/reverter, salvar projeto
- [ ] Máquina Brother: validação do bordado produzido
- [ ] Assinatura comercial original e versionCode crescente somente depois de homologação; nenhum APK debug sobre instalação comercial
