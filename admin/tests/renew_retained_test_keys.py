"""Renew expired 24-hour API-key fixtures on the owned synthetic stack only.

No users, passwords, signing secret, Auth settings, roles or database rows change.
Refuses active login quota windows; renewal must never evade their limits.
Rebuild the debug APK with the new public anon configuration afterwards.
"""
import base64
import hashlib
import hmac
import json
import os
import re
import signal
import subprocess
import time
from connected_android import LOCAL, REPO, private_json


def main():
    path = LOCAL / 'private-config.json'
    assert path.stat().st_mode & 0o077 == 0
    config = json.loads(path.read_text())
    assert config['kind'] == 'SYNTHETIC_LOCAL_INTEGRATION_ONLY'
    assert config['url'] == 'https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}', config['database'])
    env = dict(os.environ, LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    active = subprocess.run(['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-d', config['database'], '-c', "select count(*) from aq_private.login_windows where attempts>0 and started_at+interval '15 minutes'>clock_timestamp();"], env=env, check=True, capture_output=True, text=True).stdout.strip()
    assert active == '0', 'Wait for actual quota windows to expire; never reset or sidestep them'
    secret = config['auth_env']['GOTRUE_JWT_SECRET'].encode()
    now = int(time.time())
    def encode(value):
        return base64.urlsafe_b64encode(value).rstrip(b'=').decode()
    updated = dict(config)
    for name, role in [('SUPABASE_ANON_KEY', 'anon'), ('SUPABASE_SERVICE_ROLE_KEY', 'service_role')]:
        header, payload, signature = config[name].split('.')
        assert json.loads(base64.urlsafe_b64decode(header + '=='))['alg'] == 'HS256'
        expected = encode(hmac.new(secret, (header + '.' + payload).encode(), hashlib.sha256).digest())
        assert hmac.compare_digest(signature, expected), 'Fixture signature does not match owned signing secret'
        claims = json.loads(base64.urlsafe_b64decode(payload + '=='))
        assert claims['role'] == role and claims['exp'] <= now, 'Only already-expired fixture keys may renew'
        claims.update(iat=now, exp=now + 86400)
        body = header + '.' + encode(json.dumps(claims, separators=(',', ':')).encode())
        updated[name] = body + '.' + encode(hmac.new(secret, body.encode(), hashlib.sha256).digest())
    pid = int((LOCAL / 'gateway.pid').read_text())
    command = open('/proc/' + str(pid) + '/cmdline', 'rb').read().split(b'\0')
    assert str(REPO / 'admin/tests/real_services_gateway.ts').encode() in command
    assert str(path).encode() in command
    archive = LOCAL / ('private-config-before-expired-renewal-' + str(now) + '.json')
    assert not archive.exists()
    private_json(archive, config)
    state_path = LOCAL / 'expired-key-renewal-state.json'
    private_json(state_path, {'kind': config['kind'], 'old_config_archive': str(archive), 'phase': 'intent', 'expires_at': now + 86400, 'owned_gateway_pid': pid})
    private_json(path, updated)
    os.kill(pid, signal.SIGTERM)
    for _ in range(100):
        try:
            with open('/proc/' + str(pid) + '/stat') as stream:
                exited = stream.read().split(') ', 1)[1].startswith('Z')
        except FileNotFoundError:
            exited = True
        if exited:
            break
        time.sleep(.1)
    else:
        raise RuntimeError('Owned gateway did not stop; inspect renewal intent before retry')
    subprocess.run(['/workspace/tooling/aq-python/bin/python', str(REPO / 'admin/tests/start_retained_local.py')], check=True)
    private_json(state_path, {'kind': config['kind'], 'old_config_archive': str(archive), 'phase': 'completed', 'expires_at': now + 86400})
    print('PASS: expired synthetic keys renewed for24hours with same signing secret/roles; no active quota windows, users or DB rows modified; owned gateway healthy. Rebuild public debug client configuration.')


if __name__ == '__main__':
    main()
