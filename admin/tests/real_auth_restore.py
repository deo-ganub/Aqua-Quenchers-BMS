"""Owned synthetic-only real GoTrue-schema restore drill, never hosted recovery.
Retains ownership/ACLs, reconciles records, invalidates restored sessions and
checks fresh password/TOTP authentication through official Auth and REST.
"""
import base64
import hashlib
import hmac
import json
import os
import pathlib
import socket
import struct
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = pathlib.Path('/workspace/tooling')
LOCAL = ROOT / 'aq-auth-rest-local'
REPO = pathlib.Path(__file__).resolve().parents[2]
PG = ROOT / 'sysroot/usr/lib/postgresql/17/bin'
ENV = dict(os.environ, LD_LIBRARY_PATH=str(ROOT / 'sysroot/usr/lib/x86_64-linux-gnu'))


def main():
    config = json.loads((LOCAL / 'private-config.json').read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY'
    source = config['database']
    assert source.startswith('aq_http_')
    destination = 'aq_real_restore_' + uuid.uuid4().hex
    def sql(db, query):
        p = subprocess.run([str(PG / 'psql'), '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-v', 'ON_ERROR_STOP=1', '-d', db],
                           input=query, text=True, capture_output=True, env=ENV)
        if p.returncode:
            raise RuntimeError('Local restore SQL failed; diagnostics withheld to protect private records')
        return p.stdout.strip()
    def http(port, path, body=None, token=None, expected=200):
        req = urllib.request.Request('http://127.0.0.1:' + str(port) + path,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers={'content-type': 'application/json', 'Authorization': 'Bearer ' + (token or config['SUPABASE_ANON_KEY'])})
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        assert status == expected, f'Local restored endpoint {path.split("?")[0]} status{status}, expected{expected}'
        return json.loads(raw) if raw else None
    def connect_database(uri):
        parsed = urllib.parse.urlsplit(uri)
        return urllib.parse.urlunsplit(parsed._replace(path='/' + destination))
    snapshot = """select jsonb_build_object(
      'users',(select count(*) from auth.users),'factors',(select count(*) from auth.mfa_factors),
      'profiles',(select count(*) from aq_private.profiles),'orders',(select count(*) from aq_private.orders),
      'sales',(select count(*) from aq_private.sales),'receipts',(select coalesce(sum(amount_centavos),0) from aq_private.payments),
      'expenses',(select coalesce(sum(amount_centavos),0) from aq_private.expenses),
      'inventory',(select to_jsonb(i) from aq_private.inventory i),
      'reservations',(select coalesce(sum(quantity),0) from aq_private.reservations),
      'charges',(select coalesce(sum(quantity::bigint*unit_price_centavos),0) from aq_private.orders)+
                (select coalesce(sum(quantity::bigint*unit_price_centavos),0) from aq_private.sales),
      'auth_owner',(select pg_get_userbyid(relowner) from pg_class where oid='auth.users'::regclass));"""
    processes = []
    created = False
    try:
        # No concurrent business harness should run while capturing this drill.
        old = http(55434, '/token?grant_type=password', {'email': config['owner_email'], 'password': config['owner_password']})
        assert http(55435, '/rpc/aq_me', {}, old['access_token'])['role'] == 'owner'
        before = json.loads(sql(source, snapshot))
        assert before['users'] >= 2 and before['factors'] >= 1
        with tempfile.TemporaryDirectory(prefix='aq-real-auth-restore-', dir=LOCAL) as temp:
            private = pathlib.Path(temp)
            private.chmod(0o700)
            dump = private / 'database.dump'
            subprocess.run([str(PG / 'pg_dump'), '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', source,
                            '--format=custom', '--file', str(dump)], env=ENV, check=True, capture_output=True)
            dump.chmod(0o600)
            sql('postgres', 'create database ' + destination + " encoding 'UTF8' template template0;")
            created = True
            # Auth uses a dedicated non-superuser owner. --no-owner silently changes
            # RLS semantics; this drill deliberately preserves reviewed local roles.
            subprocess.run([str(PG / 'pg_restore'), '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', destination,
                            '--exit-on-error', str(dump)], env=ENV, check=True, capture_output=True)
            assert json.loads(sql(destination, snapshot)) == before
            assert sql(destination, "select bool_and(relrowsecurity) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='aq_private' and c.relkind='r';") == 't'
            assert sql(destination, "select not has_function_privilege('anon','public.aq_query(jsonb)','execute') and not has_table_privilege('authenticated','aq_private.payments','select');") == 't'
            # Invalidate ONLY the isolated restored database. Official Auth refresh
            # FK cascade is defined in its versioned sessions migration.
            sql(destination, "update aq_private.sessions set revoked_at=coalesce(revoked_at,clock_timestamp()); delete from auth.sessions;")
            assert sql(destination, 'select count(*) from auth.refresh_tokens;') == '0'
            assert json.loads(sql(destination, snapshot)) == before
            auth_env = dict(ENV, **config['auth_env'])
            auth_env.update(GOTRUE_DB_DATABASE_URL=connect_database(auth_env['GOTRUE_DB_DATABASE_URL']),
                            DATABASE_URL=connect_database(auth_env['DATABASE_URL']),
                            GOTRUE_API_PORT='55438', PORT='55438')
            source_sessions = sql(source, 'select count(*) from auth.sessions;')
            rest = private / 'postgrest.conf'
            # Reuse only reviewed local settings; change database selector, not key.
            text = (LOCAL / 'postgrest.conf').read_text()
            assert source in text
            rest.write_text(text.replace(source, destination).replace('55435', '55439'))
            rest.chmod(0o600)
            for name, port, command, env in [
                ('auth', 55438, [str(ROOT / 'supabase-auth'), 'serve'], auth_env),
                ('rest', 55439, [str(ROOT / 'postgrest/postgrest'), str(rest)], ENV)]:
                with socket.socket() as s:
                    assert s.connect_ex(('127.0.0.1', port)) != 0, 'Restore port already in use; inspect owner'
                with (private / (name + '.log')).open('wb') as log:
                    process = subprocess.Popen(command, env=env, cwd=REPO, stdout=log, stderr=subprocess.STDOUT)
                processes.append(process)
                for _ in range(120):
                    with socket.socket() as s:
                        if s.connect_ex(('127.0.0.1', port)) == 0:
                            break
                    assert process.poll() is None, 'Restore service failed; inspect private diagnostics'
                    time.sleep(.25)
                else:
                    raise RuntimeError('Restore readiness timeout')
            http(55439, '/rpc/aq_me', {}, old['access_token'], expected=403)
            http(55438, '/token?grant_type=refresh_token', {'refresh_token': old['refresh_token']}, expected=400)
            fresh = http(55438, '/token?grant_type=password', {'email': config['owner_email'], 'password': config['owner_password']})
            me = http(55439, '/rpc/aq_me', {}, fresh['access_token'])
            assert me['role'] == 'owner'
            state = json.loads((LOCAL / 'http-test-state.json').read_text())
            factor_id = state['factor_id']
            challenge = http(55438, '/factors/' + factor_id + '/challenge', {}, fresh['access_token'])
            secret = state['factor_secret']
            digest = hmac.new(base64.b32decode(secret + '=' * (-len(secret) % 8)), struct.pack('>Q', int(time.time()) // 30), hashlib.sha1).digest()
            offset = digest[-1] & 15
            code = str((struct.unpack('>I', digest[offset:offset+4])[0] & 0x7fffffff) % 1000000).zfill(6)
            verified = http(55438, '/factors/' + factor_id + '/verify', {'challenge_id': challenge['id'], 'code': code}, fresh['access_token'])
            assert verified['access_token'] != fresh['access_token']
            report = http(55439, '/rpc/aq_query', {'p_request': {'resource': 'expenses'}}, verified['access_token'])
            assert report['total_centavos'] == before['expenses']
            assert json.loads(sql(destination, snapshot)) == before
            assert json.loads(sql(source, snapshot)) == before, 'Drill changed source business state'
            assert int(sql(destination, 'select count(*) from auth.sessions;')) > 0
            assert sql(source, 'select count(*) from auth.sessions;') == source_sessions, 'Restore Auth used source database'
            evidence = 'PASS: actual official Auth schema/users/factors and BMS records restored into NEW local UTF8 database with exact ownership, financial/stock totals, RLS and grants.\nPASS: isolated restored app sessions revoked and real Auth sessions/refresh tokens invalidated; old access403 and refresh400.\nPASS: fresh official password login, retained TOTP factor/AAL2 and protected expense reconciliation. Source business state unchanged.\nNOT RUN: hosted project/config/Storage, independent custody, controller erasure approval or measured RPO/RTO.\n'
            (REPO / 'evidence/real-auth-restore.log').write_text(evidence)
            print(evidence, end='')
    finally:
        for process in reversed(processes):
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if created:
            sql('postgres', 'drop database ' + destination + ';')


if __name__ == '__main__':
    main()
