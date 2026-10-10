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
- [ ] Criar e verificar APK **release assinado** pela chave de produção esperada (workflow `.github/workflows/android-production-sign.yml`), com validação do certificado e SHA-256. Nenhuma execução de assinatura de produção foi identificada nas últimas 100 execuções consultadas.
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
