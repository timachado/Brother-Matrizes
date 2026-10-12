import {readFileSync} from 'node:fs';
import {cert,initializeApp} from 'firebase-admin/app';
import {getMessaging} from 'firebase-admin/messaging';

/**
 * Teste FCM em um aparelho com token previamente salvo no GitHub Secret.
 * A mensagem usa type=community, reconhecido pelo cliente Android 1.10.4+.
 * Nenhuma notificacao eh enviada a topicos ou para outros dispositivos.
 */
async function main() {
  if (process.env.GITHUB_REPOSITORY !== 'timachado/Brother-Matrizes' ||
      process.env.GITHUB_EVENT_NAME !== 'workflow_dispatch') {
    throw Error('Somente execucao manual no repositorio oficial');
  }
  const event = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8'));
  const inputs = event.inputs || {};
  const dryRun = String(inputs.dry_run ?? 'true') !== 'false';
  if (String(inputs.confirm || '') !== 'TESTAR-UM-APARELHO') {
    throw Error('Confirmacao explicita obrigatoria: TESTAR-UM-APARELHO');
  }

  const raw = process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  const token = String(process.env.BROTHER_FCM_TEST_DEVICE_TOKEN || '').trim();
  if (!raw || token.length < 60 || token.length > 4096) {
    throw Error('Configure a credencial Firebase e o token de UM aparelho de teste nos GitHub Secrets');
  }
  const sa = JSON.parse(raw);
  if (sa.type !== 'service_account' || sa.project_id !== 'brother-matrizes---ti-machado') {
    throw Error('Conta de servico nao corresponde ao projeto Brother Matrizes');
  }

  const app = initializeApp({
    credential: cert(sa),
    projectId: 'brother-matrizes---ti-machado'
  });
  const runId = String(process.env.GITHUB_RUN_ID || 'manual').replace(/[^0-9A-Za-z]/g, '');
  const attempt = String(process.env.GITHUB_RUN_ATTEMPT || '1').replace(/[^0-9A-Za-z]/g, '');
  const eventId = ('qa_' + runId + '_' + attempt).slice(0, 64);
  const msg = {
    token,
    data: {
      type: 'community',
      eventId,
      title: 'Brother Matrizes: teste de notificacao',
      body: 'Notificacao de teste recebida com sucesso. Nenhuma atualizacao foi publicada.'
    },
    android: {priority: 'normal', ttl: 180000}
  };

  // dryRun=true nao entrega mensagens; false envia somente ao token informado.
  await getMessaging(app).send(msg, dryRun);
  console.log(dryRun
    ? 'Credencial e token de um dispositivo validados, sem envio real.'
    : 'Teste FCM aceito para um unico aparelho; confira se a notificacao chegou.');
}

main().catch(error => {
  // Evitar exibir tokens ou credenciais em logs de Actions.
  console.error('Falha no teste individual: ' +
    String(error?.code || error?.message || 'erro desconhecido').slice(0, 180));
  process.exitCode = 1;
});
