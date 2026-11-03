"""Actual native retry/refresh and signed-token expiry on the owned local stack.
No default expiry/inactivity policy, production service or financial history changes.
"""
import json
import pathlib
import secrets
import shlex
import subprocess
import time
import ssl
import urllib.error
import urllib.request
from connected_android import ADB, LOCAL, REPO, PACKAGE, adb, private_json


def main():
    config = json.loads((LOCAL / 'private-config.json').read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and config['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    context = ssl.create_default_context(cafile=config['ca_cert'])

    def http(path, body=None, token=None, expected=200):
        req = urllib.request.Request(config['url'] + path, data=None if body is None else json.dumps(body).encode(), headers={
            'apikey': config['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or config['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        try:
            response = urllib.request.urlopen(req, context=context, timeout=20)
            status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        value = json.loads(raw) if raw else None
        assert status == expected, f'{path.split("?")[0]} status{status}, expected{expected}'
        return value

    def login():
        return http('/functions/v1/username-login', {'username': config['owner_username'], 'password': config['owner_password']})['access_token']

    assert http('/short-auth/v1/health')['name'] == 'GoTrue'
    before = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'expenses'}}, login())['total_centavos']
    directory = LOCAL / 'faults'
    for name in ['pending.json', 'short-pending.json']:
        if (directory / name).exists():
            raise RuntimeError('Prior fault control remains; inspect its outcome before any retry.')
    nonce = secrets.token_hex(4)
    fixture = {'kind': 'SYNTHETIC_LOCAL_FAILURE_UI', 'run_id': nonce, 'owner_username': config['owner_username'],
               'owner_password': config['owner_password'], 'remarks': 'LOCAL RETRY ' + nonce}
    private_json(LOCAL / 'failure-fixture.json', fixture)
    private_json(directory / 'pending.json', {'kind': 'LOCAL_POST_COMMIT_FAULT', 'nonce': nonce, 'expires_at': time.time() + 300})
    private_json(directory / 'short-pending.json', {'kind': 'LOCAL_SHORT_AUTH', 'nonce': nonce, 'expires_at': time.time() + 300})
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/failure-test-credentials.json'), input=json.dumps(fixture).encode())
    transcript = LOCAL / 'failure-instrumentation-private.log'
    expired = False
    with transcript.open('wb') as out:
        process = subprocess.Popen(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.FailureRecoveryDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'], cwd=REPO, stdout=out, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 360
        while process.poll() is None:
            if time.monotonic() > deadline:
                process.terminate()
                raise RuntimeError('Native failure test exceeded bounded wait; inspect stage before retry.')
            probe = subprocess.run(ADB + ['exec-out', 'run-as', PACKAGE, 'cat', 'files/failure-test-result.json'], capture_output=True)
            try:
                progress = json.loads(probe.stdout) if probe.returncode == 0 else {}
            except json.JSONDecodeError:
                progress = {}
            if progress.get('run_id') == nonce and progress.get('phase') == 'waiting_for_backend_expiry' and not expired:
                command = ['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', config['database'], '-v', 'ON_ERROR_STOP=1', '-v', 'owner_uid=' + config['owner_id']]
                # Controlled NEGATIVE fixture state only; actual31-minute timestamps
                # exercise the unchanged30-minute backend policy with real JWTs.
                sql = "with changed as (update aq_private.sessions set last_seen_at=clock_timestamp()-interval '31 minutes' where user_id=:'owner_uid'::uuid and revoked_at is null returning 1) select count(*) from changed;"
                r = subprocess.run(command, input=sql, text=True, capture_output=True, check=True)
                assert int(r.stdout.strip()) > 0
                ack = 'files/backend-expiry-ready-' + nonce + '.json'
                adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > ' + ack), input=b'{"ready":true}')
                expired = True
            time.sleep(.2)
    text = transcript.read_text().replace(config['owner_password'], '[private fixture withheld]')
    (REPO / 'evidence/android-failure-recovery.log').write_text('REAL LOCAL OFFICIAL AUTH/REST + CONTROLLED TRANSPORT/IDLE FIXTURES; normal policies unchanged.\n' + text)
    progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/failure-test-result.json').stdout)
    private_json(LOCAL / 'failure-result.json', progress)
    assert process.returncode == 0 and 'OK (1 test)' in text and progress.get('completed') and expired, 'Inspect native failure-recovery evidence/stage before retry'
    upstream = json.loads((directory / ('result-' + nonce + '.json')).read_text())
    refresh = json.loads((directory / 'short-active.json').read_text())
    assert upstream['response_drained'] and upstream['upstream_status'] == 200
    assert refresh['nonce'] == nonce and refresh['expires_in'] == 2 and refresh['refresh_count'] >= 1 and refresh['refreshed_expires_in'] == 3600
    after = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'expenses'}}, login())
    rows = [row for row in after['items'] if row['remarks'] == fixture['remarks']]
    assert len(rows) == 1 and rows[0]['amount_centavos'] == 1234 and after['total_centavos'] == before + 1234
    # Independent real expiry: this is issued by official Auth, not a minted user fixture.
    short = http('/short-auth/v1/token?grant_type=password', {'email': config['owner_email'], 'password': config['owner_password']})
    assert short['expires_in'] == 2
    assert http('/rest/v1/rpc/aq_me', {}, short['access_token'])['role'] == 'owner'
    time.sleep(35)
    denied = http('/rest/v1/rpc/aq_me', {}, short['access_token'], expected=401)
    assert 'expired' in denied.get('message', '').lower()
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/failure-test-credentials.json')
    with (REPO / 'evidence/real-auth-expiry-retry.log').open('w') as log:
        log.write('PASS: official2-second signed token accepted then rejected401/expired; normal issuer remains3600 seconds.\n')
        log.write('PASS: native actual refresh returns3600-second token and protected Owner read succeeds.\n')
        log.write('PASS: upstream really committed expense; native shows error, recreates fields/keys, retries same expense ID; independent ledger delta1234/count1.\n')
        log.write('PASS: unchanged30-minute backend idle policy rejects real signed native session on resume after controlled31-minute fixture timestamp; fresh login succeeds.\n')
    print('PASS: real native refresh, ambiguous post-commit retry/rotation exact-once, backend idle resume and actual signed-token expiry.')


if __name__ == '__main__':
    main()
