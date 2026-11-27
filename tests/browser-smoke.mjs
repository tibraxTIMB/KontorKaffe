import { chromium } from 'playwright';
import assert from 'node:assert/strict';
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:360,height:780},serviceWorkers:'block'});
const page=await context.newPage();
const failures=[];
page.on('pageerror',e=>failures.push(e.message));
const base='http://127.0.0.1:8765';
const api='https://rpoziaylvjhfqitfcbhm.supabase.co/functions/v1/kontorkaffe';
const card='a'.repeat(64);
let approved=false,activation='required',order=null,credits=3,mode='open',siteReady=false,failLogin=true,seen=[],special=null;
const member={id:1,name:'Testmedlem',active:true,linked:true,credits:3,pending:false};
const headers={'Access-Control-Allow-Origin':base,'Access-Control-Allow-Headers':'content-type,authorization','Access-Control-Allow-Methods':'POST,OPTIONS'};
await context.route(api,async route=>{
 const req=route.request();
 if(req.method()==='OPTIONS')return route.fulfill({status:204,headers});
 const p=req.postDataJSON();seen.push(p.action);
 let data,status=200;
 const customer=()=>({activation:approved?'approved':activation,activation_code:'abc123',name:'Testmedlem',credits,mode,order,special});
 const admin=()=>({site_ready:siteReady,site_origin:siteReady?'https://kontorkaffe.github.io':'',vapid:'',mode,special,members:[{...member,credits}],orders:order&&['pending','proposed','accepted','preparing','ready'].includes(order.status)?[{...order,name:'Testmedlem'}]:[]});
 switch(p.action){
 case 'state':assert.equal(p.card,card);assert.match(p.browser,/^[a-f0-9]{64}$/);data=customer();break;
 case 'activate':activation='pending';data=customer();break;
 case 'order':assert.ok(['Espresso','Dagens spesial'].includes(p.drink));assert.ok(p.request_id);if(p.drink==='Dagens spesial')assert.equal(p.special_revision,special.revision);credits--;order={id:'order-1',drink:p.drink,pickup:p.pickup,status:'pending',special_title:p.drink==='Dagens spesial'?special.title:null};data=customer();break;
 case 'cancel':credits++;order={...order,status:'cancelled'};data=customer();break;
 case 'admin_login':if(failLogin){status=401;data={error:'Feil e-post eller passord'};}else data={access_token:'mock-token'};break;
 case 'admin_state':assert.equal(req.headers().authorization,'Bearer mock-token');data=admin();break;
 case 'admin_configure':assert.equal(p.origin,'https://kontorkaffe.github.io');siteReady=true;data={ok:true};break;
 case 'admin_special':special=p.title?{title:p.title,detail:p.detail,revision:1}:null;data={ok:true};break;
 case 'admin_mode':mode=p.mode;data={ok:true};break;
 case 'admin_order':assert.equal(p.status,'no_show');order={...order,status:'no_show'};data={ok:true};break;
 case 'admin_logout':data={ok:true};break;
 default:throw Error('unexpected '+p.action);
 }
 await route.fulfill({status,headers,contentType:'application/json',body:JSON.stringify(data)});
});
async function visible(selector){await page.locator(selector).waitFor({state:'visible'});}
async function hidden(selector){await page.locator(selector).waitFor({state:'hidden'});}
async function contains(selector,part){await page.locator(selector).filter({hasText:part}).waitFor({state:'visible',timeout:10000});}
async function widthOK(){assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'horizontal overflow');}
try{
 await page.goto(base+'/');await contains('#message','Berre for inviterte');await hidden('#member');assert.ok(await page.locator('.hero-art').evaluate(i=>i.complete&&i.naturalWidth>0));assert.equal(seen.length,0);await widthOK();
 await page.goto(base+'/#'+card);await visible('#activation');await page.locator('#activate').click();await contains('#message','abc123');
 approved=true;await page.reload();await visible('#form');assert.equal(await page.locator('#menu button').count(),9);assert.ok(await page.locator('#specialChoice').isDisabled());assert.equal(await page.locator('input[type=email],input[type=password]').count(),0);await widthOK();
 await page.locator('#menu button').first().click();await page.locator('#send').click();await contains('#order','Bestillinga er motteken');await contains('#clips','2 / 3');await hidden('#form');
 await page.reload();await contains('#order','Bestillinga er motteken');await page.getByRole('button',{name:'Avbestill'}).click();await contains('#clips','3 / 3');await visible('#form');
 await page.goto(base+'/admin/');await visible('#login');assert.ok(await page.locator('.login-art img').evaluate(i=>i.complete&&i.naturalWidth>0));await page.locator('#email').fill('test@example.invalid');await page.locator('#password').fill('mock-password');await page.locator('#loginButton').click();await contains('#err','Feil e-post');await hidden('#dashboard');
 failLogin=false;await page.locator('#password').fill('mock-password');await page.locator('#loginButton').click();await visible('#dashboard');assert.ok(await page.locator('#open').isDisabled());await widthOK();
 await page.locator('[data-tab=settingsPane]').click();await visible('#hostSetup');await page.locator('#hostOrigin').fill('https://kontorkaffe.github.io');await page.locator('#saveHost').click();await visible('#hostReady');await hidden('#hostSetup');
 await page.locator('#closed').click();await contains('#mode','Stengt');await page.locator('#open').click();await contains('#mode','Ope');
 await page.locator('[data-tab=ordersPane]').click();order={id:'order-ready',drink:'Latte',pickup:new Date(Date.now()-60000).toISOString(),status:'ready'};await page.locator('#refreshNow').click();await contains('#orders','Latte');page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'Ikkje henta'}).click();await contains('#orders','Ingen aktive bestillingar');await contains('#stockCredits','3');await contains('#stockNeed','3');await page.locator('.special-editor summary').click();await page.locator('#specialTitle').fill('Honninglatte');await page.locator('#specialDetail').fill('Mjølk og honning');await page.locator('#saveSpecial').click();await contains('#specialStatus','Honninglatte');await page.locator('[data-tab=membersPane]').click();await contains('#members','Testmedlem');await widthOK();
 await page.setViewportSize({width:1280,height:800});await widthOK();await page.locator('#logout').click();await visible('#login');await hidden('#dashboard');await page.goto(base+'/#'+card);await visible('#form');await contains('#specialChoice','Honninglatte');await page.locator('#specialChoice').click();await page.locator('#send').click();await contains('#order','Honninglatte');await contains('#clips','2 / 3');await widthOK();
 assert.deepEqual(failures,[]);console.log('PASS: customer invitation, activation, order, rescan, refund, admin login, host setup, mode, tabs, logout, layout at 360px and 1280px (mock API only).');
}finally{await browser.close();}
