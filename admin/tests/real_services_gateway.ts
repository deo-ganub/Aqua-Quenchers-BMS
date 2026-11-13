/** Local integration fixture only: official Auth/PostgREST plus the production handlers.
 * No fake Auth responses, user tokens, business saves or production deployment.
 */
import { loginHandler, staffHandler } from '../../supabase/functions/_shared/auth.mjs';

const path = Deno.args[0];
if (!path) throw new Error('Supply the private local fixture configuration path.');
const info = await Deno.stat(path);
if (info.mode !== null && (info.mode & 0o077) !== 0) throw new Error('Configuration must be0600.');
const config = JSON.parse(await Deno.readTextFile(path));
if (config.kind !== 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' ||
    config.url !== 'https://127.0.0.1:55436' ||
    config.auth_url !== 'http://127.0.0.1:55434' ||
    config.rest_url !== 'http://127.0.0.1:55435') throw new Error('Refusing nonlocal integration configuration.');

// Diagnostics contain paths/statuses only, never headers, bodies or credentials.
const observedFetch: typeof fetch = async (input, options) => {
  const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url);
  try {
    const response = await fetch(input, options);
    console.info('local upstream', url.pathname, response.status);
    return response;
  } catch (error) {
    console.error('local upstream failure', url.pathname, error instanceof Error ? error.name : 'UnknownError',
      error instanceof Error ? error.message.replace(/https?:\/\/[^\s]+/g, '[local endpoint]').slice(0, 180) : '');
    throw error;
  }
};
const login = loginHandler((name: string) => config[name], observedFetch);
const staff = staffHandler((name: string) => config[name], observedFetch);
Deno.serve({
  hostname: '127.0.0.1', port: 55436,
  cert: await Deno.readTextFile(config.cert), key: await Deno.readTextFile(config.key),
}, async (request, connection) => {
  const reply = (status: number, error: string) => new Response(JSON.stringify({ error }), {
    status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
  });
  const apikey = request.headers.get('apikey');
  if (apikey !== config.SUPABASE_ANON_KEY && apikey !== config.SUPABASE_SERVICE_ROLE_KEY)
    return reply(401, 'Invalid API key.');
  const url = new URL(request.url);
  const headers = new Headers(request.headers);
  headers.delete('host');
  // Replace caller-supplied forwarding data with this local gateway's socket peer.
  headers.set('x-forwarded-for', connection.remoteAddr.hostname);
  if (url.pathname === '/functions/v1/username-login') return login(new Request(request, { headers }));
  if (url.pathname === '/functions/v1/staff-admin') return staff(new Request(request, { headers }));
  let target: string;
  if (url.pathname.startsWith('/auth/v1/')) target = config.auth_url + url.pathname.slice('/auth/v1'.length);
  else if (url.pathname.startsWith('/short-auth/v1/')) target = 'http://127.0.0.1:55437' + url.pathname.slice('/short-auth/v1'.length);
  else if (url.pathname.startsWith('/rest/v1/')) target = config.rest_url + url.pathname.slice('/rest/v1'.length);
  else return reply(404, 'Unknown local integration route.');
  // Optional, one-shot transport fault for a separately controlled local test.
  // The real upstream commits; the client receives an error, never a fake save.
  let suppressed = '';
  let requestId = '';
  const faultDirectory = '/workspace/tooling/aq-auth-rest-local/faults';
  let shortNonce = '';
  if (url.pathname === '/auth/v1/token' && url.searchParams.get('grant_type') === 'password') {
    try {
      const spec = JSON.parse(Deno.readTextFileSync(faultDirectory + '/short-pending.json'));
      const body = await request.clone().json();
      if (spec.kind === 'LOCAL_SHORT_AUTH' && /^[a-f0-9]{8}$/.test(spec.nonce) &&
          spec.expires_at > Date.now() / 1000 && body.email === config.owner_email) {
        Deno.renameSync(faultDirectory + '/short-pending.json', faultDirectory + '/short-consumed-' + spec.nonce + '.json');
        target = 'http://127.0.0.1:55437/token';shortNonce = spec.nonce;
      }
    } catch { /* Ordinary official Auth issuer remains the default. */ }
  }
  if (url.pathname === '/rest/v1/rpc/aq_command' && request.method === 'POST') {
    try {
      const directory = faultDirectory;
      const spec = JSON.parse(Deno.readTextFileSync(directory + '/pending.json'));
      const body = (await request.clone().json()).p_request;
      const now = Date.now() / 1000;
      if (spec.kind === 'LOCAL_POST_COMMIT_FAULT' && /^[a-f0-9]{8}$/.test(spec.nonce) &&
          spec.expires_at > now && spec.expires_at < now + 600 &&
          body?.operation === 'create_expense' && body?.remarks === 'LOCAL RETRY ' + spec.nonce) {
        Deno.renameSync(directory + '/pending.json', directory + '/consumed-' + spec.nonce + '.json');
        suppressed = directory + '/result-' + spec.nonce + '.json';
        requestId = body.request_id;
      }
    } catch { /* Absent/invalid control file means ordinary forwarding. */ }
  }
  try {
    const response = await fetch(target + url.search, {
      method: request.method, headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
      redirect: 'manual', signal: AbortSignal.timeout(15000),
    });
    if (response.ok && shortNonce) {
      const data = await response.clone().json();
      Deno.writeTextFileSync(faultDirectory + '/short-active.json', JSON.stringify({ nonce: shortNonce, user_id: data.user.id, expires_in: data.expires_in, refresh_count: 0, until: Date.now() / 1000 + 600 }), { mode: 0o600 });
    }
    if (response.ok && url.pathname === '/auth/v1/token' && url.searchParams.get('grant_type') === 'refresh_token') {
      try {
        const active = JSON.parse(Deno.readTextFileSync(faultDirectory + '/short-active.json'));
        const data = await response.clone().json();
        if (active.until > Date.now() / 1000 && active.user_id === data.user.id) {
          active.refresh_count++;active.refreshed_expires_in = data.expires_in;
          Deno.writeTextFileSync(faultDirectory + '/short-active.json', JSON.stringify(active), { mode: 0o600 });
        }
      } catch { /* No active short-session probe. */ }
    }
    if (suppressed && response.ok) {
      await response.arrayBuffer();
      await Deno.writeTextFile(suppressed, JSON.stringify({ request_id: requestId, upstream_status: response.status, response_drained: true }), { mode: 0o600 });
      return reply(503, 'Service unavailable. Retry with the same details.');
    }
    return response;
  } catch {
    return reply(503, 'Local integration upstream unavailable.');
  }
});
