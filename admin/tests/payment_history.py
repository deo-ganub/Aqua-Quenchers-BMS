"""Read-only actual customer-name payment-history UI and immutable business snapshot."""
import json
import os
import re
import shlex
import subprocess
import ssl
import urllib.request
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    assert json.loads((LOCAL / 'own-password-state.json').read_text())['completed'], 'Wait for owned password test to release emulator'
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement): return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1'], input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + t + "',(select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),'[]'::jsonb) from aq_private." + t + ' x)' for t in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations', 'profiles']) + ');'
    profiles = json.loads(sql("select jsonb_agg(jsonb_build_object('id',user_id,'username',username)) from aq_private.profiles where role='staff';"))
    ids = json.loads((LOCAL / 'http-test-state.json').read_text()); staff = next(p for p in profiles if p['id'] == ids['staff_id'])
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(path, body, token=None):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as response: return json.load(response)
    token = call('/functions/v1/username-login', {'username': staff['username'], 'password': c['staff_password']})['access_token']
    customer = call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'customer', 'id': ids['customer_id']}}, token)
    assert len(customer['payments']) == 2 and len(customer['orders']) == 1
    fixture = {'kind': 'SYNTHETIC_LOCAL_PAYMENT_HISTORY_UI', 'username': staff['username'], 'password': c['staff_password'], 'customer_name': customer['name'], 'order_reference': customer['orders'][0]['reference'], 'receipts': customer['payments']}
    private_json(LOCAL / 'payment-history-fixture.json', fixture); before = json.loads(sql(snapshot_sql))
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/payment-history-credentials.json'), input=json.dumps(fixture).encode())
    run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.PaymentHistoryDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]'); evidence = REPO / 'evidence/android-payment-history.log'; evidence.write_text(text)
    assert 'OK (1 test)' in text, 'Inspect native history evidence before retry'
    assert json.loads(sql(snapshot_sql)) == before
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/payment-history-credentials.json')
    with evidence.open('a') as log: log.write('\nPASS: actual Staff name-filtered2receipt history/ref/amount/recorder, Back retains name search, invoice-ref search still2, unknown name true empty. All business/profile records unchanged.\n')
    print('PASS: native customer-name/ref/empty payment history and exact business immutability')


if __name__ == '__main__': main()
