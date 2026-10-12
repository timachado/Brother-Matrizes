# Brother Matrizes — notificações aos usuários

## Canais disponíveis

- **Atualizações** (topic FCM `brother_matrizes_updates_stable`): avisos somente quando a versão pública é maior que a instalada; aplicativo verifica o manifesto antes de abrir o atualizador. Consentimento existente e opt-in no Android.
- **Comunidade** (topic FCM `brother_matrizes_community`): novidades e tutoriais; apenas usuários que ativarem explicitamente esta categoria na tela Comunidade (Android 1.10.4+).
- **Checagem local WorkManager**: continua em paralelo para versões publicadas; deduplicação local preservada por versionCode.
- A partir do Android 13, o usuário deve conceder `POST_NOTIFICATIONS`; aparelhos com avisos desativados não recebem notificação visual.

## Configuração protegida

1. No projeto Firebase oficial `brother-matrizes---ti-machado`, confirme FCM habilitado e aplicativo Android `com.timachado.brothermatrizes`. **Não confunda `google-services.json` (cliente) com credencial administrativa de envio**.
2. Use identidade de serviço com acesso **somente à API Firebase Cloud Messaging**. Prefira credenciais de curta duração via Workload Identity Federation para infraestrutura gerenciada quando viável; o fluxo atual usa chave JSON cifrada em GitHub Secrets.
3. No GitHub, acesse **Settings → Secrets and variables → Actions → Secrets**, ou a configuração de secrets do environment **`brother-release-signing`**, e configure o nome exato `BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON` com o JSON da conta de serviço. Nunca versionar nem enviar essa chave pelo chat. Verificar a precedência entre secrets do repositório e do environment.
4. Confirme as regras de aprovação/restrição do environment `brother-release-signing`, que também controla publicação comercial.
5. Para **novas versões**: execute o workflow comercial validado a partir da `main` com `announce=true` **somente depois** da compilação, assinatura original, upload e sincronização do manifesto. O script verifica código, pacote, URL e SHA do release público; não envia para versões fictícias.
6. Para **comunidade**: primeiro publique Android **1.10.4 (código 180)**, depois execute Actions → **Brother Matrizes - Avisos da Comunidade FCM**. Use `event_id` único de 8–64 caracteres alfanuméricos, hífen ou sublinhado, `title` (até 90), `body` (até 240), `confirm` exatamente `ENVIAR-COMUNIDADE` e `dry_run=true`.
7. Depois de observar sucesso do teste seco FCM e conferir a tela no Android real com inscrição consentida, marque `dry_run=false` para enviar a mensagem real. **Envios reais por tópico não podem ser cancelados** após aceitos pelo FCM. Não repetir o mesmo `event_id` em outra campanha.

## Homologação

- [ ] Build e unit tests Android 1.10.4 verdes.
- [ ] FCM habilitado e credencial válida no GitHub, sem exposição nos logs.
- [ ] Teste com usuário que ativou cada categoria; conferir recebimento em segundo plano e toque para abrir Home/Comunidade.
- [ ] Testar usuário que recusou categoria e usuário sem permissão Android 13+; não deve exibir notificação.
- [ ] Testar sem internet; não pode comprometer projetos/conta.
- [ ] Testar evento `community` duplicado; deduplicação por `event_id`.
- [ ] Testar FCM update e Worker concorrentes; deduplicação por `versionCode`.
- [ ] Validar instalação com assinatura comercial original e atualização mantendo matrizes, fontes e licenças.
- [ ] Nenhum disparo em massa automático antes da homologação e da aprovação do responsável.

**Importante:** sucesso no endpoint FCM significa solicitação aceita, não comprova entrega em todos os celulares. O usuário controla os avisos; Android, modo economia de bateria, internet e disponibilidade do Firebase afetam entrega. O envio pela comunidade não altera WhatsApp nem publica mensagens no grupo.
