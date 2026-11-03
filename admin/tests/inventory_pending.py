"""One persisted synthetic pending order, actual read-only Owner UI, checked settlement.

Refuses prior intent rather than blindly replaying a financial command. This owns
only local fixture data; no production deployment or live-data mutation.
"""
import datetime
import json
import ssl
import urllib.request
import uuid
from zoneinfo import ZoneInfo
from connected_android import LOCAL, REPO, private_json
import owner_dashboard


def main():
    c = json.loads((LOCAL / 'private-config.json').read_text())
    assert c['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url'] == 'https://127.0.0.1:55436'
    path = LOCAL / 'inventory-pending-state.json'
    assert not path.exists(), 'Inspect prior financial intent/result before retry'
    previous = json.loads((LOCAL / 'carryover-state.json').read_text())
    assert previous['completed'] and previous['kind'] == 'SYNTHETIC_LOCAL_CARRYOVER_UI'
    customer_id = previous['commands']['customer']['result']['id']
    context = ssl.create_default_context(cafile=c['ca_cert'])
    def call(route, body, token=None):
        req = urllib.request.Request(c['url'] + route, data=json.dumps(body).encode(), headers={'apikey': c['SUPABASE_ANON_KEY'], 'Authorization': 'Bearer ' + (token or c['SUPABASE_ANON_KEY']), 'content-type': 'application/json'})
        with urllib.request.urlopen(req, context=context, timeout=20) as response: return json.load(response)
    token = call('/functions/v1/username-login', {'username': c['owner_username'], 'password': c['owner_password']})['access_token']
    def query(resource, **fields): return call('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, token)
    customer = query('customer', id=customer_id)
    assert customer['customer_type'] == 'dealer'
    stock = query('inventory'); assert stock['pending_quantity'] == 0
    today = str(datetime.datetime.now(ZoneInfo('Asia/Manila')).date())
    past = str(datetime.date.fromisoformat(today) - datetime.timedelta(days=1))
    reports = {date: query('report', start_date=date, end_date=date) for date in [past, today]}
    state = {'kind': 'SYNTHETIC_LOCAL_INVENTORY_PENDING_UI', 'customer_id': customer_id, 'stock_before': stock, 'reports_before': reports, 'commands': {}, 'completed': False}
    private_json(path, state)
    def command(label, operation, **fields):
        payload = {'operation': operation, 'request_id': str(uuid.uuid4()), **fields}
        assert label not in state['commands']
        state['commands'][label] = {'payload': payload}; private_json(path, state)
        result = call('/rest/v1/rpc/aq_command', {'p_request': payload}, token)
        state['commands'][label]['result'] = result; private_json(path, state)
        return result
    order = command('order', 'create_order', customer_id=customer_id, quantity=1, business_date=past)
    for label, operation, fields in [
        ('for_pickup', 'transition_order', {'status': 'for_pickup'}),
        ('pickup', 'record_pickup', {'quantity': 1}),
        ('refilling', 'transition_order', {'status': 'refilling'}),
        ('ready', 'transition_order', {'status': 'ready_for_delivery'}),
    ]: command(label, operation, order_id=order['id'], **fields)
    seeded = query('inventory')
    assert seeded['pending_quantity'] == 1 and seeded['reserved_filled'] == stock['reserved_filled'] + 1
    # Runner compares all business/profile records before/after the actual UI.
    owner_dashboard.main()
    fixture = json.loads((LOCAL / 'owner-dashboard-fixture.json').read_text())
    assert any(o['id'] == order['id'] and o['pending_quantity'] == 1 for o in fixture['pending_orders'])
    state['native_verified'] = True; private_json(path, state)
    command('delivery', 'record_delivery', order_id=order['id'], quantity=1)
    done = query('order', id=order['id']); after = query('inventory')
    assert (done['status'], done['total_centavos'], done['pending_quantity'], done['reserved_quantity']) == ('delivered', 3000, 0, 0)
    for key in ['physical_filled', 'empty_quantity', 'reserved_filled', 'pending_quantity']: assert after[key] == stock[key], key
    for date, before in reports.items():
        report = query('report', start_date=date, end_date=date)
        for key in ['payments_received_centavos', 'expenses_centavos', 'net_income_centavos']: assert report[key] == before[key], key
        assert report['revenue_centavos'] == before['revenue_centavos'] + (3000 if date == past else 0)
        assert report['outstanding_centavos'] == before['outstanding_centavos'] + 3000
    state['completed'] = True; private_json(path, state)
    with (REPO / 'evidence/android-owner-dashboard.log').open('a') as log:
        log.write('\nPASS: populated Inventory pending fixture1 (customer/reference/original date/pending gallon), authoritative stock/time and Carry-over Details navigation. Native UI changed no records. Checked final delivery cleared reservation, stock returned to baseline; historical charge3000 retained, receipts/expenses/net unchanged.\n')
    print('PASS: populated Owner Inventory UI and checked exact financial/stock settlement')


if __name__ == '__main__': main()
