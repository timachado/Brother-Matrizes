# Brother Matrizes Windows — ajustes de tela e ações nativas (09/11/2026)

Foto e vídeo recebidos do proprietário mostram que o aplicativo portátil era
iniciado em 1000x860 e várias telas ficavam limitadas a 700 dp, deixando uma
grande área inutilizada no monitor widescreen.

## Correções na branch Windows, sem alterar Android

- Janela normal inicia maximizada, respeitando a barra de tarefas do Windows.
- Screenshots do CI continuam em janela flutuante de 420/920 dp.
- O host Compose permite até 1780 dp em monitores largos, sem limitar as
  telas de Home/Conta/Fontes a 700 dp. Navegação inferior até 1280 dp.
- Abrir matriz: seletor de arquivo Swing apenas na EDT, aguardado em worker
  IO, com prevenção de duplo diálogo e validação local PES/DST/JEF/FLP.
  A leitura permanece no loader Android, fora da thread de interface.
- Menu Windows -> Criar atalhos aciona apenas com clique do usuário o arquivo
  CMD já incluído no app-image, criando atalhos da conta atual no Menu Iniciar
  e Área de Trabalho, sem privilégios administrativos, instalação ou registro.
  É necessário manter a pasta portátil no local escolhido.
- Testes Kotlin verificam os filtros e a recusa de EXE/arquivos ausentes, além
  da estrutura mínima do pacote. CI Windows já exercita o script CMD real
  no usuário efêmero do runner, validando as duas entradas .lnk.

## Erro da foto e confiança do Windows

A foto mostra o erro "Fatal exception in coroutines machinery" da JVM/Compose,
não um aviso de SmartScreen. A mitigação remove o diálogo modal do dispatcher
Compose e impede cliques simultâneos. Causa raiz e resolução total dessa
exceção ainda dependem de stack trace completo no PC; não declarar corrigida
somente pela inspeção visual.

O EXE interno QA continua SEM assinatura Authenticode. Não é possível obrigar
SmartScreen/Smart App Control a confiar no arquivo com script ou via alterações
de Defender. Para distribuição comercial, usar certificado de editor confiável,
assinar o EXE e verificar a assinatura e o SHA256. Mesmo assinado, a reputação
pode levar tempo para se estabelecer. Referências oficiais:
- https://learn.microsoft.com/pt-br/windows/apps/package-and-deploy/smartscreen-reputation
- https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control

Nenhuma alteração em produção, Supabase, licenças, APK Android ou site.
A criação de atalhos é iniciada por ação explícita do usuário.

## 09/11/2026 — gravação PES e envio para máquina sem bloqueio da UI

- **Exportar matriz** (PES/DST/JEF) e **Enviar para a máquina** usam
  `DesktopNativeMatrixPicker.chooseSave` com `SwingUtilities.invokeAndWait`
  a partir de `Dispatchers.IO`, preservando o diálogo nativo do Windows.
- Um único estado de salvamento evita sobreposição de diálogos quando o botão
  é acionado mais de uma vez. O estado é limpo em `finally` após cancelar,
  falhar ou concluir.
- O caminho permanece escolhido pelo usuário; a camada `CREATE_NEW` continua
  recusando sobrescritas. O PES gerado não é modificado ou camuflado contra o
  Defender. O histórico de envio só é marcado após gravação bem-sucedida.
- QA offline verifica filtros de Salvar como nos formatos PES/DST/JEF/FLP e
  rejeição de nomes malformados, sem gravar ou enviar arquivos.
- Salvar projeto FLP mantém seu próprio fluxo pré-existente, testado pelo CI.

## Fechamento parcial: Salvar FLP e pasta da biblioteca (09/11/2026)

O projeto Windows agora compartilha o mesmo seletor Swing executado na EDT
com o leitor de matrizes, a exportação e o salvamento de projetos FLP.
`Salvar projeto original FLP` aguarda o diálogo com `Dispatchers.IO`,
protege contra diálogos duplicados, limpa o estado em `finally` e preserva
a gravação `CREATE_NEW`, o teste de tentativa de sobrescrita e o round-trip
dos dados `editableName`.

`Selecionar pasta FLP` usa `DIRECTORIES_ONLY` sem bloquear a coroutine
Compose, mantém favoritos/recentes de outras pastas e nunca consome cotas.
Testes verificam filtro nativo, caminho de QA e ausência de escrita na seleção.
