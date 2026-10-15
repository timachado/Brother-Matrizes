# Brother Matrizes — Etapa 9: Nova ferramenta na tela inicial

## Referência visual
Vídeo do app enviado em 15/10/2026: tela Início com seção **Ferramentas**, cards Biblioteca de fontes / Abrir matriz, Simulador, Editor, Converter e Enviar. A nova ferramenta usa **o mesmo componente visual e grid em duas colunas**.

## Entrega
- Novo card **Imagem para bordado** no início da seção **Ferramentas** do HomeScreen, com ícone de imagem e subtítulo **Digitalize logos e exporte PES**.
- Nova rota dedicada **Screen.ImageToEmbroidery** no MainActivity, que volta à tela Início e compartilha o fluxo normal de Gerar matriz → Viewer ou Simular → Simulator.
- A rota chama a tela CreateDrawingScreen em modo dedicado imageToEmbroideryTool=true. Este modo **abre direto na imagem** e habilita multicor inicialmente, sem obrigar a selecionar SVG ou desenho livre.
- Novo título **Imagem para Bordado**, fluxo resumido e aviso de qualidade para fotografias com tecido, fundo ou letras cursivas.
- A rota CreateDrawing original permanece como estava (modo inicial SVG e seletor de origem).
- Os motores já desenvolvidos nas fases 1–8 são reutilizados: PNG/JPG/WebP, separação de cores, camadas, fill / Satin reto / Satin curvo / Satin manual, edição de trilhos, projetos locais, ZIP de backup e sequenciamento de PES.
- O arquivo de bordado continua gerado pela cadeia existente; a nova tela não introduz alteração no motor de simulação.

## Limites explícitos
A ferramenta não deve prometer digitalização automática perfeita de **foto inteira de tecido**. O usuário precisa importar arte recortada ou separar o logo de fundos complexos. O algoritmo experimental de separação de fundo pode exigir edição de cores e trilhos; ainda não há segmentação visual inteligente robusta de fotografias de roupas, máscaras regionais ou seleção automática do logotipo de uma foto. A foto do roupão PANOBIANCO ACADEMIAS e a caligrafia Sabrina Freitas ainda requerem validação/reconstrução quando necessário.

## Testes e proteção
- [ ] :app:testDebugUnitTest e assembleDebug na branch isolada
- [ ] Abrir Ferramentas > Imagem para bordado no Android real, selecionar PNG/JPEG/WebP e retornar sem crash
- [ ] Gerar matriz, reabrir no Viewer, simular e conferir exportação PES
- [ ] Testes com arte bem recortada e com foto de fundo complexo, sem alegar equivalência de qualidade
- [ ] Prova em máquina Brother antes de qualquer produção

Produção 1.10.8 (versionCode 184) permanece inalterada. Não distribuir APK debug para atualização de versão comercial e não alterar chave de assinatura.
