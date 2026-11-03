"""Actual native named-credit creation/payment; guarded synthetic local fixture."""
import argparse
import datetime
import json
import secrets
import shlex
import ssl
import urllib.request
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resume', action='store_true', help='Resume inspected state, skipping verified roles; inspect ambiguous native mutations first.')
    args = parser.parse_args()
    config = json.loads((LOCAL / 'private-config.json').read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and config['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'credit-state.json'
    if state_path.exists() and not args.resume:
        raise RuntimeError('Credit state exists; inspect actual sales/receipts before resuming')
    context = ssl.create_default_context(cafile=config['ca_cert'])
    def http(path, body, token=None):
        req = urllib.request.Request(config['url'] + path, data=json.dumps(body).encode(), headers={
            'apikey': config['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or config['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as response:
            return json.load(response)
    token = http('/functions/v1/username-login', {'username': config['owner_username'], 'password': config['owner_password']})['access_token']
    def query(resource, **fields):
        return http('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, token)
    date = str(datetime.datetime.now(ZoneInfo('Asia/Manila')).date())
    state = json.loads(state_path.read_text()) if args.resume else {'kind': 'SYNTHETIC_LOCAL_CREDIT_UI', 'run_id': secrets.token_hex(4), 'date': date, 'stock': query('inventory'), 'report': query('report', start_date=date, end_date=date), 'completed': False}
    assert not state['completed'] and state['date'] == date
    private_json(state_path, state)
    staff_id = json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id']
    staff = next(p for p in query('staff')['items'] if p['user_id'] == staff_id)
    assert state['stock']['filled_available'] >= 2
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    evidence = REPO / 'evidence/android-named-credit.log'
    logs = [evidence.read_text()] if args.resume and evidence.exists() else []
    for role in ['owner', 'staff']:
        if state.get(role) == 'VERIFIED':
            continue
        name = 'Local credit ' + role + ' ' + state['run_id']
        assert not query('sales', search=name)['items'], 'A matching sale exists; reconcile before resuming native mutations'
        fixture = {'kind': state['kind'], 'run_id': state['run_id'] + '-' + role, 'customer_name': name, 'owner': role == 'owner',
                   'username': config['owner_username'] if role == 'owner' else staff['username'], 'password': config['owner_password'] if role == 'owner' else config['staff_password']}
        private_json(LOCAL / ('credit-fixture-' + role + '.json'), fixture)
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/credit-test-credentials.json'), input=json.dumps(fixture).encode())
        run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.NamedCreditDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
        text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
        logs.append(role.upper() + '\n' + text); evidence.write_text('\n'.join(logs))
        progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/credit-test-result.json').stdout)
        private_json(LOCAL / ('credit-result-' + role + '.json'), progress)
        assert 'OK (1 test)' in text and progress['run_id'] == fixture['run_id'] and progress.get('completed'), 'Inspect credit result/sale before retry'
        rows = query('sales', search=name)['items']; assert len(rows) == 1
        sale = query('sale', reference=rows[0]['reference'])
        assert sale['id'] == progress['sale_id'] and sale['customer_id'] is None
        assert (sale['total_centavos'], sale['balance_centavos'], sale['payment_status']) == (3500, 1750, 'partially_paid')
        assert len(sale['payments']) == 1 and sale['payments'][0]['amount_centavos'] == 1750
        state[role] = 'VERIFIED'; private_json(state_path, state)
    stock = query('inventory'); report = query('report', start_date=date, end_date=date)
    assert stock['physical_filled'] == state['stock']['physical_filled'] - 2
    assert stock['empty_quantity'] == state['stock']['empty_quantity'] and stock['reserved_filled'] == state['stock']['reserved_filled']
    for key, delta in {'revenue_centavos': 7000, 'payments_received_centavos': 3500, 'expenses_centavos': 0, 'net_income_centavos': 3500, 'outstanding_centavos': 3500}.items():
        assert report[key] == state['report'][key] + delta, key
    state['completed'] = True; private_json(state_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/credit-test-credentials.json')
    with evidence.open('a') as log:
        log.write('\nPASS: Owner/Staff invalid and cancelled POS have no effects; actual zero-cash named credit and partial receipt each. Independent2sales/7000charges/3500receipts/outstanding+3500/filled-2; expenses/empty/reservations unchanged.\n')
    print('PASS: actual native Owner/Staff named credit, cancellation/validation and partial receipts reconcile exactly.')


if __name__ == '__main__':
    main()
