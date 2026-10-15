# Brother Matrizes — QA Android de Imagem para Bordado

## Escopo
Branch de verificação após PR #95: **Ferramentas → Imagem para Bordado**, recorte de fotografias, remoção conservadora de fundo, separação multicor, geração de matriz e reabertura PES.

## O que pode ser testado automaticamente
- Exibir o card **Imagem para bordado** dentro de **Ferramentas**, acionar o evento de navegação.
- Iniciar a ferramenta em modo **Imagem**, sem mostrar as opções SVG/desenho livre.
- Arrastar o dedo simulado sobre o canvas e obter uma caixa de recorte normalizada.
- Codificar uma imagem PNG laranja/branca em fundo escuro usando o Bitmap Android real, abrir por ContentResolver / BitmapFactory, remover o tecido artificial pelo flood fill, separar duas cores, produzir PES e reabrir pelo pipeline nativo do aplicativo.
- CI separado usa emulador Android (API 35) no GitHub Actions. **Emulador NÃO substitui dispositivo real**, nem máquina de bordar.

Os testes usam uma imagem sintética; **não** foi possível recuperar a foto original PANOBIANCO ACADEMIAS a partir dos materiais acessíveis na conversa. Não afirmar homologação com a foto real.

## Checklist de teste em telefone Android real
1. Usar **um aparelho de QA separado** ou instalação isolada. **Nunca instalar o APK debug por cima da versão comercial** ou desinstalar a comercial para testar; isso arriscaria projetos locais e a assinatura de produção.
2. Abrir Início → Ferramentas → Imagem para Bordado. Verificar card, título, Voltar, importação PNG/JPG/WebP e mensagem de orientação.
3. Importar uma **foto real da logo no roupão**. Arrastar a seleção em torno apenas da marca com uma margem ao redor. Verificar coordenadas do recorte, inclusive orientações retrato e paisagem.
4. Usar Remover fundo com tolerância 55; comparar ligado/desligado e ajustar a tolerância. Verificar logo e detalhes internos (não remover o desenho junto com o tecido).
5. Aplicar preparação; conferir cores/ordem de bordado. Restaurar imagem original e repetir o recorte para validar edição reversível.
6. Com fundo suficientemente isolado, gerar a matriz, verificar o tamanho no bastidor, JUMP/TRIM, trocas de linha e prévia; reabrir PES e confirmar que não inclui um grande bloco de tecido.
7. Salvar e reabrir um projeto Satin; exportar e restaurar o backup ZIP em uma instalação de teste; verificar cores e seções.
8. Registrar capturas de tela e, para eventuais defeitos, o nome da etapa e comportamento observado.

## Checklist de bordado real
- Conferir máquina Brother, bastidor, dimensões em mm, estabilizador e densidade apropriados ao tecido.
- Testar primeiro em retalho semelhante ao roupão, nunca na peça do cliente.
- Inspecionar contorno, sequência de cores, fios soltos, franzimento e excesso de pontos.
- Se nome cursivo, revisar e ajustar trilhos Satin manualmente antes do teste físico.
- Apenas após aprovação operacional considerar uma build comercial com assinatura original e versionCode crescente.

## Status / limites
- [ ] Instrumentação no emulador — registrar GitHub Actions com resultado final
- [ ] Teste físico no Android com foto original
- [ ] Bordado e verificação em máquina Brother
- [ ] Revisão de UX e correções decorrentes dos testes reais
- [ ] Homologação da atualização comercial assinada — não iniciada

Nenhum teste Android em emulador deve ser anunciado como prova de bordado físico ou de compatibilidade de atualização comercial. O formato principal do projeto permanece PES.
