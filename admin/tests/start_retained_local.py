"""Restart retained synthetic-only services after a cloud snapshot/restart.
Never provisions users, resets databases, or changes production configuration.
"""
import json
import os
import pathlib
import socket
import ssl
import subprocess
import time
import urllib.request

ROOT = pathlib.Path('/workspace/tooling')
LOCAL = ROOT / 'aq-auth-rest-local'
REPO = pathlib.Path(__file__).resolve().parents[2]


def main():
    config_path = LOCAL / 'private-config.json'
    assert config_path.stat().st_mode & 0o077 == 0
    c = json.loads(config_path.read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY'
    assert c['url'] == 'https://127.0.0.1:55436'
    env = dict(os.environ, LD_LIBRARY_PATH=str(ROOT / 'sysroot/usr/lib/x86_64-linux-gnu'))
    pg = ROOT / 'sysroot/usr/lib/postgresql/17/bin'
    def listening(port):
        with socket.socket() as s:
            return s.connect_ex(('127.0.0.1', port)) == 0
    def launch(name, port, command, process_env=env):
        if listening(port):
            return
        log_path = LOCAL / (name + '.log')
        fd = os.open(log_path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(fd, 'ab') as out:
            p = subprocess.Popen(command, cwd=REPO, env=process_env, stdin=subprocess.DEVNULL,
                                 stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
        (LOCAL / (name + '.pid')).write_text(str(p.pid))
        for _ in range(120):
            if listening(port):
                return
            if p.poll() is not None:
                raise RuntimeError(name + ' exited; inspect private local log')
            time.sleep(.25)
        raise RuntimeError(name + ' readiness timeout')
    if not listening(55432):
        subprocess.run([str(pg / 'pg_ctl'), '-D', str(ROOT / 'pgtest'), '-l', str(ROOT / 'pgtest/server.log'),
                        '-o', '-k /workspace/tooling/pgtest -p 55432 -h 127.0.0.1', '-w', 'start'], env=env, check=True)
    auth_env = dict(env, **c['auth_env'])
    launch('auth', 55434, [str(ROOT / 'supabase-auth'), 'serve'], auth_env)
    launch('short-auth', 55437, [str(ROOT / 'supabase-auth'), 'serve'], dict(auth_env, GOTRUE_API_PORT='55437', GOTRUE_JWT_EXP='2'))
    launch('postgrest', 55435, [str(ROOT / 'postgrest/postgrest'), str(LOCAL / 'postgrest.conf')])
    deno_env = dict(env, DENO_DIR=str(ROOT / 'deno-cache'), DENO_NO_UPDATE_CHECK='1')
    launch('gateway', 55436, [str(ROOT / 'deno-cli/node_modules/.bin/deno'), 'run',
           '--cert=' + c['ca_cert'], '--allow-read=' + ','.join([str(config_path), str(LOCAL / 'tls'), str(LOCAL / 'faults'), str(REPO / 'supabase/functions/_shared/auth.mjs')]),
           '--allow-write=' + str(LOCAL / 'faults'),
           '--allow-net=127.0.0.1:55434,127.0.0.1:55435,127.0.0.1:55436,127.0.0.1:55437',
           str(REPO / 'admin/tests/real_services_gateway.ts'), str(config_path)], deno_env)
    context = ssl.create_default_context(cafile=c['ca_cert'])
    for path in ['/auth/v1/health', '/short-auth/v1/health']:
        req = urllib.request.Request(c['url'] + path, headers={'apikey': c['SUPABASE_ANON_KEY']})
        with urllib.request.urlopen(req, context=context, timeout=20) as response:
            assert json.load(response)['name'] == 'GoTrue'
    print('PASS: retained local PostgreSQL, official Auth/short Auth, REST and HTTPS gateway healthy; no fixture reset.')


if __name__ == '__main__':
    main()
