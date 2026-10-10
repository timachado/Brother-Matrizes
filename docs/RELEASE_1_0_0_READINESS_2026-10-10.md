# Brother Matrizes — portão de lançamento 1.0.0

Data de revisão: 2026-10-10.
Branch de preparação: `release/brother-matrizes-1.0.0-prep`.
Base imutável de QA: `c6b163011b5a07870632b307e0319ffc0212ef5f` (`0.50.7-rc4`).
**Não anunciar como APK estável ou disponibilizar publicamente até cumprir todas as condições abaixo.**

## Homologações aprovadas
- [x] GitHub Actions execução #1316 (`38071725174`): testes unitários, Android Lint, APK debug, release unsigned, alinhamento/assinatura de debug, smoke tests em emulador API 35.
- [x] Login Google do aplicativo utilizado para identificar a conta; usuário confirmou QA visual/funcional da ativação.
- [x] Teste Pro gratuito de 7 dias implementado exclusivamente no Supabase, sem dados de cartão ou cobranças.
- [x] Um registro de teste ativo foi confirmado por leitura no Supabase: início 2026-10-10 17:37:03 UTC, fim 2026-10-17 17:37:03 UTC. Isso valida uma ativação real; não simula a expiração.
- [x] WordPress/WooCommerce e Efí continuam fonte financeira autoritativa; Bíblia EBD não deve sofrer mudanças.

## Bloqueadores para declarar 1.0.0 estável
- [ ] Criar e verificar APK **release assinado** pela chave de produção esperada (workflow `.github/workflows/android-production-sign.yml`), com validação do certificado e SHA-256. Ensaio #38073302039 executado: unit tests e Lint aprovados, mas bloqueado pelo secret `BROTHER_MATRIZES_RELEASE_KEYSTORE_BASE64` ausente. Conferir a chave original antes de cadastrar os quatro secrets no GitHub. Não gerar nova identidade automaticamente.
- [ ] Testar instalação e atualização de versão assinada num aparelho com dados reais, preservando fontes, matrizes, projetos e sessão.
- [ ] Confirmar assinatura mensal/anual paga, renovação, suspensão, cancelamento e reembolso Efí/WooCommerce → espelhamento → conta Google → Pro no APK. Na checagem de 2026-10-10, a tabela de eventos do Brother continha somente ocorrências `cancelled` e `failed` para `pro_lifetime_launch`; não havia evento `active` de pagamento.
- [ ] Rever a segurança das cotas gratuitas de importação: `BETA_LOCAL_IMPORTS=true` e `LocalBetaImportQuota` usam contador por aparelho (não por conta), reinicializável por limpeza de dados/reinstalação. A política comercial final precisa ser resistente a fraude sem quebrar arquivos existentes nem o teste Pro.
- [ ] Validar expiração natural ou simulada em ambiente **separado**, sem alterar o único teste legítimo ativo, e retorno gratuito sem apagar projetos.
- [ ] Verificar a rota de checkout de cada plano e o estoque real do Vitalício Lançamento; o valor no app é informativo e WooCommerce tem autoridade sobre valor/estoque.
- [ ] Conferir comunicações de pedido por e-mail e ação agendada no WooCommerce, pois o painel exibiu falhas SMTP e 12 ações vencidas.

## Política de escopo
- Não mover pagamentos/assinaturas para Supabase. Supabase gerencia Google login e período não financeiro de 7 dias, e espelha evidências de compra emitidas pelo WooCommerce.
- Não liberar plano Pro com URL do checkout, alterações locais ou dados editáveis do usuário.
- Não copiar chaves de assinatura, Efí, service role ou tokens para o APK/repositório.
- Não substituir o histórico/certificado do aplicativo com uma nova chave incompatível.
- Não alterar Bíblia EBD, checkout vitalício, simulação de bordado, fontes ou biblioteca sem teste regressivo.

## Próximo corte técnico
Concluir os bloqueadores acima em branch dedicada. Quando aprovados, marcar `versionName=1.0.0`, aumentar `versionCode`, compilar, assinar e verificar o APK de produção. Registrar SHA-256 e certificado antes de distribuir.

## Avanço da assinatura — 10/10/2026

- [x] Primeira keystore candidata RSA-4096 criada e mantida fora do GitHub, com credenciais privadas destinadas à custódia do titular.
- [x] Compilação Android de `1.0.0-rc1` na branch isolada: GitHub Actions `38076036907`, testes unitários, Android Lint e release unsigned aprovados.
- [x] APK `1.0.0-rc1` assinado fora do GitHub usando Android SDK 36 oficial; assinatura v2 e v3 verificadas, certificado `fac3e71b641805e1183e34a636e50e4890e8fe24873f53e85d863b934815de4a`, APK SHA-256 `97bec26cb16d5d9e2510e088ba27212fb6ff9b8a17472851de11597dfeaeeb8f`.
- [ ] Titular deve guardar keystore e credenciais em dois backups seguros e cadastrar secrets no GitHub; o CI de assinatura ainda não está configurado com a chave.
- [ ] Validar assinatura e funcionamento em aparelho de teste (não instalar por cima de APK debug com chave distinta). Não apagar dados locais.
- [ ] Bloqueadores comerciais, quota gratuita local de beta, teste físico de bordado e renovação de pagamentos mantidos. A candidata não pode ser anunciada como estável.
