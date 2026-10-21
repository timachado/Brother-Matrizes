# Brother Matrizes Premium — GitHub como origem única, OTA + site automáticos

**Correção de escopo em 21/10/2026, recuperada do histórico do projeto:** não reinventar a distribuição. O proprietário já definiu o fluxo **GitHub Releases → manifesto OTA verificado → aviso e atualização dentro do app + espelho automático no WordPress → comunicação ao grupo**. A **Google Play é uma fase posterior**. O APK não é hospedado uma segunda vez pelo WordPress.

## Etapas e componentes realmente existentes

1. **QA e aprovação, ANTES de tornar uma versão pública** — validar Home, imagem do bordado, ícone BM, acesso ao grupo oficial, fluxos PES/JEF/DST, Criar, Biblioteca, Simulador, Editor, Converter, Enviar, login, licenciamento e checkout. Validar atualização real sobre o APK público 1.10.10 e preservação dos dados.
2. **Assinatura do APK oficial** — gerar `com.timachado.brothermatrizes` (sem sufixo QA), candidata 1.11.0 / versionCode 187, com certificado comercial **idêntico ao original**. Fazer `zipalign`, `apksigner verify` e SHA-256 do arquivo. A chave deve permanecer em segredos protegidos do Actions; jamais enviar JKS/senhas para README ou repositório.
3. **Publicar uma GitHub Release oficial após autorização** — tag `v1.11.0`, exatamente um APK com nome no padrão `Brother-Matrizes-1.11.0-OFICIAL-COMERCIAL-ASSINADO.apk`, release pública (não prerelease). Este é o **único arquivo oficial de download**; não enviar APK QA ao site ou ao grupo.
4. **Disparo OTA existente no repositório** — `.github/workflows/brother-ota-after-published.yml` executa em `release: published`; confere nome, tag, digest SHA-256, certificado da assinatura comercial e versionCode, então atualiza `main/updates/stable.json`. O workflow histórico 1.10.10 também solicita manualmente esse mesmo OTA. Jamais modificar o manifesto antes da publicação validada.
5. **Aviso e atualização no aplicativo** — `BrotherUpdateClient` consulta o GitHub Raw, com fallback WordPress. `BrotherUpdateNotificationWorker` usa WorkManager periódico de 1 hora, sujeito a disponibilidade de rede, permissões e políticas do Android. O módulo Firebase FCM está implementado, com tópico `brother_matrizes_updates_stable` e consentimento, e já houve confirmação histórica de recebimento. **A workflow OTA atual NÃO contém envio automático obrigatório ao FCM**: validar o mecanismo de disparo e, se desejado aviso push imediato a cada Release, integrar/testar em etapa protegida após verificação do manifesto, sem duplicar notificações.
6. **Download e atualização dentro do app** — `BrotherApkUpdater` obtém a URL oficial verificada do manifesto, baixa APK, confere SHA-256, pacote, versão e certificado, depois abre o instalador nativo do Android. Usuário pode precisar conceder permissão e confirmar **Instalar**. Não prometer instalação silenciosa.
7. **Atualização automática do site, sem reupload** — plugin `wordpress/tim-brother-sync` e vitrine da página `/aplicativos/brother-matrizes/` leem `main/updates/stable.json`. O plugin oferece sincronização REST com cache de 3 minutos e vitrine dinâmica via navegador. **Não altera automaticamente todo botão comercial existente nem substitui as regras de acesso do App Commerce Core**; verificar plugin instalado/atualizado, shortcodes, versão exibida e link efetivo no Android. WordPress continua sem armazenar o APK.
8. **Comunicar ao grupo de bordados** — somente depois da GitHub Release e do site estarem verificadamente consistentes, enviar o link do site; a infraestrutura OTA alcança os usuários do aplicativo sem requerer download manual do grupo.
9. **Google Play** — preparar e publicar depois, mediante nova aprovação e estratégia de compatibilidade da app signing key para quem instalou o APK do site.

## Workflow de assinatura — atenção a versões obsoletas

- `.github/workflows/android-production-sign.yml` é manual e destina-se a artefatos candidatos, sem publicação automática. Confirmar build release com checkout ativo e fingerprint correta.
- `.github/workflows/brother-11010-publish-commercial.yml` é específico da versão 1.10.10: **não reutilizar para 1.11.0** sem generalizar e testar. Ele faz publicação e dispara OTA para 1.10.10.
- O README do plugin ainda cita `brother-commercial-release.yml`, que não foi encontrado entre os workflows da branch main em 21/10; não assumir que exista um botão pronto de publicação genérica sem verificação.
- Um GitHub Actions que publica uma Release via `GITHUB_TOKEN` pode exigir disparo explícito por `workflow_dispatch` ou token permitido para acionar outro workflow. Validar realmente o gatilho OTA; o workflow 1.10.10 prevê disparo explícito.
- A publicação pública é o gatilho que pode anunciar uma versão a todos os usuários: nunca usá-la como teste.

## Critérios de conclusão

- [ ] Tela premium e ícone aprovados em Android e QA funcional concluído.
- [ ] Convite oficial do grupo WhatsApp abre diretamente o grupo.
- [ ] APK oficial 1.11.0 assinado com a chave existente e instala por cima da versão 1.10.10 sem perda de dados.
- [ ] GitHub Release publicada somente mediante autorização explícita, com nome, versão, certificado e SHA-256 corretos.
- [ ] Workflow OTA realmente atualizou o manifesto estável após a publicação.
- [ ] Notificação de nova versão validada (checagem periódica; FCM imediato somente se verificado).
- [ ] Aplicativo verifica, baixa e oferece instalação do APK oficial.
- [ ] Página e download do site refletem a nova versão sem upload redundante; regras comerciais preservadas.
- [ ] Comunicado do grupo apenas após validação.
- [ ] Nenhuma publicação Google Play nesta fase.

## Referências

- GitHub Releases: `https://github.com/timachado/Brother-Matrizes/releases`.
- Manifesto oficial: `https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json`.
- Página no site: `https://timachado.ifree.page/aplicativos/brother-matrizes/`.
- Código de sincronização: `wordpress/tim-brother-sync/`.
- PR de homologação: `https://github.com/timachado/Brother-Matrizes/pull/188`.
