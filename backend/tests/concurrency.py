"""Real multi-connection last-gallon test on an explicitly isolated local database."""
import concurrent.futures,os,pathlib,subprocess,uuid
root=pathlib.Path(__file__).resolve().parents[2]
psql='/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql'
base=[psql,'-h','/workspace/tooling/pgtest','-p','55432','-v','ON_ERROR_STOP=1']
database='aq_test_'+uuid.uuid4().hex

def run(sql,db=database):return subprocess.run(base+['-d',db,'-At','-c',sql],capture_output=True,text=True)
r=run(f"create database {database} encoding 'UTF8' template template0",db='postgres');assert r.returncode==0,r.stderr
try:
 # Roles already exist on the local cluster; schemas/functions are isolated per database.
 stub=(root/'backend/tests/auth_stub.sql').read_text();stub='\n'.join(x for x in stub.splitlines() if not x.startswith('create role '))
 for sql in [stub,(root/'supabase/migrations/202611030001_core.sql').read_text()]:
  r=run(sql);assert r.returncode==0,r.stderr
 seed="""insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid'),('10000000-0000-4000-8000-000000000002','staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner'),('10000000-0000-4000-8000-000000000002','staff.test','Synthetic Staff','09000000002','staff');
update aq_private.inventory set physical_filled=1,initialized=true;"""
 r=run(seed);assert r.returncode==0,r.stderr
 def sell(i):
  actor=f'10000000-0000-4000-8000-00000000000{i+1}'
  sql=f"""begin;
select set_config('request.jwt.claim.sub','{actor}',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','{uuid.uuid4()}','iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
select public.aq_command('{{"operation":"create_sale","request_id":"{uuid.uuid4()}","customer_name":"Synthetic concurrent sale","quantity":1,"cash_centavos":3500}}');commit;"""
  return run(sql)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(sell,range(2)))
 assert sum(r.returncode==0 for r in results)==1,[(r.returncode,r.stderr) for r in results]
 assert any('Not enough filled gallons.' in r.stderr for r in results)
 r=run("select physical_filled||','||(select count(*) from aq_private.sales)||','||(select count(*) from aq_private.payments) from aq_private.inventory")
 assert r.returncode==0 and r.stdout.strip()=='0,1,1',(r.stdout,r.stderr)
 print('PASS: concurrent Owner/Staff sale of last gallon: one sale/receipt, one stock rejection, stock zero.')
finally:
 r=run(f'drop database {database}',db='postgres');assert r.returncode==0,r.stderr
