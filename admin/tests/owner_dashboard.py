"""Real Owner dashboard/modal/quick-action verification; no business writes."""
import datetime
import json
import os
import re
import shlex
import subprocess
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', c['database'])
    assert adb('emu', 'avd', 'name').stdout.decode().splitlines()[0] == 'aq_api29_software'
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(statement, **variables):
        command = ['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', c['database'], '-v', 'ON_ERROR_STOP=1']
        for key, value in variables.items(): command += ['-v', key + '=' + value]
        return subprocess.run(command, input=statement, text=True, capture_output=True, env=env, check=True).stdout.strip()
    snapshot_sql = "select jsonb_build_object(" + ','.join("'" + table + "',(select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]'::jsonb) from aq_private." + table + ' t)' for table in ['orders', 'sales', 'payments', 'expenses', 'inventory', 'reservations', 'profiles']) + ');'
    before = json.loads(sql(snapshot_sql))
    today = datetime.datetime.now(ZoneInfo('Asia/Manila')).date(); past = str(today - datetime.timedelta(days=1)); today = str(today)
    # SQL supplies independent expected data; native UI authenticates through actual Auth/REST.
    figures = {date: json.loads(sql("select aq_private.financial_summary(:'day'::date,:'day'::date)||jsonb_build_object('inventory',aq_private.stock_json());", day=date)) for date in [today, past]}
    reference = sql('select reference from aq_private.orders order by created_at desc limit 1;')
    fixture = {'kind': 'SYNTHETIC_LOCAL_OWNER_DASHBOARD_UI', 'username': c['owner_username'], 'password': c['owner_password'], 'today': today, 'past': past, 'figures': figures, 'reference': reference, 'pending_orders': json.loads(sql("select coalesce(jsonb_agg(aq_private.order_json(o.id) order by business_date),'[]') from aq_private.orders o where status in ('ready_for_delivery','undelivered');"))}
    private_json(LOCAL / 'owner-dashboard-fixture.json', fixture)
    for path in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: adb('install', '-r', '-t', str(REPO / path))
    adb('reverse', 'tcp:55436', 'tcp:55436')
    adb('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote('umask 077; cat > files/owner-dashboard-credentials.json'), input=json.dumps(fixture).encode())
    run = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', PACKAGE + '.OwnerDashboardDeviceTest', PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=300)
    text = run.stdout.decode().replace(fixture['password'], '[private fixture withheld]')
    evidence = REPO / 'evidence/android-owner-dashboard.log'; evidence.write_text(text)
    assert 'OK (1 test)' in text, 'Inspect dashboard evidence before retry'
    assert json.loads(sql(snapshot_sql)) == before, 'Read-only dashboard workflow changed business/profile state'
    adb('shell', 'run-as', PACKAGE, 'rm', 'files/owner-dashboard-credentials.json')
    with evidence.open('a') as log: log.write('\nPASS: actual Owner dashboard figures/current inventory/time, named period dialog/cancel/reversed-date preservation/correction/rotation, recent order and6content quick actions; Inventory all4counts/update-time/pending details/carry-over route/no manual adjustment screen; exact business/profile snapshots unchanged. No Figma fidelity claim.\n')
    print('PASS: actual Owner dashboard period selector/figures/quick actions with unchanged business records')


if __name__ == '__main__': main()
