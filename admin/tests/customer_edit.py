"""Actual Staff customer edit: profile changes must preserve all financial records."""
import argparse
import hashlib
import hmac
import time
import json
import os
import re
import secrets
import shlex
import ssl
import subprocess
import urllib.request
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--reviewed-owner-run',action='store_true')
    options=parser.parse_args()
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    previous_path=LOCAL/'customer-edit-host-state.json'
    previous=json.loads(previous_path.read_text()) if options.reviewed_owner_run else None
    if previous: assert previous['completed'] and previous['kind']=='SYNTHETIC_LOCAL_CUSTOMER_EDIT_UI'
    result_path = LOCAL / ('customer-edit-owner-host-state.json' if options.reviewed_owner_run else 'customer-edit-host-state.json')
    if result_path.exists():
        raise RuntimeError('Prior customer edit state exists; inspect profile and outcome before retry')
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement):
        return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1'], input=statement, capture_output=True, text=True, check=True, env=env).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + table + "',(select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) from aq_private." + table + ' t)' for table in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations', 'profiles']) + ');'
    staff_id = json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id']
    profiles = json.loads(sql("select jsonb_agg(jsonb_build_object('id',user_id,'username',username)) from aq_private.profiles where role='staff' and active;"))
    staff = next(p for p in profiles if p['id'] == staff_id)
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def http(path, body, token=None):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as r:
            return json.load(r)
    username=c['owner_username'] if options.reviewed_owner_run else staff['username']
    password=c['owner_password'] if options.reviewed_owner_run else c['staff_password']
    def bucket(kind,value): return hmac.new(c['SUPABASE_SERVICE_ROLE_KEY'].encode(),json.dumps({kind:value},sort_keys=True,separators=(',',':')).encode(),hashlib.sha256).hexdigest()
    for _ in range(35):
        delay=int(sql("select coalesce(max(ceil(greatest(0,extract(epoch from started_at+interval '15 minutes'-clock_timestamp()))))::int,0) from aq_private.login_windows where (bucket='u:"+bucket('username',username)+"' and attempts+2>10) or (bucket='s:"+bucket('source','127.0.0.1')+"' and attempts+2>60);"))
        if not delay: break
        print('Waiting actual login quota',delay,'seconds; no reset',flush=True);time.sleep(min(30,delay+1))
    else: raise RuntimeError('Inspect login quota before retry')
    token = http('/functions/v1/username-login', {'username':username,'password':password})['access_token']
    def query(resource, **fields):
        return http('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, token)
    operational = json.loads((LOCAL / 'operational-state.json').read_text())
    assert operational['completed'] and len(operational['orders']) == 2
    order = query('order', id=operational['orders'][1])
    customer = query('customer', id=previous['customer_id'] if previous else order['customer_id'])
    if not previous: assert customer['name'].startswith('Local pickup staff ') and customer['customer_type']=='dealer'
    else: assert customer['customer_type']=='household'
    assert len(customer['orders']) == 1 and len(customer['payments']) == 1
    duplicate = query('order', id=operational['orders'][0])['contact_number']
    nonce = secrets.token_hex(4)
    fixture = {'kind': 'SYNTHETIC_LOCAL_CUSTOMER_EDIT_UI', 'run_id': nonce, 'username':username,'password':password,'owner':options.reviewed_owner_run,'old_type':customer['customer_type'],'new_type':'dealer' if previous else 'household','new_type_index':2 if previous else 1,'new_unit':3000 if previous else 3500,'order_customer_name':customer['orders'][0]['customer_name'],
               'customer_id': customer['id'], 'old_name': customer['name'], 'contact': customer['contact_number'], 'duplicate_contact': duplicate,
               'new_name': 'Local edited customer ' + nonce, 'new_address': 'LOCAL EDITED ADDRESS ' + nonce}
    before = json.loads(sql(snapshot_sql))
    before_customers = query('customers')['items']
    identity_sql="select jsonb_agg(jsonb_build_object('id',id,'email',email) order by id) from auth.users;"
    identities=sql(identity_sql)
    state = {'kind': fixture['kind'], 'run_id': nonce, 'customer_id': customer['id'], 'before': before, 'completed': False}
    if previous:
        for name in ['customer-edit-host-state.json','customer-edit-fixture.json','customer-edit-result.json']:
            source=LOCAL/name
            if source.exists():
                archive=LOCAL/(name+'.verified-'+previous['run_id']);assert not archive.exists();archive.write_bytes(source.read_bytes());archive.chmod(0o600)
    private_json(result_path, state)
    private_json(LOCAL / 'customer-edit-fixture.json', fixture)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/customer-edit-credentials.json'), input=json.dumps(fixture).encode())
    r = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.CustomerEditDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = r.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
    (REPO / ('evidence/android-customer-edit-owner.log' if previous else 'evidence/android-customer-edit.log')).write_text(text)
    progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/customer-edit-result.json').stdout)
    private_json(LOCAL / 'customer-edit-result.json', progress)
    assert 'OK (1 test)' in text and progress['run_id'] == nonce and progress.get('completed'), 'Inspect profile/phase before retrying'
    assert json.loads(sql(snapshot_sql)) == before, 'Customer edit changed financial/stock snapshots'
    after = query('customer', id=customer['id'])
    assert after['name'] == fixture['new_name'] and after['customer_type'] == fixture['new_type'] and after['contact_number'] == fixture['contact'] and after['address'] == fixture['new_address']
    all_after=query('customers')['items']
    assert len(all_after)==len(before_customers)
    assert [x for x in all_after if x['id']!=customer['id']]==[x for x in before_customers if x['id']!=customer['id']]
    assert sql(identity_sql)==identities
    assert after['orders'] == customer['orders'] and after['payments'] == customer['payments']
    state['completed'] = True; private_json(result_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/customer-edit-credentials.json')
    with (REPO / ('evidence/android-customer-edit-owner.log' if previous else 'evidence/android-customer-edit.log')).open('a') as log:
        log.write('\nPASS: actual '+('Owner' if previous else 'Staff')+' cancel/invalid/duplicate/rotation corrected same-ID customer edit; original order/payment snapshots retained; prospective '+str(fixture['new_unit'])+'centavo price preview cancelled. All financial/stock/profiles/Auth identities/other customers unchanged; no duplicate.\n')
    print('PASS: native '+('Owner' if previous else 'Staff')+' customer edit preserves financial history and applies prospective pricing only.')


if __name__ == '__main__':
    main()
