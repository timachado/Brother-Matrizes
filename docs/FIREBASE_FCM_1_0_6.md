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

## Fluxo de notificacao de release — implementacao existente

O workflow oficial **ja esta na branch main**:
`.github/workflows/brother-fcm-send.yml`, com emissor
`.github/scripts/send-brother-fcm.mjs`.
Nao manter um segundo workflow para a mesma operacao.

O painel WordPress `Central de notificacoes — Firebase Push` apresenta
`Validar configuracao (sem disparar)` e `Enviar notificacao aos usuarios`.
O teste do workflow em 11/10/2026 (run `38181291065`) foi concluido
com sucesso e registrou `Validacao de FCM aprovada, sem envio`.
Isto confirma o dry-run no GitHub/Firebase, **nao** comprova recebimento
fisico de notificacoes nos celulares.

- Manifesto oficial: `updates/stable.json` em `main`, atualmente
  versao publica 1.0.5 (`versionCode=171`).
- O codigo do emissor bloqueia push real para `versionCode<=172`.
  Somente considerar avisos reais apos publicar uma versao posterior a
  1.0.6, instalacao do cliente com opt-in e verificacao de hash/manifesto.
- O envio usa `BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON` somente como
  GitHub Actions Secret. Nunca acrescentar credenciais privadas no APK,
  WordPress frontend, logs ou codigo.
- Conferir a autorizacao do WordPress, nonce, permissao de administrador,
  disparo autenticado da Action e registros de auditoria antes de permitir
  operacao comercial.
- O sender oficial recebe `version_code`, `version_name`, `title`,
  `body` e `dry_run`. Utilizar `dry_run=true` nos testes.
- O campo titulo e mensagem do painel pode variar, respeitando 90 e 240
  caracteres, respectivamente; o cliente so mostra aviso se a versao
  publicada for superior a instalada.
- Preservar consulta periodica WorkManager como fallback para aparelhos
  1.0.5 e para clientes sem consentimento Firebase Push.

A 1.0.6 permanece candidata a QA Android ate validar instalacao sobre
a 1.0.5, permissao, recebimento end-to-end e permanencia das
funcionalidades aprovadas. **Nao atualizar manifesto publico ou
enviar push real como parte da revisao documental.**
