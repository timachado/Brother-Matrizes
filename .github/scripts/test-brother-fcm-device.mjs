import {readFileSync} from 'node:fs';
import {cert,initializeApp} from 'firebase-admin/app';
import {getMessaging} from 'firebase-admin/messaging';

/** Teste restrito a UM token de aparelho, sem alterar manifesto ou disparar para tópicos. */
async function main(){
  if(process.env.GITHUB_EVENT_NAME!=='workflow_dispatch') throw Error('Somente execução manual');
  const event=JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH,'utf8'));
  const dryRun=String(event.inputs?.dry_run ?? 'true')==='true';
  const raw=process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  const token=String(process.env.BROTHER_FCM_TEST_DEVICE_TOKEN||'').trim();
  if(!raw||!token||token.length<60||token.length>4096)throw Error('Configure credencial Firebase e token de teste no GitHub Secrets');
  const sa=JSON.parse(raw);
  if(sa.type!=='service_account'||sa.project_id!=='brother-matrizes---ti-machado')
    throw Error('Conta de serviço não corresponde ao projeto Brother Matrizes');
  const app=initializeApp({credential:cert(sa),projectId:'brother-matrizes---ti-machado'});
  const msg={
    token,
    data:{type:'diagnostic',testId:'brother-push-qa-v1'},
    android:{priority:'high',ttl:180000}
  };
  // A verificação dryRun não entrega mensagens, independentemente dos segredos.
  await getMessaging(app).send(msg,dryRun);
  console.log(dryRun
    ? 'Validação de token Firebase aprovada, nenhuma notificação enviada.'
    : 'Firebase aceitou notificação de diagnóstico para um único aparelho.');
}
main().catch(error=>{
  console.error('Falha no teste FCM individual: '+String(error?.code||error?.message||'erro desconhecido').slice(0,200));
  process.exitCode=1;
});
