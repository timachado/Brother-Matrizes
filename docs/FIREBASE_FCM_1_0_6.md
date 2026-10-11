# Firebase Cloud Messaging — Brother Matrizes 1.0.6

## Projeto Android configurado
- Projeto Firebase: `brother-matrizes---ti-machado`.
- App Android: `com.timachado.brothermatrizes`.
- O arquivo `app/google-services.json` é o arquivo de configuração pública do cliente Android. **Nunca inserir service account JSON, private keys ou OAuth de administrador neste repositório.**
- Apenas `firebase-messaging` SDK instalado. Supabase permanece responsável pelo login Google; WooCommerce/WordPress e Efí continuam comerciais.
- A versão do Android é 1.0.6 (`versionCode=172`).

## Inscrição voluntária
- Na primeira abertura da 1.0.6, o usuário pode aceitar notificações push.
- Android 13+ pede permissão POST_NOTIFICATIONS, se ainda não concedida.
- Ao aceitar, o cliente registra a instalação no tópico `brother_matrizes_updates_stable` por meio do SDK FCM. A opção de desativar está em Minha Conta > Atualizações.
- Quem não aceita continua com o aplicativo e as verificações de atualização periódicas, sem subscrição FCM.

## Enviar mensagem pelo FCM (futuro backend de disparos)
Use um remetente autenticado, por exemplo Firebase Admin SDK num serviço confiável ou Cloud Functions, para chamar FCM HTTP v1. O emissor jamais deve rodar dentro do APK. No WordPress, o botão Enviar notificação deve chamar um endpoint protegido ou um serviço backend autenticado; nunca incluir uma chave de serviço na interface ou no frontend.

Somente publique um aviso de versão depois de o APK assinado estar disponível na página oficial e de atualizar o manifesto WordPress e GitHub.

Exemplo de payload para FCM HTTP v1 (enviar **somente dados**, sem seção `notification`, para o app validar versão no Android mesmo fechado):
```json
{
  "message": {
    "topic": "brother_matrizes_updates_stable",
    "data": {
      "type": "update",
      "versionCode": "173",
      "versionName": "1.0.7",
      "title": "Brother Matrizes: atualização disponível",
      "body": "A versão 1.0.7 foi publicada. Abra o aplicativo para atualizar."
    },
    "android": {"priority": "high"}
  }
}
```

**Exemplo apenas — não envie versão 1.0.7 enquanto não houver APK 1.0.7 validado e publicado.**

Com o app 1.0.6 instalado (172), somente `type=update` com `versionCode > 172` resultará em notificação e cada versão será avisada uma vez por aparelho. Uma mensagem sem esse campo é ignorada. O botão abre Minha Conta para consultar a Central de Atualizações.

## O que foi validado / o que ainda falta
- A compilação e os testes unitários validam o código Android FCM e a configuração do cliente.
- Ainda requer instalação real de 1.0.6 em um Android com Google Play Services, consentimento do usuário, conexão real com Firebase e envio end-to-end por um remetente autenticado.
- Um novo plugin/ajuste backend WordPress para disparar mensagens exige credenciais de conta de serviço Firebase armazenadas **no servidor**, com proteção contra CSRF, capacidade restrita e auditoria. Na InfinityFree, disponibilidade de chamadas para `fcm.googleapis.com` precisa ser testada; uma Cloud Function pode resolver esse limite.
- Notificações push não garantem entrega imediata em todos os aparelhos.

## Fluxo de envio seguro para a proxima release

O codigo da branch 1.0.6 prepara a Action `.github/workflows/brother-fcm-release.yml` e
o emissor `.github/scripts/send-brother-fcm-release.mjs`. **Ainda nao ha publicacao
automatica nem botao WordPress conectado.** Uma Action com `workflow_dispatch`
precisa estar na branch padrao `main` para ser disparada pela API do GitHub;
nao mesclar ou publicar sem o QA Android.

Precondicoes para envio: instalar primeiro a 1.0.6 (clientes da 1.0.5 ainda nao
tem inscricao no FCM), publicar o proximo APK assinado no site e atualizar
`updates/stable.json` na `main` com `versionCode`, `versionName` e SHA-256
exatos. A Action compara essas informacoes com os inputs e recusa inconsistencias.

Inputs obrigatorios da Action: `version_code`, `version_name`,
`release_sha256`. O input `dry_run` e **true** por padrao, sem entrega real.
Para enviar a um topico de clientes exige `dry_run=false` e confirmacao
literal `ENVIAR <versionCode>`. O emissor usa apenas o GitHub Secret
`BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON`, nunca uma conta de servico no APK
ou na interface WordPress.

Quando o plugin WordPress estiver pronto, seu botao restrito a administrador
devera usar `current_user_can('manage_options')`, nonce anti-CSRF, validacao
do manifesto e `TIM_BROTHER_GH_ACTIONS_TOKEN` guardado no servidor, sem
registrar o token em logs. O servidor envia um POST autenticado para:
`https://api.github.com/repos/timachado/Brother-Matrizes/actions/workflows/brother-fcm-release.yml/dispatches`
com `ref=main` e `inputs` citados acima. Conferir permissao minima
`Actions: write` do token fino e testar a comunicacao InfinityFree -> GitHub
em modo `dry_run` antes de habilitar envio real.

Nem uma resposta 204 do GitHub nem um aceite da API Firebase garantem que o
aviso apareceu no aparelho; validar entrega em Android, consentimento e
restricoes de bateria/rede separadamente.
