# Brother Matrizes Windows — PiP e ajuste automático horizontal

Implementação no editor Android compartilhado, com ambas opções OFF
por padrão; somente FielMain ativa o comportamento no Windows.

- Em widescreen (>=760dp) e após scroll de 220px, o bastidor aparece
  em PiP de 232×208dp no topo direito. A região dos ajustes reserva
  244dp, evitando cobrir textos, sliders e botões. O desenho e nome são
  pré-visualizados em tempo real pelo canvas e Satin originais, sem gesto
  nem alterar pontadas.
- Ao voltar ao topo o PiP desaparece. Janelas estreitas e Android usam
  o scroll tradicional, sem o preview flutuante cobrindo elementos.
- Ao selecionar bastidor horizontal, calcula escala uniforme usando
  largura/altura ÚTEIS (com margem), e, apenas se necessário, aplica
  o motor existente MatrixNameRotation.resizeLetter. Valida o
  posicionamento físico travado. Se não couber, conserva a matriz
  anterior e informa que precisa de bastidor maior.
- Nenhuma ação no Supabase/Android produção/cotas e nenhuma gravação
  do PES original. Testes Kotlin verificam matemática do encaixe
  e paridade original Android->Windows.

QA visual: --capture-name-editor-scroll-qa.
