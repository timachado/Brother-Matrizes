import {readFileSync} from 'node:fs';
import {cert, initializeApp} from 'firebase-admin/app';
import {getMessaging} from 'firebase-admin/messaging';

const prefix='FCM DRY-RUN';
async function main() {
  const manifest=JSON.parse(readFileSync('updates/stable.json','utf8'));
  if(manifest.channel!=='stable'||manifest.applicationId!=='com.timachado.brothermatrizes')
    throw new Error('Manifesto da versão estável incorreto.');
  const json=process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  if(!json)throw new Error('Segredo Firebase não configurado no GitHub Actions.');
  let auth;
  try{auth=JSON.parse(json)}catch{throw new Error('Credencial Firebase contém JSON inválido.');}
  if(auth.type!=='service_account'||auth.project_id!=='brother-matrizes---ti-machado'||
      typeof auth.private_key!=='string'||typeof auth.client_email!=='string')
    throw new Error('Conta de serviço do projeto Brother Matrizes inválida.');
  const app=initializeApp({credential:cert(auth),projectId:'brother-matrizes---ti-machado'});
  const msg={
    topic:'brother_matrizes_updates_stable',
    data:{
      type:'update',
      versionCode:String(manifest.versionCode),
      versionName:String(manifest.versionName),
      title:'Brother Matrizes — teste seguro',
      body:'Validação de envio; mensagem não deve ser entregue.'
    },
    android:{priority:'normal'}
  };
  // true = validar no FCM SEM enviar a mensagem
  await getMessaging(app).send(msg,true);
  console.log(prefix+': autenticação, autorização e payload validados. NENHUM envio realizado.');
}
main().catch(err=>{
  console.error(prefix+': '+String(err?.code||err?.message||'falha de validação').slice(0,220));
  process.exitCode=1;
});
