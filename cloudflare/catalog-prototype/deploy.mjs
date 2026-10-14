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
  { ok: config.includes('"preview_urls": false'), label: 'Preview URLs nao correspondem ao Worker' },
  { ok: config.includes('"observability": {') && config.includes('"enabled": true') && config.includes('"logs": {'), label: 'Observabilidade remota deve ser mantida' },
]) {
  if (!check.ok) throw new Error(check.label + '. Implantacao abortada.');
}

// O Wrangler exige database_name localmente mesmo quando o binding remoto o omite.
 // O modo --strict compara os metadados e bloqueia esse diff nao destrutivo.
 // Conferimos explicitamente Worker, database_id, preview_urls e observabilidade;
 // --keep-vars preserva as variaveis e segredos ja existentes.
console.log('Worker, banco D1, previews e logs validados. Publicando com --keep-vars...');
execFileSync('npx', ['wrangler', 'deploy', '--keep-vars'], { stdio: 'inherit' });
