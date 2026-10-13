# Brother Matrizes 1.10.6 — login, cotas e atualização obrigatória

**Situação: candidato de homologação, não publicado.** Base de código 1.10.5; versão Android `1.10.6`, `versionCode=182`.

## Proteções existentes preservadas

- Ação **Abrir matriz** na Home e seletor de documentos já exigem sessão Google verificada, reservam `import_matrix` no servidor e finalizam com sucesso/falha após interpretar o arquivo.
- Receber PES/DST/JEF via `ACTION_SEND` ou `ACTION_VIEW` já segue a mesma regra. O extrator de ZIP/RAR faz reserva individual por importação e só finaliza quando conclui.
- Gratuito: **5 matrizes e 3 fontes importadas com sucesso por mês**. PRO ativo (mensal, anual, vitalício, lançamento, ou teste válido): importações sem essas cotas. Projetos já salvos no aparelho não são cobrados novamente ao reabrir.
- Exclusivamente Google OAuth: autenticação Supabase deve demonstrar provedor Google em `auth.app_metadata`, verificado novamente pela Edge Function. Email e senha não substituem essa prova. As cotas pertencem à conta autenticada, não ao armazenamento local.
- Nenhum entitlement PRO é concedido ao retornar do navegador. WooCommerce/Efí e o `brother-entitlement` permanecem a fonte de verdade.

## Complemento 1.10.6

| Ação no aplicativo | Gratuito | PRO válido | Quando contabiliza |
|---|---|---|---|
| Importar matriz (Arquivos, Telegram, compartilhamento) | 5/mês, já existente | Sem cota | Arquivo validado/importado |
| Importar fonte (TTF/OTF, ZIP, DaFont) | 3/mês, já existente | Sem cota | Fonte nova importada |
| Pesquisar no Telegram | **60/mês** | Sem cota | Ao abrir uma pesquisa no Telegram |
| Pesquisar no DaFont pelo buscador integrado | **60/mês** | Sem cota | Ao iniciar pesquisa na WebView |
| Abrir RAR e enviar arquivo ao RAR | **30/mês** | Sem cota | Após iniciar o handoff ao aplicativo |

Valores verificados no banco Supabase em 13/10/2026, tabela `brother_matrizes_monthly_action_limits`; o servidor ativo `brother-usage` (v4) já reconhece as operações `telegram_search`, `dafont_search`, `rar_receive` via `action=reserve/finalize` e verifica as licenças usando o `brother-entitlement`. **Nenhuma migração/deploy do Supabase foi necessária nem efetuada.**

No cancelamento do seletor, nada é reservado. Handoff/pesquisa que não abre é finalizado com `success=false`. Reservas temporárias expiram na janela do servidor. O Google é exigido antes da ação. Em caso de limite atingido, conexão ausente ou falha de verificação, a ação não é realizada e a mensagem informa o motivo.

**Limite técnico:** ações externas feitas totalmente fora do Brother Matrizes (pesquisas independentes no aplicativo Telegram, RAR ou site DaFont) não podem ser contabilizadas, pois o Brother não controla software de terceiros. O controle cobre os comandos integrados do próprio Brother e a importação quando um arquivo volta ao aplicativo.

## Atualização obrigatória — dois tipos de versões

**1.10.4 e versões posteriores que incluam a proteção:** já há `BrotherMandatoryUpdateGate` e `BrotherUpdateClient`, ligados ao manifesto HTTPS `updates/stable.json`. Após publicar uma versão estável assinada, é possível definir `minSupportedVersionCode` maior que os builds obsoletos. O gate exibe tela não dispensável, baixa o APK oficial, confere SHA-256, pacote, versionCode e certificado comercial e abre o instalador Android. Não existe instalação silenciosa nem bypass de Play Protect. **Nunca elevar o mínimo antes de validar link, hash e atualização sobre a instalação existente.**

**1.0.0 original:** verificamos o código na ref `release/brother-matrizes-1.0.0`. `MainActivity.kt` NÃO possui `BrotherMandatoryUpdateGate`. Mesmo que o manifesto remoto imponha versão mínima, **não é tecnicamente possível ativar a tela de bloqueio dentro de um APK antigo já instalado que não a implementou.** Para esses usuários, disponibilizar a nova versão na página oficial, comunicar atualização via grupo, e opcionalmente exigir `BROTHER_MINIMUM_CLIENT_VERSION_CODE` nos endpoints próprios depois de a versão estável assinada estar publicada; isso impede certas chamadas de API antigas, mas NÃO desativa operações 100% offline de APKs antigos. Não alegar atualização obrigatória universal.

**Política de rollout pendente, NÃO ativada:**
1. QA Kotlin compilação + unit tests e validação no Android real (sem login, Google, gratuito esgotado, trial, PRO, sem internet, RAR cancelado).
2. Confirmar acesso a todos os produtos WooCommerce, renovação e licença antes de divulgação.
3. Gerar APK 1.10.6 assinado com a assinatura comercial original. Comparar certificado e hash; confirmar instalação por cima, nunca pedir desinstalação.
4. Publicar APK em URL HTTPS oficial e só então atualizar `updates/stable.json` com **versionCode=182** e `minSupportedVersionCode` escolhido pela política de suporte, sem inventar hash.
5. Validar update in-app em pelo menos uma instalação anterior compatível, incluindo rede instável e Android 15.
6. Somente depois ajustar `BROTHER_MINIMUM_CLIENT_VERSION_CODE` no Supabase, se realmente desejado, testando erros `426`. Não habilitar esse bloqueio enquanto o APK não estiver disponível.
7. Para 1.0.0, adotar comunicação de migração e nova distribuição; não é possível inserir remotamente uma tela em versão pré-existente sem código de atualização.

## Escopo protegido

Nenhuma alteração em WooCommerce, Efí, sessões existentes, licenças, dados de matrizes, projetos, grupos do Telegram ou arquivos locais. Não publicar até homologar.
