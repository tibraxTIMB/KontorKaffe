import QRCode from 'npm:qrcode@1.5.4';
import webpush from 'npm:web-push@3.6.7';
const env = (name: string) => Deno.env.get(name) || '';
const origin = env('KK_SITE_ORIGIN');
const site = env('KK_SITE_URL').replace(/\/$/,'');
const db = env('SUPABASE_URL');
const secret = env('SUPABASE_SERVICE_ROLE_KEY');
const admin = env('KK_ADMIN_KEY');
const hash = async (s: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))),x=>x.toString(16).padStart(2,'0')).join('');
const random = () => Array.from(crypto.getRandomValues(new Uint8Array(32)),x=>x.toString(16).padStart(2,'0')).join('');
const keyValid = (s: unknown): s is string => typeof s==='string' && /^[a-f0-9]{64}$/.test(s);
const same = async (a: string,b: string) => { const x=await hash(a), y=await hash(b); let d=0; for(let i=0;i<x.length;i++)d|=x.charCodeAt(i)^y.charCodeAt(i);return d===0; };
const nativeOrigin='https://appassets.androidplatform.net';
const siteReady=(()=>{try{const u=new URL(site);return u.protocol==='https:'&&u.origin===origin&&site===origin&&!u.hostname.endsWith('.github.io')&&u.hostname!=='github.io';}catch{return false;}})();
const baseHeaders={'Access-Control-Allow-Headers':'content-type, authorization','Access-Control-Allow-Methods':'POST, OPTIONS','Content-Type':'application/json','Cache-Control':'no-store','Vary':'Origin'};

const restHeaders={apikey:secret,Authorization:'Bearer '+secret,'Content-Type':'application/json'};
const allowed=new Set(['state','activate','order','accept','cancel','admin_state','admin_mode','admin_issue','admin_approve','admin_renew','admin_block','admin_order','admin_push']);
async function notify(){
 if(!env('KK_VAPID_PUBLIC')||!env('KK_VAPID_PRIVATE'))return;
 webpush.setVapidDetails(env('KK_VAPID_SUBJECT'),env('KK_VAPID_PUBLIC'),env('KK_VAPID_PRIVATE'));
 const response=await fetch(db+'/rest/v1/kk_push?select=subscription,endpoint',{headers:restHeaders});
 if(!response.ok)return;
 await Promise.all((await response.json()).map(async (row:{endpoint:string,subscription:any})=>{
  try {await webpush.sendNotification(row.subscription,JSON.stringify({title:'KontorKaffe',body:'Ny kaffibestilling. Opne masterappen.'}),{TTL:300,timeout:5000});}
  catch(e:any){if([404,410].includes(e.statusCode))await fetch(db+'/rest/v1/kk_push?endpoint=eq.'+encodeURIComponent(row.endpoint),{method:'DELETE',headers:restHeaders});}
 }));
}
Deno.serve(async req=>{
 const requestOrigin=req.headers.get('origin')||'';
 const native=requestOrigin===nativeOrigin;
 const trusted=native||(siteReady&&requestOrigin===origin);
 const headers={...baseHeaders,...(trusted?{'Access-Control-Allow-Origin':requestOrigin}:{})};
 const reply=(v:unknown,status=200)=>new Response(JSON.stringify(v),{status,headers});
 if(!trusted)return reply({error:'Ikkje tillate'},403);
 if(req.method==='OPTIONS')return new Response(null,{status:204,headers});
 if(req.method!=='POST')return reply({error:'Ikkje tillate'},405);
 if(!secret||!keyValid(admin))return reply({error:'Administrasjonsnøkkel må konfigurerast i KontorKaffe sin Supabase først.'},503);
 try{
  const raw=await req.text(); if(raw.length>12000)return reply({error:'For stor førespurnad'},413);
  const {action,...p}=JSON.parse(raw); if(!allowed.has(action))return reply({error:'Ukjend handling'},400);
  if(native&&!action.startsWith('admin_'))return reply({error:'Ikkje tillate'},403);
  let qrToken='';
  if(action.startsWith('admin_')){
   const auth=req.headers.get('authorization')||'';
   if(!await same(auth,'Bearer '+admin))return reply({error:'Feil administratortilgang'},401);
   if(!siteReady&&(action==='admin_issue'||(action==='admin_mode'&&p.mode!=='closed')||action==='admin_push'))return reply({error:'Medlemsnettsida må publiserast på eiga adresse først.'},409);
   if(action==='admin_issue'){qrToken=random();p.qr_hash=await hash(qrToken);}
   if(action==='admin_push'){
    const sub=p.subscription; const u=new URL(sub.endpoint);
    if(u.protocol!=='https:'||!['fcm.googleapis.com','updates.push.services.mozilla.com','web.push.apple.com','wns.notify.windows.com'].some(h=>u.hostname===h||u.hostname.endsWith('.'+h))||!sub.keys?.p256dh||!sub.keys?.auth)return reply({error:'Varslingstenesta er ikkje støtta'},400);
   }
  } else {
   if(!keyValid(p.card)||!keyValid(p.browser))return reply({error:'Ugyldig medlemskort'},403);
   p.qr_hash=await hash(p.card);p.browser_hash=await hash(p.browser);delete p.card;delete p.browser;
  }
  const res=await fetch(db+'/rest/v1/rpc/kk_action',{method:'POST',headers:restHeaders,body:JSON.stringify({a:action,p})});
  const data=await res.json();
  if(!res.ok){return reply({error:data.code==='P0001'?data.message:'Handlinga kunne ikkje gjennomførast. Kontroller opplysningane.'},400);}
  if(qrToken){const url=site+'/#'+qrToken;data.url=url;data.qr=await QRCode.toDataURL(url,{margin:4,width:400,errorCorrectionLevel:'M'});}
  if(action==='admin_state'){data.vapid=env('KK_VAPID_PUBLIC');data.site_ready=siteReady;}
  if(data.notify) { try{await notify();}catch{} delete data.notify; }
  return reply(data);
 }catch{return reply({error:'Tenesta kunne ikkje handsama førespurnaden'},400);}
});