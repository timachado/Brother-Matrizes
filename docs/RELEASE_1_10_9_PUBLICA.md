# Brother Matrizes 1.10.9 — versão comercial pública

Atualização **somente da linha comercial**, derivada diretamente da `main` 1.10.8 (`versionCode 184`). Não utiliza quaisquer branches experimentais.

- Identificador: `com.timachado.brothermatrizes`
- Versão: `1.10.9` (`versionCode 185`)
- APK autorizado: `Brother-Matrizes-1.10.9-OFICIAL-COMERCIAL-ASSINADO.apk`
- SHA-256 APK: `3bb264ff7f62476849aec8b8f14475de921deed22f04b33c0b1e1093664e85c5`
- SHA-256 do certificado de assinatura comercial: `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`
- Tamanho: 14.330.245 bytes

## Alterações comerciais
- NetFontes com busca própria, lupa e importação seletiva no mesmo fluxo da Biblioteca de Fontes.
- NetFontes nas informações de Planos; importações usam a cota já existente de fontes.
- Verificação de atualizações via manifesto oficial presente em `updates/stable.json` e tratamento de falhas.
- Motor de bordado, simulação, licenças, planos e outros fluxos comerciais preservados.

## QA
- GitHub Actions `38983149709`: `:app:testDebugUnitTest` e `:app:assembleRelease` aprovados.
- Assinatura local com chave comercial original; conferidos esquemas Android v2/v3, `zipalign`, SHA-256, pacote e código da versão.
- Teste físico de atualização em celular comercial requer confirmação antes de ampla divulgação.

## Publicação
A nova release deve conter **exatamente o APK assinado acima**. Nunca publicar candidato unsigned/debug ou substituir arquivos silenciosamente por nome semelhante.

**Não alterar** `updates/stable.json` para `versionCode 185` até o download final HTTPS do APK público ser verificado e seu hash ser idêntico ao registrado acima. A atualização automática não deve anunciar um arquivo inexistente.

Após o APK público existir, atualizar o manifesto com `versionCode 185`, `versionName 1.10.9`, `downloadUrl` da release oficial e o SHA-256 acima. Verificar a URL GitHub Raw e o comportamento em Android antes de anunciar no site/WhatsApp.
