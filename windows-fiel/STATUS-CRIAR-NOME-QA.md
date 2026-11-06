# Brother Matrizes Windows fiel — Criar Nome (QA, não comercial)

Data da auditoria: **06/11/2026**. PR **#226** permanece rascunho.

## Implementação comprovada

- **Home** e **CreateNameScreen** são gerados DIRETAMENTE das fontes
  `app/src/main/java/com/timachado/brothermatrizes/HomeScreen.kt` e
  `CreateNameScreen.kt` originais do Android 1.11.8. O gerador não muda
  hierarquia, rótulos, estilos, controles nem disposição dos campos.
  Adapta apenas imports de `Context` e fontes TTF/OTF.
- `BrotherMatrizesTheme.kt`, `EmbroideryCanvas.kt`, `ViewerScreen.kt`,
  `SimulatorScreen.kt` e seus componentes são copiados da fonte Android.
- `TextLayoutGenerator`, `TextMatrixGenerator`, `TextHoopAutoFit`,
  `MatrixConverter` e demais classes do motor são compilados da
  fonte **original** Kotlin, sem reescrever algoritmo ou formatos PES/DST.
- Botão **Criar nome** da Home e aba **Criar** abrem a verdadeira tela
  `CreateNameScreen`. Sua prévia é gerada pelo mesmo motor; `Criar matriz`
  abre o Viewer e `▶ Simular` abre o simulador Android original.
- O comando **Salvar cópia** do Viewer usa `MatrixConverter.convert` (PES)
  e o seletor nativo de arquivos Windows; escrita usa `CREATE_NEW`,
  que recusa sobrescrever matrizes já existentes. O comando
  **Salvar PE-Design** chama o perfil `PE_DESIGN_PC`.
- Preferências de exibição, fonte nativa e bastidor são isoladas em uma
  árvore privada `windows-fiel-qa` de `java.util.prefs`, sem tocar
  em dados do aplicativo Android ou cliente.

## Evidência de QA

- **CI Windows #42194059305:** compilação Kotlin, testes de paridade
  de código e geração/conversão real do nome sintético, mais capturas
  compacta e larga da Home e do Criar Nome, concluída com sucesso.
- Artefato do run: `brother-windows-fiel-HOME-RENDER-QA`,
  incluindo `create-name-windows-original-port.png` (404×641).
- A captura demonstra prévia real de `Maria`, bastidor e contagem de
  pontadas, cinco abas originais e controles da aba `Texto`.
- Captura equivalente **Android** está em instrumentação separada
  (`BrotherCreateNameScreenshotTest`) e deve ser inspecionada antes
  de declarar paridade visual da rota.

## Bloqueios que NÃO podem ser ocultados

1. **TTF/OTF importadas ainda não funcionam.** A lista Desktop foi
   mantida vazia para que nenhuma fonte externa seja apresentada como
   funcional sem motor equivalente. É obrigatório portar o motor
   Android de fontes com validação de saída, ou um adaptador de
   Windows testado por pontadas equivalentes.
2. A Biblioteca de Fontes, conta, Projetos, importação de matrizes e
   outras telas ainda não foram portadas; os callbacks exibem estado
   de pendência explícito e não gravam dados fictícios.
3. Salvar projeto FLP, compartilhar, transferir e outras ações do
   Viewer ainda precisam de adaptadores reais, com testes de arquivos
   e dos direitos/licenciamento.
4. O fluxo visual **Criar Nome → Viewer → Simulador** compila e
   apresenta pontadas, mas ainda precisa de teste de mouse,
   teclado, salvamento por diálogo real e confirmação em PC físico.
5. Nenhum arquivo Windows resultante deve ser divulgado, publicado,
   instalado em máquinas de clientes ou confundido com release.

O critério é preservar o aplicativo Android aprovado e só marcar
concluída cada função depois de testes de paridade visual, matrizes
binárias, estados de erro e operação real no Windows.
