# Brother Matrizes — Registro de desenvolvedor e Play Protect (outubro de 2026)

## O aviso observado
No Motorola Android 15 o APK 1.0.8 mostrou duas telas: "A verificação do app é recomendada" e "O app foi bloqueado para proteger seu dispositivo — Play Protect nunca viu um app desse desenvolvedor". Esta última é compatível com a categoria pública *Uncommon* (desenvolvedor desconhecido), **não** é prova de malware. A classificação e a instalação são decisões do Google/Android; nunca desativar o Play Protect nem tentar ocultá-lo no app.

## Verificações técnicas
- Nome: **Brother Matrizes**; canal oficial: `https://timachado.ifree.page/aplicativos/brother-matrizes/`
- Pacote Android: `com.timachado.brothermatrizes`
- Certificado comercial SHA-256 público: `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`
- Assinaturas originais v2/v3 validadas localmente com as ferramentas oficiais Android SDK 36.
- Auditoria do AndroidManifest empacotado em 1.0.8: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` (exclusivamente para fluxo de atualização dentro do app), permissões antigas de leitura/escrita restritas aos SDKs legados; `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` e `FOREGROUND_SERVICE` das bibliotecas integradas. **Sem** SMS, Accessibility e Notification Listener no APK.
- Verificar periodicamente dependências de terceiros, distribuição, segurança do código, disponibilidade de download e privacidade.

## Ação necessária pelo titular da T.I. Machado
1. Entrar em `https://android.google.com/developerconsole/developers` com conta Google autorizada do titular.
2. Criar/abrir conta no **Android Developer Console** para **distribuição completa fora da Google Play**. Escolher pessoa física ou empresa conforme a identidade legal, concluir a verificação documental pelo titular; se a conta for da organização, verificar o domínio da empresa no Google Search Console se exigido.
3. Na aba **Package names / Nomes de pacotes**, registrar `com.timachado.brothermatrizes` e adicionar o SHA-256 público acima.
4. Se o Google pedir **prova de propriedade**, ele fornecerá um trecho/arquivo de desafio para a pasta `assets/`. Acrescentar o desafio conforme as instruções exatas, compilar e **assinar com a mesma chave comercial original** (não criar nova), enviar apenas o APK de comprovação. Não enviar o `.jks`, senha nem conta de serviço Firebase.
5. Aguardar a confirmação no Android Developer Console de estado **Registered**. Registrar o resultado de análise do Play Protect separadamente, pois verificação da identidade e escaneamento antimalware são controles distintos.
6. Se o Play Protect continuar sinalizando por engano, consultar `https://developers.google.com/android/play-protect/warning-dev-guidance?hl=pt-BR` e, para classificações indevidas, a ferramenta oficial de contestação `https://support.google.com/googleplay/android-developer/contact/protectappeals?hl=pt-br`.

## Avisos in-app da 1.0.9
- Card premium na Home, notas da versão publicada, download verificado, ignorar até a próxima versão.
- Push do Firebase e verificação periódica WorkManager permanecem na barra do Android quando permitidos.
- O card é independente da permissão de notificações, reaparece ao publicar nova versão e se atualiza ao abrir/reabrir o app ou ao receber push enquanto aberto.
- A funcionalidade exige **app 1.0.9 ou superior já instalado**; não altera retroativamente APKs anteriores.

Fontes:
- https://developer.android.com/developer-verification/guides/android-developer-console
- https://developers.google.com/android/play-protect/warning-dev-guidance?hl=pt-BR
- https://developers.google.com/android/play-protect/warning-strings?hl=pt-BR

**Nenhum registro Google ou contestação foi enviado por esta alteração de código.**
