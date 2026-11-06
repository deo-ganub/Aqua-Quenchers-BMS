"""Synthetic owner-module tests in a NEW database on the dedicated no-TCP fixture cluster.

Never accepts a connection URL, credentials, retained business DB or hosted address.
Claims are local SQL stubs; these tests do not validate managed JWT signatures.
Start the dedicated cluster as described in the implementation notes.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import tempfile
import unittest
import uuid

ROOT=Path(__file__).resolve().parents[2]
PG=Path('/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql')
SOCKET='/workspace/tooling/aq-algoias-isolated'
OWNER='10000000-0000-4000-8000-000000000001'
STAFF='10000000-0000-4000-8000-000000000002'
SESSION='20000000-0000-4000-8000-000000000001'
ENV=dict(os.environ,LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
for k in ('PGHOST','PGPORT','PGDATABASE','PGUSER','PGPASSWORD','PGSERVICE','PGSERVICEFILE','PGPASSFILE'):
    ENV.pop(k,None)

def sql(database,body,ok=True):
    p=subprocess.run([str(PG),'-X','-qAt','-h',SOCKET,'-p','55472','-d',database,
                      '-v','ON_ERROR_STOP=1'],input="set statement_timeout='5s';\n"+body,
                     env=ENV,text=True,capture_output=True,timeout=20)
    if ok and p.returncode:raise AssertionError(p.stderr)
    if not ok and not p.returncode:raise AssertionError('Expected a denied/invalid operation.')
    return p.stdout.strip() if ok else p.stderr

class DatabasePrivacy(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        directory=sql('postgres','show data_directory;')
        if directory!=SOCKET+'/data':raise RuntimeError('Wrong fixture cluster; refusing any changes.')
        cls.db='aq_privacy_'+uuid.uuid4().hex
        sql('postgres',f'create database {cls.db} template template0;')
        sql(cls.db,"""
        create role anon nologin;
        create role authenticated nologin;
        create role service_role nologin bypassrls;
        """ if sql('postgres',"select count(*) from pg_roles where rolname='anon';")=='0' else '')
        sql(cls.db,"""
        create schema auth;
        create table auth.users(id uuid primary key,email text);
        create function auth.jwt() returns jsonb language sql stable as $$
          select coalesce(nullif(current_setting('request.jwt.claims',true),'')::jsonb,'{}'::jsonb)
        $$;
        create function auth.uid() returns uuid language sql stable as $$ select (auth.jwt()->>'sub')::uuid $$;
        grant usage on schema auth to anon,authenticated,service_role;
        """)
        for migration in sorted((ROOT/'supabase/migrations').glob('*.sql')):
            sql(cls.db,migration.read_text())
        # Reapplying the new migration must retain the patched financial writers.
        sql(cls.db,(ROOT/'supabase/migrations/202611070001_session_privacy.sql').read_text())
    @classmethod
    def tearDownClass(cls):
        if hasattr(cls,'db'):sql('postgres',f'drop database {cls.db} with(force);')
    def setUp(self):
        sql(self.db,"""update public.inventory set reserved_filled=0,physical_filled=0,empty_gallons=0;
        truncate public.profiles,public.customers,public.orders,public.order_events,
        public.walkin_sales,public.payments,public.expenses,public.idempotency_keys,public.audit_log,
        public.reference_counters,aq_private.sessions,aq_private.privacy_decisions,auth.users cascade;
        """+f"""
        insert into auth.users(id) values('{OWNER}'),('{STAFF}');
        insert into public.profiles(id,role,username,full_name,status) values
        ('{OWNER}','owner','fixture.owner','Fixture Owner','Active'),
        ('{STAFF}','staff','fixture.staff','Fixture Staff','Active');
        """)
    def actor(self,body,actor=OWNER,sid=SESSION,issued=None,ok=True):
        claims=json.dumps(dict(sub=actor,session_id=sid,iat=int(time.time()) if issued is None else issued))
        value=sql(self.db,"set role authenticated; select set_config('request.jwt.claims','"+claims+"',false);\n"+body,ok)
        return value.splitlines()[-1] if ok else value
    def touch(self,**kwargs):return self.actor('select public.aq_session_touch();',**kwargs)
    def customer(self,key=None,phone='09000000001'):
        key=key or str(uuid.uuid4());self.touch()
        result=json.loads(self.actor(f"select public.save_customer(null,'Fixture Customer','Household','{phone}','Fixture Address','{key}');"))
        return result['id'],key
    def decision(self,customer,decision=None,execute=True,reason='Minimum necessary contact',cutoff='null',ok=True):
        decision=decision or str(uuid.uuid4())
        return sql(self.db,f"select aq_private.erase_customer_contact('{customer}','{decision}','APPROVAL-FIXTURE','{reason}',{str(execute).lower()},{cutoff});",ok)
    def settled_order(self,customer):
        order=str(uuid.uuid4())
        sql(self.db,f"""insert into public.orders(id,order_no,customer_id,customer_name,customer_type,
        delivery_address,unit_price,original_gallons,gallons,paid_amount,status,order_date,pickup_date,
        recorded_by,recorded_by_name) values('{order}','FIXTURE-{order}','{customer}','Fixture Customer',
        'Household','Fixture Address',35,2,2,70,'Delivered',current_date,current_date,'{OWNER}','Fixture Owner');""")
        return order
    def test_session_registration_role_and_raw_table_boundaries(self):
        self.assertEqual(self.actor('select count(*) from public.customers;'),'0')
        self.assertIn('Session expired',self.actor('select public.whoami();',ok=False))
        self.touch();self.assertEqual(json.loads(self.actor('select public.whoami();'))['role'],'owner')
        self.assertIn('permission denied',sql(self.db,'set role anon; select public.aq_session_touch();',False))
        self.assertIn('permission denied',self.actor('select * from aq_private.sessions;',ok=False))
        self.assertIn('permission denied',self.actor("update public.inventory set empty_gallons=999;",ok=False))
        self.touch(actor=STAFF,sid='20000000-0000-4000-8000-000000000002')
        self.assertIn('Owner',self.actor('select public.financial_summary(current_date,current_date);',actor=STAFF,sid='20000000-0000-4000-8000-000000000002',ok=False))
    def test_expired_session_cannot_be_renewed_or_browsed(self):
        self.assertIn('Session expired',self.touch(issued=int(time.time())-1900,ok=False))
        self.touch();sql(self.db,"update aq_private.sessions set last_seen=clock_timestamp()-interval '30 minutes';")
        self.assertIn('Session expired',self.touch(ok=False))
        self.assertEqual(self.actor('select count(*) from public.profiles;'),'0')
        self.assertIn('Session expired',self.actor('select public.inventory_status();',ok=False))
        self.assertIn('Session expired',self.actor('select public.aq_session_touch();',sid='bad',ok=False))
    def test_logout_binds_identity_and_audits_exactly_once(self):
        self.touch();self.actor('select public.aq_logout();');self.actor('select public.aq_logout();')
        self.assertEqual(sql(self.db,"select count(*) from public.audit_log where action='logout';"),'1')
        self.assertIn('Session expired',self.touch(ok=False))
        self.assertIn('Session expired',self.actor('select public.aq_logout();',actor=STAFF,ok=False))
    def test_deactivation_and_role_changes_revoke_existing_sessions(self):
        self.touch();sql(self.db,f"update public.profiles set status='Inactive' where id='{OWNER}';")
        self.assertIn('Session expired',self.touch(ok=False))
        sql(self.db,f"update public.profiles set status='Active' where id='{OWNER}';")
        self.assertIn('Session expired',self.touch(ok=False))
        self.touch(sid='20000000-0000-4000-8000-000000000003')
        sql(self.db,f"update public.profiles set role='staff' where id='{OWNER}';")
        self.assertIn('Session expired',self.touch(sid='20000000-0000-4000-8000-000000000003',ok=False))
    def test_payload_bound_retries_and_legacy_fail_closed(self):
        customer,key=self.customer()
        again=json.loads(self.actor(f"select public.save_customer(null,'Fixture Customer','Household','09000000001','Fixture Address','{key}');"))
        self.assertEqual(again['id'],customer)
        changed=self.actor(f"select public.save_customer(null,'Changed','Household','09000000001','Fixture Address','{key}');",ok=False)
        self.assertIn('conflicts',changed)
        self.assertIn('conflicts',self.actor(f"select public.save_expense(current_date,'Fuel',25,'fixture','manual',null,'{key}');",ok=False))
        sql(self.db,'update public.idempotency_keys set payload_hash=null;')
        self.assertIn('conflicts',self.actor(f"select public.save_customer(null,'Fixture Customer','Household','09000000001','Fixture Address','{key}');",ok=False))
        self.assertEqual(sql(self.db,'select count(*) from public.customers;'),'1')
    def test_concurrent_identical_retry_has_one_effect(self):
        self.touch();key=str(uuid.uuid4())
        body=f"select public.save_customer(null,'Concurrent Fixture','Household','09000000003','Fixture Address','{key}');"
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            values=list(pool.map(lambda _:json.loads(self.actor(body))['id'],range(2)))
        self.assertEqual(values[0],values[1]);self.assertEqual(sql(self.db,'select count(*) from public.customers;'),'1')
    def test_privacy_dry_run_authority_and_active_balance_guard(self):
        customer,_=self.customer();decision=str(uuid.uuid4())
        self.assertEqual(json.loads(self.decision(customer,decision,False))['status'],'DRY_RUN')
        self.assertEqual(sql(self.db,'select count(*) from aq_private.privacy_decisions;'),'0')
        self.assertEqual(sql(self.db,'select contact_number from public.customers;'),'09000000001')
        for role in ['anon','authenticated','service_role']:
            err=sql(self.db,f"set role {role}; select aq_private.erase_customer_contact('{customer}','{decision}','A','R',true);",False)
            self.assertIn('permission denied',err)
        order=self.settled_order(customer);sql(self.db,f"update public.orders set paid_amount=0 where id='{order}';")
        self.assertIn('unsettled',self.decision(customer,decision,ok=False))
    def test_privacy_removes_copies_preserves_money_hashes_and_retry(self):
        customer,key=self.customer();other,_=self.customer(phone='09000000002');self.settled_order(customer)
        before=sql(self.db,f"select payload_hash from public.idempotency_keys where key='{key}';")
        decision=str(uuid.uuid4());receipt=json.loads(self.decision(customer,decision))
        self.assertEqual(receipt['status'],'EXECUTED')
        self.assertEqual(sql(self.db,f"select contact_number is null and address is null from public.customers where id='{customer}';"),'t')
        self.assertEqual(sql(self.db,'select total::text||\'/\'||paid_amount::text from public.orders;'),'70.00/70.00')
        self.assertEqual(sql(self.db,'select delivery_address is null from public.orders;'),'t')
        self.assertEqual(sql(self.db,f"select payload_hash from public.idempotency_keys where key='{key}';"),before)
        self.assertEqual(sql(self.db,f"select contact_number from public.customers where id='{other}';"),'09000000002')
        self.assertEqual(sql(self.db,"select count(*) from public.audit_log where details::text like '%09000000001%' or details::text like '%Fixture Address%' and details->>'id'='"+customer+"';"),'0')
        replay=json.loads(self.actor(f"select public.save_customer(null,'Fixture Customer','Household','09000000001','Fixture Address','{key}');"))
        self.assertEqual(replay['id'],customer);self.assertIsNone(replay['contact_number'])
        self.assertEqual(json.loads(self.decision(customer,decision)),receipt)
        self.assertIn('conflicts',self.decision(customer,decision,reason='Different approval',ok=False))
        self.assertEqual(sql(self.db,'select count(*) from aq_private.privacy_decisions;'),'1')
        self.assertIn('Complete the required',self.actor(f"select public.save_customer(null,'Missing Contact','Household',null,null,'{uuid.uuid4()}');",ok=False))
    def test_privacy_cutoff_preserves_later_reacquired_contact(self):
        customer,_=self.customer();order=self.settled_order(customer)
        sql(self.db,f"update public.orders set created_at=clock_timestamp()-interval '2 days' where id='{order}';")
        self.decision(customer,cutoff="clock_timestamp()-interval '1 day'")
        self.assertEqual(sql(self.db,'select contact_number from public.customers;'),'09000000001')
        self.assertEqual(sql(self.db,'select delivery_address is null from public.orders;'),'t')
    def test_approved_bootstrap_is_once_only_and_recovery_revokes(self):
        self.touch()
        for role in ['anon','authenticated','service_role']:
            self.assertIn('permission denied',sql(self.db,f"set role {role}; select aq_private.initialize_inventory(10,20,'APPROVED');",False))
        value=json.loads(sql(self.db,"select aq_private.initialize_inventory(10,20,'APPROVAL-FIXTURE');"))
        self.assertTrue(value['initialized'])
        self.assertIn('already initialized',sql(self.db,"select aq_private.initialize_inventory(1,2,'APPROVAL-FIXTURE');",False))
        self.assertEqual(sql(self.db,"select physical_filled||'/'||empty_gallons from public.inventory;"),'10/20')
        result=json.loads(sql(self.db,f"select aq_private.revoke_actor_sessions('{OWNER}','RECOVERY-FIXTURE');"))
        self.assertEqual(result['revoked_sessions'],1)
        self.assertIn('PROVIDER_ADMINISTRATOR',result['managed_auth_revocation'])
        self.assertIn('Session expired',self.touch(ok=False))

    def test_current_customer_price_payment_bounds_and_receipt_retry(self):
        customer,_=self.customer()
        self.assertIn('Customer type changed',self.actor(f"select public.create_order('{customer}','Dealer',2,current_date,'{uuid.uuid4()}');",ok=False))
        order=json.loads(self.actor(f"select public.create_order('{customer}','Household',2,current_date,'{uuid.uuid4()}');"))
        self.assertEqual(order['unit_price'],35);self.assertEqual(order['total'],70)
        key=str(uuid.uuid4());body=f"select public.record_payment('{order['id']}',null,20,'{key}');"
        first=json.loads(self.actor(body));second=json.loads(self.actor(body));self.assertEqual(first,second)
        self.assertEqual(sql(self.db,'select count(*) from public.payments;'),'1')
        self.assertEqual(sql(self.db,'select paid_amount from public.orders;'),'20.00')
        self.assertIn('conflicts',self.actor(f"select public.record_payment('{order['id']}',null,30,'{key}');",ok=False))
        self.assertIn('not above the balance',self.actor(f"select public.record_payment('{order['id']}',null,100,'{uuid.uuid4()}');",ok=False))
        self.assertEqual(sql(self.db,'select count(*) from public.payments;'),'1')
        self.assertEqual(sql(self.db,'select payment_date=public.manila_today() from public.payments;'),'t')
        self.assertEqual(sql(self.db,"select count(*) from public.audit_log where entity='payment';"),'1')

    def test_removed_contact_requires_complete_reacquisition_for_new_order(self):
        customer,_=self.customer();self.decision(customer)
        self.assertIn('Complete the customer contact',self.actor(f"select public.create_order('{customer}','Household',1,current_date,'{uuid.uuid4()}');",ok=False))
        self.assertEqual(sql(self.db,'select count(*) from public.orders;'),'0')
        self.actor(f"select public.save_customer('{customer}','Fixture Customer','Household','09000000009','New Fixture Address','{uuid.uuid4()}');")
        order=json.loads(self.actor(f"select public.create_order('{customer}','Household',1,current_date,'{uuid.uuid4()}');"))
        self.assertEqual(order['delivery_address'],'New Fixture Address')

    def test_independent_ledger_export_restore_replay_and_safe_paths(self):
        customer,_=self.customer();self.settled_order(customer)
        restored='aq_restore_'+uuid.uuid4().hex
        sql('postgres',f'create database {restored} template {self.db};')
        try:
            self.decision(customer)
            with tempfile.TemporaryDirectory(prefix='aq-ledger-fixture-') as folder:
                directory=Path(folder);services=directory/'pg_service.conf'
                services.write_text(f'[source_fixture]\nhost={SOCKET}\nport=55472\ndbname={self.db}\n\n[restore_fixture]\nhost={SOCKET}\nport=55472\ndbname={restored}\n')
                services.chmod(0o600);ledger=directory/'decisions.json'
                env=dict(ENV,PGSERVICEFILE=str(services),PATH=str(PG.parent)+':'+os.environ.get('PATH',''))
                def command(*args,ok=True):
                    p=subprocess.run(['python3',str(ROOT/'tools/privacy_ledger.py'),*args],env=env,text=True,capture_output=True,timeout=20)
                    self.assertEqual(p.returncode==0,ok,p.stderr if ok else 'Expected failure')
                    return json.loads(p.stdout) if ok else p.stderr
                self.assertEqual(command('export','--service','source_fixture','--output',str(ledger))['decisions'],1)
                self.assertEqual(ledger.stat().st_mode&0o777,0o600)
                command('export','--service','source_fixture','--output',str(ledger),ok=False)
                linked=directory/'linked.json';linked.symlink_to(ledger)
                command('replay','--service','restore_fixture','--ledger',str(linked),ok=False)
                self.assertEqual(command('replay','--service','restore_fixture','--ledger',str(ledger))['status'],'DRY_RUN')
                self.assertEqual(sql(restored,'select contact_number from public.customers;'),'09000000001')
                command('replay','--service','restore_fixture','--ledger',str(ledger),'--execute','--confirm-database','wrong_database',ok=False)
                receipt=command('replay','--service','restore_fixture','--ledger',str(ledger),'--execute','--confirm-database',restored)
                self.assertEqual(receipt['status'],'EXECUTED');self.assertFalse(receipt['reopened_access'])
                self.assertEqual(sql(restored,'select contact_number is null from public.customers;'),'t')
                self.assertEqual(sql(restored,'select paid_amount from public.orders;'),'70.00')
                command('replay','--service','restore_fixture','--ledger',str(ledger),'--execute','--confirm-database',restored)
                self.assertEqual(sql(restored,'select count(*) from aq_private.privacy_decisions;'),'1')
        finally:sql('postgres',f'drop database {restored} with(force);')

    def test_redaction_targets_only_subject_and_descendants(self):
        customer,_=self.customer();other,_=self.customer(phone='09000000002')
        value={'rows':[{'id':customer,'contact_number':'09000000001','nested':{'address':'private'}},
                       {'id':other,'contact_number':'09000000002'}],'total':70}
        result=json.loads(sql(self.db,"select aq_private.redact_contact('"+json.dumps(value)+"'::jsonb,'"+customer+"');"))
        self.assertIsNone(result['rows'][0]['nested']['address']);self.assertEqual(result['rows'][1]['contact_number'],'09000000002');self.assertEqual(result['total'],70)

if __name__=='__main__':unittest.main(verbosity=2)
