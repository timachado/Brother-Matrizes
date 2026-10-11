import {readFileSync} from 'node:fs';
import {cert, initializeApp} from 'firebase-admin/app';
import {getMessaging} from 'firebase-admin/messaging';

const PROJECT_ID = 'brother-matrizes---ti-machado';
const APP_ID = 'com.timachado.brothermatrizes';
const TOPIC = 'brother_matrizes_updates_stable';
const MANIFEST_URL =
  'https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json';

function requiredString(value, name) {
  const text = String(value ?? '').trim();
  if (!text) throw Error('Campo obrigatorio: ' + name);
  return text;
}

function releaseFromInputs(inputs) {
  const codeText = requiredString(inputs.version_code, 'version_code');
  if (!/^[1-9][0-9]*$/.test(codeText)) throw Error('version_code invalido');
  const versionCode = Number(codeText);
  if (!Number.isSafeInteger(versionCode) || versionCode <= 172) {
    throw Error('Avisos FCM exigem release posterior ao primeiro cliente 1.0.6 (172)');
  }
  const versionName = requiredString(inputs.version_name, 'version_name');
  if (!/^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$/.test(versionName)) {
    throw Error('version_name invalido');
  }
  const sha256 = requiredString(inputs.release_sha256, 'release_sha256').toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(sha256)) throw Error('SHA-256 invalido');

  const dryRun = String(inputs.dry_run ?? 'true');
  if (dryRun !== 'true' && dryRun !== 'false') throw Error('dry_run deve ser true ou false');
  if (dryRun === 'false' && String(inputs.confirm_live || '') !== 'ENVIAR ' + versionCode) {
    throw Error('Disparo real exige confirm_live igual a ENVIAR seguido do versionCode');
  }
  return {versionCode, versionName, sha256, dryRun: dryRun === 'true'};
}

async function verifiedManifest(release) {
  const response = await fetch(MANIFEST_URL, {
    signal: AbortSignal.timeout(15000),
    headers: {'Accept': 'application/json', 'Cache-Control': 'no-cache'},
  });
  if (!response.ok) throw Error('Manifesto oficial indisponivel: HTTP ' + response.status);
  const m = await response.json();
  if (m.applicationId !== APP_ID || m.channel !== 'stable' ||
      Number(m.versionCode) !== release.versionCode ||
      m.versionName !== release.versionName ||
      String(m.sha256 || '').toLowerCase() !== release.sha256) {
    throw Error('Versao informada nao corresponde ao manifesto publico assinado');
  }
  const url = new URL(requiredString(m.downloadUrl, 'downloadUrl'));
  if (url.protocol !== 'https:' || url.hostname !== 'timachado.ifree.page') {
    throw Error('URL de download nao corresponde ao site oficial');
  }
  return m;
}

async function main() {
  if (process.env.GITHUB_EVENT_NAME !== 'workflow_dispatch') {
    throw Error('Permitida apenas execucao manual ou integracao autenticada por dispatch');
  }
  const event = JSON.parse(readFileSync(requiredString(process.env.GITHUB_EVENT_PATH, 'GITHUB_EVENT_PATH'), 'utf8'));
  const release = releaseFromInputs(event.inputs || {});
  await verifiedManifest(release);

  const raw = requiredString(
    process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON,
    'BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON'
  );
  const sa = JSON.parse(raw);
  if (sa.type !== 'service_account' || sa.project_id !== PROJECT_ID ||
      !sa.client_email || !sa.private_key) {
    throw Error('Credencial de servico nao pertence ao projeto Brother Matrizes');
  }
  const app = initializeApp({credential: cert(sa), projectId: PROJECT_ID});
  const msg = {
    topic: TOPIC,
    data: {
      type: 'update',
      versionCode: String(release.versionCode),
      versionName: release.versionName,
      title: 'Brother Matrizes: atualizacao disponivel',
      body: 'Nova versao ' + release.versionName +
        ' publicada. Abra o aplicativo para verificar e atualizar.',
    },
    android: {priority: 'high', ttl: 21600000},
  };
  await getMessaging(app).send(msg, release.dryRun);
  console.log(release.dryRun
    ? 'DRY-RUN FCM aprovado para a versao ' + release.versionName + '. Nenhum push entregue.'
    : 'Firebase aceitou o envio da versao ' + release.versionName + ' ao topico comercial.');
}

main().catch(error => {
  // Nao imprime payloads, credenciais, tokens nem conteudo de respostas externas.
  console.error('Operacao FCM interrompida: ' +
    String(error?.code || error?.message || 'erro desconhecido').slice(0,180));
  process.exitCode = 1;
});
