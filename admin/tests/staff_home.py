"""Read-only real Staff Home/queue/permission integration with exact snapshot check."""
import datetime
import json
import os
import re
import shlex
import ssl
import subprocess
import urllib.request
import urllib.error
import uuid
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement):
        return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1'], input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + table + "',(select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) from aq_private." + table + ' t)' for table in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations', 'profiles', 'customers']) + ');'
    profiles = json.loads(sql("select jsonb_agg(jsonb_build_object('id',user_id,'username',username)) from aq_private.profiles where role='staff' and active;"))
    staff_id = json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id']
    staff = next(p for p in profiles if p['id'] == staff_id)
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(path, body, token=None, expected=200):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        try:
            with urllib.request.urlopen(req, context=context, timeout=20) as response: status, data = response.status, json.load(response)
        except urllib.error.HTTPError as error: status, data = error.code, json.loads(error.read())
        assert status == expected, f'Staff endpoint status{status}, expected{expected}'
        return data
    token = call('/functions/v1/username-login', {'username': staff['username'], 'password': c['staff_password']})['access_token']
    fixture = {'kind': 'SYNTHETIC_LOCAL_STAFF_HOME_UI', 'username': staff['username'], 'password': c['staff_password'], 'today': str(datetime.datetime.now(ZoneInfo('Asia/Manila')).date()), 'home': call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'home'}}, token)}
    fixture['me'] = call('/rest/v1/rpc/aq_me', {}, token)
    fixture['full_name'] = fixture['me']['full_name']
    fixture['current_order'] = call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'orders'}}, token)['items'][0]
    private_json(LOCAL / 'staff-home-fixture.json', fixture)
    before = json.loads(sql(snapshot_sql))
    target = json.loads((LOCAL / 'managed-account-state.json').read_text())['target_id']
    call('/functions/v1/staff-admin', {'operation': 'set_active', 'request_id': str(uuid.uuid4()), 'user_id': target, 'active': False}, token, expected=403)
    assert json.loads(sql(snapshot_sql)) == before, 'Staff account-management denial changed records'
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/staff-home-credentials.json'), input=json.dumps(fixture).encode())
    run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.StaffHomeDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
    evidence = REPO / 'evidence/android-staff-home.log'; evidence.write_text(text)
    assert 'OK (1 test)' in text, 'Inspect Staff Home evidence before retry'
    assert json.loads(sql(snapshot_sql)) == before, 'Read-only Staff workflow changed business state'
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/staff-home-credentials.json')
    with evidence.open('a') as log:
        log.write('\nPASS: actual Staff counters/queue filters/empty states/new-order date/POS/settings links; Owner surfaces absent; report/expense/account-list denied403 and account mutation denied403 and revalidated active Staff workspace retained. Full financial/stock snapshots unchanged.\n')
    print('PASS: real Staff Home/queue navigation/Owner permission denial with exact unchanged business records')


if __name__ == '__main__':
    main()
