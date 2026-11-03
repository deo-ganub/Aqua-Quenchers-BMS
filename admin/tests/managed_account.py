"""Real native managed-MFA account edit/deactivation/reactivation, synthetic only."""
import json
import os
import re
import secrets
import shlex
import ssl
import subprocess
import time
import urllib.error
import urllib.request
from connected_android import ADB, LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'managed-account-state.json'
    if state_path.exists():
        raise RuntimeError('Prior account state exists; inspect stable identity/active state before any retry')
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement):
        return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1'], input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + table + "',(select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) from aq_private." + table + ' t)' for table in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations']) + ');'
    source = json.loads((LOCAL / 'native-fixture.json').read_text())
    assert source['kind'] == 'SYNTHETIC_LOCAL_CONNECTED_UI'
    profiles = json.loads(sql('select jsonb_agg(to_jsonb(p)) from aq_private.profiles p;'))
    target = next(p for p in profiles if p['username'] == source['new_staff_username'])
    assert target['role'] == 'staff' and target['active']
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def http(path, body, token=None, expected=200):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        try:
            with urllib.request.urlopen(req, context=context, timeout=20) as r:
                status, raw = r.status, r.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        assert status == expected, f'Local managed account {path.split("?")[0]} status{status}, expected{expected}'
        return json.loads(raw) if raw else None
    old = http('/functions/v1/username-login', {'username': target['username'], 'password': source['new_staff_password']})
    assert old['user']['id'] == target['user_id'] and old['user']['role'] == 'staff'
    assert http('/rest/v1/rpc/aq_me', {}, old['access_token'])['user_id'] == target['user_id']
    nonce = secrets.token_hex(4)
    fixture = {'kind': 'SYNTHETIC_LOCAL_MANAGED_ACCOUNT_UI', 'run_id': nonce,
               'owner_username': c['owner_username'], 'owner_password': c['owner_password'],
               'owner_totp_secret': json.loads((LOCAL / 'http-test-state.json').read_text())['factor_secret'],
               'target_id': target['user_id'], 'old_name': target['full_name'], 'old_username': target['username'],
               'new_name': 'Local managed Staff ' + nonce, 'new_username': 'managed.' + nonce}
    state = {'kind': fixture['kind'], 'run_id': nonce, 'target_id': target['user_id'], 'before': json.loads(sql(snapshot_sql)), 'completed': False}
    private_json(state_path, state); private_json(LOCAL / 'managed-account-fixture.json', fixture)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/managed-account-credentials.json'), input=json.dumps(fixture).encode())
    transcript = LOCAL / 'managed-account-instrumentation.log'
    checked = False
    with transcript.open('wb') as log:
        process = subprocess.Popen(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.ManagedAccountDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 360
        while process.poll() is None:
            if time.monotonic() > deadline:
                process.terminate(); raise RuntimeError('Managed native bounded wait exceeded; inspect actual profile state')
            probe = subprocess.run(ADB + ['exec-out', 'run-as', PACKAGE, 'cat', 'files/managed-account-result.json'], capture_output=True)
            try:
                progress = json.loads(probe.stdout) if probe.returncode == 0 else {}
            except json.JSONDecodeError:
                progress = {}
            if progress.get('run_id') == nonce and progress.get('phase') == 'waiting_inactive_checks' and not checked:
                http('/rest/v1/rpc/aq_me', {}, old['access_token'], expected=403)
                http('/functions/v1/username-login', {'username': target['username'], 'password': source['new_staff_password']}, expected=401)
                http('/functions/v1/username-login', {'username': fixture['new_username'], 'password': source['new_staff_password']}, expected=401)
                private_json(state_path, {**state, 'inactive_old_session_and_signins_denied': True})
                ack = 'files/managed-inactive-ready-' + nonce + '.json'
                adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > ' + ack), input=b'{"ready":true}')
                checked = True
            time.sleep(.2)
    text = transcript.read_text().replace(c['owner_password'], '[private fixture withheld]')
    (REPO / 'evidence/android-managed-account.log').write_text(text)
    progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/managed-account-result.json').stdout)
    private_json(LOCAL / 'managed-account-result.json', progress)
    assert process.returncode == 0 and 'OK (1 test)' in text and progress['run_id'] == nonce and progress.get('completed') and checked, 'Inspect stable account/MFA stage before retry'
    fresh = http('/functions/v1/username-login', {'username': fixture['new_username'], 'password': source['new_staff_password']})
    assert fresh['user']['id'] == target['user_id'] and fresh['user']['role'] == 'staff'
    http('/functions/v1/username-login', {'username': target['username'], 'password': source['new_staff_password']}, expected=401)
    assert json.loads(sql(snapshot_sql)) == state['before'], 'Account edit changed financial/stock records'
    after = json.loads(sql('select jsonb_agg(to_jsonb(p)) from aq_private.profiles p;'))
    assert len(after) == len(profiles)
    actual = next(p for p in after if p['user_id'] == target['user_id'])
    assert actual['username'] == fixture['new_username'] and actual['full_name'] == fixture['new_name'] and actual['active'] and actual['contact_number'] == target['contact_number']
    state.update(completed=True, inactive_old_session_and_signins_denied=True); private_json(state_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/managed-account-credentials.json')
    with (REPO / 'evidence/android-managed-account.log').open('a') as log:
        log.write('\nPASS: actual native cancel/MFA-cancel/rotation, real TOTP/AAL2 Staff rename+deactivate; old session403 and old/new alias signins401 while inactive; actual TOTP reactivation and fresh same-ID Staff login/new alias. No duplicate account; all financial/stock records exact unchanged.\n')
    print('PASS: real native managed-MFA Staff edit/deactivation/reactivation and account/financial reconciliation.')


if __name__ == '__main__':
    main()
