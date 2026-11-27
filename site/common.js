import {API} from './config.js';
export const $=id=>document.getElementById(id);
export const el=(tag,text='',cls='')=>{const e=document.createElement(tag);e.textContent=text;e.className=cls;return e;};
export const random=()=>Array.from(crypto.getRandomValues(new Uint8Array(32)),x=>x.toString(16).padStart(2,'0')).join('');
export async function api(action,p={},key=''){
 if(!API)throw Error('KontorKaffe er ikkje kopla til databasen enno.');
 const headers={'Content-Type':'application/json'};if(key)headers.Authorization='Bearer '+key;
 const r=await fetch(API,{method:'POST',headers,body:JSON.stringify({action,...p}),cache:'no-store',referrerPolicy:'no-referrer'});
 const d=await r.json();if(!r.ok||d.error){const e=Error(d.error||'Nettverksfeil');e.status=r.status;throw e;}return d;
}
export const time=t=>new Date(t).toLocaleString('nn-NO',{hour:'2-digit',minute:'2-digit',day:'numeric',month:'short'});
export const dateInput=(t=new Date(Date.now()+10*60000))=>{const d=new Date(t);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
export const labels={pending:'Bestilling motteken – ventar på svar',proposed:'Nytt tidspunkt foreslått',accepted:'Godkjend',preparing:'Kaffien vert laga',expired:'Hentetida passerte – klippet tilbake',no_show:'Ikkje henta',ready:'Kaffien er klar!',collected:'Henta',rejected:'Avslått – klippet tilbake',cancelled:'Avbroten – klippet tilbake'};
export const button=(label,fn,cls='')=>{const b=el('button',label,cls);b.type='button';b.onclick=fn;return b;};