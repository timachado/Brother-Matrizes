# Brother Matrizes Windows — Enviar para a máquina (QA sem hardware)

**Escopo:** branch `feature/windows-fiel-android-1-11-8-20261106`,
PR #226. A versão Android comercial 1.11.8 não foi alterada.
Esta entrega **não** constitui homologação em bordadeira real ou
recurso de transferência Wi-Fi direta.

## O que foi implementado

1. Gerador `copyAndroidMachineTransfer` importa a tela
   **MachineTransferScreen.kt** do Android oficial para Compose Desktop,
   conservando formatos, bastidores, avisos e o validador
   `MachineTransferValidator`. Somente textos sobre Android e
   callbacks de destino são adaptados para Windows.
2. A Home, o Viewer e a Biblioteca passam a navegar para essa tela
   quando há matriz válida em memória. A Biblioteca carrega o projeto
   FLP original antes de prosseguir.
3. O formato PES/DST/JEF e o bastidor efetivamente selecionados na tela
   são verificados novamente em `DesktopMachineTransferFile.prepare`
   com o validador **original do Android**. Se houver impedimento,
   nenhum arquivo é escrito.
4. Exportação chama `MatrixConverter.convert`, também original do
   Android. O usuário escolhe o destino através do diálogo nativo do
   Windows; pode selecionar um pendrive montado, um diretório local
   ou pasta para abrir posteriormente no app da bordadeira.
5. `saveNew` exige extensão correta, pasta existente e arquivo novo.
   `CREATE_NEW` impede sobrescrever matrizes. Saídas ficam limitadas
   a 64 MB. Falhas de escrita tentam limpar apenas o arquivo novo.
6. **Wi-Fi / app da máquina** no Windows está identificado como
   **exportação manual** para o software da bordadeira. Nenhum
   protocolo de rede, Bluetooth, USB direto ou acesso ao dispositivo
   é simulado, e não há alteração de licenças ou cotas.
7. Captura de tela `--capture-machine-transfer-qa` utiliza matriz
   sintética e renderiza o Composable real no runner Windows.

## Verificações obrigatórias

- Teste unitário `DesktopMachineTransferFileTest`: gerar PES/DST/JEF
  sintéticos, gravar somente novos arquivos, conferir os bytes,
  recusar extensão incorreta, formato sem suporte, pasta ausente,
  não modificar matriz de origem.
- Workflow
  [Windows transferência QA](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-windows-machine-transfer-qa.yml):
  compilar, validar a transformação Android/Windows, executar
  testes unitários, capturar a tela e impedir instalador comercial.
- Testes adicionais exigidos antes da versão comercial: selecionar
  pendrive real no PC, validar formatos em máquinas Brother e outras
  bordadeiras compatíveis, conferir tamanho/cores/bastidor em hardware
  real, confirmar nomes de arquivos e comportamento com espaço cheio,
  mídia removida e falhas de permissão.

**Critério comercial:** não liberar como conexão direta com máquina,
nem divulgar EXE final até aceitação de interoperabilidade física,
revisão da assinatura e conclusão das demais rotas Android.
