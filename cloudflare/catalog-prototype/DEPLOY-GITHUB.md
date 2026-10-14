# Brother Matrizes — Publicacao pelo GitHub

Worker **existente**: `brother-matrizes-api`; **nao criar outro**.
Conta Cloudflare Free; WordPress, WooCommerce, Efí Bank e Biblia EBD permanecem intactos.

## Erro confirmado no log de 2026-10-10
O Cloudflare Builds estava executando `npx wrangler preview`, e falhou com
`Your Wrangler configuration is missing a previews block`.

**Diagnostico:** a branch de trabalho provavelmente foi classificada como **nao producao**.
Cloudflare distingue deploy de producao (`wrangler deploy`) de previews (`wrangler preview`).
Nao adicionar banco D1 de producao em `previews` so para silenciar o erro.

## Correcao obrigatoria no painel Cloudflare (sem copiar o codigo)
1. Workers & Pages -> `brother-matrizes-api` -> **Settings -> Builds -> Branch control**.
2. Configure **Production branch** = `refactor/brother-matrizes-clean-0469` (nao `main`).
3. Deixe **Preview builds** desativados por enquanto: nao precisamos de previsualizacoes de licenca nem usar banco real em preview.
4. Em **Build configuration** confira:
   - Root directory = `cloudflare/catalog-prototype`;
   - Build command = vazio;
   - **Production deploy command** = `npm run deploy:cloudflare`;
   - Nao usar `npx wrangler preview` como comando de producao.
5. Salve e execute novamente o build. O log de **produçao** deve mostrar `Executing user deploy command: npm run deploy:cloudflare`. Se continuar mostrando `npx wrangler preview`, a branch ainda esta classificada como preview.
6. Se falhar, nao recriar Worker nem D1: revisar erro do log e ajustar somente o necessario.

## Codigo versionado
- `worker.js`: somente catalogo de teste, sem autorizacao comercial, sem pagamentos.
- `wrangler.jsonc`: nome Worker e binding D1 `DB` ao banco `brother-matrizes-catalog-teste`.
- `deploy.mjs`: verifica destino, D1 e ausencia de autorizacao comercial, depois executa `wrangler deploy --keep-vars --strict`, preservando variaveis e impedindo substituicoes arriscadas.
- `CATALOG_SYNC_SECRET` **NAO** deve ser publicado em GitHub, no APK ou chat. Sem o segredo, sincronizacao continua bloqueada.

## Pos-publicacao
`GET https://brother-matrizes-api.servicospremiummachadoti.workers.dev/health` => JSON com
`status: prototype` e `ready_for_sales: false`.

GET `/wp-json/brother-matrizes/v1/plans` => 503 enquanto catalogo nao foi enviado pelo WordPress.

GET `/wp-json/brother-matrizes/v1/me` => 503; licencas/assinaturas ainda nao estao implementadas.

O problema de importacao TTF/ZIP do APK ainda depende do licenciamento remoto e **nao e resolvido** nesta fase.

Referencias:
- https://developers.cloudflare.com/workers/ci-cd/builds/build-branches/
- https://developers.cloudflare.com/workers/ci-cd/builds/configuration/
- https://developers.cloudflare.com/workers/wrangler/commands/workers/
