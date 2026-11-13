"""Real managed deactivation and negative idle fixture validate denied-session safety."""
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import shlex
import struct
import subprocess
import time
import urllib.request
import ssl
import uuid
from connected_android import ADB, LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'denied-session-state.json'
    if state_path.exists(): raise RuntimeError('Prior denial state exists; inspect profile/session outcome before retry')
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(path, body, token=None):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as response: return json.load(response)
    owner = call('/functions/v1/username-login', {'username': c['owner_username'], 'password': c['owner_password']})['access_token']
    auth = json.loads((LOCAL / 'http-test-state.json').read_text())
    factor = '/auth/v1/factors/' + auth['factor_id']
    challenge = call(factor + '/challenge', {}, owner)
    secret = auth['factor_secret']
    digest = hmac.new(base64.b32decode(secret + '=' * (-len(secret) % 8)), struct.pack('>Q', int(time.time()) // 30), hashlib.sha1).digest()
    offset = digest[-1] & 15
    code = str((struct.unpack('>I', digest[offset:offset+4])[0] & 0x7fffffff) % 1000000).zfill(6)
    owner = call(factor + '/verify', {'challenge_id': challenge['id'], 'code': code}, owner)['access_token']
    managed = json.loads((LOCAL / 'managed-account-state.json').read_text()); assert managed['completed']
    target = next(p for p in call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'staff'}}, owner)['items'] if p['user_id'] == managed['target_id'])
    assert target['active'] and target['role'] == 'staff' and target['username'].startswith('managed.')
    uid = str(uuid.UUID(target['user_id']))
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement):
        return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1', '-v', 'target=' + uid], input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + table + "',(select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) from aq_private." + table + ' t)' for table in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations']) + ');'
    before = json.loads(sql(snapshot_sql)); nonce = secrets.token_hex(4)
    fixture = {'kind': 'SYNTHETIC_LOCAL_DENIED_SESSION_UI', 'run_id': nonce, 'username': target['username'], 'password': json.loads((LOCAL / 'native-fixture.json').read_text())['new_staff_password']}
    state = {'kind': fixture['kind'], 'run_id': nonce, 'target_id': uid, 'requests': {}, 'completed': False}
    private_json(state_path, state); private_json(LOCAL / 'denied-session-fixture.json', fixture)
    def active(flag):
        label = 'reactivate' if flag else 'deactivate'
        rid = state['requests'].setdefault(label, str(uuid.uuid4())); private_json(state_path, state)
        result = call('/functions/v1/staff-admin', {'operation': 'set_active', 'request_id': rid, 'user_id': uid, 'active': flag}, owner)
        assert result['user_id'] == uid and result['active'] is flag
    def ack(name):
        path = 'files/' + name + '-' + nonce + '.json'
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > ' + path), input=b'{"ready":true}')
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/denied-session-credentials.json'), input=json.dumps(fixture).encode())
    changed = restored = expired = False; process = None
    try:
        with (LOCAL / 'denied-session-instrumentation.log').open('wb') as log:
            process = subprocess.Popen(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.DeniedSessionDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 300
            while process.poll() is None:
                if time.monotonic() > deadline: process.terminate(); raise RuntimeError('Denial test exceeded bounded wait')
                probe = subprocess.run(ADB + ['exec-out', 'run-as', PACKAGE, 'cat', 'files/denied-session-result.json'], capture_output=True)
                try: progress = json.loads(probe.stdout) if probe.returncode == 0 else {}
                except json.JSONDecodeError: progress = {}
                if progress.get('run_id') == nonce:
                    phase = progress.get('phase')
                    if phase == 'ready_for_deactivation' and not changed:
                        changed = True; state['deactivation_intent'] = True; private_json(state_path, state)
                        active(False); ack('deactivated')
                    elif phase == 'inactive_denied' and not restored:
                        active(True); restored = True; ack('reactivated')
                    elif phase == 'ready_for_expiry' and not expired:
                        count = int(sql("with changed as (update aq_private.sessions set last_seen_at=clock_timestamp()-interval '31 minutes' where user_id=:'target'::uuid and revoked_at is null returning 1) select count(*) from changed;"))
                        assert count > 0
                        expired = True; state['negative_31minute_fixture'] = True; private_json(state_path, state); ack('expired')
                time.sleep(.2)
        text = (LOCAL / 'denied-session-instrumentation.log').read_text().replace(fixture['password'], '[private fixture withheld]')
        (REPO / 'evidence/android-denied-session.log').write_text(text)
        progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/denied-session-result.json').stdout)
        private_json(LOCAL / 'denied-session-result.json', progress)
        assert process.returncode == 0 and 'OK (1 test)' in text and progress.get('completed') and changed and restored and expired, 'Inspect denied-session phase before retry'
        assert json.loads(sql(snapshot_sql)) == before
        state['completed'] = True; state['active_restored'] = True; private_json(state_path, state)
        adb('shell', 'run-as', PACKAGE, 'rm', 'files/denied-session-credentials.json')
        with (REPO / 'evidence/android-denied-session.log').open('a') as log: log.write('\nPASS: real managed-MFA deactivated dedicated Staff signs out on protected denial; checked reactivation permits fresh login; unchanged30minute policy rejects forbidden request after controlled31minute session timestamp and recheck clears session. All financial/stock records unchanged.\n')
        print('PASS: inactive and expired denial revalidation still ends native session; financial/stock snapshots unchanged')
    finally:
        if changed and not restored: active(True)
        if process is not None and process.poll() is None: process.terminate()


if __name__ == '__main__': main()
