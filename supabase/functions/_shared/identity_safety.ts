/** Pure transport/input guards. No credentials, network or server startup on import. */
export class InputError extends Error {}
export async function boundedText(body: ReadableStream<Uint8Array> | null, max: number, milliseconds=15000): Promise<string> {
  if (!body) return "";
  const reader=body.getReader(); let timer: ReturnType<typeof setTimeout> | undefined;
  const collect=async () => {
    const chunks: Uint8Array[]=[]; let total=0;
    while (true) {
      const {done,value}=await reader.read(); if (done) break;
      total+=value.byteLength; if (total>max) throw new InputError("Request or response exceeds its size limit.");
      chunks.push(value);
    }
    const bytes=new Uint8Array(total); let offset=0;
    for (const chunk of chunks) { bytes.set(chunk,offset); offset+=chunk.byteLength; }
    try { return new TextDecoder("utf-8",{fatal:true}).decode(bytes); }
    catch { throw new InputError("Invalid UTF-8 JSON."); }
  };
  try {
    return await Promise.race([collect(),new Promise<string>((_,reject) => {
      timer=setTimeout(() => {reject(new InputError("Input deadline exceeded."));void reader.cancel().catch(() => {});},milliseconds);
    })]);
  } finally {
    if (timer!==undefined) clearTimeout(timer);
    await reader.cancel().catch(() => {});reader.releaseLock();
  }
}
export async function requestObject(req: Request): Promise<Record<string,unknown>> {
  if (!req.headers.get("content-type")?.toLowerCase().startsWith("application/json"))
    throw new InputError("Use application/json.");
  const length=req.headers.get("content-length");
  if (length!==null && (!/^\d+$/.test(length) || Number(length)>16384)) throw new InputError("Invalid request size.");
  let value;
  try { value=JSON.parse(await boundedText(req.body,16384)); }
  catch(e) { if(e instanceof InputError) throw e;throw new InputError("Invalid JSON request."); }
  if (!value || typeof value!=="object" || Array.isArray(value)) throw new InputError("Use a JSON object.");
  const fields: Record<string,string[]>={login:["action","username","password"],
    save_staff:["action","id","full_name","username","contact_number","status","temporary_password"],
    change_password:["action","current_password","new_password"]};
  const keys=fields[String(value.action)];
  if (!keys || Object.keys(value).some(k => !keys.includes(k))) throw new InputError("Unknown action or request field.");
  for (const [key,v] of Object.entries(value)) {
    if (key==="id" && v===null) continue;
    if (typeof v!=="string") throw new InputError("Request fields must use the expected text types.");
    const bound=key.includes("password") ? 4096 : key==="full_name" ? 160 : 40;
    if ([...v].length>bound) throw new InputError("Request field exceeds its length limit.");
  }
  if (value.id!==undefined && value.id!==null && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.id))
    throw new InputError("Invalid Staff identifier.");
  if (value.status!==undefined && !["Active","Inactive"].includes(value.status)) throw new InputError("Invalid account status.");
  return value;
}
