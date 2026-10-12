# Publicação comercial automática — Brother Matrizes

**Versão pública atual:** 1.10.1 (código 177). Esta instrução não publica outra versão.

## Ações da T.I. Machado em cada lançamento

1. Preparar e revisar as alterações em uma ramificação própria: `release/brother-matrizes-X.Y.Z`. Essa ramificação deve ter o aplicativo Brother Matrizes aprovado, com `versionName` e `versionCode` atualizados no arquivo `app/build.gradle.kts`. **Não usar a branch main como fonte Android**: a main centraliza o manifesto e o sistema de distribuição.
2. Abrir [Ações → Lançamento Comercial Automatizado](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-commercial-release.yml) e selecionar **Executar fluxo de trabalho** na ramificação `main`.
3. Informar a ramificação aprovada, número de versão, código numérico e texto simples das novidades. Exemplo **ilustrativo, não publicar por antecipação**: `source_ref=release/brother-matrizes-1.10.2`, `version_name=1.10.2`, `version_code=178`.
4. Manter a opção de aviso Firebase desmarcada até a credencial `BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON` ter sido configurada e validada; os alertas não são pré-requisito para baixar novas versões.
5. Acompanhar o resultado. O fluxo realiza testes Android, compila, assina com a **chave comercial original**, verifica certificado, pacote, código, assinaturas v2/v3 e SHA-256, cria o release e testa o download público sem login.
6. Somente depois da verificação completa, atualiza `main/updates/stable.json`. O plugin **Brother Matrizes — Atualizações Automáticas 1.1.0** já instalado no WordPress sincroniza versão, novidades e endereço do APK no catálogo App Commerce Core `ti_app` 307.

O proprietário **não precisa carregar arquivos APK manualmente no GitHub ou WordPress** nas versões futuras se o fluxo comercial for bem-sucedido. A instalação da atualização continua pedindo confirmação do Android.

## Dois fluxos com propósitos distintos

- [Verificar assinatura comercial](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-verificar-assinatura.yml): confere credenciais e certificado sem compilar APK.
- [Homologar compilação e assinatura](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-homologar-assinatura-completa.yml): testa e compila a fonte aprovada 1.10.1, assina temporariamente e valida o APK **sem publicar, sem salvar o APK como artefato e sem avisar usuários**.
- [Lançamento Comercial Automatizado](https://github.com/timachado/Brother-Matrizes/actions/workflows/brother-commercial-release.yml): somente quando existe nova versão aprovada e homologada; publica o novo APK e atualiza o manifesto após validação.

## Segurança

- Segredos ficam **somente** no ambiente `brother-release-signing`, restrito à ramificação `main`. As quatro variáveis de chave privada devem permanecer como **Segredos do meio ambiente**, não como variáveis públicas.
- Certificado esperado (SHA-256): `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`.
- Jamais copiar o JKS, senhas ou Base64 para commits, Issues, prints, WordPress ou logs.
- Nunca criar outra assinatura ou trocar `applicationId=com.timachado.brothermatrizes`; isso impediria atualizar por cima de versões existentes.
- O WordPress sincroniza metadados e mantém WooCommerce, Efí, Supabase e licenças intactos. As opções de acesso do App Commerce Core continuam prevalecendo.
- Testar atualização em um Android real antes de anunciar uma versão nova à base de usuários.
- Não tentar desativar ou contornar Play Protect.

## Limitações e reversão

Se o fluxo falhar antes de publicar, a versão atual permanece 1.10.1. Se publicar o release mas falhar antes da sincronização, **não** mudar manualmente o manifesto para um hash não testado: diagnosticar o erro e concluir a publicação segura. Ações repetidas com a mesma versão são bloqueadas para evitar substituir o APK já distribuído.

O fluxo de notificações Firebase é separado e opcional. Um aviso aceito pelo serviço não garante entrega a cada usuário e não substitui a confirmação de download/instalação.
