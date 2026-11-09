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
