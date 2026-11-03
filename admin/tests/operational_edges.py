"""Guarded synthetic native Owner/Staff pickup/reschedule edge verification."""
import datetime
import argparse
import json
import pathlib
import secrets
import shlex
import ssl
import subprocess
import urllib.request
import uuid
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resume', action='store_true', help='Resume inspected incomplete state without replaying verified roles.')
    args = parser.parse_args()
    config = json.loads((LOCAL / 'private-config.json').read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and config['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'operational-state.json'
    if state_path.exists() and not args.resume:
        raise RuntimeError('Operational state exists; inspect outcome before explicit archive/new fixture.')
    context = ssl.create_default_context(cafile=config['ca_cert'])
    def http(path, body, token=None):
        req = urllib.request.Request(config['url'] + path, data=json.dumps(body).encode(), headers={
            'apikey': config['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or config['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as r:
            return json.load(r)
    token = http('/functions/v1/username-login', {'username': config['owner_username'], 'password': config['owner_password']})['access_token']
    def query(resource, **fields):
        return http('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, token)
    state = json.loads(state_path.read_text()) if args.resume else {'kind': 'SYNTHETIC_LOCAL_OPERATIONAL_UI', 'run_id': secrets.token_hex(4), 'requests': {}, 'orders': [], 'completed': False}
    assert not state['completed']
    nonce = state['run_id']
    private_json(state_path, state)
    def command(operation, label, **fields):
        commands = state.setdefault('commands', {})
        if label not in commands:
            state['requests'][label] = str(uuid.uuid4())
            commands[label] = {'payload': {'operation': operation, 'request_id': state['requests'][label], **fields}}
            private_json(state_path, state)  # persist exact intent before external operation
        entry = commands[label]
        assert {k: v for k, v in entry['payload'].items() if k != 'request_id'} == {'operation': operation, **fields}, 'Changed payload on resume'
        if 'result' not in entry:
            entry['result'] = http('/rest/v1/rpc/aq_command', {'p_request': entry['payload']}, token)
            private_json(state_path, state)
        return entry['result']
    today = datetime.datetime.now(ZoneInfo('Asia/Manila')).date()
    if not args.resume:
        state['baseline'] = {'date': str(today), 'stock': query('inventory'), 'report': query('report', start_date=str(today), end_date=str(today))}
        private_json(state_path, state)
    assert state['baseline']['date'] == str(today), 'Business day changed; reconcile periods before resuming'
    before_stock = state['baseline']['stock']
    before_report = state['baseline']['report']
    staff = next(x for x in query('staff')['items'] if x['user_id'] == json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id'])
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    evidence = REPO / 'evidence/android-operational-edges.log'
    transcript = [evidence.read_text()] if args.resume and evidence.exists() else []
    for role in ['owner', 'staff']:
        if state.get(role) == 'VERIFIED':
            continue
        contact = '09' + str((int(nonce, 16) * 2 + (role == 'staff')) % 1000000000).zfill(9)
        customer = command('create_customer', role + '-customer', name='Local pickup ' + role + ' ' + nonce, customer_type='dealer', contact_number=contact, address='LOCAL SYNTHETIC FIXTURE')
        order = command('create_order', role + '-order', customer_id=customer['id'], quantity=10, business_date=str(today))
        state['orders'].append(order['id']); private_json(state_path, state)
        command('record_payment', role + '-payment', reference=order['reference'], amount_centavos=27000)
        command('transition_order', role + '-pickup', order_id=order['id'], status='for_pickup')
        fixture = {'kind': state['kind'], 'run_id': nonce + '-' + role, 'owner': role == 'owner',
                   'username': config['owner_username'] if role == 'owner' else staff['username'],
                   'password': config['owner_password'] if role == 'owner' else config['staff_password'],
                   'order_id': order['id'], 'reference': order['reference'], 'notes': 'Customer unavailable ' + role + ' ' + nonce,
                   'past_date': str(today - datetime.timedelta(days=1)), 'future_date': str(today + datetime.timedelta(days=1))}
        private_json(LOCAL / ('operational-fixture-' + role + '.json'), fixture)
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/operational-test-credentials.json'), input=json.dumps(fixture).encode())
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OperationalEdgeDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
        text = result.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
        transcript.append(role.upper() + '\n' + text)
        (REPO / 'evidence/android-operational-edges.log').write_text('\n'.join(transcript))
        progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/operational-test-result.json').stdout)
        private_json(LOCAL / ('operational-result-' + role + '.json'), progress)
        assert 'OK (1 test)' in text and progress['run_id'] == fixture['run_id'] and progress.get('completed'), 'Inspect operational evidence/stage before retry'
        actual = query('order', id=order['id'])
        assert (actual['status'], actual['expected_quantity'], actual['quantity'], actual['shortfall'], actual['total_centavos']) == ('picked_up', 10, 9, 1, 27000)
        assert actual['reschedule_notes'] == fixture['notes']
        state[role] = 'VERIFIED'; private_json(state_path, state)
    stock = query('inventory')
    report = query('report', start_date=str(today), end_date=str(today))
    assert stock['empty_quantity'] == before_stock['empty_quantity'] + 18
    assert stock['physical_filled'] == before_stock['physical_filled'] and stock['reserved_filled'] == before_stock['reserved_filled']
    assert report['revenue_centavos'] == before_report['revenue_centavos'] + 54000
    assert report['payments_received_centavos'] == before_report['payments_received_centavos'] + 54000
    assert report['expenses_centavos'] == before_report['expenses_centavos'] and report['outstanding_centavos'] == before_report['outstanding_centavos']
    assert report['net_income_centavos'] == before_report['net_income_centavos'] + 54000
    state['completed'] = True; private_json(state_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/operational-test-credentials.json')
    with (REPO / 'evidence/android-operational-edges.log').open('a') as log:
        log.write('\nPASS: Owner and Staff invalid collection/past-date/cancel/rotation/resume/short-pickup; exact2 orders,54,000 charges/receipts, empty+18 with filled/reservations/expenses/outstanding unchanged.\n')
    print('PASS: real native Owner/Staff pickup/reschedule edge checks and independent ledger/stock reconciliation.')


if __name__ == '__main__':
    main()
