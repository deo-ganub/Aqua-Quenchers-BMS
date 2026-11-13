"""Checked synthetic carry-over fixture + actual Staff card/queue/final delivery."""
import datetime
import json
import secrets
import shlex
import ssl
import urllib.request
import uuid
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'carryover-state.json'
    if state_path.exists(): raise RuntimeError('Prior carry-over intent exists; inspect commands/order/delivery before retry')
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(path, body, token=None):
        req = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as response: return json.load(response)
    owner = call('/functions/v1/username-login', {'username': c['owner_username'], 'password': c['owner_password']})['access_token']
    def query(resource, **fields): return call('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, owner)
    today = datetime.datetime.now(ZoneInfo('Asia/Manila')).date(); past = str(today - datetime.timedelta(days=1))
    home = query('home'); assert home['pending_quantity'] == 0, 'Existing carry-over needs separate reviewed fixture selection'
    reports = {date: query('report', start_date=date, end_date=date) for date in [str(today), past]}
    staff_id = json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id']
    staff = next(p for p in query('staff')['items'] if p['user_id'] == staff_id)
    nonce = secrets.token_hex(4)
    state = {'kind': 'SYNTHETIC_LOCAL_CARRYOVER_UI', 'run_id': nonce, 'today': str(today), 'business_date': past, 'home_before': home, 'reports_before': reports, 'commands': {}, 'completed': False}
    private_json(state_path, state)
    def command(label, operation, **fields):
        payload = {'operation': operation, 'request_id': str(uuid.uuid4()), **fields}
        assert label not in state['commands']; state['commands'][label] = {'payload': payload}; private_json(state_path, state)
        result = call('/rest/v1/rpc/aq_command', {'p_request': payload}, owner)
        state['commands'][label]['result'] = result; private_json(state_path, state)
        return result
    customer = command('customer', 'create_customer', name='LOCAL CARRYOVER ' + nonce, customer_type='dealer', contact_number='09' + str(int(nonce, 16) % 1000000000).zfill(9), address='LOCAL SYNTHETIC CARRYOVER')
    order = command('order', 'create_order', customer_id=customer['id'], quantity=3, business_date=past)
    state['order_id'] = order['id']; private_json(state_path, state)
    command('for_pickup', 'transition_order', order_id=order['id'], status='for_pickup')
    command('pickup', 'record_pickup', order_id=order['id'], quantity=3)
    command('refilling', 'transition_order', order_id=order['id'], status='refilling')
    command('ready', 'transition_order', order_id=order['id'], status='ready_for_delivery')
    command('partial', 'record_delivery', order_id=order['id'], quantity=1)
    seeded = query('home'); assert seeded['pending_quantity'] == 2
    fixture = {'kind': state['kind'], 'run_id': nonce, 'username': staff['username'], 'password': c['staff_password'], 'order_id': order['id'], 'reference': order['reference'], 'business_date': past, 'home_before': home, 'home_seeded': seeded}
    private_json(LOCAL / 'carryover-fixture.json', fixture)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/carryover-credentials.json'), input=json.dumps(fixture).encode())
    run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.CarryoverQueueDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
    evidence = REPO / 'evidence/android-carryover-queue.log'; evidence.write_text(text)
    progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/carryover-result.json').stdout); private_json(LOCAL / 'carryover-result.json', progress)
    assert 'OK (1 test)' in text and progress['run_id'] == nonce and progress.get('completed'), 'Inspect carry-over order/deliveries before any retry'
    done = query('order', id=order['id']); stock = query('inventory')
    assert (done['status'], done['quantity'], done['delivered_quantity'], done['pending_quantity'], done['reserved_quantity'], done['total_centavos']) == ('delivered', 3, 3, 0, 0, 9000)
    assert done['business_date'] == past and len(done['deliveries']) == 2
    assert sorted(d['quantity'] for d in done['deliveries']) == [1, 2]
    final = next(d for d in done['deliveries'] if d['quantity'] == 2); assert final['recorded_by'] == staff_id
    for d in done['deliveries']: assert str(datetime.datetime.fromisoformat(d['created_at'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Manila')).date()) == str(today)
    for key in ['physical_filled', 'empty_quantity', 'reserved_filled']: assert stock[key] == home[key], key
    assert stock['pending_quantity'] == 0
    for date in [str(today), past]:
        report = query('report', start_date=date, end_date=date); before = reports[date]
        for key in ['payments_received_centavos', 'expenses_centavos', 'net_income_centavos']: assert report[key] == before[key], key
        assert report['revenue_centavos'] == before['revenue_centavos'] + (9000 if date == past else 0)
        assert report['outstanding_centavos'] == before['outstanding_centavos'] + 9000
    state['completed'] = True; private_json(state_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/carryover-credentials.json')
    with evidence.open('a') as log: log.write('\nPASS: populated actual Staff card/carry-over original date/1day/pending2, invalid deliveries unchanged, rotated final delivery2 clears reservation/queue and updates work counts. Stock returns to baseline; charge9000 retained on original prior business date, actual delivery timestamps today, receipts/expenses/net unchanged.\n')
    print('PASS: populated carry-over navigation/final delivery, work counts, original-date finances and exact stock reconciliation')


if __name__ == '__main__': main()
