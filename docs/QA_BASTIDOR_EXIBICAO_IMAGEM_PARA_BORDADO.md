# Brother Matrizes — Imagem para Bordado: bastidor e exibição

## Diferença detectada nas capturas (16/10/2026)
O usuário comparou a opção nova **Imagem para Bordado** com **Abrir Matrizes**, que possui a ação **Exibição**, modal com **Sólida / Pontos / Realista**, lista de bastidores e opção **Traços de conexão**, além de visualizador, informação de dimensões e simulação. A nova tela não mostrava bastidor nem tamanho da arte e fixava 60 mm/100×100 mm silenciosamente.

## Melhorias QA
- **Bastidor** visível antes de preparar a arte com chips horizontais para TODOS os HoopProfile do próprio app original.
- Botão **Exibição** que abre exatamente `DisplaySettingsSheet` compartilhado com `ViewerScreen` original, incluindo modos **Sólida/Pontos/Realista**, bastidor e traços de conexão.
- **Tamanho da arte em mm** antes da simulação; slider de 10 mm até largura máxima que respeita largura E altura útil do bastidor (incluindo margens de segurança), calculado a partir da proporção do raster preparado.
- Quando mudar para bastidor menor, a largura escolhida é reduzida para o valor máximo que cabe, quando possível. Se não couber, bloquear botão Simular bordado e orientar redução/seleção de bastidor.
- Estado das opções persiste ao ir ao simulador e voltar para a tela simples, sem reabrir involuntariamente o picker.
- **Simular bordado** passa `referenceHoop`, `displayMode` e `showConnections` escolhidos para o **SimulatorScreen original**, mantendo flag de QA somente visualização.
- Nenhuma alteração em `SimulatorScreen.kt`, `ViewerScreen.kt` ou `EmbroideryDisplay.kt`. Sem botão Enviar à máquina, Converter, Compartilhar ou Salvar cópia na rota da foto porque não existe matriz validada.
- O gerador continua restrito a Satin reto de agulhadas verticais e pode recusar logo Panobianco completo. Não retorna ao preenchimento horizontal.
- Aplicativo experimental separado `com.timachado.brothermatrizes.qahoop` (Brother Matrizes QA Bastidor), sem substituir versão comercial oficial ou QAs anteriores.

## QA
- [ ] `testDebugUnitTest` e `assembleDebug`
- [ ] Verificar seleção dos 5 bastidores na interface real
- [ ] Confirmar modal de Exibição idêntico ao do Viewer
- [ ] Ajustar tamanho e verificar limites de width/height antes de simular
- [ ] Verificar que o simulador usa as opções escolhidas e Voltar mantém imagem/seleção
- [ ] Confirmar que exportação PES e envio à máquina seguem bloqueados
