import { writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const NAME = 'brother-matrizes-api';
const DB_NAME = 'brother-matrizes-catalog-teste';
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
const token = process.env.CLOUDFLARE_API_TOKEN;

// The Cloudflare Builds token can read this existing Worker's script settings.
// Discover the D1 database ID instead of asking the user to copy a UUID.
if (!/^[a-f0-9]{32}$/i.test(accountId ?? '') || !token?.trim()) {
  throw new Error('Cloudflare Builds nao forneceu CLOUDFLARE_ACCOUNT_ID/CLOUDFLARE_API_TOKEN. Publicacao interrompida sem alteracoes.');
}
const url = 'https://api.cloudflare.com/client/v4/accounts/' + accountId +
  '/workers/scripts/' + NAME + '/settings';
const response = await fetch(url, {
  headers: { Authorization: 'Bearer ' + token, Accept: 'application/json' },
});
if (!response.ok) throw new Error('Nao foi possivel verificar o Worker (HTTP ' + response.status + '). Nenhum deploy.');
const parsed = await response.json();
if (parsed.success !== true || !Array.isArray(parsed.result?.bindings)) {
  throw new Error('Resposta inesperada ao verificar bindings. Nenhum deploy.');
}
const bindings = parsed.result.bindings;
const dbs = bindings.filter(item => item.type === 'd1' && item.name === 'DB');
const unexpected = bindings.filter(item => !['d1', 'secret_text'].includes(item.type));
if (dbs.length !== 1 || bindings.filter(item => item.type === 'd1').length !== 1 || unexpected.length) {
  throw new Error('Bindings divergentes/inesperados: revisao manual necessaria. Nenhum deploy.');
}
if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(dbs[0].id ?? '')) {
  throw new Error('ID do D1 invalido ou nao disponivel. Nenhum deploy.');
}
const config = {
  name: NAME,
  main: './worker.js',
  compatibility_date: '2026-10-10',
  workers_dev: true,
  keep_vars: true,
  d1_databases: [{
    binding: 'DB',
    database_name: DB_NAME,
    database_id: dbs[0].id,
  }],
};
writeFileSync('wrangler.generated.json', JSON.stringify(config, null, 2) + '\n', { mode: 0o600 });
console.log('Worker e D1 verificados; configuracao temporaria preparada.');
if (process.env.BM_DEPLOY_CHECK_ONLY === '1') process.exit(0);
execFileSync(process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['wrangler', 'deploy', '--config', 'wrangler.generated.json', '--keep-vars'],
  { stdio: 'inherit' });
