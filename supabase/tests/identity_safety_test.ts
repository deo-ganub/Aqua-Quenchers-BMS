import { boundedText, requestObject, InputError } from "../functions/_shared/identity_safety.ts";
function check(value:unknown,message:string) {if(!value) throw new Error(message);}
async function denied(work:()=>Promise<unknown>) {try{await work();}catch(e){check(e instanceof InputError,"typed safe error");return;}throw new Error("Expected rejection");}
const req=(body:string,headers:Record<string,string>={}) => new Request("https://fixture.invalid",{method:"POST",headers:{"content-type":"application/json",...headers},body});
Deno.test("strict request and no privileged fields",async()=>{
 const accepted=await requestObject(req(JSON.stringify({action:"login",username:"fixture",password:"fixture"})));
 check(accepted.action==="login","valid object");
 for(const value of [{action:"login",username:"fixture",password:"fixture",role:"owner"},[],null,{action:"login",username:{},password:"fixture"},
 {action:"save_staff",id:"not-a-uuid"},{action:"save_staff",status:"Unexpected"}])await denied(()=>requestObject(req(JSON.stringify(value))));
});
Deno.test("actual streamed bytes bounded without trusting content length",async()=>{
 await denied(()=>requestObject(req("x".repeat(16385))));
 await denied(()=>requestObject(req("x".repeat(16385),{"content-length":"1"})));
 await denied(()=>requestObject(req("{}",{"content-length":"999999"})));
});
Deno.test("JSON shape, encoding, content type and field lengths",async()=>{
 await denied(()=>requestObject(req("{bad")));
 await denied(()=>requestObject(req("{}",{"content-type":"text/plain"})));
 await denied(()=>requestObject(req(JSON.stringify({action:"save_staff",full_name:"a".repeat(161)}))));
 await denied(()=>boundedText(new ReadableStream({start(c){c.enqueue(new Uint8Array([255]));c.close();}}),4));
});
Deno.test("stream deadline cancels rather than hanging",async()=>{
 let cancelled=false;
 await denied(()=>boundedText(new ReadableStream({cancel(){cancelled=true;}}),16,10));
 check(cancelled,"pending input cancelled");
});
Deno.test("bounded upstream decoding preserves valid content",async()=>{
 check(await boundedText(new Response('{"active":true}').body,64)==='{"active":true}',"valid content");
 await denied(()=>boundedText(new Response("x".repeat(65)).body,64));
});
