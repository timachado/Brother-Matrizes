# Brother Matrizes — critérios de lançamento: Imagem para Bordado

**Status verificado em 20/10/2026:** funcionalidade **experimental**.
A publicação do aplicativo Brother Matrizes na Google Play **não implica**
que a digitalização automática de imagens esteja pronta ou homologada.

## Já comprovado nos ramos experimentais

- Importação de imagem no Android e inspeção de canais alfa por limiar.
- Auditoria de regiões, conectores frágeis e vazados; comparação espacial
  sobre a imagem original.
- Caso PANOBIANCO 313×266: pixel (42,178), cavidade (42,179)–(42,183),
  14→13→14 vazados com 1 px original reincluído na **prévia QA**.
  A fidelidade matemática não comprova intenção tipográfica.
- Ampliação e escala geométrica de 0,415×2,075 mm para essa cavidade quando
  a largura de trabalho escolhida é 129,907 mm; **sem mínimo universal de
  bordabilidade**.
- Testes de regressão e Android emulado para fontes TTF, Satin e DST nos
  ramos QA. Isso comprova o motor de fontes, **não a conversão de raster**.
- Nesta etapa, geometria de contornos fechados de máscaras alfa está sendo
  preparada em **somente leitura**, com recusa de vértices ambíguos.
- Nenhuma dessas provas autoriza exportação da imagem para STITCH/PES.

## Critérios obrigatórios pendentes para versão definitiva

1. **Segmentação e revisão da arte:** selecionar regiões, detectar ruído,
   simplificar a imagem, preservar letras e espaços internos, resolver
   componentes diagonais e microdetalhes com intervenção do operador. Definir
   políticas para fotos complexas, logotipos e textos, sem inventar contornos.
2. **Planejador geométrico de pontos:** gerar realmente trajetórias
   de **ponto corrido, Satin e Tatami** de formas selecionadas, com direção,
   espaçamento, densidade, largura, compensação de repuxo, underlay e
   limites técnicos por técnica. O extrator de contornos NÃO implementa isso.
3. **Roteamento e segurança:** sequência de cores, início/fim, JUMP/TRIM,
   arremates, viagens, colisões/cobertura, fidelidade de vazados e contorno,
   limites de bastidor e tamanho de ponto. Casos degenerados devem falhar
   sem gerar arquivo de bordado não confiável.
4. **Exportação e reabertura:** criar PES (formato prioritário), respeitar
   política de outros formatos compatíveis JEF/DST quando suportados,
   validar o round-trip de pontos, cores, offsets e escala em Abrir Matriz
   e no simulador. A exportação de raster permanece bloqueada até então.
5. **Homologação física e UX comercial:** costurar amostras reais em máquina
   Brother, com tecido, agulha, linha e tamanhos distintos; auditar qualidade
   e segurança, corrigir casos de falha; completar fluxo de edição, mensagens,
   limites da função, testes em dispositivos e revisão de publicação.

### Política de aprovação

- **Não publicar “Imagem para Bordado” como recurso definitivo** até todos
  os cinco critérios passarem e a validação física estar documentada.
- O bloqueio `RasterEmbroideryApproval.EXPORT_ALLOWED=false` permanece ativo.
- PRs de QA empilhadas/draft não são funcionalidades já mescladas ao
  aplicativo comercial ou publicadas na Play Store.
- O intervalo de tempo ainda NÃO pode ser estimado com confiabilidade a
  partir dos testes atuais: não há prova de um primeiro fluxo completo
  raster→pontos→PES→bordado físico. Mensurar prazo depois de construir o
  protótipo funcional e executar o primeiro teste em máquina.
- Continuar testes automáticos no GitHub; evitar pedir um APK diferente
  por cada correção. Jam só complementa falhas visuais com gravação real.
