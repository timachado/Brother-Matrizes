// Node v24 native test runner; no real Cloudflare or WordPress access needed.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import worker from './worker.js';

const secret = 'local-test-only-32-characters-minimum-DO-NOT-DEPLOY';
const url = 'https://example.workers.dev';
const payload = {revision: 1000000000001, plans: [
  {code:'free',name:'Gratuito',price_cents:0,currency:'BRL',active:true},
  {code:'pro_monthly',name:'Mensal',price_cents:2990,currency:'BRL',active:false}
]};
class FakeDb {
  constructor(){this.row = null;}
  prepare(sql){
    const db=this;
    return {
      bind(revision, value, now) {
        return {async run(){
          if (db.row && revision <= db.row.revision) return {meta:{changes:0}};
          db.row={revision, payload:value,synced_at:now};
          return {meta:{changes:1}};
        }};
      },
      async first(){return db.row;}
    };
  }
}
function signedPost(input, {timestamp=Math.floor(Date.now()/1000), key=secret}={}){
  const body=JSON.stringify(input);
  const sig=createHmac('sha256',key).update(timestamp+'.'+body).digest('hex');
  return new Request(url+'/internal/catalog-sync', {
    method:'POST',headers:{'x-bm-timestamp':String(timestamp),'x-bm-signature':'sha256='+sig,'content-type':'application/json'},body
  });
}
async function read(r){return {code:r.status,data:await r.json()};}

test('health never claims a sale is ready', async()=>{
  const r=await read(await worker.fetch(new Request(url+'/health'),{}));
  assert.equal(r.code,200);assert.equal(r.data.ready_for_sales,false);
});
test('commercial entitlements always fail closed',async()=>{
  const r=await read(await worker.fetch(new Request(url+'/wp-json/brother-matrizes/v1/me'),{DB:new FakeDb()}));
  assert.equal(r.code,503);assert.equal(r.data.code,'commercial_api_not_enabled');
});
test('missing secret disables sync',async()=>{
  const r=await read(await worker.fetch(signedPost(payload),{DB:new FakeDb()}));
  assert.equal(r.code,503);assert.equal(r.data.code,'not_configured');
});
test('unsigned sync rejected',async()=>{
  const r=await read(await worker.fetch(new Request(url+'/internal/catalog-sync',{method:'POST',body:JSON.stringify(payload)}),{DB:new FakeDb(),CATALOG_SYNC_SECRET:secret}));
  assert.equal(r.code,401);
});
test('signed WordPress catalog saved to D1 and queried',async()=>{
  const env={DB:new FakeDb(),CATALOG_SYNC_SECRET:secret};
  const post=await read(await worker.fetch(signedPost(payload),env));
  assert.equal(post.code,200);assert.equal(post.data.revision,payload.revision);
  const get=await read(await worker.fetch(new Request(url+'/wp-json/brother-matrizes/v1/plans'),env));
  assert.equal(get.code,200);assert.deepEqual(get.data.plans,payload.plans);
});
test('replaying a revision fails',async()=>{
  const env={DB:new FakeDb(),CATALOG_SYNC_SECRET:secret};
  assert.equal((await worker.fetch(signedPost(payload),env)).status,200);
  const again=await read(await worker.fetch(signedPost(payload),env));
  assert.equal(again.code,409);assert.equal(again.data.code,'stale_revision');
});
test('timestamp more than 5 minutes old rejected',async()=>{
  const env={DB:new FakeDb(),CATALOG_SYNC_SECRET:secret};
  const r=await read(await worker.fetch(signedPost(payload,{timestamp:Math.floor(Date.now()/1000)-301}),env));
  assert.equal(r.code,401);
});
test('wrong HMAC rejected',async()=>{
  const env={DB:new FakeDb(),CATALOG_SYNC_SECRET:secret};
  const r=await read(await worker.fetch(signedPost(payload,{key:secret+'wrong'}),env));
  assert.equal(r.code,401);
});
test('paid active plan without price rejected',async()=>{
  const env={DB:new FakeDb(),CATALOG_SYNC_SECRET:secret};
  const p={revision:1000000000002,plans:[{code:'pro_monthly',name:'Mensal',price_cents:null,currency:'BRL',active:true}]};
  const r=await read(await worker.fetch(signedPost(p),env));
  assert.equal(r.code,422);
});
test('catalog expires and returns 503',async()=>{
  const db=new FakeDb();
  db.row={revision:1000000000002,payload:JSON.stringify({plans:payload.plans}),synced_at:Math.floor(Date.now()/1000)-601};
  const r=await read(await worker.fetch(new Request(url+'/wp-json/brother-matrizes/v1/plans'),{DB:db}));
  assert.equal(r.code,503);assert.equal(r.data.code,'catalog_not_synchronized');
});
test('no catalog yet returns 503',async()=>{
  const r=await read(await worker.fetch(new Request(url+'/wp-json/brother-matrizes/v1/plans'),{DB:new FakeDb()}));
  assert.equal(r.code,503);
});
test('invalid/duplicate plan codes rejected',async()=>{
  const env={DB:new FakeDb(),CATALOG_SYNC_SECRET:secret};
  const p={revision:1000000000002,plans:[payload.plans[0],payload.plans[0]]};
  const r=await read(await worker.fetch(signedPost(p),env));
  assert.equal(r.code,422);
});
