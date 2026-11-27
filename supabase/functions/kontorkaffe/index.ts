import QRCode from 'npm:qrcode@1.5.4';
import webpush from 'npm:web-push@3.6.7';
const env = (name: string) => Deno.env.get(name) || '';
const db = env('SUPABASE_URL');
const secret = env('SUPABASE_SERVICE_ROLE_KEY');
const authKey=env('SUPABASE_ANON_KEY');
const hash = async (s: string) => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s))),x=>x.toString(16).padStart(2,'0')).join('');
const random = () => Array.from(crypto.getRandomValues(new Uint8Array(32)),x=>x.toString(16).padStart(2,'0')).join('');
const keyValid = (s: unknown): s is string => typeof s==='string' && /^[a-f0-9]{64}$/.test(s);
const same = async (a: string,b: string) => { const x=await hash(a), y=await hash(b); let d=0; for(let i=0;i<x.length;i++)d|=x.charCodeAt(i)^y.charCodeAt(i);return d===0; };
const nativeOrigin='https://appassets.androidplatform.net';
const dedicatedOrigin='https://kontorkaffe.github.io';
const baseHeaders={'Access-Control-Allow-Headers':'content-type, authorization','Access-Control-Allow-Methods':'POST, OPTIONS','Content-Type':'application/json','Cache-Control':'no-store','Vary':'Origin'};

