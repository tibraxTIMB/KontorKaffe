import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import assert from 'node:assert/strict';
const source=readFileSync(new URL('../supabase/functions/kontorkaffe/index.ts',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'');
let handler,verified=false,rpcCalls=0,deleted=0;
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
