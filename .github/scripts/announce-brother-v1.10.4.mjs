import { readFileSync } from 'node:fs';
import { cert, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

/**
 * Notificação única do lançamento comercial 1.10.4.
 * Enviar SOMENTE para usuários inscritos voluntariamente no tópico de atualizações.
 * Uma simulação FCM antecede o envio real, sem distribuição adicional.
 */
const REPO = 'timachado/Brother-Matrizes';
const TOPIC = 'brother_matrizes_updates_stable';
const VERSION = '1.10.4';
const CODE = 180;
const APK_SHA256 = '62a663f3710c5989b2bc128f8a0b301bc2cd5f45ad1572a2d618cf51367d4ac0';
const PROJECT = 'brother-matrizes---ti-machado';
const APPROVAL_PATH = 'releases/brother-1.10.4-notification-approved.json';

function check(condition, why) {
  if (!condition) throw Error(why);
}

async function getJson(url) {
  const response = await fetch(url, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'BrotherMatrizes-Release-Announcement/1.10.4' },
    signal: AbortSignal.timeout(20000),
    cache: 'no-store'
  });
  check(response.ok, 'Consulta de release/manifesto falhou; envio bloqueado');
  return await response.json();
}

async function main() {
  check(process.env.GITHUB_REPOSITORY === REPO, 'Repositório incorreto');
  check(process.env.GITHUB_EVENT_NAME === 'push', 'Somente acionamento de aprovação único');
  check(process.env.GITHUB_REF === 'refs/heads/main', 'Aprovação fora da branch principal');
  check(process.env.GITHUB_RUN_ATTEMPT === '1', 'Tentativa repetida bloqueada para evitar avisos duplicados');
  const approval = JSON.parse(readFileSync(APPROVAL_PATH, 'utf8'));
  check(
    approval.campaignId === 'brother-v1.10.4-update-20261012' &&
    approval.authorized === true &&
    approval.applicationId === 'com.timachado.brothermatrizes' &&
    approval.versionCode === CODE &&
    approval.versionName === VERSION &&
    approval.topic === TOPIC &&
    approval.sha256 === APK_SHA256,
    'Registro de aprovação inválido ou divergente'
  );
  const title = approval.title;
  const body = approval.body;
  check(typeof title === 'string' && title.length >= 8 && title.length <= 90, 'Título inválido');
  check(typeof body === 'string' && body.length >= 10 && body.length <= 240, 'Mensagem inválida');

  const manifest = await getJson('https://raw.githubusercontent.com/' + REPO + '/main/updates/stable.json?announcement=' + Date.now());
  check(manifest.applicationId === approval.applicationId, 'Pacote divergente');
  check(manifest.channel === 'stable' && manifest.versionName === VERSION && manifest.versionCode === CODE, 'Versão estável divergente');
  check(manifest.sha256 === APK_SHA256, 'SHA do manifesto divergente');
  check(manifest.downloadApkUrl === 'https://github.com/' + REPO + '/releases/download/v1.10.4/Brother-Matrizes-v1.10.4-ASSINADO.apk', 'Link público inesperado');
  const release = await getJson('https://api.github.com/repos/' + REPO + '/releases/tags/v1.10.4');
  check(release.draft === false && release.prerelease === false, 'Release não estável');
  const apk = (release.assets || []).find(a => a.name === 'Brother-Matrizes-v1.10.4-ASSINADO.apk');
  check(apk && apk.state === 'uploaded' && apk.digest === 'sha256:' + APK_SHA256, 'APK público não confere com manifesto');

  const raw = process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  check(typeof raw === 'string' && raw.length > 100, 'Credencial Firebase indisponível');
  const svc = JSON.parse(raw);
  check(svc.type === 'service_account' && svc.project_id === PROJECT, 'Projeto Firebase incorreto');
  const app = initializeApp({credential:cert(svc),projectId:PROJECT});
  const message = {
    topic:TOPIC,
    data: {type:'update',versionCode:String(CODE),versionName:VERSION,title,body},
    android: {priority:'high',ttl:604800000}
  };
  await getMessaging(app).send(message,true);
  console.log('Simulação FCM validada. Ainda sem entrega.');
  // Autorização explícita para esta campanha, versão, APK e único tópico.
  await getMessaging(app).send(message,false);
  console.log('AVISO OFICIAL 1.10.4 ACEITO PELO FCM para o tópico opt-in de atualizações. Entrega individual não garantida; não repetir.');
}
main().catch(err=>{
  console.error('Aviso não confirmado: ' + String(err?.code || err?.message || 'erro').slice(0,230));
  process.exitCode=1;
});
