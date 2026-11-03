"""Real native Owner/Staff own password changes; recover confirmed synthetic credentials."""
import argparse
import hashlib
import hmac
import json
import os
import re
import secrets
import shlex
import subprocess
import time
import urllib.request
import urllib.error
import ssl
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resume', action='store_true', help='After inspecting state, skip verified roles and resume only unchanged credentials.')
    args = parser.parse_args()
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'own-password-state.json'
    if state_path.exists() and not args.resume: raise RuntimeError('Prior password intent exists; inspect outcome/config before retry')
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement, **variables):
        cmd = ['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1']
        for key, value in variables.items(): cmd += ['-v', key + '=' + value]
        return subprocess.run(cmd, input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + table + "',(select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) from aq_private." + table + ' t)' for table in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations', 'profiles']) + ",'auth_ids',(select jsonb_agg(id order by id) from auth.users),'verified_factors',(select jsonb_agg(id order by id) from auth.mfa_factors where status='verified'));"
    state = json.loads(state_path.read_text()) if args.resume else {'kind': 'SYNTHETIC_LOCAL_OWN_PASSWORD_UI', 'run_id': secrets.token_hex(4), 'roles': {}, 'before': json.loads(sql(snapshot_sql)), 'completed': False}
    assert not state['completed']; private_json(state_path, state)
    profiles = json.loads(sql('select jsonb_agg(to_jsonb(p)) from aq_private.profiles p;'))
    staff_id = json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id']
    def bucket(kind, value):
        body = json.dumps({kind: value}, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()
        return hmac.new(c['SUPABASE_SERVICE_ROLE_KEY'].encode(), body, hashlib.sha256).hexdigest()
    def quota(username, budget):
        # Inspect only; never reset counters, alter source address or bypass login.
        for _ in range(100):
            delay = int(sql("select coalesce(max(ceil(greatest(0,extract(epoch from started_at+interval '15 minutes'-clock_timestamp()))))::int,0) from aq_private.login_windows where (bucket=:'username' and attempts+" + str(budget) + ">10) or (bucket=:'source' and attempts+" + str(budget) + ">60);", username='u:' + bucket('username', username), source='s:' + bucket('source', '127.0.0.1')))
            if delay == 0: return
            print('Waiting for actual login quota window:', delay, 'seconds; no counter reset.', flush=True)
            time.sleep(min(30, delay + 1))
        raise RuntimeError('Login window did not expire as expected')
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def probe(username, password):
        req = urllib.request.Request(c['url'] + '/functions/v1/username-login', data=json.dumps({'username': username, 'password': password}).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + c['SUPABASE_ANON_KEY'], 'content-type': 'application/json'})
        try:
            with urllib.request.urlopen(req, context=context, timeout=20) as r: return r.status, json.load(r)
        except urllib.error.HTTPError as error: return error.code, json.loads(error.read())
    def confirmed_config(role, fixture):
        current = json.loads((LOCAL / 'private-config.json').read_text())
        assert current['kind'] == c['kind'] and current['url'] == c['url']
        key = role + '_password'
        assert current[key] in [fixture['password'], fixture['new_password']], 'Concurrent credential change; preserve it'
        current[key] = fixture['new_password']; private_json(LOCAL / 'private-config.json', current)
        c[key] = fixture['new_password']
        state['roles'][role]['password_updated'] = True; state['roles'][role]['private_config_updated'] = True; private_json(state_path, state)
    evidence = REPO / 'evidence/android-own-password.log'
    transcripts = [evidence.read_text()] if args.resume and evidence.exists() else []
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    failures = []
    for role in ['owner', 'staff']:
        if state['roles'].get(role, {}).get('verified'): continue
        if state['roles'].get(role, {}).get('password_updated'): raise RuntimeError('Password already changed; verify new candidate login without repeating update')
        person = next(p for p in profiles if p['user_id'] == (c['owner_id'] if role == 'owner' else staff_id))
        quota(person['username'], 9 if role == 'owner' else 7)
        fixture_path = LOCAL / ('own-password-fixture-' + role + '.json')
        fixture = json.loads(fixture_path.read_text()) if args.resume and fixture_path.exists() else {'kind': state['kind'], 'run_id': state['run_id'] + '-' + role, 'owner': role == 'owner', 'user_id': person['user_id'], 'username': person['username'], 'password': c[role + '_password'], 'new_password': secrets.token_urlsafe(24) + 'Aq9!'}
        if role == 'owner': fixture['owner_totp_secret'] = json.loads((LOCAL / 'http-test-state.json').read_text())['factor_secret']
        assert fixture['password'] == c[role + '_password'] and fixture['new_password'] != fixture['password']
        private_json(fixture_path, fixture); state['roles'][role] = {'run_id': fixture['run_id'], 'user_id': person['user_id'], 'update_intent': True}; private_json(state_path, state)
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/own-password-credentials.json'), input=json.dumps(fixture).encode())
        try:
            run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OwnPasswordDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=360)
            text = run.stdout.decode()
            for key in ['password', 'new_password', 'owner_totp_secret']:
                if key in fixture: text = text.replace(fixture[key], '[private fixture withheld]')
            transcripts.append(role.upper() + '\n' + text); evidence.write_text('\n'.join(transcripts))
        except subprocess.TimeoutExpired:
            adb('shell', 'am', 'force-stop', PACKAGE)
            failures.append(role + ' timeout; reconcile credential outcome')
            raise
        finally:
            raw = subprocess.run(['/workspace/tooling/android-sdk/platform-tools/adb', '-s', 'emulator-5554', 'exec-out', 'run-as', PACKAGE, 'cat', 'files/own-password-result.json'], capture_output=True)
            try: progress = json.loads(raw.stdout)
            except json.JSONDecodeError: progress = {}
            if progress.get('run_id') == fixture['run_id']:
                private_json(LOCAL / ('own-password-result-' + role + '.json'), progress)
                if progress.get('password_updated'): confirmed_config(role, fixture)
        native_ok = 'OK (1 test)' in text and progress.get('run_id') == fixture['run_id'] and progress.get('completed')
        if not native_ok and not state['roles'][role].get('password_updated') and progress.get('phase') == 'submitting_password':
            # Resolve an uncertain provider result before considering another update.
            status, result = probe(fixture['username'], fixture['new_password'])
            if status == 200:
                assert result['user']['id'] == person['user_id']; confirmed_config(role, fixture)
            elif status == 401:
                old_status, old = probe(fixture['username'], fixture['password'])
                assert old_status == 200 and old['user']['id'] == person['user_id'], 'Credential outcome unresolved; no retry'
                state['roles'][role]['unchanged_verified'] = True
            else: raise RuntimeError('Credential outcome check blocked; inspect private state, no update retry')
        if native_ok:
            assert state['roles'][role].get('private_config_updated'); state['roles'][role]['verified'] = True
            adb('shell', 'run-as', PACKAGE, 'rm', 'files/own-password-credentials.json')
        else: failures.append(role + ' native validation failed; inspect private result/config')
        private_json(state_path, state)
    assert json.loads(sql(snapshot_sql)) == state['before'], 'Password workflow changed business/profile/identity/factor mapping'
    if failures: raise RuntimeError('; '.join(failures))
    state['completed'] = True; private_json(state_path, state)
    with evidence.open('a') as log: log.write('\nPASS: Owner/Staff min15/mismatch/wrong-current validation; Owner managed-MFA cancel/invalid/valid step-up; actual password update acknowledged, old credentials rejected, new same-ID login accepted. Private synthetic config updated only after confirmed provider ack. Full business/profiles/Auth IDs/verified-factor mappings unchanged.\n')
    print('PASS: actual Owner MFA and Staff own-password updates/new-old authentication; credential config reconciled privately')


if __name__ == '__main__': main()
