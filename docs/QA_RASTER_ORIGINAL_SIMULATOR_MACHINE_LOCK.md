# Brother Matrizes QA — raster não homologado, somente simulador original

**Referência:** vídeo do usuário em 15/10/2026, cerca de 80s com simulação da logo PANOBIANCO por faixas horizontais.

## Diagnóstico fundamentado
- A opção Ferramentas > Imagem para Bordado gera pontos pelo MultiColorRasterDigitizer, que percorre máscaras raster em **linhas horizontais**. Trata-se de um algoritmo de preenchimento experimental, não necessariamente da técnica apropriada a cada objeto, letra e tecido.
- O caminho anterior em MainActivity já usava a **mesma tela original SimulatorScreen** que a opção Abrir Matriz (Screen.Simulator). Por isso substituir o simulador por outro não corrige a própria matriz.
- O algoritmo automático não foi validado em máquina Brother física; não há evidência para afirmar que densidade, união de segmentos, cortes e deslocamento sejam adequados a produção.
- É incorreto declarar qualquer PES automaticamente seguro apenas porque sua animação no simulador parece correta.

## Correção de contenção
- Ferramentas > Imagem para Bordado: **Gerar matriz / exportar** desabilitado para trajetória raster automática até futura homologação física, sem afetar o antigo Criar desenho/logo ou matrizes PES/JEF/DST abertas.
- Botão **Prévia no simulador original (sem exportar)** permanece disponível, usando SimulatorScreen original e aviso no topo **APENAS PRÉVIA — NÃO ENVIAR PARA A MÁQUINA**.
- A rota de prévia NÃO chama activateDesign/ActiveDesignStore, não salva Recentes, e o botão Voltar leva diretamente à ferramenta Imagem para Bordado, **nunca ao Viewer/exportador**.
- Callbacks protegidos: mesmo que a UI seja acionada de forma indevida, onCreate da ferramenta rejeita gerar/exportar.
- `RasterEmbroideryApproval` isola a política de bloqueio. Testes unitários garantem bloqueio de raster experimental e preservam modo padrão de matrizes abertas.
- A classe **SimulatorScreen.kt não foi alterada**, nem seu motor de simulação. Formatos PES/JEF/DST e assinatura comercial não foram alterados.
- APK de QA isolado `com.timachado.brothermatrizes.qasafe` com nome **Brother Matrizes QA Seguro** (não substitui nem comercial nem QAs existentes).

## O que ainda falta desenvolver ANTES de permitir PES em produção
1. Classificação por objeto e técnica de bordado: Satin para colunas/letras adequadas, tatami com caminhos planejados em regiões amplas, contorno/corrida para detalhes; sem aplicar um único padrão horizontal a toda a arte.
2. Validação de saltos, distância entre agulhadas, densidade, trims, underlay, limite de largura Satin e continuidade, compatível com a máquina e o tecido alvo.
3. Comparação com arquivo PES de referência testado em Brother. A mesma simulação pode ser usada na análise de trajetórias, mas não comprova segurança física.
4. Teste real em **retalho**, com bastidor e estabilizador corretos, revendo os resultados. **Não bordar o arquivo do QA anterior**.

Este documento não altera o simulador aprovado. A base comercial oficial 1.10.8/184 permanece intacta.
