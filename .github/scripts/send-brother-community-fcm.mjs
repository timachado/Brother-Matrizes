import { readFileSync } from 'node:fs';
import { cert, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

/**
 * Envios manuais exclusivos da comunidade Brother Matrizes.
 * Data-only: somente o cliente 1.10.4+ consentido exibe o aviso.
 * Nenhum token individual é coletado, exportado ou registrado.
 */
const EXPECTED_PROJECT = 'brother-matrizes---ti-machado';
const TOPIC = 'brother_matrizes_community';
const MANIFEST = 'https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json';

async function main() {
  if (process.env.GITHUB_EVENT_NAME !== 'workflow_dispatch' ||
      process.env.GITHUB_REPOSITORY !== 'timachado/Brother-Matrizes') {
    throw new Error('Somente disparo manual no repositorio oficial');
  }
  const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
  const inputs = event.inputs || {};
  const eventId = String(inputs.event_id || '').trim();
  const title = String(inputs.title || '').trim();
  const body = String(inputs.body || '').trim();
  const dryRun = String(inputs.dry_run) !== 'false';

  if (String(inputs.confirm || '') !== 'ENVIAR-COMUNIDADE') {
    throw new Error('Confirmacao de envio invalida');
  }
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(eventId)) {
    throw new Error('event_id invalido: use 8 a 64 letras, numeros, - ou _');
  }
  if (!title || title.length > 90 || !body || body.length > 240) {
    throw new Error('Titulo ou mensagem fora dos limites permitidos');
  }

  const response = await fetch(MANIFEST, { signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error('Manifesto oficial indisponivel');
  const release = await response.json();
  if (release.applicationId !== 'com.timachado.brothermatrizes' ||
      release.channel !== 'stable' || !Number.isSafeInteger(release.versionCode)) {
    throw new Error('Identidade comercial do manifesto invalida');
  }
  if (!dryRun && release.versionCode < 180) {
    throw new Error('Envio real bloqueado ate a versao 1.10.4 estar publicada');
  }

  const key = process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!key) throw new Error('Credencial Firebase nao cadastrada nos Secrets do GitHub');
  const credential = JSON.parse(key);
  if (credential.type !== 'service_account' || credential.project_id !== EXPECTED_PROJECT) {
    throw new Error('Projeto de origem da credencial Firebase nao corresponde ao Brother Matrizes');
  }

  const app = initializeApp({ credential: cert(credential), projectId: EXPECTED_PROJECT });
  const message = {
    topic: TOPIC,
    data: { type: 'community', eventId, title, body },
    android: { priority: 'normal', ttl: 172800000 }
  };
  await getMessaging(app).send(message, dryRun);
  console.log(dryRun
    ? 'Validacao Firebase aprovada; nenhum usuario recebeu notificacao.'
    : 'Aviso aceito pelo Firebase; a entrega por aparelho nao e garantida.');
}
main().catch(error => {
  console.error('Envio nao concluido: ' + String(error?.message || error));
  process.exitCode = 1;
});
