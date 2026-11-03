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
  else if (url.pathname.startsWith('/rest/v1/')) target = config.rest_url + url.pathname.slice('/rest/v1'.length);
  else return reply(404, 'Unknown local integration route.');
  try {
    return await fetch(target + url.search, {
      method: request.method, headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
      redirect: 'manual', signal: AbortSignal.timeout(15000),
    });
  } catch {
    return reply(503, 'Local integration upstream unavailable.');
  }
});
