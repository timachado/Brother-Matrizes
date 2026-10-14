# Nova tentativa do Cloudflare Workers Builds

Reexecucao solicitada em 2026-10-10, apos o usuario informar que ajustou a configuracao.
Somente arquivo informativo para acionar o build vinculado ao GitHub.
Nao altera codigo nem permissoes, nao publica segredos nem concede licencas.

Esperado no Cloudflare Build de producao:
- Root: `cloudflare/catalog-prototype`
- Branch: `refactor/brother-matrizes-clean-0469`
- Deploy command: `npm run deploy:cloudflare`
- Endpoint `/health`: JSON `{"service":"brother-matrizes-catalog-relay","status":"prototype","ready_for_sales":false}`

O script preserva o binding D1 `DB` e rotas comerciais continuam bloqueadas.
