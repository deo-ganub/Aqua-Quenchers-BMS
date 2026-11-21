/** Edge-only Auth integration. Service credentials never leave this process. */
export class SafeError extends Error { constructor(status,code) { super(code); this.status=status; } }
export const json=(status,body)=>new Response(JSON.stringify(body),{status,headers:{'content-type':'application/json','cache-control':'no-store'}});
const exact=(o,allowed)=>{if(!o||Array.isArray(o)||typeof o!=='object'||Object.keys(o).some(k=>!allowed.includes(k)))throw new SafeError(400,'Invalid request.');};
export function normalizeUsername(value){if(typeof value!=='string')throw new SafeError(400,'Invalid request.');const s=value.normalize('NFKC').trim().toLowerCase();if(!/^[a-z0-9][a-z0-9_.-]{2,39}$/.test(s))throw new SafeError(400,'Invalid request.');return s;}
const uuid=v=>typeof v==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
export function validateLogin(body){exact(body,['username','password']);const username=normalizeUsername(body.username);if(typeof body.password!=='string'||body.password.length>4096||!body.password)throw new SafeError(400,'Invalid request.');return{username,password:body.password};}
// Same policy as Rules.password in the app: 15+ characters, 5+ different characters, no common
// words/sequences or short repeated patterns, and not the person's own username or name.
const WEAK_PARTS=['password','passw0rd','qwerty','asdfgh','zxcvbn','123456','654321','abcdef','iloveyou','letmein','welcome','aquaquencher','admin'];
export function checkPassword(p,personal=[]){
 if(typeof p!=='string'||[...p].length<15)throw new SafeError(400,'Use at least 15 characters.');
 if(p.length>4096)throw new SafeError(400,'Use 4096 characters or fewer.');
 const n=p.toLowerCase().replace(/[^a-z0-9]/g,'');
 const repeats=k=>k<n.length&&[...n].every((c,i)=>i<k||c===n[i-k]);
 if(new Set([...p]).size<5||(n.length>0&&[1,2,3,4].some(repeats)))throw new SafeError(400,'Avoid repeated characters or simple patterns.');
 if(WEAK_PARTS.some(w=>n.includes(w)))throw new SafeError(400,'Avoid common words or sequences such as "password" or "123456".');
 for(const value of personal){if(typeof value!=='string')continue;for(const part of value.toLowerCase().split(/[^a-z0-9]+/))if(part.length>=3&&n.includes(part))throw new SafeError(400,"Don't use your name or username in the password.");}
}
export function validateStaff(body){exact(body,['operation','request_id','user_id','username','full_name','contact_number','temporary_password','active']);if(!['create','update','set_active'].includes(body.operation)||!uuid(body.request_id))throw new SafeError(400,'Invalid request.');
 const out={operation:body.operation,request_id:body.request_id};
 if(body.operation!=='create'){if(!uuid(body.user_id))throw new SafeError(400,'Invalid request.');out.user_id=body.user_id;}
 if(body.operation!=='set_active'){
  out.username=normalizeUsername(body.username);if(typeof body.full_name!=='string'||!body.full_name.trim()||[...body.full_name].length>160||!/^\d{11}$/.test(body.contact_number))throw new SafeError(400,'Invalid staff details.');out.full_name=body.full_name.trim();out.contact_number=body.contact_number;
 }
 if(body.operation==='create'){checkPassword(body.temporary_password,[body.username,body.full_name]);out.temporary_password=body.temporary_password;}
 else if('temporary_password'in body)throw new SafeError(400,'Invalid request.');
 if('active'in body){if(typeof body.active!=='boolean')throw new SafeError(400,'Invalid request.');out.active=body.active;}else if(body.operation==='set_active')throw new SafeError(400,'Invalid request.');
 return out;
}
export function config(env){const url=env('SUPABASE_URL'),anon=env('SUPABASE_ANON_KEY'),service=env('SUPABASE_SERVICE_ROLE_KEY');if(!url||!anon||!service||!/^https:\/\/[a-z0-9.-]+(?::\d+)?$/.test(url))throw new SafeError(503,'Service unavailable.');return{url,anon,service};}
export function client(cfg,fetcher=fetch){return async(path,{token=cfg.service,key=cfg.service,method='POST',body}={})=>{
 let response;try{response=await fetcher(cfg.url+path,{method,headers:{apikey:key,Authorization:`Bearer ${token}`,'content-type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});}catch{throw new SafeError(503,'Service unavailable.');}
 let data;try{data=await response.json();}catch{throw new SafeError(503,'Service unavailable.');}
 if(!response.ok){const safeMessages=['Enter a valid verification code.','Username already exists.','Session expired. Sign in again.','Request ID conflicts with another request.'];const message=safeMessages.includes(data?.message)?data.message:'Request rejected.';const e=new SafeError(response.status>=500?503:[401,403,409,429].includes(response.status)?response.status:400,message);e.upstreamStatus=response.status;throw e;}return data;
};}
const canonical=v=>Array.isArray(v)?v.map(canonical):v&&typeof v==='object'?Object.fromEntries(Object.keys(v).sort().map(k=>[k,canonical(v[k])])):v;
export async function fingerprint(secret,input){const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);const mac=await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(JSON.stringify(canonical(input))));return [...new Uint8Array(mac)].map(x=>x.toString(16).padStart(2,'0')).join('');}
async function readBody(request){
 if(request.method!=='POST')throw new SafeError(405,'Method not allowed.');
 const length=request.headers.get('content-length');if(length!==null&&(!/^\d+$/.test(length)||Number(length)>16384))throw new SafeError(413,'Request too large.');
 let raw='',size=0;const reader=request.body?.getReader();const decoder=new TextDecoder('utf-8',{fatal:true});
 try{
  if(reader)while(true){const {value,done}=await reader.read();if(done)break;size+=value.byteLength;if(size>16384){await reader.cancel();throw new SafeError(413,'Request too large.');}raw+=decoder.decode(value,{stream:true});}
  raw+=decoder.decode();return JSON.parse(raw);
 }catch(error){if(error instanceof SafeError)throw error;throw new SafeError(400,'Invalid request.');}
 finally{reader?.releaseLock();}
}
// Client address for the per-source sign-in limit. Callers can put anything at the START of
// x-forwarded-for, so it is never trusted: use the header the edge proxy sets itself
// (AQ_CLIENT_IP_HEADER, default cf-connecting-ip), else the LAST x-forwarded-for hop, which the
// nearest proxy appends. The per-username limit applies regardless.
export function clientSource(request,env=()=>undefined){
 const named=(env('AQ_CLIENT_IP_HEADER')||'cf-connecting-ip').toLowerCase();
 const direct=request.headers.get(named)?.trim();if(direct&&direct.length<=64)return direct;
 const hops=(request.headers.get('x-forwarded-for')||'').split(',').map(v=>v.trim()).filter(Boolean);
 const last=hops[hops.length-1];return last&&last.length<=64?last:'unknown';
}
export function loginHandler(env,fetcher=fetch){return async request=>{try{
 const body=validateLogin(await readBody(request)),cfg=config(env),api=client(cfg,fetcher);
 // Source comes from the proxy-set header (see clientSource). Username bucket always throttles.
 const bucket=await fingerprint(cfg.service,{username:body.username});
 const source=await fingerprint(cfg.service,{source:clientSource(request,env)});
 const permitted=await api('/rest/v1/rpc/aq_auth_login_attempt',{body:{p_username_key:bucket,p_source_key:source}});if(permitted!==true)throw new SafeError(429,'Sign-in unavailable. Try again later.');
 const mapping=await api('/rest/v1/rpc/aq_admin_resolve_username',{body:{p_username:body.username}});
 // Same Auth request for invalid/inactive names avoids a distinct early response.
 const email=mapping?.email||'unavailable@identity.aquaquenchers.invalid';
 let session;try{session=await api('/auth/v1/token?grant_type=password',{key:cfg.anon,token:cfg.anon,body:{email,password:body.password}});}catch{throw new SafeError(401,'Invalid username or password.');}
 if(!mapping?.user_id||session.user?.id!==mapping.user_id)throw new SafeError(401,'Invalid username or password.');
 // Re-check active identity after password verification (deactivation race).
 let me;try{me=await api('/rest/v1/rpc/aq_me',{token:session.access_token,key:cfg.anon,body:{}});}catch{throw new SafeError(401,'Invalid username or password.');}
 if(!me?.active||me.user_id!==mapping.user_id)throw new SafeError(401,'Invalid username or password.');
 return json(200,{access_token:session.access_token,refresh_token:session.refresh_token,expires_in:session.expires_in,token_type:session.token_type,user:{id:me.user_id,role:me.role,username:me.username}});
 }catch(e){return json(e instanceof SafeError?e.status:503,{error:e instanceof SafeError?e.message:'Service unavailable.'});}};}
export function staffHandler(env,fetcher=fetch){return async request=>{try{
 const body=validateStaff(await readBody(request)),cfg=config(env),api=client(cfg,fetcher),authorization=request.headers.get('authorization');if(!authorization?.startsWith('Bearer '))throw new SafeError(401,'Authentication required.');const token=authorization.slice(7);
 // Auth validates signatures/expiry; database performs current active Owner + AAL2 checks.
 const user=await api('/auth/v1/user',{method:'GET',token,key:cfg.anon});if(!user?.id)throw new SafeError(401,'Authentication required.');
 const {temporary_password,...safe}=body;
 const hash=await fingerprint(cfg.service,body);
 if(body.operation!=='create'){
  const result=await api('/rest/v1/rpc/aq_admin_update_staff',{token,key:cfg.anon,body:{p_request:{...safe,payload_hash:hash}}});return json(200,result);
 }
 const operation=await api('/rest/v1/rpc/aq_admin_prepare_staff',{token,key:cfg.anon,body:{p_request:{...safe,payload_hash:hash}}});
 if(operation.state==='completed')return json(200,operation.result);
 if(operation.state==='failed')throw new SafeError(400,'Account details were rejected. Correct them and start a new request.');
 // Stable internal email lets an uncertain Auth create be reconciled on retry.
 let identity=await api('/rest/v1/rpc/aq_admin_auth_identity',{body:{p_request_id:body.request_id}});
 if(!identity?.user_id){try{const created=await api('/auth/v1/admin/users',{body:{email:operation.email,password:temporary_password,email_confirm:true,app_metadata:{provisioned_by:'aqua_quenchers'}}});identity={user_id:created.id};}catch(error){
  identity=await api('/rest/v1/rpc/aq_admin_auth_identity',{body:{p_request_id:body.request_id}});if(!identity?.user_id){if(error instanceof SafeError&&[400,422].includes(error.upstreamStatus)){await api('/rest/v1/rpc/aq_admin_fail_staff',{body:{p_request_id:body.request_id,p_payload_hash:hash}});throw new SafeError(400,'Account details were rejected. Correct them and start a new request.');}throw new SafeError(503,'Account creation needs reconciliation. Retry with the same request ID.');}
 }}
 const result=await api('/rest/v1/rpc/aq_admin_finish_staff',{token,key:cfg.anon,body:{p_request_id:body.request_id,p_user_id:identity.user_id,p_payload_hash:hash}});return json(200,result);
 }catch(e){return json(e instanceof SafeError?e.status:503,{error:e instanceof SafeError?e.message:'Service unavailable.'});}};}
