# QA — Vasos de Bênção, imagem para bordado

## Proveniência
- Imagem fornecida pelo usuário durante a revisão de **Imagem para Bordado**.
- Arquivo de origem: `1000376190.jpg`, JPEG RGB 1536 × 1536.
- SHA-256 do JPG original: `3bcc4367374db3ec79a80dbcf9588041b78dde8ff43a2587edf32db3c926af91`.
- Fixture reprodutível derivada: `app/src/test/resources/qa/vasos_de_bencao_96px_palette_zlib.b64`, 96 × 96, paleta indexada de 12 cores e zlib. SHA-256 dos bytes descompactados: `4efbfcf10197f75d92f2cdff05a5c5fa935886c209a0a32bc60323f42ecaa44f`.
- **A fixture reduzida serve apenas para regressão estrutural**, não preserva as curvas finas suficientes para aprovar a digitalização completa. Comparações visuais de fidelidade exigem a referência original.

## Complexidade identificada no JPG
- Fundo branco predominante; no original, aproximadamente 66,1% dos pixels apresentam componentes RGB acima de 235.
- Aproximadamente 13,3% dos pixels pertencem a regiões azul-marinho por critério de cor conservador; 11,9% às regiões douradas (critérios independentes, apenas estimativas).
- Um anel duplo dourado, faixas finas e sombras de metal.
- Inscrição cursiva azul-escuro "Vasos"; subtítulo "de"; "Bênção" em dourado com serifas e acentos.
- Chama com degradê e raios delicados; cântaro azul-escuro com bordas douradas, alça e fluxo de líquido; Bíblia aberta com finas linhas nas páginas e base azul.

## Diferença fundamental: imagem vs matriz importada
- Abrir um PES/JEF/DST válido recebido pelo Telegram disponibiliza pontos de costura e comandos de mudança de cor/salto. O simulador pode seguir os pontos reais já codificados no arquivo.
- Importar um JPG não disponibiliza pontos. Mostrar a foto sobre o bastidor **não equivale à simulação**.
- Qualquer prévia de pontos precisa ser derivada de colunas Satin, áreas de preenchimento e pontos corridos construídos e checados. Jamais converter fotografia inteira em Satin vertical por aparência.

## Técnicas prováveis para digitalização real
1. Satin acompanhando direção das letras estreitas e dos traços; separar intersecções e contraformas.
2. Fill/tatami apropriado nas regiões largas (cântaro e base), com ângulos, densidade e underlay definidos.
3. Ponto corrido/triplo para raios e linhas finas da Bíblia ou detalhes onde Satin não é adequado.
4. Discretização de tons para paleta real de linhas de bordado, reconhecendo que degradês e reflexos digitais não podem ser idênticos ponto a ponto.
5. Ordem de costura, cortes, saltos, compensação de puxamento e seleção do bastidor; nunca disfarçar saltos como pontos costurados.

## Critérios para recomendar APK de teste
- [x] Fixture derivada anexada e testável com verificação criptográfica.
- [x] A imagem complexa pode permanecer visível no bastidor sem inventar matriz.
- [x] Rota automática simplificada recusa como Satin completo artes multitonais com azul/dourado desta classe.
- [ ] Comparação visual da geometria real dos pontos com o JPG original em resolução adequada.
- [ ] Digitalização por famílias de pontos: Satin, preenchimento e traçado, com paleta e sequência de costura.
- [ ] Cores, ilhas e contraformas preservadas para esta imagem e para a PANOBIANCO.
- [ ] Teste no Android com barra de progresso no **simulador original**; mesmas coordenadas e comandos que um PES correspondente.
- [ ] Evidência de não regressão de importação PES/JEF/DST, bastidores e versão comercial.
- [ ] Teste de costura em tecido e revisão de qualidade antes de qualquer exportação ou envio à máquina.

**Estado:** QA de segurança e referência, não matriz PES aprovada. Exportação da ferramenta continua bloqueada. Não publicar nem usar na máquina.
