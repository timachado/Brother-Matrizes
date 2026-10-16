# Brother Matrizes — QA corretiva para bastidor e simulação de imagem

## Falha reproduzida (vídeo 1000375916.mp4, 16/10/2026)
O bastidor selecionado (130 × 180) aparece como texto mas as opções de troca estavam parcialmente escondidas numa faixa horizontal abaixo do recorte, gerando confusão. Ao tocar em **Simular bordado** com a logo PANOBIANCO inteira, o fluxo retornava "Este elemento não forma uma coluna Satin com pontos verticais" sem abrir o simulador.

## Correções
1. **Bastidor** agora é botão claro e sempre visível abaixo do título da ferramenta, separado de **Exibição**. A seleção abre modal com **TODOS os cinco HoopProfile** em lista vertical de botões e selecionado realçado. Ao selecionar novo bastidor, aplica limite de largura para respeitar área útil e proporção.
2. **Exibição** continua abrindo o `DisplaySettingsSheet` original para Sólida/Pontos/Realista, bastidor e conexões, sem alterar código do Viewer/Simulator original.
3. Reduzida área de foto para 210dp apenas na tela simples; mantém recorte fora da rolagem. Removido nome técnico `file_0000...` da UI e chips duplicados de bastidor do painel inferior.
4. **Simular bordado** continua tentando Satin reto com agulhadas verticais. Se uma logo complexa não passar na validação geométrica Satin, agora cria uma **amostragem ilustrativa vertical**, com `format=QA_PREVIEW` e `sourceBytes=ByteArray(0)`, para o SimulatorScreen original animar uma representação em linhas verticais. Assim a experiência não termina em erro, mas **essa prévia não é Satin digitalizado**, nem uma matriz de máquina. O próprio simulador mostra a advertência explícita.
5. O gerador de amostragem ilustrativa aplica deslocamentos como `JUMP`, nunca `STITCH` horizontal. Não serve para salvamento ou bordado, não calcula trilhos de Satin, tensão, underlay, cortes ou segurança de percurso; os caminhos anteriores de exportação e envio permanecem bloqueados, inclusive por código de navegação.
6. A interface de foto e o próprio `SimulatorScreen.kt` comercial não foram alterados em sua lógica central. Pacote `com.timachado.brothermatrizes.qahoopfix`, nome **Brother Matrizes QA Corrigido**, instalado em paralelo sem afetar oficial.

## Testes
- [ ] `:app:testDebugUnitTest` cobrindo imagem colorida complicada, inexistência de pontos STITCH horizontais na prévia geométrica, limite de bastidor e bloqueio de exportação.
- [ ] `:app:assembleDebug` e artefato QA extraído
- [ ] Manual no Android: abrir Ferramentas → Imagem para Bordado; bastidor aparece no topo, cinco opções legíveis, selecionar 130 × 180; preparar logo PANOBIANCO inteira, Simular bordado abre simulator original, mostra banner "PRÉVIA ILUSTRATIVA — NÃO É MATRIZ SATIN", voltar preserva imagem.
- [ ] QA físico de Satin por objeto ainda pendente. Nunca bordar uma amostragem ilustrativa ou exportar como PES.

## Critério para próxima etapa
Construir detector de seções de rails Satin por objeto, dividir letras e símbolo, gerar geometrias com larguras e direções corretas e validar em máquina Brother real. A prévia gráfica não substitui esse motor.
