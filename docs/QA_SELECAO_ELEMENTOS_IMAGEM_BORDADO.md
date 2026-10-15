# Brother Matrizes QA — Ajuste de usabilidade + regiões bordáveis (proposta)

## Evidência
Vídeo enviado pelo usuário em 15/10/2026: a foto aparece no rodapé da tela em um pequeno retângulo de preparação dentro de uma lista com rolagem; o topo, mesmo com imagem importada, ocupa grande espaço vazio aguardando a matriz. Arrastar o dedo na foto disputa o gesto com a rolagem vertical do painel.

## Ajustes implementados
- **Imagem original no topo**, com área de toque de **280dp**, fora do painel rolável. O painel inferior fica reservado para ferramentas/seleção.
- O preview vazio do bordado é reduzido quando a ferramenta está importada, mas ainda não tem matriz confirmada. Após confirmar a máscara, retorna a prévia de pontos existente.
- **Identificar elementos:** após abrir PNG/JPG/WebP, um detector **local e heurístico** procura regiões isoladas pelo contraste em relação à borda (ou pelo alfa em PNG), agrupa pixels adjacentes e sugere **até 8 regiões**.
- Regiões candidatas contornadas em azul-turquesa na foto e listadas como **Elemento 1, 2, ...**. O usuário escolhe qual preparar **primeiro** tocando a sugestão; isso ajusta o recorte amarelo automaticamente e invalida prévias antigas.
- **Recorte manual** permanece disponível por arrastar o dedo sobre a foto, no topo sem competição com scroll.
- O detector sugere **áreas contrastantes**, não entende semanticamente roupas, pessoas e logotipos. Pode sugerir região errada numa foto com muitas sombras, não assegura identificação profissional de cada arte.
- A escolha define qual região editar primeiro, **não muda automaticamente a ordem de costura PES**. O sistema ainda gera a matriz a partir da região efetivamente confirmada.
- Instalação de testes continua em pacote separado **com.timachado.brothermatrizes.qa** (não substitui o aplicativo comercial).

## Limites
A arte em fotografia com tecido amassado/textura/pouco contraste pode continuar exigindo seleção manual, segmentação mais avançada ou reconstrução da logo; o detector não equivale a rede neural treinada. A estabilidade real do gesto e a qualidade da matriz precisam ser verificadas no celular do usuário e em uma máquina Brother.

## Gates
- [ ] Testes JUnit: regiões separadas, fundo uniforme, PNG alfa e recorte por região
- [ ] Build APK debug .qa isolado
- [ ] Testar importação, tocar candidato, arrastar para selecionar manualmente e reabrir
- [ ] Testar arte verdadeira, separar logo e nome, gerar PES e prova em máquina Brother
- [ ] Não mesclar na produção nem usar assinatura comercial sem aprovação explícita