const restHeaders={apikey:secret,Authorization:'Bearer '+secret,'Content-Type':'application/json'};
const allowed=new Set(['admin_configure','admin_login','admin_logout','state','activate','order','accept','cancel','admin_state','admin_rename','admin_delete','admin_reset_browser','admin_mode','admin_issue','admin_approve','admin_renew','admin_block','admin_order','admin_push','admin_push_test']);
type HostConfig={origin:string,vapid_public:string,vapid_private:string};
function validOrigin(raw:string){try{const u=new URL(raw);return u.origin===raw&&u.protocol==='https:'&&!u.port&&!u.username&&!u.password&&(/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.(?:netlify\.app|pages\.dev|vercel\.app)$/.test(u.hostname)||/^kontorkaffe(?:-[a-z0-9]+)*\.github\.io$/.test(u.hostname));}catch{return false;}}
async function readConfig():Promise<HostConfig|null>{
 const r=await fetch(db+'/rest/v1/kk_host_config?id=eq.1&select=origin,vapid_public,vapid_private',{headers:restHeaders});
 if(!r.ok)throw Error('Host config unavailable');
 const rows=await r.json();return rows[0]||null;
}
async function notify(config:HostConfig|null){
 if(!config)return;
 webpush.setVapidDetails(config.origin,config.vapid_public,config.vapid_private);
 const response=await fetch(db+'/rest/v1/kk_push?select=subscription,endpoint',{headers:restHeaders});
 if(!response.ok)return;
 await Promise.all((await response.json()).map(async (row:{endpoint:string,subscription:any})=>{
  try {await webpush.sendNotification(row.subscription,JSON.stringify({title:'KontorKaffe',body:'Ny kaffibestilling. Opna baristapanelet.'}),{TTL:300,timeout:5000});}
  catch(e:any){if([404,410].includes(e.statusCode))await fetch(db+'/rest/v1/kk_push?endpoint=eq.'+encodeURIComponent(row.endpoint),{method:'DELETE',headers:restHeaders});}
 }));
}
Deno.serve(async req=>{
 const requestOrigin=req.headers.get('origin')||'';
 const native=requestOrigin===nativeOrigin;
 let config:HostConfig|null=null;
 if(native||validOrigin(requestOrigin)){try{config=await readConfig();}catch{const h={...baseHeaders,...(native?{'Access-Control-Allow-Origin':nativeOrigin}:{})};return new Response(JSON.stringify({error:'Servertilkopling manglar'}),{status:503,headers:h});}}
 const site=config?.origin||'';
 const siteReady=!!config&&validOrigin(site);
 // Permit only the dedicated origin to bootstrap administrator login before configuration.
 const bootstrap=!config&&requestOrigin===dedicatedOrigin;
 const trusted=native||bootstrap||(siteReady&&requestOrigin===site);
 const headers={...baseHeaders,...(trusted?{'Access-Control-Allow-Origin':requestOrigin}:{})};
 const reply=(v:unknown,status=200)=>new Response(JSON.stringify(v),{status,headers});
 if(!trusted)return reply({error:'Ikkje tillate'},403);
 if(req.method==='OPTIONS')return new Response(null,{status:204,headers});
 if(req.method!=='POST')return reply({error:'Ikkje tillate'},405);
 if(!secret||!authKey)return reply({error:'Servertilkopling manglar'},503);
 try{
  const raw=await req.text(); if(raw.length>12000)return reply({error:'For stor førespurnad'},413);
  const {action,...p}=JSON.parse(raw); if(!allowed.has(action))return reply({error:'Ukjend handling'},400);
  if((native||bootstrap)&&!action.startsWith('admin_'))return reply({error:'Ikkje tillate'},403);
  const authHeaders={apikey:authKey,'Content-Type':'application/json'};
  const checkAdmin=async(token:string)=>{
   if(!token||token.length>8000)return false;
   const response=await fetch(db+'/auth/v1/user',{headers:{...authHeaders,Authorization:'Bearer '+token}});
   if(!response.ok)return false;
   const user=await response.json();
   return user.app_metadata?.kontorkaffe_admin===true && !!user.id;
  };
  if(action==='admin_login'){
   if(typeof p.email!=='string'||typeof p.password!=='string'||p.email.length>254||p.password.length>256)return reply({error:'Feil e-post eller passord'},401);
   const response=await fetch(db+'/auth/v1/token?grant_type=password',{method:'POST',headers:authHeaders,body:JSON.stringify({email:p.email,password:p.password})});
   if(!response.ok)return reply({error:'Feil e-post eller passord'},response.status===429?429:401);
   const session=await response.json();
   if(!await checkAdmin(session.access_token))return reply({error:'Ingen administratortilgang'},403);
   return reply({access_token:session.access_token,expires_in:session.expires_in});
  }
  let qrToken='';
  if(action.startsWith('admin_')){
   const auth=req.headers.get('authorization')||'';
   const token=auth.startsWith('Bearer ')?auth.slice(7):'';
   if(!await checkAdmin(token))return reply({error:'Logg inn på nytt med administratorkontoen'},401);
   if(action==='admin_logout'){await fetch(db+'/auth/v1/logout',{method:'POST',headers:{...authHeaders,Authorization:'Bearer '+token}});return reply({ok:true});}
   if(action==='admin_configure'){
    if(config)return reply({error:'Medlemsadressa er låst for å verna eksisterande QR-kort.'},409);
    const proposed=typeof p.origin==='string'?p.origin.trim().replace(/\/$/,''):'';
    if(bootstrap&&proposed!==dedicatedOrigin)return reply({error:'Bruk den faste KontorKaffe-adressa.'},400);
    if(!validOrigin(proposed))return reply({error:'Bruk eit eige KontorKaffe GitHub Pages-domene eller ei separat HTTPS-adresse frå Netlify, Cloudflare Pages eller Vercel.'},400);
    let verified=false;
    try{const r=await fetch(proposed+'/kontorkaffe-verify.txt',{redirect:'error',signal:AbortSignal.timeout(5000)});verified=r.ok&&(await r.text()).trim()==='kontorkaffe-rpoziaylvjhfqitfcbhm-v1';}catch{}
    if(!verified)return reply({error:'Klarte ikkje stadfesta at adressa har den rette KontorKaffe-nettsida.'},400);
    const keys=webpush.generateVAPIDKeys();
    const r=await fetch(db+'/rest/v1/kk_host_config',{method:'POST',headers:restHeaders,body:JSON.stringify({id:1,origin:proposed,vapid_public:keys.publicKey,vapid_private:keys.privateKey})});
    if(!r.ok)return reply({error:'Adressa kunne ikkje lagrast, eller er allereie lagra.'},409);
    return reply({ok:true,origin:proposed});
   }
   if(!siteReady&&(action==='admin_issue'||(action==='admin_mode'&&p.mode!=='closed')||action==='admin_push'||action==='admin_push_test'))return reply({error:'Medlemsnettsida må publiserast på eiga adresse først.'},409);
   if(action==='admin_push_test'){
    if(!config||typeof p.endpoint!=='string'||p.endpoint.length>3000)return reply({error:'Slå på telefonvarsling først.'},400);
    const r=await fetch(db+'/rest/v1/kk_push?select=subscription,endpoint&endpoint=eq.'+encodeURIComponent(p.endpoint),{headers:restHeaders});
    if(!r.ok)return reply({error:'Klarte ikkje kontrollera varslingsoppsettet.'},502);
    const row=(await r.json())[0];
    if(!row)return reply({error:'Telefonen er ikkje registrert. Slå på varsling på nytt.'},409);
    webpush.setVapidDetails(config.origin,config.vapid_public,config.vapid_private);
    try{
     await webpush.sendNotification(row.subscription,JSON.stringify({title:'KontorKaffe',body:'Testvarsel frå kaffibaren. Varsling er sett opp.'}),{TTL:120,timeout:5000});
     return reply({ok:true});
    }catch(e:any){
     if([404,410].includes(e.statusCode)){
      await fetch(db+'/rest/v1/kk_push?endpoint=eq.'+encodeURIComponent(row.endpoint),{method:'DELETE',headers:restHeaders});
      return reply({error:'Varslinga er utløpt. Slå på varsling på nytt.'},409);
     }
     return reply({error:'Varslingstenesta tok ikkje imot testvarselet.'},502);
    }
   }
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
  if(action==='admin_state'){data.vapid=config?.vapid_public||'';data.site_ready=siteReady;data.site_origin=site;}
  if(data.notify) { try{await notify(config);}catch{} delete data.notify; }
  return reply(data);
 }catch{return reply({error:'Tenesta kunne ikkje handsama førespurnaden'},400);}
});