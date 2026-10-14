import { readFileSync } from 'node:fs';
import { initializeApp, cert } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

const expected = {
  applicationId: 'com.timachado.brothermatrizes',
  versionName: '1.10.8',
  versionCode: 184,
  sha256: '67126397ee416386d2991214208a637165e73401aa6c71693495f10d62638819',
  topic: 'brother_matrizes_updates_stable',
  apk: 'Brother-Matrizes-1.10.8-COTAS-NOVIDADES-HOMOLOGACAO.apk'
};
const manifestURL = 'https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json';
const releaseURL = 'https://api.github.com/repos/timachado/Brother-Matrizes/releases/tags/v1.10.8';
const approvalPath = 'releases/brother-1.10.8-notification-approved.json';
const site = 'https://timachado.ifree.page/aplicativos/brother-matrizes/';

async function readJson(url) {
  const res = await fetch(url, {
    headers: { Accept: 'application/json', 'User-Agent': 'brother-release-notification-validator' },
    signal: AbortSignal.timeout(18000)
  });
  if (!res.ok) throw Error('Publicação ainda indisponível para verificação HTTP ' + res.status);
  return res.json();
}

async function main() {
  if (process.env.GITHUB_REPOSITORY !== 'timachado/Brother-Matrizes' ||
      process.env.GITHUB_EVENT_NAME !== 'push' ||
      process.env.GITHUB_REF !== 'refs/heads/main' ||
      process.env.GITHUB_RUN_ATTEMPT !== '1') {
    throw Error('Esta campanha exige primeiro evento push na main, sem repetição.');
  }
  const approval = JSON.parse(readFileSync(approvalPath, 'utf8'));
  if (approval.authorized !== true || approval.campaignId !== 'brother-v1.10.8-update-20261014' ||
      approval.versionName !== expected.versionName ||
      approval.versionCode !== expected.versionCode ||
      approval.sha256 !== expected.sha256 ||
      approval.topic !== expected.topic ||
      approval.doNotRepeat !== true) throw Error('Aprovação de campanha divergente.');

  const [manifest, release] = await Promise.all([
    readJson(manifestURL + '?fcm=184'),
    readJson(releaseURL)
  ]);
  if (manifest.applicationId !== expected.applicationId ||
      manifest.channel !== 'stable' ||
      manifest.versionName !== expected.versionName ||
      manifest.versionCode !== expected.versionCode ||
      manifest.sha256 !== expected.sha256 ||
      manifest.downloadUrl !== site ||
      release.tag_name !== 'v1.10.8' || release.draft || release.prerelease)
    throw Error('Release pública e manifesto estável não estão alinhados.');

  const asset = release.assets?.find(a => a.name === expected.apk);
  if (!asset || asset.digest !== 'sha256:' + expected.sha256 ||
      asset.browser_download_url !== manifest.downloadApkUrl)
    throw Error('Arquivo comercial publicado não corresponde ao hash autorizado.');

  const encoded = process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!encoded) throw Error('Credencial de envio Firebase ausente.');
  const credential = JSON.parse(encoded);
  if (credential.type !== 'service_account' ||
      credential.project_id !== 'brother-matrizes---ti-machado' ||
      !credential.client_email || !credential.private_key) {
    throw Error('Projeto ou credencial Firebase incorretos.');
  }
  const app = initializeApp({
    credential: cert(credential), projectId: 'brother-matrizes---ti-machado'
  });
  const message = {
    topic: expected.topic,
    data: {
      type: 'update',
      versionCode: String(expected.versionCode),
      versionName: expected.versionName,
      title: approval.title,
      body: approval.body
    },
    android: { priority: 'high', ttl: 604800000 }
  };
  await getMessaging(app).send(message, true);
  console.log('Pré-validação FCM concluída para a release 1.10.8; nenhum aviso enviado nesta etapa.');
  await getMessaging(app).send(message, false);
  console.log('FCM aceitou a única campanha 1.10.8; somente aparelhos inscritos e com permissão podem receber o aviso.');
}
main().catch(error => {
  console.error('Aviso 1.10.8 não enviado: ' + String(error?.message || error).slice(0, 220));
  process.exitCode = 1;
});
