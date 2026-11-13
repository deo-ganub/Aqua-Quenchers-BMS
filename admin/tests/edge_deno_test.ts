import {loginHandler,staffHandler} from '../../supabase/functions/_shared/auth.mjs';
function assert(value: boolean,message: string){if(!value)throw new Error(message);}
Deno.test('native Edge runtime rejects staff administration without managed bearer before upstream',async()=>{
 let calls=0;const values:Record<string,string>={SUPABASE_URL:'https://example.supabase.co',SUPABASE_ANON_KEY:'synthetic-public',SUPABASE_SERVICE_ROLE_KEY:'synthetic-server-only'};
 const handler=staffHandler((name:string)=>values[name],async()=>{calls++;throw new Error('Unexpected upstream');});
 const response=await handler(new Request('https://local.test',{method:'POST',body:JSON.stringify({operation:'set_active',request_id:'30000000-0000-4000-8000-000000000001',user_id:'10000000-0000-4000-8000-000000000002',active:false})}));
 assert(response.status===401&&calls===0,'Unauthorized operation reached upstream');
});
Deno.test('native Edge runtime rejects oversized chunked input before identity resolution',async()=>{
 let calls=0,cancelled=false;const handler=loginHandler(()=>undefined,async()=>{calls++;throw new Error('Unexpected upstream');});let count=0;
 const stream=new ReadableStream<Uint8Array>({pull(controller){if(count++<10)controller.enqueue(new Uint8Array(5000).fill(32));else controller.close();},cancel(){cancelled=true;}});
 const response=await handler(new Request('https://local.test',{method:'POST',body:stream}));
 assert(response.status===413&&calls===0&&cancelled,'Stream bound not enforced');
});
Deno.test('native Edge runtime never authenticates when configuration is unavailable',async()=>{
 const response=await loginHandler(()=>undefined)(new Request('https://local.test',{method:'POST',body:JSON.stringify({username:'staff.test',password:'synthetic long passphrase'})}));
 const result=await response.json();assert(response.status===503&&result.error==='Service unavailable.'&&!result.access_token,'Unconfirmed authentication reported success');
});
