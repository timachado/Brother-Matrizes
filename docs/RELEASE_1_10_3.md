# Brother Matrizes 1.10.3 — Homologação da Central de Atualizações

**Versão:** 1.10.3 · **Código:** 179 · **Origem:** release/brother-matrizes-1.10.3

## Notas para os usuários
- Atualização mais prática: instalação diretamente pelo aplicativo, sem abrir o navegador desnecessariamente.
- Porcentagem de progresso no download das novas versões.
- Melhorias no controle das notificações para evitar alertas repetidos.
- Refinamentos de estabilidade e segurança na atualização.

## Comportamento esperado
1. Com **1.10.2** instalada, atualizar para o APK **1.10.3 assinado de homologação** usando o instalador do Android. Não desinstalar.
2. Confirmar que fontes, bibliotecas, matrizes, desenhos, projetos e configurações permanecem.
3. Verificar que o aplicativo abre e apresenta a versão **1.10.3** nas configurações.
4. O botão **Verificar novamente** deve indicar aplicativo atualizado, pois a versão pública ainda é **1.10.2**.
5. Quando **1.10.4** ou posterior existir, o aviso e os cards terão **Atualizar agora** e baixarão APK dentro do aplicativo com porcentagem, só abrindo o instalador do Android depois de conferir SHA-256, pacote, código e certificado.
6. Se o Android pedir autorização para instalar aplicativos dessa fonte, conceder apenas para Brother Matrizes e usar **Concluir instalação** ao retornar.
7. Links para o site existem apenas como alternativa manual em caso de erro de download, e não serão abertos automaticamente pelo aviso de atualização.

## Segurança e distribuição
- Assinatura comercial: mesmo certificado SHA-256 da 1.10.2, verificado no pipeline.
- O APK de homologação fica em um artefato temporário de GitHub Actions, por 7 dias.
- **Não publicar** a 1.10.3 no WordPress ou manifesto estável antes da aprovação do teste no Android real.
- Firebase: consentimento opt-in respeitado; dois caminhos de notificação compartilham trava e registro de versão. O envio automático segue **desligado** até configurar a credencial de serviço no GitHub.
- Sem alterações em pagamentos, licenças, banco Supabase, login Google e dados internos.

## Comprovações
- `.github/workflows/verify-1.10.3.yml`: compilação e testes Android.
- `.github/workflows/qa-1.10.3-upgrade.yml`: atualização 1.10.2→1.10.3 no emulador com arquivo persistente.
- `.github/workflows/brother-homologar-apk-1.10.3.yml`: assinatura temporária do APK por JKS original e conferência de certificado v2/v3.

Somente após esses critérios é permitida a publicação comercial via `.github/workflows/brother-commercial-release.yml`, com notificações Firebase desligadas inicialmente.
