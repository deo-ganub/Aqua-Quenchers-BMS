"""Actual local GoTrue/PostgREST/production-handler integration; no hosted claim.

Private generated fixture configuration and tokens stay outside the repository.
Mutations use durable request IDs so a failed run can be inspected before retry.
"""
import base64
import datetime
import hashlib
import hmac
import json
import os
import pathlib
import ssl
import struct
import time
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo

ROOT = pathlib.Path('/workspace/tooling/aq-auth-rest-local')


class Fixture:
    def __init__(self):
        path = ROOT / 'private-config.json'
        if path.stat().st_mode & 0o077:
            raise RuntimeError('Private local configuration must be0600.')
        self.config = json.loads(path.read_text())
        if self.config.get('kind') != 'SYNTHETIC_LOCAL_INTEGRATION_ONLY' or self.config.get('url') != 'https://127.0.0.1:55436':
            raise RuntimeError('Refusing nonlocal or unreviewed integration target.')
        self.path = ROOT / 'http-test-state.json'
        self.state = json.loads(self.path.read_text()) if self.path.exists() else {}
        if self.state.get('completed'):
            raise RuntimeError('Fixture already completed; preserve evidence and create a new isolated stack to rerun.')
        self.context = ssl.create_default_context(cafile=self.config['ca_cert'])
        self.owner = json.loads((ROOT / 'owner-session.json').read_text())['access_token']
        self.owner_aal1 = self.owner
        self.staff = None

    def save(self):
        temporary = self.path.with_suffix('.tmp')
        with os.fdopen(os.open(temporary, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600), 'w') as out:
            json.dump(self.state, out, indent=2)
        temporary.replace(self.path)

    def key(self, name):
        self.state.setdefault('keys', {}).setdefault(name, str(uuid.uuid4()))
        self.save()
        return self.state['keys'][name]

    def call(self, path, body=None, token=None, method=None, expected=200, service=False):
        key = self.config['SUPABASE_SERVICE_ROLE_KEY' if service else 'SUPABASE_ANON_KEY']
        headers = {'apikey': key, 'Authorization': 'Bearer ' + (token or key), 'content-type': 'application/json'}
        request = urllib.request.Request(self.config['url'] + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, context=self.context, timeout=20)
            status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        data = json.loads(raw) if raw else None
        if status != expected:
            temporary = ROOT / 'last-http-failure.json'
            temporary.write_text(json.dumps({'path': path.split('?')[0], 'status': status, 'response': data}))
            temporary.chmod(0o600)
            raise AssertionError(f'{path.split("?")[0]} returned{status}, expected{expected}; private failure saved.')
        return data

    def query(self, resource, token, **fields):
        return self.call('/rest/v1/rpc/aq_query', {'p_request': {'resource': resource, **fields}}, token)

    def command(self, operation, label, token, **fields):
        body = {'p_request': {'operation': operation, 'request_id': self.key(label), **fields}}
        return self.call('/rest/v1/rpc/aq_command', body, token)

    def staff_payload(self, operation, label, **fields):
        return {'operation': operation, 'request_id': self.key(label), **fields}

    def verify_auth_and_mfa(self):
        assert self.call('/auth/v1/health')['name'] == 'GoTrue'
        disabled = self.call('/auth/v1/signup', {'email': 'disabled@aqua-local.invalid', 'password': self.config['staff_password']}, expected=422)
        assert disabled['error_code'] == 'signup_disabled'
        self.call('/rest/v1/rpc/aq_me', {}, expected=401)
        me = self.call('/rest/v1/rpc/aq_me', {}, self.owner)
        assert me['user_id'] == self.config['owner_id'] and me['role'] == 'owner'
        parts = self.owner.split('.')
        forged = parts[0] + '.' + parts[1] + '.' + ('A' if parts[2][0] != 'A' else 'B') + parts[2][1:]
        self.call('/rest/v1/rpc/aq_me', {}, forged, expected=401)
        payload = self.staff_payload('create', 'staff-create', username='staff.integration', full_name='Synthetic HTTP Staff', contact_number='09000000002', active=True, temporary_password=self.config['staff_password'])
        denied = self.call('/functions/v1/staff-admin', payload, self.owner_aal1, expected=403)
        assert denied['error'] == 'Enter a valid verification code.'
        if 'factor_id' not in self.state:
            factor = self.call('/auth/v1/factors', {'factor_type': 'totp', 'friendly_name': 'Synthetic integration factor'}, self.owner)
            self.state.update(factor_id=factor['id'], factor_secret=factor['totp']['secret'])
            self.save()
        factor_path = '/auth/v1/factors/' + self.state['factor_id']

        def code():
            secret = self.state['factor_secret']
            key = base64.b32decode(secret + '=' * ((-len(secret)) % 8))
            digest = hmac.new(key, struct.pack('>Q', int(time.time()) // 30), hashlib.sha1).digest()
            offset = digest[-1] & 15
            return f'{(struct.unpack(">I", digest[offset:offset+4])[0] & 0x7fffffff) % 1000000:06d}'

        challenge = self.call(factor_path + '/challenge', {}, self.owner)
        wrong = f'{(int(code()) + 1) % 1000000:06d}'
        self.call(factor_path + '/verify', {'challenge_id': challenge['id'], 'code': wrong}, self.owner, expected=422)
        challenge = self.call(factor_path + '/challenge', {}, self.owner)
        session = self.call(factor_path + '/verify', {'challenge_id': challenge['id'], 'code': code()}, self.owner)
        self.owner = session['access_token']
        self.state['owner_mfa_session'] = session
        self.save()
        staff = self.call('/functions/v1/staff-admin', payload, self.owner)
        assert staff['role'] == 'staff'
        assert self.call('/functions/v1/staff-admin', payload, self.owner)['user_id'] == staff['user_id']
        self.state['staff_id'] = staff['user_id']
        self.save()
        session = self.call('/functions/v1/username-login', {'username': 'staff.integration', 'password': self.config['staff_password']})
        self.staff = session['access_token']
        self.state['staff_session'] = session
        self.save()
        assert self.call('/rest/v1/rpc/aq_me', {}, self.staff)['role'] == 'staff'
        self.call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'report'}}, self.staff, expected=403)
        self.call('/rest/v1/rpc/aq_command', {'p_request': {'operation': 'create_expense', 'request_id': str(uuid.uuid4()), 'business_date': '2026-10-15', 'category': 'Fuel', 'amount_centavos': 100, 'selection_source': 'manual'}}, self.staff, expected=403)
        self.call('/auth/v1/user', {'data': {'role': 'owner'}}, self.staff, method='PUT')
        assert self.call('/rest/v1/rpc/aq_me', {}, self.staff)['role'] == 'staff'
        self.call('/rest/v1/rpc/aq_query', {'p_request': {'resource': 'report'}}, self.staff, expected=403)
        self.call('/rest/v1/profiles', token=self.staff, expected=404)
        print('PASS: real password/JWT verification; forged/anonymous denial; actual managed TOTP invalid/valid + AAL2 Staff creation/idempotency; Staff/metadata privilege denial.', flush=True)

    def verify_business(self):
        today = datetime.datetime.now(ZoneInfo('Asia/Manila')).date()
        self.state.setdefault('business_date', (today - datetime.timedelta(days=1)).isoformat())
        self.save()
        day = self.state['business_date']
        customer = self.command('create_customer', 'customer', self.staff, name='Synthetic real HTTP customer', customer_type='household', contact_number='0900 000 0003', address='LOCAL SYNTHETIC FIXTURE')
        order = self.command('create_order', 'order', self.staff, customer_id=customer['id'], quantity=5, business_date=day)
        first = self.command('record_payment', 'payment-first', self.staff, reference=order['reference'], amount_centavos=5000)
        assert first['recorded_by'] == self.state['staff_id']
        self.command('transition_order', 'for-pickup', self.staff, order_id=order['id'], status='for_pickup')
        pickup = self.command('record_pickup', 'pickup', self.staff, order_id=order['id'], quantity=4)
        assert pickup['expected_quantity'] == 5 and pickup['total_centavos'] == 14000
        self.command('transition_order', 'refilling', self.staff, order_id=order['id'], status='refilling')
        ready = self.command('transition_order', 'ready', self.staff, order_id=order['id'], status='ready_for_delivery')
        assert self.command('transition_order', 'ready', self.staff, order_id=order['id'], status='ready_for_delivery') == ready
        self.command('record_delivery', 'delivery-first', self.staff, order_id=order['id'], quantity=2)
        sale = self.command('create_sale', 'sale', self.staff, customer_name='Synthetic HTTP counter', quantity=3, cash_centavos=15000)
        assert sale['change_centavos'] == 4500 and sale['paid_centavos'] == 10500
        self.command('record_delivery', 'delivery-final', self.staff, order_id=order['id'], quantity=2)
        final = self.command('record_payment', 'payment-final', self.staff, reference=order['reference'], amount_centavos=9000)
        assert self.command('record_payment', 'payment-final', self.staff, reference=order['reference'], amount_centavos=9000) == final
        self.command('create_expense', 'expense', self.owner, business_date=day, category='Fuel', amount_centavos=75000, remarks='', selection_source='manual')
        stock = self.query('inventory', self.owner)
        assert (stock['physical_filled'], stock['reserved_filled'], stock['empty_quantity']) == (17, 0, 20), stock
        report = self.query('report', self.owner, start_date=day, end_date=day)
        assert [report[k] for k in ['revenue_centavos', 'payments_received_centavos', 'expenses_centavos', 'net_income_centavos', 'outstanding_centavos']] == [14000, 0, 75000, -75000, 14000]
        report = self.query('report', self.owner, start_date=today.isoformat(), end_date=today.isoformat())
        assert [report[k] for k in ['revenue_centavos', 'payments_received_centavos', 'net_income_centavos', 'outstanding_centavos']] == [10500, 24500, 24500, 0]
        self.state.update(customer_id=customer['id'], order_id=order['id'], sale_id=sale['id'])
        self.save()
        print('PASS: actual HTTPS Staff customer/order/receipt/short-pickup/refill/reservation/partial-final delivery/POS/Owner manual expense and exact date/as-of financial reports; retries have one effect.', flush=True)

    def verify_identity_and_logout(self):
        profile = self.command('update_profile', 'profile-alias', self.staff, full_name='Synthetic HTTP Staff', username='staff.renamed', contact_number='09000000002')
        assert profile['user_id'] == self.state['staff_id']
        self.call('/functions/v1/username-login', {'username': 'staff.integration', 'password': self.config['staff_password']}, expected=401)
        session = self.call('/functions/v1/username-login', {'username': 'staff.renamed', 'password': self.config['staff_password']})
        assert session['user']['id'] == self.state['staff_id']
        self.staff = session['access_token']
        self.state['staff_session'] = session
        self.save()
        payload = self.staff_payload('set_active', 'deactivate', user_id=self.state['staff_id'], active=False)
        self.call('/functions/v1/staff-admin', payload, self.owner)
        self.call('/rest/v1/rpc/aq_me', {}, self.staff, expected=403)
        self.call('/functions/v1/username-login', {'username': 'staff.renamed', 'password': self.config['staff_password']}, expected=401)
        self.call('/functions/v1/staff-admin', self.staff_payload('set_active', 'reactivate', user_id=self.state['staff_id'], active=True), self.owner)
        assert self.call('/rest/v1/rpc/aq_me', {}, self.staff)['active']
        assert self.call('/rest/v1/rpc/aq_logout', {}, self.staff)['logged_out']
        self.call('/auth/v1/logout?scope=local', token=self.staff, method='POST', expected=204)
        self.call('/rest/v1/rpc/aq_me', {}, self.staff, expected=403)
        self.call('/auth/v1/token?grant_type=refresh_token', {'refresh_token': session['refresh_token']}, expected=400)
        print('PASS: actual alias edits preserve Auth/transaction identity; deactivation denies old signed access/new login; reactivation, BMS logout and managed refresh revocation verified.', flush=True)


if __name__ == '__main__':
    fixture = Fixture()
    fixture.verify_auth_and_mfa()
    fixture.verify_business()
    fixture.verify_identity_and_logout()
    fixture.state['completed'] = True
    fixture.save()
    print('PASS: real local Auth/PostgREST/production-handler integration. NOT RUN: hosted Supabase, Figma, declared-device/real-model gates, hosted recovery custody.', flush=True)
