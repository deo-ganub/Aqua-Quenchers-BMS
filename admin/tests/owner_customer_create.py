"""Actual Owner customer save with persisted unique intent and exact reconciliation."""
import argparse
import hashlib
import hmac
import time
import secrets
import uuid
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
    parser.add_argument('--no-order-fixture', action='store_true')
    options = parser.parse_args()
    assert options.owner, 'Only missing Owner path; reuse existing verified Staff creation'
    state_path = LOCAL / ('customer-lookup-empty-state.json' if options.no_order_fixture else 'owner-customer-create-state.json')
    assert not state_path.exists(), 'Inspect prior customer intent/outcome before retry'
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
    nonce = secrets.token_hex(4)
    contact = '09' + str(int(nonce,16) % 1000000000).zfill(9)
    assert not call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'customers', 'search': contact}}, token)['items']
    fixture = {'kind': 'SYNTHETIC_LOCAL_OWNER_CUSTOMER_CREATE_UI', 'username': username, 'password': password, 'customer_name': 'LOCAL OWNER CUSTOMER ' + nonce, 'contact': contact, 'contact_display': contact[:4]+' '+contact[4:7]+' '+contact[7:], 'address': 'LOCAL SYNTHETIC OWNER CUSTOMER ' + nonce, 'customer_type':'walk_in' if options.no_order_fixture else 'dealer','type_index':3 if options.no_order_fixture else 2,'unit_centavos':3500 if options.no_order_fixture else 3000,'verify_no_history':options.no_order_fixture}
    before = json.loads(sql(snapshot_sql))
    state = {'kind': fixture['kind'], 'contact': contact, 'intended_name': fixture['customer_name'], 'intended_type': fixture['customer_type'], 'intended_address': fixture['address'], 'before': before, 'completed': False}
    private_json(state_path, state); private_json(LOCAL / ('customer-lookup-empty-fixture.json' if options.no_order_fixture else 'owner-customer-create-fixture.json'), fixture)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/owner-customer-create-credentials.json'), input=json.dumps(fixture).encode())
    run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OwnerCustomerCreateDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]'); evidence = REPO / ('evidence/android-customer-lookup-empty-create.log' if options.no_order_fixture else 'evidence/android-owner-customer-create.log'); evidence.write_text(text)
    # Resolve the unique persisted intent BEFORE allowing any future retry.
    after = json.loads(sql(snapshot_sql)); created = [row for row in after['customers'] if row['contact_number'] == contact]
    state['observed_customers'] = created; private_json(state_path, state)
    assert 'OK (1 test)' in text and len(created)==1, 'Inspect recorded intent/customer outcome before retry'
    row = created[0]; uuid.UUID(row['id'])
    assert (row['name'],row['customer_type'],row['contact_number'],row['address']) == (fixture['customer_name'],fixture['customer_type'],contact,fixture['address'])
    assert len(after['customers']) == len(before['customers'])+1
    after['customers'] = [r for r in after['customers'] if r['id'] != row['id']]
    assert after == before, 'Owner customer creation changed unrelated records or finances'
    if options.no_order_fixture: assert not any(o['customer_id']==row['id'] for o in before['orders'])
    state.update(completed=True, customer_id=row['id']); private_json(state_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/owner-customer-create-credentials.json')
    with evidence.open('a') as log: log.write('\nPASS: actual Owner customer save/normalized display-spaced contact, saved profile/list and selected fixed-price2gallon preview, cancelled Order. Exactly1server UUID customer; every preexisting customer/financial/stock/profile/Auth identity/factor mapping unchanged.\n')
    print('PASS: actual Owner customer creation and exact nonfinancial reconciliation')


if __name__ == '__main__': main()
