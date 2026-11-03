import test from 'node:test';
import assert from 'node:assert/strict';
import {validateLogin,validateStaff,loginHandler,staffHandler} from '../../supabase/functions/_shared/auth.mjs';
const request_id='30000000-0000-4000-8000-000000000001';
test('strict login schema and normalized alias',()=>{
 assert.equal(validateLogin({username:' ＡＮＡ.Test ',password:'long passphrase'}).username,'ana.test');
 for(const extra of ['actor_id','email','url','role'])assert.throws(()=>validateLogin({username:'ana.test',password:'pass',[extra]:'bad'}));
});
test('Staff creation fixes role and validates long passwords/phone',()=>{
 const base={operation:'create',request_id,full_name:'Synthetic Staff',username:'staff.test',contact_number:'09000000000',temporary_password:'passphrase long enough'};
 assert.equal(validateStaff(base).operation,'create');
 for(const delta of [{role:'owner'},{temporary_password:'short'},{contact_number:'090 00000000'},{active:'true'},{user_id:request_id,actor_id:request_id}])assert.throws(()=>validateStaff({...base,...delta}));
 assert.throws(()=>validateStaff({...base,operation:'update',user_id:request_id}));
});
test('unconfigured login never claims a successful save/authentication',async()=>{
 let calls=0;const handler=loginHandler(()=>undefined,async()=>{calls++;throw Error();});
 const r=await handler(new Request('https://local.test',{method:'POST',body:JSON.stringify({username:'staff.test',password:'passphrase long enough'})}));
 assert.equal(r.status,503);assert.equal(calls,0);assert.deepEqual(await r.json(),{error:'Service unavailable.'});
});
test('staff operation without bearer rejected before upstream writes',async()=>{
 let calls=0;const cfg={SUPABASE_URL:'https://example.supabase.co',SUPABASE_ANON_KEY:'public-test',SUPABASE_SERVICE_ROLE_KEY:'synthetic-test-only'};
 const handler=staffHandler(k=>cfg[k],async()=>{calls++;throw Error();});
 const r=await handler(new Request('https://local.test',{method:'POST',body:JSON.stringify({operation:'set_active',request_id,user_id:request_id,active:false})}));
 assert.equal(r.status,401);assert.equal(calls,0);
});
for(const upstream of [400,503])test(`Auth creation ${upstream} distinguishes definite rejection from uncertain outcome`,async()=>{
 const cfg={SUPABASE_URL:'https://example.supabase.co',SUPABASE_ANON_KEY:'public-test',SUPABASE_SERVICE_ROLE_KEY:'synthetic-server-only'};
 const paths=[];const fetcher=async(url,options)=>{const path=new URL(url).pathname;paths.push(path);let data={};let status=200;
  if(path==='/auth/v1/user')data={id:'10000000-0000-4000-8000-000000000001'};
  else if(path.endsWith('aq_admin_prepare_staff'))data={state:'pending',email:request_id+'@identity.aquaquenchers.invalid'};
  else if(path.endsWith('aq_admin_auth_identity'))data=null;
  else if(path==='/auth/v1/admin/users'){status=upstream;data={message:'Managed Auth rejected/unavailable test fixture'};}
  else if(path.endsWith('aq_admin_fail_staff'))data={state:'failed'};
  else throw Error('Unexpected mutation');
  return new Response(JSON.stringify(data),{status,headers:{'content-type':'application/json'}});
 };
 const handler=staffHandler(k=>cfg[k],fetcher);const r=await handler(new Request('https://local.test',{method:'POST',headers:{Authorization:'Bearer synthetic-owner-token'},body:JSON.stringify({operation:'create',request_id,full_name:'Synthetic Staff',username:'staff.test',contact_number:'09000000000',temporary_password:'a long synthetic passphrase'})}));
 assert.equal(r.status,upstream===400?400:503);assert.equal(paths.filter(x=>x.endsWith('aq_admin_fail_staff')).length,upstream===400?1:0);assert.equal(paths.filter(x=>x.endsWith('aq_admin_finish_staff')).length,0);assert.ok((await r.json()).error);
});
test('chunked oversized request is bounded before any credential lookup',async()=>{
 let calls=0;const handler=loginHandler(()=>undefined,async()=>{calls++;throw Error();});
 let n=0,cancelled=false;const stream=new ReadableStream({pull(c){if(n++<10)c.enqueue(new Uint8Array(5000).fill(32));else c.close();},cancel(){cancelled=true;}});
 const r=await handler(new Request('https://local.test',{method:'POST',body:stream,duplex:'half'}));assert.equal(r.status,413);assert.equal(calls,0);assert.equal(cancelled,true);
});
