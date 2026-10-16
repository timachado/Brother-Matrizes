# Brother Matrizes — Simular pontos Satin na arte inteira (QA)

## Problema confirmado em 16/10/2026
A logo PANOBIANCO é exibida dentro do bastidor na ferramenta Imagem para Bordado e redimensiona com o bastidor. Contudo, o botão "Simular pontos Satin" não abre o simulador porque RasterStraightSatin exige que uma cor inteira seja uma única coluna reta. Uma logo com símbolo, letras e furos sempre falha nessa restrição.

## Modificação
- A simulação continua usando **o mesmo Screen.Simulator / SimulatorScreen original** usado em Abrir Matrizes; nenhuma mudança no motor ou na barra progressiva original.
- Primeiro tenta Satin reto (restrições antigas) para formas que funcionam.
- Se a arte for complexa, nova classe `RasterAdaptiveVerticalSatinPreview` decompõe as regiões de cor da imagem em **pequenas bandas horizontais com trilhos verticais**, agrupando faixas semelhantes em caminhos locais e gerando zigue-zague de **agulhadas com predominância vertical**.
- As faixas descontínuas são separadas por **JUMP e TRIM**, nunca ligadas com costura horizontal através de espaços vazios.
- Pontos gerados são comandos reais `STITCH`/`JUMP` processados pelo SimulatorScreen, não desenhos de linhas verticais fictícias.
- Há limites explícitos de 0,8 a 6 mm de largura de ponto vertical por seção, limite de 7,5 mm por passo e máximo de 110 mil comandos.
- Conversão de cores, enquadramento e dimensões continuam usando as opções escolhidas na ferramenta. Voltar preserva imagem e bastidor.
- Saída usa formato interno `QA_SATIN_REVIEW` e `sourceBytes=ByteArray(0)`; **nunca é matriz PES exportável**. A rota de visualização QA não salva em Recentes, não abre Viewer/compartilhar nem envia a Brother.

## Limitações sérias
Embora essa implementação desenhe zigue-zagues de agulhadas reais em vez de linhas ilustrativas, ela **não é Satin profissional de produção**: as juntas entre seções, compensação de repuxo, sustentação, tensão, pontos de amarração e sequência ótima precisam ser aperfeiçoadas. A prévia do simulador não garante segurança física. **Não enviar para máquina, não liberar exportação.**

## Testes
- [ ] Testes unitários de logo com símbolo oco + várias letras desconectadas, STITCH vertical, saltos entre trechos, cores e limites 7,5 mm
- [ ] Compilação Android QA e APK separado
- [ ] Teste Android com logo Panobianco real e barra de progresso da simulação
- [ ] Teste em máquina Brother (pendente; só com matriz aprovada futuramente)

A versão comercial Brother Matrizes 1.10.8/184, sua assinatura e seu simulador original permanecem intocados.
