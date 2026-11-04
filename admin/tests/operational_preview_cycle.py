"""Separate guarded native pickup/delivery preview and multiple-date carryover cycle."""
import datetime
import argparse
import os
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
    parser.add_argument('--resume-preview-reviewed', action='store_true', help='Only the inspected first-Owner ready/no-delivery failure; never replay pickup or existing orders')
    args = parser.parse_args()
    args.resume = args.resume_preview_reviewed
    config = json.loads((LOCAL / 'private-config.json').read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and config['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'operational-preview-state.json'
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
    state = json.loads(state_path.read_text()) if args.resume else {'kind': 'SYNTHETIC_LOCAL_OPERATIONAL_PREVIEW_CYCLE', 'run_id': secrets.token_hex(4), 'requests': {}, 'orders': [], 'completed': False}
    assert not state['completed']
    if args.resume:
        assert state['kind'] == 'SYNTHETIC_LOCAL_OPERATIONAL_PREVIEW_CYCLE' and not state['completed']
        assert len(state['orders']) == 1 and not state.get('owner') and not state.get('staff')
        observed = query('order', id=state['orders'][0])
        assert (observed['status'], observed['quantity'], observed['expected_quantity'], observed['delivered_quantity'], observed['pending_quantity'], observed['reserved_quantity'], observed['total_centavos']) == ('ready_for_delivery', 9, 10, 0, 9, 9, 27000)
        assert observed['deliveries'] == [], 'Uncertain delivery must reconcile, never replay'
        private_json(LOCAL / ('operational-preview-state-before-reviewed-resume-' + state['run_id'] + '.json'), state)
        state['reviewed_resume'] = 'original Owner ready/no delivery; skip completed pickup/reschedule/stages'
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
        state['baseline'] = {'date': str(today), 'stock': query('inventory'), 'report': query('report', start_date='1900-01-01', end_date='2100-12-31')}
        private_json(state_path, state)
    assert state['baseline']['date'] == str(today), 'Business day changed; reconcile periods before resuming'
    assert state['baseline']['stock']['pending_quantity'] == 0, 'Separate cycle requires no preexisting carryover; never clear existing work'
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    tables = ['customers', 'profiles', 'orders', 'sales', 'payments', 'expenses', 'reservations', 'deliveries']
    def raw_snapshot():
        statement = "select jsonb_build_object(" + ','.join("'" + t + "',(select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),'[]'::jsonb) from aq_private." + t + ' x)' for t in tables) + ');'
        return json.loads(subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', config['database'], '-v', 'ON_ERROR_STOP=1'], input=statement, env=env, text=True, capture_output=True, check=True).stdout)
    if not args.resume: state['baseline_rows'] = raw_snapshot(); private_json(state_path, state)
    else:
        current = raw_snapshot()
        for table, rows in state['baseline_rows'].items(): assert all(row in current[table] for row in rows), 'Preexisting row changed before reviewed resume: ' + table
    before_stock = state['baseline']['stock']
    before_report = state['baseline']['report']
    staff = next(x for x in query('staff')['items'] if x['user_id'] == json.loads((LOCAL / 'http-test-state.json').read_text())['staff_id'])
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    evidence = REPO / 'evidence/android-operational-preview-cycle.log'
    transcript = [evidence.read_text()] if args.resume and evidence.exists() else []
    for role in ['owner', 'staff']:
        if state.get(role) == 'VERIFIED':
            continue
        contact = '09' + str((int(nonce, 16) * 2 + (role == 'staff')) % 1000000000).zfill(9)
        customer = command('create_customer', role + '-customer', name='Local pickup ' + role + ' ' + nonce, customer_type='dealer', contact_number=contact, address='LOCAL SYNTHETIC FIXTURE')
        order = command('create_order', role + '-order', customer_id=customer['id'], quantity=10, business_date=str(today - datetime.timedelta(days=2 if role == 'owner' else 1)))
        if order['id'] not in state['orders']: state['orders'].append(order['id']); private_json(state_path, state)
        command('record_payment', role + '-payment', reference=order['reference'], amount_centavos=27000)
        command('transition_order', role + '-pickup', order_id=order['id'], status='for_pickup')
        fixture = {'kind': 'SYNTHETIC_LOCAL_OPERATIONAL_UI', 'run_id': nonce + '-' + role + ('-reviewed-delivery' if args.resume and role == 'owner' else ''), 'owner': role == 'owner', 'delivery_resume_only': args.resume and role == 'owner',
                   'username': config['owner_username'] if role == 'owner' else staff['username'],
                   'password': config['owner_password'] if role == 'owner' else config['staff_password'],
                   'order_id': order['id'], 'reference': order['reference'], 'full_cycle': True, 'customer_name': customer['name'], 'contact_number': customer['contact_number'], 'address': customer['address'], 'today': str(today), 'recorder': query('me')['full_name'] if role == 'owner' else staff['full_name'], 'notes': 'Customer unavailable ' + role + ' ' + nonce,
                   'past_date': str(today - datetime.timedelta(days=1)), 'future_date': str(today + datetime.timedelta(days=1))}
        private_json(LOCAL / ('operational-preview-fixture-' + role + '.json'), fixture)
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/operational-test-credentials.json'), input=json.dumps(fixture).encode())
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OperationalEdgeDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
        text = result.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
        transcript.append(role.upper() + '\n' + text)
        (REPO / 'evidence/android-operational-preview-cycle.log').write_text('\n'.join(transcript))
        progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/operational-test-result.json').stdout)
        private_json(LOCAL / ('operational-preview-result-' + role + '.json'), progress)
        assert 'OK (1 test)' in text and progress['run_id'] == fixture['run_id'] and progress.get('completed'), 'Inspect operational evidence/stage before retry'
        actual = query('order', id=order['id'])
        assert (actual['status'], actual['expected_quantity'], actual['quantity'], actual['shortfall'], actual['total_centavos']) == ('undelivered', 10, 9, 1, 27000)
        assert actual['reschedule_notes'] == fixture['notes']
        state[role] = 'VERIFIED'; private_json(state_path, state)
    pending = sorted([query('order', id=order_id) for order_id in state['orders']], key=lambda row: row['business_date'])
    assert len(pending) == 2 and all(row['pending_quantity'] == 5 and row['reserved_quantity'] == 5 for row in pending)
    assert pending[0]['business_date'] < pending[1]['business_date']
    for row in pending: row['days_waiting'] = (today - datetime.date.fromisoformat(row['business_date'])).days
    for role in ['owner', 'staff']:
        fixture = json.loads((LOCAL / ('operational-preview-fixture-' + role + '.json')).read_text())
        fixture.update(pending_only=True, pending=pending, run_id=nonce + '-queue-' + role)
        private_json(LOCAL / ('operational-preview-queue-' + role + '.json'), fixture)
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/operational-test-credentials.json'), input=json.dumps(fixture).encode())
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OperationalEdgeDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
        text = result.stdout.decode().replace(fixture['password'], '[private fixture withheld]');transcript.append(role.upper() + ' READONLY MULTIPLE-DATE QUEUE\n' + text);evidence.write_text('\n'.join(transcript))
        progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/operational-test-result.json').stdout)
        private_json(LOCAL / ('operational-preview-queue-result-' + role + '.json'), progress)
        assert 'OK (1 test)' in text and progress['run_id'] == fixture['run_id'] and progress.get('completed'), 'Inspect readonly queue result before retry'
        state[role + '_queue'] = 'VERIFIED';private_json(state_path, state)
    # Real final RPCs settle only these two fixture orders. Same persisted payload is
    # retained before every operation, including an uncertain transport outcome.
    for index, role in enumerate(['owner', 'staff']):
        if role == 'staff':
            token = http('/functions/v1/username-login', {'username': staff['username'], 'password': config['staff_password']})['access_token']
        actor = query('me')['user_id']
        order_id = state['orders'][index]
        before = query('order', id=order_id)
        assert before['delivered_quantity'] == 4 and before['pending_quantity'] == 5
        final = command('record_delivery', role + '-final-delivery', order_id=order_id, quantity=5)
        assert final['status'] == 'delivered' and final['reserved_quantity'] == 0 and final['pending_quantity'] == 0
        actual = query('order', id=order_id)
        assert actual['total_centavos'] == 27000 and actual['paid_centavos'] == 27000 and len(actual['deliveries']) == 2
        last = next(row for row in actual['deliveries'] if row['quantity'] == 5)
        assert last['recorded_by'] == actor
        assert abs((datetime.datetime.now(datetime.timezone.utc) - datetime.datetime.fromisoformat(last['created_at'].replace('Z', '+00:00'))).total_seconds()) < 60
    # Owner-only report/inventory checks use a new authentic Owner session.
    token = http('/functions/v1/username-login', {'username': config['owner_username'], 'password': config['owner_password']})['access_token']
    after_rows = raw_snapshot()
    for table, rows in state['baseline_rows'].items():
        assert all(row in after_rows[table] for row in rows), 'Preexisting row changed: ' + table
        delta = len(after_rows[table]) - len(rows)
        assert delta == {'customers': 2, 'profiles': 0, 'orders': 2, 'sales': 0, 'payments': 2, 'expenses': 0, 'reservations': 2, 'deliveries': 4}[table], 'Unexpected new rows: ' + table
    stock = query('inventory')
    report = query('report', start_date='1900-01-01', end_date='2100-12-31')
    assert stock['empty_quantity'] == before_stock['empty_quantity']
    assert stock['physical_filled'] == before_stock['physical_filled'] and stock['reserved_filled'] == before_stock['reserved_filled']
    assert report['revenue_centavos'] == before_report['revenue_centavos'] + 54000
    assert report['payments_received_centavos'] == before_report['payments_received_centavos'] + 54000
    assert report['expenses_centavos'] == before_report['expenses_centavos'] and report['outstanding_centavos'] == before_report['outstanding_centavos']
    assert report['net_income_centavos'] == before_report['net_income_centavos'] + 54000
    state['completed'] = True; private_json(state_path, state)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/operational-test-credentials.json')
    with (REPO / 'evidence/android-operational-preview-cycle.log').open('a') as log:
        log.write('\nPASS: both-role exact pickup/contact/address/date/fixed-price/shortfall-adjusted previews; delivery form/cumulative-pending preview/0-negative-fractional-nonnumeric-overdelivery rejection/Cancel/rotation and actual partial4; multiple-date carryover oldest-first both roles. Real final5 RPCs settle2orders with exact actor/time,4deliveries;54,000charges/cash/net, stock/reservations restored and all preexisting business rows unchanged. No production/manual stock mutation.\n')
    print('PASS: separate native operational previews/negative delivery/multiple-date queue and exact full-cycle reconciliation.')


if __name__ == '__main__':
    main()
