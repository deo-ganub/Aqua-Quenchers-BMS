// Execute the actual handler with in-process managed-Auth/REST responses. No server/network permission.
const OWNER="10000000-0000-4000-8000-000000000001",STAFF="10000000-0000-4000-8000-000000000002";
Deno.env.set("SUPABASE_URL","https://identity.fixture.invalid");
Deno.env.set("SUPABASE_ANON_KEY","fixture-public-key");
Deno.env.set("SUPABASE_SERVICE_ROLE_KEY","fixture-server-only-marker");
let handle:(r:Request)=>Promise<Response>;
const originalServe=Deno.serve;
Deno.serve=((fn:unknown)=>{handle=fn as typeof handle;return {} as Deno.HttpServer;}) as typeof Deno.serve;
await import("../functions/identity/index.ts");Deno.serve=originalServe;
const originalFetch=globalThis.fetch;
function check(v:unknown,message:string){if(!v)throw new Error(message);}
function json(value:unknown,status=200){return new Response(JSON.stringify(value),{status,headers:{"content-type":"application/json"}});}
function profile(id=OWNER,status="Active"){return {id,status,role:id===OWNER?"owner":"staff",username:id===OWNER?"fixture.owner":"fixture.staff",full_name:"Fixture"};}
function request(body:unknown,token?:string){return new Request("https://local.fixture.invalid",{method:"POST",headers:{"content-type":"application/json",...(token?{Authorization:"Bearer "+token}:{})},body:JSON.stringify(body)});}
const login={action:"login",username:"fixture.owner",password:"fixture-passphrase-only"};
Deno.test("actual login rechecks current identity and returns no-store",async()=>{
 let reads=0;
 globalThis.fetch=(async(input:RequestInfo|URL)=>{
  const path=new URL(String(input)).pathname;
  if(path==="/rest/v1/profiles"){reads++;return json([profile()]);}
  if(path.startsWith("/auth/v1/admin/users/"))return json({email:"fixture@example.invalid"});
  if(path==="/auth/v1/token")return json({access_token:"fixture-access",refresh_token:"fixture-refresh",user:{id:OWNER}});
  if(path==="/rest/v1/audit_log")return new Response(null,{status:201});
  throw new Error("Unexpected fixture route");
 }) as typeof fetch;
 try{const response=await handle(request(login));const body=await response.json();check(response.status===200&&reads===2,"current profile rechecked");check(body.user_id===OWNER&&body.access_token==="fixture-access","expected managed identity");check(response.headers.get("cache-control")==="no-store","private response");check(!JSON.stringify(body).includes("fixture-server-only-marker"),"no service credential output");}
 finally{globalThis.fetch=originalFetch;}
});
Deno.test("deactivation during password grant denies returned session",async()=>{
 let reads=0,audits=0;
 globalThis.fetch=(async(input:RequestInfo|URL)=>{
  const path=new URL(String(input)).pathname;
  if(path==="/rest/v1/profiles")return json([profile(OWNER,++reads===1?"Active":"Inactive")]);
  if(path.startsWith("/auth/v1/admin/users/"))return json({email:"fixture@example.invalid"});
  if(path==="/auth/v1/token")return json({access_token:"fixture-access",user:{id:OWNER}});
  if(path==="/rest/v1/audit_log"){audits++;return json({});}
  throw new Error("Unexpected fixture route");
 }) as typeof fetch;
 try{const response=await handle(request(login));const body=await response.json();check(response.status===401&&!body.access_token&&audits===0,"deactivated identity rejected");}
 finally{globalThis.fetch=originalFetch;}
});
Deno.test("revoked database session denies privileged operation",async()=>{
 let writes=0;
 globalThis.fetch=(async(input:RequestInfo|URL,init?:RequestInit)=>{
  const path=new URL(String(input)).pathname;
  if(path==="/auth/v1/user")return json({id:OWNER});
  if(path==="/rest/v1/rpc/aq_session_touch"){check(new Headers(init?.headers).get("Authorization")==="Bearer fixture-token","same caller token to SQL guard");return json({message:"Session expired"},403);}
  writes++;throw new Error("Privileged path should be unreachable");
 }) as typeof fetch;
 try{const response=await handle(request({action:"change_password",current_password:"fixture-current",new_password:"fixture-next-password"},"fixture-token"));check(response.status===401&&writes===0,"no admin operation after denied session");}
 finally{globalThis.fetch=originalFetch;}
});
Deno.test("account mutation with failed audit returns reconciliation not success",async()=>{
 let patches=0;const token="fixture."+btoa(JSON.stringify({aal:"aal2",amr:[{method:"totp",timestamp:Date.now()/1000}]}))+".fixture";
 globalThis.fetch=(async(input:RequestInfo|URL,init?:RequestInit)=>{
  const url=new URL(String(input)),path=url.pathname;
  if(path==="/auth/v1/user")return json({id:OWNER});
  if(path==="/rest/v1/rpc/aq_session_touch")return json({active:true});
  if(path==="/rest/v1/profiles"){
   if(init?.method==="PATCH"){patches++;return new Response(null,{status:204});}
   if(url.searchParams.has("username"))return json([]);
   return json([profile(url.searchParams.get("id")==="eq."+STAFF?STAFF:OWNER)]);
  }
  if(path==="/rest/v1/audit_log")return json({message:"fixture failure"},500);
  throw new Error("Unexpected fixture route");
 }) as typeof fetch;
 try{const response=await handle(request({action:"save_staff",id:STAFF,full_name:"Fixture",username:"fixture.staff",contact_number:"09000000001",status:"Active",temporary_password:""},token));const body=await response.json();check(patches===1&&response.status===503&&body.details.includes("may already have changed"),"uncertain result reported truthfully");}
 finally{globalThis.fetch=originalFetch;}
});
Deno.test("privileged unknown input denied before provider access",async()=>{
 globalThis.fetch=(async()=>{throw new Error("No provider call expected");}) as typeof fetch;
 try{const response=await handle(request({...login,role:"owner"}));check(response.status===400,"unknown authority field rejected");}
 finally{globalThis.fetch=originalFetch;}
});
