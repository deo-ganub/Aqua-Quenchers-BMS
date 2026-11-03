"""Read-only native report/expense/customer history checks with real local Auth."""
import datetime
import json
import secrets
import shlex
import ssl
import urllib.request
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    config = json.loads((LOCAL / 'private-config.json').read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and config['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    context = ssl.create_default_context(cafile=config['ca_cert'])
    def http(path, body, token=None):
        request = urllib.request.Request(config['url'] + path, data=json.dumps(body).encode(), headers={
            'apikey': config['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or config['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(request, context=context, timeout=20) as response:
            return json.load(response)
    token = http('/functions/v1/username-login', {'username': config['owner_username'], 'password': config['owner_password']})['access_token']
    def query(resource, **fields):
        return http('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, token)
    source = json.loads((LOCAL / 'http-test-state.json').read_text())
    customer = query('customer', id=source['customer_id'])
    assert len(customer['orders']) == 1 and len(customer['payments']) == 2
    business = customer['orders'][0]['business_date']
    def received_day(payment):
        return str(datetime.datetime.fromisoformat(payment['received_at'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Manila')).date())
    receipt = received_day(customer['payments'][0])
    assert business < receipt and all(received_day(p) == receipt for p in customer['payments'])
    def snapshot():
        report = query('report', start_date='1900-01-01', end_date='2100-12-31')
        # Generation time is response metadata, not persisted financial state.
        report.pop('generated_at')
        return {'all_report': report,
                'inventory': query('inventory'), 'customer': query('customer', id=source['customer_id'])}
    before = snapshot()
    fixture = {'kind': 'SYNTHETIC_LOCAL_FINANCIAL_READ_UI', 'run_id': secrets.token_hex(4),
               'username': config['owner_username'], 'password': config['owner_password'],
               'customer_name': customer['name'], 'business_date': business, 'receipt_date': receipt,
               'reports': {date: query('report', start_date=date, end_date=date) for date in [business, receipt]},
               'fuel_total': query('expenses', start_date=business, end_date=business, category='Fuel')['total_centavos']}
    assert fixture['fuel_total'] == 75000 and query('expenses', start_date=business, end_date=business, category='Electricity')['total_centavos'] == 0
    private_json(LOCAL / 'financial-fixture.json', fixture)
    private_json(LOCAL / 'financial-baseline.json', before)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/financial-test-credentials.json'), input=json.dumps(fixture).encode())
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.FinancialHistoryDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = result.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
    (REPO / 'evidence/android-financial-history.log').write_text(text)
    progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/financial-test-result.json').stdout)
    private_json(LOCAL / 'financial-result.json', progress)
    assert 'OK (1 test)' in text and progress['run_id'] == fixture['run_id'] and progress.get('completed'), 'Inspect financial-history phase before retry'
    assert snapshot() == before, 'Read-only native workflow changed financial/stock/customer state'
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/financial-test-credentials.json')
    with (REPO / 'evidence/android-financial-history.log').open('a') as log:
        log.write('\nPASS: actual protected period reports/preview/rotation, invalid-period repair, Fuel75000/empty category, customer business-date versus receipt-date histories/clear/rotation; independent financial/stock/customer snapshot unchanged.\n')
    print('PASS: native financial periods, expense filters and customer history with no financial mutations.')


if __name__ == '__main__':
    main()
