"""Real native DocumentsUI cancellation; read-only financial reconciliation."""
import json
import argparse
import shlex
import ssl
import urllib.request
from connected_android import LOCAL, REPO, PACKAGE, adb


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-failure', action='store_true')
    output_failure = parser.parse_args().output_failure
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    assert adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() == b'1'
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(path, body, token=None):
        request = urllib.request.Request(c['url'] + path, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(request, context=context, timeout=20) as response:
            return json.load(response)
    token = call('/functions/v1/username-login', {'username': c['owner_username'], 'password': c['owner_password']})['access_token']
    def snapshot():
        report = call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'report', 'start_date': '1900-01-01', 'end_date': '2100-12-31'}}, token)
        report.pop('generated_at')
        return [report, call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'inventory'}}, token)]
    before = snapshot()
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    fixture = json.loads((LOCAL / 'financial-fixture.json').read_text())
    assert fixture['kind'] == 'SYNTHETIC_LOCAL_FINANCIAL_READ_UI'
    # Prior read-only fixture dates are reusable; credential values may have rotated.
    fixture.update(username=c['owner_username'], password=c['owner_password'])
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/financial-test-credentials.json'), input=json.dumps(fixture).encode())
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.PdfCancellationDeviceTest#' + ('controlledOutputFailureNeverAcknowledgesSave' if output_failure else 'realDocumentPickerCancelNeverAcknowledgesExport'), PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=240)
    text = result.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
    evidence = REPO / ('evidence/android-pdf-output-failure.log' if output_failure else 'evidence/android-pdf-cancel.log')
    evidence.write_text(text)
    assert 'OK (1 test)' in text, 'Inspect PDF cancellation evidence before retry'
    assert snapshot() == before, 'Financial/inventory state changed'
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/financial-test-credentials.json')
    with evidence.open('a') as log:
        log.write('\nControlled negative platform-result fixtures exercised for missing-output/denied-contacts URI; no successful output claimed.\n' if output_failure else '')
        log.write('\nPASS: actual IOException/SecurityException mapped to export failure; retry payload retained then cancelled/cleared; no false success; financial/inventory snapshots unchanged.\n' if output_failure else '\nPASS: actual DocumentsUI cancellation clears pending PDF, retains preview through rotation, gives no false success; financial/inventory snapshots unchanged.\n')
    print('PASS: native SAF cancellation and exact financial/inventory reconciliation')


if __name__ == '__main__':
    main()
