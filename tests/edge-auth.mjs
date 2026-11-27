import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import assert from 'node:assert/strict';
const source=readFileSync(new URL('../supabase/functions/kontorkaffe/index.ts',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'');
let handler,verified=false,rpcCalls=0,deleted=0,watchHash='',watchMode='open',watchFail=false;
const factor='11111111-1111-4111-8111-111111111111';
const origin='https://kontorkaffe.github.io';
const token=level=>'mock.'+Buffer.from(JSON.stringify({aal:level})).toString('base64url')+'.mock';
const user=()=>({id:'mock-admin',app_metadata:{kontorkaffe_admin:true},factors:verified?[{id:factor,status:'verified',factor_type:'totp'}]:[]});
const json=(v,status=200)=>new Response(JSON.stringify(v),{status,headers:{'Content-Type':'application/json'}});
const mockFetch=async (url,opts={})=>{
 const u=new URL(url),path=u.pathname,b=opts.body?JSON.parse(opts.body):{};
 if(path==='/rest/v1/kk_host_config')return json([{origin,vapid_public:'mock',vapid_private:'mock'}]);
 if(path==='/auth/v1/user')return [token('aal1'),token('aal2')].includes(opts.headers.Authorization?.slice(7))?json(user()):json({},401);
 if(path==='/auth/v1/token')return json({access_token:token('aal1'),expires_in:3600});
 if(path==='/auth/v1/factors'&&opts.method==='POST'){assert.equal(b.factor_type,'totp');return json({id:factor,totp:{uri:'otpauth://mock',secret:'MOCK'}});}
 if(path==='/auth/v1/factors/'+factor+'/challenge')return json({id:'challenge'});
 if(path==='/auth/v1/factors/'+factor+'/verify'){assert.equal(b.challenge_id,'challenge');if(b.code!=='123456')return json({},400);verified=true;return json({access_token:token('aal2'),expires_in:3600});}
 if(path==='/rest/v1/kk_settings'){assert.equal(u.searchParams.get('select'),'mode');return json([{mode:watchMode}]);}
 if(path==='/rest/v1/kk_watch_sessions'){
  if(watchFail)return json({code:'PGRST204'},400);
  if(opts.method==='POST'){assert.equal(u.searchParams.get('on_conflict'),'slot');assert.equal(b.slot,1);assert.match(b.session_hash,/^[a-f0-9]{64}$/);assert.ok(Date.parse(b.expires_at)>Date.now());assert.match(opts.headers.Prefer,/resolution=merge-duplicates/);watchHash=b.session_hash;return new Response(null,{status:201});}
  const match=u.searchParams.get('session_hash')==='eq.'+watchHash&&!!watchHash;
  if(opts.method==='DELETE'){if(match)watchHash='';return new Response(null,{status:204});}
  assert.equal(u.searchParams.get('select'),'slot');assert.match(u.searchParams.get('expires_at'),/^gt\./);return json(match?[{slot:1}]:[]);
 }
 if(path==='/rest/v1/kk_orders'){assert.equal(u.searchParams.get('status'),'eq.pending');assert.equal(u.searchParams.get('select'),'id');return json([{id:'mock-order'}]);}
 if(path==='/rest/v1/rpc/kk_action'){rpcCalls++;return json({ok:true});}
 if(path==='/rest/v1/kk_push'&&opts.method==='DELETE'){deleted++;assert.equal(u.searchParams.get('endpoint'),'eq.https://fcm.googleapis.com/mock');return json({});}
 if(path==='/auth/v1/logout')return json({});
 throw Error('unexpected mock URL '+path);
};
const deno={env:{get:k=>({SUPABASE_URL:'https://mock.invalid',SUPABASE_SERVICE_ROLE_KEY:'mock',SUPABASE_ANON_KEY:'mock'})[k]},serve:fn=>{handler=fn;}};
new Function('Deno','fetch','QRCode','webpush',stripTypeScriptTypes(source))(deno,mockFetch,{toDataURL:async()=> 'data:image/png;base64,mock'},{});
async function request(action,p={},key='',host=origin){const headers={origin:host,'content-type':'application/json'};if(key)headers.authorization='Bearer '+key;const r=await handler(new Request('https://mock.invalid',{method:'POST',headers,body:JSON.stringify({action,...p})}));return {status:r.status,data:await r.json()};}
assert.equal((await request('admin_state')).status,401);
assert.equal((await request('admin_state',{},token('aal1'),'https://untrusted.invalid')).status,403);
assert.equal((await request('admin_login',{email:'mock@example.invalid',password:'mock'})).data.mfa_required,false);
assert.equal((await request('admin_state',{},token('aal1'))).data.mfa_enabled,false);
const enroll=await request('admin_mfa_enroll',{},token('aal1'));assert.equal(enroll.status,200);assert.equal(enroll.data.factor_id,factor);
assert.equal((await request('admin_mfa_verify',{factor_id:factor,code:'000000'},token('aal1'))).status,400);
assert.equal((await request('admin_mfa_verify',{factor_id:factor,code:'123456'},token('aal1'))).status,200);
assert.equal((await request('admin_login',{email:'mock@example.invalid',password:'mock'})).data.mfa_required,true);
const before=rpcCalls;
for(const action of ['admin_state','admin_mode','admin_issue','admin_delete','admin_rename','admin_push_off','admin_mfa_enroll'])assert.equal((await request(action,{},token('aal1'))).status,428,action);
assert.equal(rpcCalls,before);assert.equal(deleted,0);
assert.equal((await request('admin_mfa_enroll',{},token('aal2'))).status,409);
assert.equal((await request('admin_state',{},token('aal2'))).data.mfa_enabled,true);
assert.equal((await request('admin_push_off',{endpoint:'https://fcm.googleapis.com/mock'},token('aal2'))).status,200);assert.equal(deleted,1);
console.log('PASS: actual edge authorization, TOTP enrollment/verification, AAL2 gate, origin rejection and push removal with mocked Auth and DB');

const native='https://appassets.androidplatform.net';
assert.equal((await request('admin_watch_start',{},'',native)).status,401);
assert.equal((await request('admin_watch_start',{},token('aal1'),native)).status,428);
assert.equal((await request('admin_watch_start',{},token('aal2'),origin)).status,403);
const watch=await request('admin_watch_start',{},token('aal2'),native);assert.equal(watch.status,200);assert.match(watch.data.session,/^[a-f0-9]{64}$/);assert.notEqual(watch.data.session,watchHash);
assert.equal((await request('native_watch_state',{session:watch.data.session},'',origin)).status,403);
assert.equal((await request('native_watch_state',{session:'a'.repeat(64)},'',native)).status,410);
assert.deepEqual((await request('native_watch_state',{session:watch.data.session},'',native)).data,{mode:'open',pending:['mock-order']});
assert.equal((await request('native_watch_stop',{session:watch.data.session},'',native)).status,200);
assert.equal((await request('native_watch_state',{session:watch.data.session},'',native)).status,410);
console.log('PASS: watch session is native-only, admin/MFA-gated, read-only, hashed and revocable');

watchMode='closed';assert.equal((await request('admin_watch_start',{},token('aal2'),native)).status,409);watchMode='open';watchFail=true;assert.equal((await request('admin_watch_start',{},token('aal2'),native)).status,503);watchFail=false;
const newer=await request('admin_watch_start',{},token('aal2'),native);assert.equal(newer.status,200);
const latest=await request('admin_watch_start',{},token('aal2'),native);assert.equal(latest.status,200);
assert.equal((await request('native_watch_state',{session:newer.data.session},'',native)).status,410);
await request('native_watch_stop',{session:newer.data.session},'',native);
assert.equal((await request('native_watch_state',{session:latest.data.session},'',native)).status,200);
watchMode='closed';assert.deepEqual((await request('native_watch_state',{session:latest.data.session},'',native)).data,{mode:'closed',pending:[]});assert.equal(watchHash,'');
console.log('PASS: direct REST slot replacement, stale stop cannot revoke new session, closed bar and upstream 400 handled');
