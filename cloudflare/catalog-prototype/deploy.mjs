// Brother Matrizes: implantacao existente com verificacao conservadora.
// O Cloudflare Builds fornece autenticacao; nao use secrets no repositorio.
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const worker = readFileSync('worker.js', 'utf8');
const config = readFileSync('wrangler.jsonc', 'utf8');

for (const check of [
  { ok: worker.includes('ready_for_sales: false'), label: 'Worker de homologacao nao identificado' },
  { ok: worker.includes('commercial_api_not_enabled'), label: 'Bloqueio comercial ausente' },
  { ok: config.includes('"name": "brother-matrizes-api"'), label: 'Nome do Worker divergente' },
  { ok: config.includes('"binding": "DB"'), label: 'Binding D1 DB ausente' },
  { ok: config.includes('"database_id": "9fd50dab-b8ea-4edb-8243-6b465fc58f55"'), label: 'Banco D1 divergente' },
]) {
  if (!check.ok) throw new Error(check.label + '. Implantacao abortada.');
}

console.log('Worker existente e D1 verificados. Iniciando deploy conservador...');
execFileSync('npx', ['wrangler', 'deploy', '--keep-vars', '--strict'], { stdio: 'inherit' });
