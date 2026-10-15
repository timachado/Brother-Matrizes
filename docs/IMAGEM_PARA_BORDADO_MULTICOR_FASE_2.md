# Imagem para Bordado — Fase 2: separação multicor (experimental)

## Objetivo
Experimentar **arte com cores chapadas**, como símbolo laranja + texto branco em fundo preto, gerando blocos de linha distintos em PES. O motor anterior de imagem monocromática fica disponível e inalterado.

## Funcionalidades presentes no protótipo
- Seletor nativo de PNG/JPG/WebP, sobre o fluxo da etapa 1.
- Modo alternável **Monocromático / Multicor (experimental)** no módulo Criar desenho/logo.
- Quantização local RGB e agrupamento por centro de cor em no máximo **2 a 4 cores**.
- Estimativa da cor de fundo pela mediana das bordas (ou transparência alfa para PNG com fundo transparente), e descarte de pixels próximos à cor de fundo.
- Áreas isoladas por cor com comandos de JUMP entre faixas e TRIM/COLOR_CHANGE entre blocos.
- Preenchimento em linhas alternadas, com pontos internos desencontrados (*Tatami-like*), comprimento limitado e prevenção de costurar diretamente sobre buracos.
- Aproveita o writer e o reabridor PES existentes sem modificar seus motores; preserva JEF/DST como formatos alternativos.
- Testes unitários de separação entre branco/laranja/preto, fundo PNG alfa e reabertura PES com mudança de cor.

## Limitações
**Este módulo ainda não faz digitalização profissional automática.** A separação de cores é uma aproximação. Não usar a fotografia inteira do roupão com a modelo: o aplicativo tentaria interpretar detalhes da roupa e pele como bordado. Recortar **só os elementos gráficos** ou obter a arte original (preferencialmente SVG/PNG transparente).

Pendências de qualidade:
1. Pré-processamento avançado: remover fundo manualmente, mascarar cor, desfazer sombras/antialiasing, editar paleta, restaurar contornos e vetorização.
2. Geometria: classificação de ilha, oclusão, direção da fibra, identificação de colunas estreitas e centro do traço; não apenas scanlines raster.
3. **Satin profissional** com rails, comprimentos variáveis, acabamento de curvas, compensação do tecido e underlay de sustentação.
4. **Tatami profissional** com direção, percursos sob a área, density por tipo de tecido, anti-colisão e compensação de bordas.
5. Otimizador: sequência multicor e prioridade de bordados, cortes, puxamentos, trocas e paradas.
6. Perfis de tecido de cetim leve, espuma/estabilizador, teste real em amostras, calibração por máquina e bastidor.
7. Controles de edição e alteração da sequência de cores pelo usuário.

## Exemplo do roupão
- **Frente:** recorte da marca Panobianco separando símbolo laranja e escrita branca. A imagem de referência não garante vetor fiel nem costura sem ajustes; preparar arte original.
- **Costas:** usar modo **Criar Nome** com fonte cursiva compatível e contorno original; símbolo/linhas podem ser criados como outras camadas. Ajustar manualmente os pontos Satin para preservar conexões.

## Publicação
Módulo exclusivo da branch de pesquisa, com CI para testes JVM e APK debug. **Não realizar merge na main nem lançar versão comercial** até inspecionar matriz em dispositivo e fazer bordado real. O arquivo PES é um conjunto real de pontos, mas sua resistência e acabamento exigem prova em tecido.

### Documentação de referência
- https://inkstitch.org/docs/workflow/
- https://inkstitch.org/docs/stitches/fill-stitch/
- https://inkstitch.org/docs/stitches/satin-column/
