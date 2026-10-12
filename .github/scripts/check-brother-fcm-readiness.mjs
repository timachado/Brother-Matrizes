import { cert, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

// Pré-validação: Firebase Admin valida credenciais e payload sem entregar notificações.
const project = 'brother-matrizes---ti-machado';

async function main() {
  if (process.env.GITHUB_REPOSITORY !== 'timachado/Brother-Matrizes') {
    throw new Error('Repositório diferente do oficial; validação cancelada.');
  }

  const raw = process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error('Falta configurar BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON nos Secrets do environment/repositório.');
  }

  let credential;
  try {
    credential = JSON.parse(raw);
  } catch {
    throw new Error('A credencial Firebase cadastrada não é um JSON válido.');
  }
  if (credential.type !== 'service_account' || credential.project_id !== project ||
      typeof credential.client_email !== 'string' ||
      typeof credential.private_key !== 'string') {
    throw new Error('Credencial não corresponde a uma conta de serviço do projeto Brother Matrizes.');
  }

  const app = initializeApp({ credential: cert(credential), projectId: project });
  const message = {
    topic: 'brother_matrizes_community',
    data: {
      type: 'community',
      eventId: 'preflight_1_10_4',
      title: 'Pré-validação Brother Matrizes',
      body: 'Verificação interna, sem entrega a usuários.'
    },
    android: { priority: 'normal', ttl: 60000 }
  };
  // dryRun=true: o FCM valida autenticação e payload, mas não envia aos assinantes.
  await getMessaging(app).send(message, true);
  console.log('FCM: credencial, autenticação e dry-run aprovados. Nenhum push real enviado.');
}

main().catch(error => {
  // Evita imprimir a credencial ou stack com variáveis ambientais.
  const msg = String(error?.message ?? 'Falha na conexão Firebase').slice(0, 300);
  console.error('FCM readiness falhou: ' + msg);
  process.exitCode = 1;
});
