# Brother Matrizes QA — Simular Satin depois de preparar a imagem

## Pedido
Adicionar na tela simples Ferramentas > Imagem para Bordado, logo após a prévia da arte, **▶ Simular bordado**, usando **exatamente SimulatorScreen.kt original**, como a abertura de matriz no aplicativo comercial.

## Entrega
- Botão de simulação na própria área da prévia.
- Tenta construir pontos **SATIN_STRAIGHT** para as cores isoladas da foto, em direção **vertical da agulhada**. Só aceita componentes com eixo alongado horizontalmente e largura compatível; forma vertical, larga/ramificada/com buracos gera **erro explícito** e nunca cai em FILL/Tatami ou varredura horizontal.
- Desativa underlay central para que o preview simplificado não inclua passe horizontal ao longo da peça.
- Usa `MultiColorRasterDigitizer` apenas no modo `verticalSatinPreviewOnly`, que exige toda camada SATIN_STRAIGHT e nenhuma camada FILL ou underlay central.
- A geração ocorre em `Dispatchers.Default` fora da thread de interface. Falha mostra mensagem acessível de que a arte deve ser simplificada/reconstruída; não simula bordado supostamente profissional em imagem incompatível.
- Quando há desenho compatível, `MainActivity` encaminha a criação interna diretamente para **Screen.Simulator(qaRasterPreviewOnly = true)**, sem `activateDesign`, gravação em Recentes, visualizador ou exportação.
- `goBack` retorna à ferramenta mantendo foto, recorte e prévia em memória, sem reabrir o picker ao voltar. Ao entrar em nova sessão de Ferramentas, inicia seleção de imagem novamente.
- Simulador original preservado. A tela mostra a proteção QA já existente de que a matriz não está homologada para máquina.
- APK distinto `com.timachado.brothermatrizes.qasatinpreview`, nome **Brother Matrizes QA Satin**, sem modificar a versão comercial, licenças, login ou assinatura de produção.

## Limites
- Esta é uma prévia conservadora de colunas Satin simples e adequadas à orientação pedida. Uma logo complexa como o símbolo completo e letras PANOBIANCO/ACADEMIAS pode não passar pela triagem, portanto o botão mostrará erro, não um preenchimento horizontal falso.
- A tela do simulador exibe pontos gerados, mas **não certifica qualidade nem segurança de agulhas/máquina**. Sem bordado real e planejamento por objeto não liberar PES.

## Testes
- [ ] JUnit verifica barra horizontal → pontos Satin verticais; barra vertical → falha explícita, sem alternar orientação.
- [ ] JUnit verifica que FILL não é aceito em modo preview Satin e exportação segue bloqueada.
- [ ] assembleDebug e artefato instalável em package QA separado.
- [ ] Testar foto real no Android, prévia, clique, erro quando não suportado e Voltar sem perder imagem.
- [ ] Futuro Satin por objeto complexo, aprovado em máquina real, antes de qualquer exportação.
