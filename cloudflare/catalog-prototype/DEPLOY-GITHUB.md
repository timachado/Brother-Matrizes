# Brother Matrizes — Publicar pelo GitHub, sem editar código no celular

Este diretório é **somente o Worker Cloudflare**. Não mexe no APK Android, WordPress ou Bíblia EBD.

## Preparação já feita
- Worker existente: `brother-matrizes-api`
- D1 criado: `brother-matrizes-catalog-teste`
- Binding do Worker: `DB`
- Código: `worker.js` (catálogo somente, nenhum acesso Pro nem pagamentos habilitados)
- O código ainda precisa ser implantado; adicionar arquivos ao GitHub **não publica** o Worker.

## Conectar o Worker existente ao GitHub (uma única vez)
No painel Cloudflare, selecione **Workers & Pages → brother-matrizes-api → Settings → Builds → Connect**.
Use o repositório `timachado/Brother-Matrizes` e confira todos estes campos:

| Campo | Valor |
| --- | --- |
| Production branch | `refactor/brother-matrizes-clean-0469` (**não main**) |
| Root directory | `cloudflare/catalog-prototype` |
| Build command | deixar vazio |
| Deploy command | `npm run deploy:cloudflare` |

Autorize apenas a integração GitHub necessária. Não crie outro Worker e não utilize a opção Pages.
O Cloudflare Builds poderá exigir uma confirmação para instalar a integração GitHub.

## Proteções de publicação
O script `deploy.mjs` lê via API as configurações **do Worker existente**, verifica que existe exatamente um binding D1 `DB`, e obtém o identificador do banco. Não é preciso colar UUID nem credenciais em chat, arquivo ou repositório.
Se a Cloudflare não fornecer ao build a autorização necessária para consultar as configurações do Worker ou se houver bindings inesperados, o script **falha sem fazer deploy**.
O Wrangler recebe arquivo transitório `wrangler.generated.json` com o DB correto e usa `--keep-vars` para preservar as variáveis existentes; segredos são preservados pelo Wrangler.
Não coloque segredo `CATALOG_SYNC_SECRET` em GitHub nem na interface do chat; nesta etapa ele permanece ausente, bloqueando qualquer sincronização de planos.

Quando salvar a conexão, se não houver uma execução automática, um novo commit neste diretório disparará o build.
Verificar resultado em **Settings → Builds**.
Um build com falha **não** significa que o Worker foi publicado.

## Testes de verificação, após build bem-sucedido
1. `GET https://brother-matrizes-api.servicospremiummachadoti.workers.dev/health` deve retornar JSON com `status: prototype`, `ready_for_sales: false`.
2. `GET .../wp-json/brother-matrizes/v1/plans` deve retornar HTTP 503 `catalog_not_synchronized`, pois ainda não há catálogo enviado pelo WordPress.
3. `GET .../wp-json/brother-matrizes/v1/me` deve retornar HTTP 503 `commercial_api_not_enabled`.
4. Essa fase **não** corrige importação de ZIP no APK nem ativa cobranças. Licenciamento e comunicação com WordPress permanecem a homologar.

Referência: https://developers.cloudflare.com/workers/ci-cd/builds/
