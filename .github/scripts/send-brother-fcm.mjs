import {readFileSync} from 'node:fs';
import {cert, initializeApp} from 'firebase-admin/app';
import {getMessaging} from 'firebase-admin/messaging';

const manifestURL='https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json';
const project='brother-matrizes---ti-machado';
const topic='brother_matrizes_updates_stable';

async function main() {
  if(process.env.GITHUB_EVENT_NAME!=='workflow_dispatch') throw Error('Disparo manual obrigatório');
  const event=JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH,'utf8'));
  const input=event.inputs||{};
  const code=Number(input.version_code);
  const version=String(input.version_name||'').trim();
  const title=String(input.title||'').trim();
  const body=String(input.body||'').trim();
  const dryRun=String(input.dry_run)==='true';
  if(!dryRun && code<=172)throw Error('Push real disponível somente para versões futuras 1.0.7 (código 173) ou superiores');
  if(!Number.isSafeInteger(code)||code<1||!/^[0-9]+[.][0-9]+[.][0-9]+$/.test(version))throw Error('Versão inválida');
  if(!title||title.length>90||!body||body.length>240)throw Error('Mensagem fora do limite');
  const response=await fetch(manifestURL,{signal:AbortSignal.timeout(15000)});
  if(!response.ok)throw Error('Manifesto indisponível');
  const data=await response.json();
  if(data.applicationId!=='com.timachado.brothermatrizes'||data.channel!=='stable'||data.versionCode!==code||data.versionName!==version)
    throw Error('Manifesto público não corresponde à release solicitada');
  const url=new URL(data.downloadUrl);
  if(url.hostname!=='timachado.ifree.page'||url.protocol!=='https:'||url.pathname!=='/aplicativos/brother-matrizes/')
    throw Error('Link comercial diferente do endereço oficial');
  if(!/^[a-f0-9]{64}$/i.test(data.sha256))throw Error('Hash inválido');
  const encoded=process.env.BROTHER_FIREBASE_SERVICE_ACCOUNT_JSON;
  if(!encoded)throw Error('Credencial Firebase não cadastrada no GitHub Actions');
  const credential=JSON.parse(encoded);
  if(credential.project_id!==project||credential.type!=='service_account')throw Error('Projeto Firebase incorreto');
  const firebase=initializeApp({credential:cert(credential),projectId:project});
  const message={topic,data:{type:'update',versionCode:String(code),versionName:version,title,body},android:{priority:'high',ttl:604800000}};
  await getMessaging(firebase).send(message,dryRun);
  console.log(dryRun?'Validação de FCM aprovada, sem envio':'FCM aceitou o envio; entrega não garantida');
}
main().catch(e=>{console.error('Erro FCM: '+String(e.message||e));process.exitCode=1});
