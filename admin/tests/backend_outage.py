"""Owned local gateway outage; native truthful failure then exact-once expense retry."""
import json
import os
import re
import secrets
import shlex
import signal
import socket
import subprocess
import time
from pathlib import Path
from connected_android import ADB, LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    state_path = LOCAL / 'outage-state.json'
    if state_path.exists():
        raise RuntimeError('Prior outage intent exists; inspect gateway/expense outcome before retry')
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    nonce = secrets.token_hex(4)
    fixture = {'kind': 'SYNTHETIC_LOCAL_OUTAGE_UI', 'run_id': nonce, 'owner_username': c['owner_username'], 'owner_password': c['owner_password'], 'remarks': 'LOCAL OUTAGE ' + nonce}
    def sql(statement):
        return subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1', '-v', 'remark=' + fixture['remarks']], input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object('expenses',(select coalesce(sum(amount_centavos),0) from aq_private.expenses),'expense_count',(select count(*) from aq_private.expenses),'receipts',(select coalesce(sum(amount_centavos),0) from aq_private.payments),'charges',(select coalesce(sum(quantity::bigint*unit_price_centavos),0) from aq_private.orders)+(select coalesce(sum(quantity::bigint*unit_price_centavos),0) from aq_private.sales),'inventory',(select to_jsonb(i) from aq_private.inventory i),'reserved',(select coalesce(sum(quantity),0) from aq_private.reservations));"
    before = json.loads(sql(snapshot_sql))
    state = {'kind': fixture['kind'], 'run_id': nonce, 'before': before, 'completed': False}
    private_json(state_path, state); private_json(LOCAL / 'outage-fixture.json', fixture)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/outage-test-credentials.json'), input=json.dumps(fixture).encode())
    stopped = restored = False
    process = None
    def restore():
        subprocess.run(['python3', str(REPO / 'admin/tests/start_retained_local.py')], check=True, capture_output=True, timeout=60)
    def ack(label):
        path = 'files/outage-' + label + '-' + nonce + '.json'
        adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > ' + path), input=b'{"ready":true}')
    try:
        with (LOCAL / 'outage-instrumentation.log').open('wb') as log:
            process = subprocess.Popen(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.BackendOutageDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 300
            while process.poll() is None:
                if time.monotonic() > deadline:
                    process.terminate(); raise RuntimeError('Outage native bounded wait exceeded; inspect phase')
                probe = subprocess.run(ADB + ['exec-out', 'run-as', PACKAGE, 'cat', 'files/outage-test-result.json'], capture_output=True)
                try:
                    progress = json.loads(probe.stdout) if probe.returncode == 0 else {}
                except json.JSONDecodeError:
                    progress = {}
                if progress.get('run_id') == nonce:
                    if progress.get('phase') == 'form_ready' and not stopped:
                        pid = int((LOCAL / 'gateway.pid').read_text())
                        proc = Path('/proc') / str(pid)
                        assert proc.stat().st_uid == os.getuid()
                        command = (proc / 'cmdline').read_bytes().split(b'\0')
                        assert str(REPO / 'admin/tests/real_services_gateway.ts').encode() in command, 'Refusing to stop an unowned gateway'
                        state['stopping_owned_gateway_pid'] = pid; private_json(state_path, state)
                        stopped = True; os.kill(pid, signal.SIGTERM)
                        for _ in range(50):
                            with socket.socket() as connection:
                                closed = connection.connect_ex(('127.0.0.1', 55436)) != 0
                            if closed: break
                            time.sleep(.1)
                        assert closed, 'Gateway listener did not close'
                        ack('stopped')
                    elif progress.get('phase') == 'offline_failure' and not restored:
                        assert sql("select count(*) from aq_private.expenses where remarks=:'remark';") == '0'
                        assert json.loads(sql(snapshot_sql)) == before, 'Offline attempt committed data'
                        state['offline_no_commit_verified'] = True; private_json(state_path, state)
                        restore(); restored = True; ack('restored')
                time.sleep(.2)
        text = (LOCAL / 'outage-instrumentation.log').read_text().replace(c['owner_password'], '[private fixture withheld]')
        (REPO / 'evidence/android-backend-outage.log').write_text(text)
        progress = json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/outage-test-result.json').stdout)
        private_json(LOCAL / 'outage-result.json', progress)
        assert process.returncode == 0 and 'OK (1 test)' in text and progress.get('completed') and restored and state.get('offline_no_commit_verified'), 'Inspect outage phase/outcome before retry'
        after = json.loads(sql(snapshot_sql))
        assert after['expenses'] == before['expenses'] + 1299 and after['expense_count'] == before['expense_count'] + 1
        for key in ['receipts', 'charges', 'inventory', 'reserved']: assert after[key] == before[key], key
        assert sql("select count(*) from aq_private.expenses where remarks=:'remark' and amount_centavos=1299;") == '1'
        state['completed'] = True; private_json(state_path, state)
        adb('shell', 'run-as', PACKAGE, 'rm', 'files/outage-test-credentials.json')
        with (REPO / 'evidence/android-backend-outage.log').open('a') as log:
            log.write('\nPASS: owned gateway really stopped; native connection failure preserved inputs/no false save, independent0commits; gateway restored without reset, rotation/retry created exactly1 expense1299, other financial/stock/reservations unchanged.\n')
        print('PASS: real backend outage/no false save and exact-once recovery')
    finally:
        if stopped and not restored: restore()
        if process is not None and process.poll() is None: process.terminate()


if __name__ == '__main__':
    main()
