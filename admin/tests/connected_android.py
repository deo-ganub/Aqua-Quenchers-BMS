"""Real local connected UI runner. Test-only credentials use a private temporary
device fixture, never APK assets, command arguments, source or public logs.
"""
import argparse
import datetime
import json
import os
import pathlib
import secrets
import shlex
from decimal import Decimal
import ssl
import subprocess
import urllib.error
import urllib.request
import zipfile
from zoneinfo import ZoneInfo

REPO = pathlib.Path(__file__).resolve().parents[2]
LOCAL = pathlib.Path('/workspace/tooling/aq-auth-rest-local')
ADB = ['/workspace/tooling/android-sdk/platform-tools/adb', '-s', 'emulator-5554']
PACKAGE = 'com.aquaquenchers.bms'


def adb(*args, input=None, timeout=120):
    return subprocess.run(ADB + list(args), input=input, check=True, capture_output=True, timeout=timeout)


def private_json(path, value):
    temporary = path.with_suffix('.tmp')
    with os.fdopen(os.open(temporary, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600), 'w') as stream:
        json.dump(value, stream, indent=2)
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--new-fixture', action='store_true', help='After inspecting a prior outcome, create a new synthetic run and baseline.')
    args = parser.parse_args()
    config_path = LOCAL / 'private-config.json'
    config = json.loads(config_path.read_text())
    assert config_path.stat().st_mode & 0o077 == 0
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and config['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    context = ssl.create_default_context(cafile=config['ca_cert'])

    def http(path, body, token=None):
        request = urllib.request.Request(config['url'] + path, data=json.dumps(body).encode(), headers={
            'apikey': config['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or config['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(request, context=context, timeout=20) as response:
            return json.load(response)

    fixture_path = LOCAL / 'native-fixture.json'
    if fixture_path.exists() and not args.new_fixture:
        raise RuntimeError('Prior connected fixture exists. Inspect native-result.json before explicitly choosing --new-fixture; do not blindly replay mutations.')
    session = http('/functions/v1/username-login', {'username': config['owner_username'], 'password': config['owner_password']})
    owner = session['access_token']
    day = datetime.datetime.now(ZoneInfo('Asia/Manila')).date().isoformat()
    baseline = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'report', 'start_date': day, 'end_date': day}}, owner)
    inventory = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'inventory'}}, owner)
    accounts = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'staff'}}, owner)['items']
    state = json.loads((LOCAL / 'http-test-state.json').read_text())
    staff = next(row for row in accounts if row['user_id'] == state['staff_id'])
    nonce = secrets.token_hex(4)
    fixture = {'kind': 'SYNTHETIC_LOCAL_CONNECTED_UI', 'run_id': nonce,
               'owner_username': config['owner_username'], 'owner_password': config['owner_password'],
               'staff_username': staff['username'], 'staff_password': config['staff_password'],
               'owner_totp_secret': state['factor_secret'], 'baseline_today': baseline, 'baseline_inventory': inventory,
               'new_staff_name': 'Native Account ' + nonce, 'new_staff_username': 'native.' + nonce,
               'new_staff_contact': '091' + str(int(nonce, 16) % 100000000).zfill(8), 'new_staff_password': secrets.token_urlsafe(24),
               'customer_name': 'Native Customer ' + nonce, 'customer_contact': '090' + str(int(nonce, 16) % 100000000).zfill(8),
               'counter_name': 'Native Counter ' + nonce, 'renamed_staff_username': 'staff.native.' + nonce,
               'updated_staff_password': secrets.token_urlsafe(24), 'pdf_filename': 'aq-native-' + nonce + '.pdf'}
    private_json(fixture_path, fixture)
    sensitive = [config['SUPABASE_SERVICE_ROLE_KEY'], config['auth_env']['GOTRUE_JWT_SECRET']] + [fixture[k] for k in ['owner_password', 'staff_password', 'owner_totp_secret', 'new_staff_password', 'updated_staff_password']]
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        with zipfile.ZipFile(REPO / path) as archive:
            for member in archive.infolist():
                if member.is_dir(): continue
                data = archive.read(member)
                assert not any(value.encode() in data for value in sensitive), 'Private fixture credential found in APK'
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/connected-test-credentials.json'), input=json.dumps(fixture).encode())
    command = ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                     PACKAGE + '.ConnectedWorkflowDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner']
    completed = subprocess.run(command, cwd=REPO, capture_output=True, text=True, timeout=900)
    transcript = completed.stdout + completed.stderr
    for value in sensitive: transcript = transcript.replace(value, '[private fixture withheld]')
    (REPO / 'evidence/android-connected-ui.log').write_text('REAL LOCAL HTTPS/AUTH/REST, OWNED SOFTWARE API29; not hosted or declared-device performance.\n' + transcript)
    result = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/connected-test-result.json').stdout)
    assert result['run_id'] == nonce
    private_json(LOCAL / 'native-result.json', result)
    # Reconcile a confirmed password change even if a later test assertion failed.
    if result.get('new_password_applied'):
        config['staff_password'] = fixture['updated_staff_password']
        private_json(config_path, config)
    if completed.returncode or 'OK (1 test)' not in transcript or not result.get('completed'):
        raise RuntimeError('Connected native test failed; inspect evidence and private native-result.json before retry.')
    after = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'report', 'start_date': day, 'end_date': day}}, owner)
    for key, delta in {'revenue_centavos': 10500, 'payments_received_centavos': 5500, 'expenses_centavos': 5000, 'net_income_centavos': 500, 'outstanding_centavos': 5000}.items():
        assert after[key] == baseline[key] + delta, 'Connected native ledger delta mismatch: ' + key
    stock = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'inventory'}}, owner)
    assert stock['physical_filled'] == inventory['physical_filled'] - 1 and stock['empty_quantity'] == inventory['empty_quantity'] and stock['reserved_filled'] == inventory['reserved_filled']
    users = http('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'staff'}}, owner)['items']
    assert sum(row['username'] == fixture['new_staff_username'] for row in users) == 1
    renamed = next(row for row in users if row['user_id'] == state['staff_id'])
    assert renamed['username'] == fixture['renamed_staff_username']
    pdf = adb('exec-out', 'cat', '/sdcard/Download/' + fixture['pdf_filename']).stdout
    output = LOCAL / ('native-report-' + nonce + '.pdf');output.write_bytes(pdf);output.chmod(0o600)
    text = subprocess.run(['pdftotext', '-layout', str(output), '-'], check=True, capture_output=True, text=True).stdout
    amount = lambda n: '₱' + format(Decimal(n) / Decimal(100), '.2f')
    expected = {'Revenue': baseline['revenue_centavos'], 'Payments received': baseline['payments_received_centavos'], 'Expenses': baseline['expenses_centavos'] + 5000,
                'Net income': baseline['payments_received_centavos'] - baseline['expenses_centavos'] - 5000, 'Outstanding balances': baseline['outstanding_centavos']}
    for label, value in expected.items(): assert label + ': ' + amount(value) in text, 'Saved native PDF mismatch: ' + label
    (REPO / 'evidence/pdf-connected-text.log').write_text('REAL LOCAL AUTH/REST REPORT, SYNTHETIC FIXTURE DATA; actual native SAF export.\n' + text)
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/connected-test-credentials.json')
    print('PASS: real connected Owner/Staff native flows, rotation/manual model fallback, managed MFA422/valid save, actual SAF PDF, payments/POS, stable alias, password old/new login and logout; independent ledger/stock/PDF reconciliation.')


if __name__ == '__main__':
    main()
