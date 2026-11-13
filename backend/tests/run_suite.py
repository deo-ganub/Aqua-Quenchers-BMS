"""Current migrations and rollback fixtures in a NEW disposable local UTF8 database.

Only the prepared local PostgreSQL socket is supported; never accepts a cloud URL.
Put the verified PostgreSQL17 binaries first in PATH. Output is suitable for a
restricted development evidence log. The original aq_bms_dev is never modified.
"""
import pathlib
import subprocess
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ['psql', '-X', '-At', '-h', '/workspace/tooling/pgtest', '-p', '55432', '-v', 'ON_ERROR_STOP=1']


def sql(database, body):
    subprocess.run(BASE + ['-d', database], input=body, text=True, check=True)


def main():
    database = 'aq_suite_' + uuid.uuid4().hex
    sql('postgres', f"create database {database} encoding 'UTF8' template template0;")
    try:
        # Stub roles already exist in the prepared isolated cluster. Do not alter
        # global roles or reuse hosted credentials just to make tests pass.
        stub = (ROOT / 'backend/tests/auth_stub.sql').read_text()
        sql(database, '\n'.join(line for line in stub.splitlines() if not line.startswith('create role ')))
        migrations = sorted((ROOT / 'supabase/migrations').glob('*.sql'))
        fixtures = sorted(p for p in (ROOT / 'backend/tests').glob('*.sql') if p.name != 'auth_stub.sql')
        if not migrations or not fixtures:
            raise RuntimeError('Missing migrations or fixtures; zero-test success is forbidden.')
        for path in migrations + fixtures:
            print('RUN ' + str(path.relative_to(ROOT)), flush=True)
            sql(database, path.read_text())
        print(f'PASS: {len(migrations)} ordered migrations and {len(fixtures)} rollback fixtures.', flush=True)
    finally:
        sql('postgres', 'drop database ' + database + ';')


if __name__ == '__main__':
    main()
