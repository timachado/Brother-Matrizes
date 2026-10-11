# Notificações de atualização — Brother Matrizes

## Como avisar quem já tem o aplicativo instalado

Há dois tipos de aviso. **1.0.5 implementa verificação automática local com WorkManager**, aproximadamente a cada hora, dependendo de rede, bateria e políticas do Android. **Não é push em tempo real**. Para aviso imediato, integrar futuramente Firebase Cloud Messaging (FCM) com tópico `brother_matrizes_updates_stable`, projeto Firebase e remetente autorizado no servidor.

## Pré-requisitos da 1.0.5
- O cliente precisa ter instalado a versão 1.0.5 ou posterior (releases antigas não adquirem tarefas de background sem instalar uma atualização).
- No Android 13+, aceitar o pedido 'Ativar avisos'. Também é possível gerenciar em Minha Conta > Atualizações > Configurar notificações.
- O cliente pode continuar usando o app se recusar as notificações.

## Publicar atualização, sem antecipar notificações
1. Compilar, testar em um dispositivo e **assinar com o certificado de produção original**. Nunca distribuir APK unsigned ou assinado com chave debug.
2. Publicar o APK comercial na hospedagem, obtendo URL HTTPS de download direto da versão. Confirmar que o link entrega um APK, não uma página HTML.
3. Cadastrar a mesma release estável no plugin WordPress já instalado: `applicationId=com.timachado.brothermatrizes`, `versionName`, `versionCode` crescente, `downloadUrl`, `sha256` real do APK, notas e data.
4. Na branch `main` do GitHub atualizar **`updates/stable.json`** com os mesmos dados comerciais publicados. Essa sincronização é **necessária**, pois a hospedagem InfinityFree pode bloquear a API WordPress para Android com 403. O app então consulta o espelho público no GitHub Raw.
5. Depois da publicação do manifesto, aparelhos com versão instalada menor detectarão a versão nova nas verificações do WorkManager e mostrarão a notificação, respeitando as permissões do Android.

## Segurança e comportamento
- O arquivo JSON público contém somente informações da versão, sem segredos de WooCommerce, Efí, Supabase, chaves de assinatura ou tokens Google.
- A notificação aparece **uma vez por versionCode por aparelho**. Pode ser atrasada por Doze, rede ou economia de bateria; heads-up/alerta visual depende das configurações do Android.
- Se `versionCode` publicado <= instalado, **nenhuma notificação** é criada.
- O botão abre a tela Minha Conta, onde fica a Central de Atualizações.
- Não publica automaticamente nem instala APK silenciosamente.
- Se um usuário não instalar a 1.0.5, a versão 1.0.3 continua consultando atualizações ao abrir o app, mas não receberá os novos avisos periódicos.

## Futuro: push com FCM
- Cadastrar um projeto Firebase e o aplicativo Android com o mesmo `applicationId` comercial.
- Integrar Firebase Messaging SDK e arquivo de configuração Android no build, registrar os clientes (com opt-in) no tópico `brother_matrizes_updates_stable`.
- No WordPress, um remetente autenticado usando FCM HTTP v1 ou Firebase Admin SDK enviará a mensagem **somente após concluir a publicação** da versão e do manifesto.
- Manter credenciais do remetente **somente no servidor**. O APK nunca deve conter chave de serviço FCM.
- O cliente precisa de rede/serviços Google compatíveis; push não tem prazo de entrega absoluto.

## Testes de lançamento
- Primeiro validar versão instalada e ícone Brother, login Google, matrizes e fontes persistidas.
- Depois simular em ambiente controlado `publishedCode > installedCode` e conferir notificação sem duplicidade.
- Revalidar que `publishedCode == installedCode` não avisa; consentimento negado não atrapalha o uso.
- Não publicar o manifesto de uma versão antes de seu APK oficial estar disponível.