"""Actual own-profile validation/duplicate/cancel UI and immutable business snapshot."""
import argparse
import hashlib
import hmac
import time
import json
import os
import re
import shlex
import subprocess
import ssl
import urllib.request
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--owner', action='store_true')
    options = parser.parse_args()
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    assert json.loads((LOCAL / 'own-password-state.json').read_text())['completed'], 'Wait for owned password test to release emulator'
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement): return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1'], input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + t + "',(select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),'[]'::jsonb) from aq_private." + t + ' x)' for t in ['customers', 'orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations', 'profiles']) + ');'
    snapshot_sql = snapshot_sql.rstrip(';') + " || jsonb_build_object('auth_identity_mappings',(select jsonb_agg(jsonb_build_object('id',id,'email',email) order by id) from auth.users),'verified_factors',(select jsonb_agg(jsonb_build_object('id',id,'user_id',user_id) order by id) from auth.mfa_factors where status='verified'));"
    profiles = json.loads(sql("select jsonb_agg(jsonb_build_object('id',user_id,'username',username)) from aq_private.profiles where role='staff';"))
    ids = json.loads((LOCAL / 'http-test-state.json').read_text()); staff = next(p for p in profiles if p['id'] == ids['staff_id'])
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(path, body, token=None):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as response: return json.load(response)
    username = c['owner_username'] if options.owner else staff['username']
    password = c['owner_password'] if options.owner else c['staff_password']
    # Inspect the owned fixture only. Never reset counters or change source.
    def bucket(kind, value):
        body = json.dumps({kind: value}, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()
        return hmac.new(c['SUPABASE_SERVICE_ROLE_KEY'].encode(), body, hashlib.sha256).hexdigest()
    user_bucket, source_bucket = 'u:' + bucket('username', username), 's:' + bucket('source', '127.0.0.1')
    for attempt in range(35):
        delay = int(sql("select coalesce(max(ceil(greatest(0,extract(epoch from started_at+interval '15 minutes'-clock_timestamp()))))::int,0) from aq_private.login_windows where (bucket='" + user_bucket + "' and attempts+2>10) or (bucket='" + source_bucket + "' and attempts+2>60);"))
        if delay == 0: break
        print('Waiting for actual login quota window:', delay, 'seconds; no reset.', flush=True)
        time.sleep(min(30, delay + 1))
    else: raise RuntimeError('Login quota window did not expire; inspect before retry')
    token = call('/functions/v1/username-login', {'username': username, 'password': password})['access_token']
    me = call('/rest/v1/rpc/aq_me', {}, token)
    duplicate = next(p for p in profiles if p['id'] != me['user_id'])
    fixture = {'kind': 'SYNTHETIC_LOCAL_OWN_PROFILE_NEGATIVE_UI', 'username': username, 'password': password, 'owner': options.owner, 'full_name': me['full_name'], 'contact': me['contact_number'], 'duplicate_username': duplicate['username']}
    private_json(LOCAL / 'own-profile-negative-fixture.json', fixture); before = json.loads(sql(snapshot_sql))
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/own-profile-negative-credentials.json'), input=json.dumps(fixture).encode())
    run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OwnProfileNegativeDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]'); evidence = REPO / ('evidence/android-own-profile-negative-owner.log' if options.owner else 'evidence/android-own-profile-negative.log'); evidence.write_text(text)
    assert 'OK (1 test)' in text, 'Inspect native own-profile-negative evidence before retry'
    assert json.loads(sql(snapshot_sql)) == before
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/own-profile-negative-credentials.json')
    with evidence.open('a') as log: log.write('\nPASS: actual ' + ('Owner' if options.owner else 'Staff') + ' required name/username/contact, oversized name/invalid contact/real invalid and duplicate aliases, rotation and Cancel. Customers/financial/stock/profiles/Auth identity and verified-factor mappings unchanged.\n')
    print('PASS: native own-profile validation/alias rejection/rotation/cancel and exact business immutability')


if __name__ == '__main__': main()
