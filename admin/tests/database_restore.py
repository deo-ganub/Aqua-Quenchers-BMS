"""Disposable synthetic DB restore/reconciliation, not hosted Supabase/RPO/RTO evidence."""
import importlib.util,json,os,pathlib,subprocess,tempfile,uuid,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('backup',ROOT/'admin/recovery/backup.py');backup=importlib.util.module_from_spec(spec);spec.loader.exec_module(backup)
PSQL=['psql','-h','/workspace/tooling/pgtest','-p','55432','-v','ON_ERROR_STOP=1','-At']
source='aq_recovery_source_'+uuid.uuid4().hex;destination='aq_recovery_dest_'+uuid.uuid4().hex;created=[]
def sql(database,text,variables=None):
 extra=[]
 for key,value in (variables or {}).items():extra += ['-v',key+'='+value]
 r=subprocess.run(PSQL+['-d',database]+extra,input=text,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout
try:
 for db in [source,destination]:sql('postgres',f"create database {db} encoding 'UTF8' template template0;");created.append(db)
 stub='\n'.join(line for line in (ROOT/'backend/tests/auth_stub.sql').read_text().splitlines() if not line.startswith('create role '))
 sql(source,stub)
 for migration in sorted((ROOT/'supabase/migrations').glob('*.sql')):sql(source,migration.read_text())
 # Existing lifecycle assertions exercise real records; commit only inside this disposable source.
 fixture=(ROOT/'backend/tests/lifecycle.sql').read_text();fixture=fixture.rsplit('rollback;',1)[0]+'commit;';sql(source,fixture)
 reconciliation="""select jsonb_build_object('orders',(select count(*) from aq_private.orders),'sales',(select count(*) from aq_private.sales),'charges',(select coalesce(sum(quantity::bigint*unit_price_centavos),0) from aq_private.orders)+(select coalesce(sum(quantity::bigint*unit_price_centavos),0) from aq_private.sales),'receipts',(select sum(amount_centavos) from aq_private.payments),'expenses',(select sum(amount_centavos) from aq_private.expenses),'physical',(select physical_filled from aq_private.inventory),'empty',(select empty_quantity from aq_private.inventory),'reserved',(select sum(quantity) from aq_private.reservations),'identities',(select count(*) from auth.users),'profiles',(select count(*) from aq_private.profiles),'audit',(select count(*) from aq_private.audit_events));"""
 # Outstanding balances must block erasure; settle through checked API before eligibility.
 blocked_id=sql(source,'select id from aq_private.customers limit 1;').strip()
 try:
  sql(source,f"select aq_private.erase_eligible_contact('{blocked_id}','{uuid.uuid4()}','SYNTHETIC APPROVAL','SYNTHETIC TEST',false);")
  raise AssertionError('Unsettled contact removal allowed')
 except RuntimeError as e:assert 'unsettled financial records' in str(e)
 sql(source,"select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',false);select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,false);select public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',(select reference from aq_private.orders limit 1),'amount_centavos',18000));")
 original_request=sql(source,"select payload from aq_private.requests where payload->>'operation'='create_customer' limit 1;")
 # Disposable fixture stock only, then checked POS; never a business adjustment path.
 sql(source,"update aq_private.inventory set physical_filled=physical_filled+1;select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',false);select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,false);select public.aq_command(jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_name','SYNTHETIC PREVIEW','quantity',1,'cash_centavos',3500));")
 before=json.loads(sql(source,reconciliation))
 customer_id=sql(source,'select id from aq_private.customers limit 1;').strip()
 # Export first, erase source later, then replay the separately retained decision on the old restore.
 decision_id=str(uuid.uuid4())
 with tempfile.TemporaryDirectory(prefix='aq-db-restore-') as t:
  root=pathlib.Path(t);root.chmod(0o700);bundle=root/'bundle';bundle.mkdir(mode=0o700)
  # Actual correction-preview CLI: coherent read-only snapshot, private evidence, no writes.
  service=root/'pg_service.conf';service.write_text(f'[correction-test]\nhost=/workspace/tooling/pgtest\nport=55432\ndbname={source}\n');service.chmod(0o600)
  preview_env={**os.environ,'PGSERVICEFILE':str(service)}
  for kind,table in [('order','orders'),('sale','sales')]:
   target=sql(source,f'select id from aq_private.{table} limit 1;').strip()
   assert target,kind
   output=root/(kind+'-preview.json')
   command=[sys.executable,str(ROOT/'admin/exception_preview.py'),'--service','correction-test','--kind',kind,'--id',target,'--output',str(output)]
   subprocess.run(command,env=preview_env,check=True,capture_output=True,text=True)
   report=json.loads(output.read_text());assert report['mode']=='READ_ONLY_DRY_RUN' and report['correction_executed'] is False
   assert report['original']['id']==target and report['balance_centavos']==report['charge_centavos']-report['receipts_centavos']
   assert output.stat().st_mode&0o777==0o600
   assert subprocess.run(command,env=preview_env,capture_output=True).returncode!=0,'Existing preview overwritten'
  assert json.loads(sql(source,reconciliation))==before,'Preview modified financial/audit state'
  service.write_text(service.read_text()+f'[ordinary-test]\nhost=/workspace/tooling/pgtest\nport=55432\ndbname={source}\noptions=-c role=authenticated\n')
  denied=[sys.executable,str(ROOT/'admin/exception_preview.py'),'--service','ordinary-test','--kind','order','--id',sql(source,'select id from aq_private.orders limit 1;').strip(),'--output',str(root/'forbidden.json')]
  assert subprocess.run(denied,env=preview_env,capture_output=True).returncode!=0
  assert not (root/'forbidden.json').exists(),'Ordinary role exported correction evidence'
  print('PASS: order/sale correction previews reconcile charges/receipts/balances, preserve database state and refuse evidence overwrite (0600).')
  print('PASS: ordinary authenticated database role cannot export privileged correction evidence.')
  subprocess.run(['pg_dump','-h','/workspace/tooling/pgtest','-p','55432','-d',source,'--format=custom','--file',str(bundle/'database.dump')],check=True,capture_output=True)
  (bundle/'roles.sql').write_text('-- Existing local test cluster roles; no production passwords or role restore.\n')
  for name in ['project-config.json','auth-config.json']:(bundle/name).write_text('{"kind":"SYNTHETIC LOCAL TEST; managed configuration not simulated"}')
  (bundle/'storage-inventory.json').write_text('{"uses_storage":false}')
  key=root/'recovery.identity';subprocess.run(['age-keygen','-o',str(key)],check=True,capture_output=True);key.chmod(0o600)
  recipient=root/'public-recipients';recipient.write_bytes(subprocess.run(['age-keygen','-y',str(key)],check=True,capture_output=True).stdout)
  dry=json.loads(sql(source,f"select aq_private.erase_eligible_contact('{customer_id}','{decision_id}','SYNTHETIC APPROVAL','SYNTHETIC CONTACT REMOVAL TEST',false);"));assert dry['mode']=='DRY RUN' and dry['customer_contact_rows']==1
  decision=json.loads(sql(source,f"select aq_private.erase_eligible_contact('{customer_id}','{decision_id}','SYNTHETIC APPROVAL','SYNTHETIC CONTACT REMOVAL TEST',true);"));assert decision['mode']=='EXECUTED'
  cutoff=decision['effective_cutoff']
  ledger=root/'independent-erasure-ledger.json';base_env={**os.environ,'PGHOST':'/workspace/tooling/pgtest','PGPORT':'55432'}
  subprocess.run([sys.executable,str(ROOT/'admin/recovery/privacy_ledger.py'),'export','--ledger',str(ledger)],env={**base_env,'PGDATABASE':source},check=True,capture_output=True)
  entry=backup.package(bundle,recipient,root/'primary',root/'independent',root/'catalog')
  backup.restore(root/'primary'/entry['filename'],root/'catalog'/(entry['filename']+'.json'),key,root/'decrypted')
  subprocess.run(['pg_restore','-h','/workspace/tooling/pgtest','-p','55432','-d',destination,'--exit-on-error','--no-owner',str(root/'decrypted/bundle/database.dump')],check=True,capture_output=True)
  assert sql(destination,"select not has_function_privilege('anon','public.aq_query(jsonb)','execute') and has_function_privilege('authenticated','public.aq_query(jsonb)','execute') and not has_function_privilege('authenticated','public.aq_admin_resolve_username(text)','execute');").strip()=='t','Restored API grants widened'
  assert sql(destination,"select bool_and(relrowsecurity) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='aq_private' and c.relkind='r';").strip()=='t','Restored private RLS missing'
  subprocess.run([sys.executable,str(ROOT/'admin/recovery/privacy_ledger.py'),'replay','--ledger',str(ledger)],env={**base_env,'PGDATABASE':destination},check=True,capture_output=True)
  subprocess.run([sys.executable,str(ROOT/'admin/recovery/privacy_ledger.py'),'replay','--ledger',str(ledger),'--execute','--confirm-database',destination],env={**base_env,'PGDATABASE':destination},check=True,capture_output=True)
  replay=json.loads(sql(destination,f"select aq_private.erase_eligible_contact('{customer_id}','{decision_id}','SYNTHETIC APPROVAL','SYNTHETIC CONTACT REMOVAL TEST',true,'{cutoff}'::timestamptz);"));assert replay['customer_contact_rows']==1
  assert sql(destination,"select not has_function_privilege('authenticated','aq_private.erase_eligible_contact(uuid,uuid,text,text,boolean,timestamptz)','execute') and not has_function_privilege('service_role','aq_private.erase_eligible_contact(uuid,uuid,text,text,boolean,timestamptz)','execute');").strip()=='t'
  assert sql(destination,"select count(*) from aq_private.customers where contact_number is not null or address is not null;").strip()=='0'
  assert sql(destination,"select count(*) from aq_private.orders where contact_number is not null or address is not null;").strip()=='0'
  assert sql(destination,"select count(*) from aq_private.audit_events where before_state ? 'contact_number' or before_state ? 'address' or after_state ? 'contact_number' or after_state ? 'address';").strip()=='0','Audit contact snapshots survived replay'
  assert sql(destination,"select count(*) from aq_private.requests where payload ? 'contact_number' or payload ? 'address' or result ? 'contact_number' or result ? 'address';").strip()=='0','Retry contact snapshots survived replay'
  retry=sql(destination,"select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',false);select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,false);select public.aq_command(:'original'::jsonb);",{'original':original_request})
  assert 'contact_number' not in json.loads(retry.splitlines()[-1]),'Idempotent retry resurrected erased contact'
  after=json.loads(sql(destination,reconciliation));assert before['audit']+1==after['audit'];before['audit']=after['audit'];assert before==after,(before,after);assert after['orders']==1 and after['sales']==1 and after['charges']==31500 and after['receipts']==31500 and after['expenses']==75000
  assert sql(destination,'select reschedule_notes from aq_private.orders;').strip()=='SYNTHETIC OPERATIONAL NOTE','Operational notes missing after restore'
  assert sql(destination,'select bool_and(physical_filled>=(select coalesce(sum(quantity),0) from aq_private.reservations) and physical_filled>=0 and empty_quantity>=0) from aq_private.inventory;').strip()=='t'
  print('PASS: synthetic populated DB age package/decrypt + pg_restore into new UTF8 database; charge/receipt/expense/stock/reservation/Auth-mapping/audit reconciliation and protected grants/RLS match; dry-run/approved-contact ledger replay removes restored contact snapshots while retaining financial history.')
  print('NOT RUN: hosted managed Auth/config/Storage restore, actual controller approval/external ledger custody and operating-hours RPO/RTO measurement.')
finally:
 for db in reversed(created):sql('postgres',f'drop database {db};')
