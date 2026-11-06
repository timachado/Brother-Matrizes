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
- **Projeto FLP v11**: o `ProjectCodec` é extraído literalmente da
  fonte Android. `DesktopFlpProjectFiles` abre/salva projetos FLP reais,
  limita tamanho a 64 MB, valida integridade das pontadas e usa
  `CREATE_NEW` para nunca sobrescrever outro projeto.
- **Importação DST, PES e JEF**: o `EmbroideryLoader.loadBytes` e os
  parsers originais Android são compilados no Windows, removendo somente
  o método de acesso a URI Android. `DesktopEmbroideryFiles` valida
  extensão, tamanho, entradas inválidas e links simbólicos. A ação
  `Abrir matriz` da Home e do Viewer usa o seletor local Windows.

## Evidência de QA

- **CI Windows #42194059305:** compilação Kotlin, testes de paridade
  de código e geração/conversão real do nome sintético, mais capturas
  compacta e larga da Home e do Criar Nome, concluída com sucesso.
- Artefato do run: `brother-windows-fiel-HOME-RENDER-QA`,
  incluindo `create-name-windows-original-port.png` (404×641).
- A captura demonstra prévia real de `Maria`, bastidor e contagem de
  pontadas, cinco abas originais e controles da aba `Texto`.
- Captura equivalente **Android** foi executada. A primeira captura,
  apesar da QA verde, estava inválida por um alerta **Pixel Launcher ANR**
  sobre o aplicativo. Uma segunda captura removeu o alerta, mas ainda
  mostrava **Atualizando prévia...**, sem pontadas prontas. O teste
  `BrotherCreateNameScreenshotTest` agora exige primeiro plano Android
  e botão **Criar matriz** habilitado antes de aceitar a imagem.
- **QA Windows #42196408550** e **#42196929853**: compilações,
  testes de formatos FLP/PES/DST/JEF e testes adicionais de equivalência
  de código aprovados.

## Captura Android confiável e comparativo (06/11/2026)

A QA [#42196696590](https://github.com/timachado/Brother-Matrizes/actions/runs/42196696590)
gerou uma nova captura Android oficial, com o alerta Pixel Launcher
removido e a prévia de **Maria** já concluída. O teste exige que a janela
ativa pertença ao Brother Matrizes e que o botão **Criar matriz** esteja
habilitado antes de capturar.

Na comparação **Android 1080×2400** versus **Windows 404×641**,
ambos mostram **Maria, 1.071 pontos e 88,1 × 28,3 mm**, além do tema
escuro, formulário, bastidor e navegação de cinco abas com identidade
equivalente. O Windows tem uma viewport vertical menor; os controles
Reto/Arco ficam abaixo da área inicialmente visível e precisam de
rolagem. Portanto, confirma-se **paridade da configuração básica
testada**, não equivalência de todas as telas, gestos, estados e opções.

A área de fontes TTF/OTF está sendo tratada separadamente em
`STATUS-FONTES-TTF-OTF-QA.md`. Nenhum recurso do motor original de
fontes importadas deve ser substituído por simples texto estilizado.

## Bloqueios que NÃO podem ser ocultados

1. **TTF/OTF importadas ainda não funcionam.** A lista Desktop foi
   mantida vazia para que nenhuma fonte externa seja apresentada como
   funcional sem motor equivalente. É obrigatório portar o motor
   Android de fontes com validação de saída, ou um adaptador de
   Windows testado por pontadas equivalentes.
2. A Biblioteca de Fontes, conta, Projetos, importação de matrizes e
   outras telas ainda não foram portadas; os callbacks exibem estado
   de pendência explícito e não gravam dados fictícios.
3. **Salvar/abrir FLP foi implementado**. Compartilhar, transferir,
   conta e outras ações do Viewer ainda precisam de adaptadores reais
   e homologação de direitos/licenciamento. Os diálogos nativos
   FLP/PES/DST/JEF foram compilados, porém ainda não foram testados
   manualmente em PC físico.
4. O fluxo visual **Criar Nome → Viewer → Simulador** compila e
   apresenta pontadas, mas ainda precisa de teste de mouse,
   teclado, salvamento por diálogo real e confirmação em PC físico.
5. Nenhum arquivo Windows resultante deve ser divulgado, publicado,
   instalado em máquinas de clientes ou confundido com release.

O critério é preservar o aplicativo Android aprovado e só marcar
concluída cada função depois de testes de paridade visual, matrizes
binárias, estados de erro e operação real no Windows.
