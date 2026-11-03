"""Controlled initial setup is one-time; test only in a freshly created disposable database."""
import pathlib,subprocess,uuid
root=pathlib.Path(__file__).resolve().parents[2];db='aq_bootstrap_'+uuid.uuid4().hex
base=['psql','-h','/workspace/tooling/pgtest','-p','55432','-v','ON_ERROR_STOP=1','-At']
def run(database,sql,args=()):return subprocess.run(base+['-d',database]+list(args),input=sql,capture_output=True,text=True)
r=run('postgres',f"create database {db} encoding 'UTF8' template template0;");assert r.returncode==0,r.stderr
try:
 stub='\n'.join(x for x in (root/'backend/tests/auth_stub.sql').read_text().splitlines() if not x.startswith('create role '))
 r=run(db,stub+(root/'supabase/migrations/202611030001_core.sql').read_text()+"insert into auth.users values ('10000000-0000-4000-8000-000000000001','synthetic-owner@test.invalid');");assert r.returncode==0,r.stderr
 args=[]
 for key,val in dict(auth_id='10000000-0000-4000-8000-000000000001',username='synthetic.owner',full_name='Synthetic Owner',contact='09000000000',empty_count='11',filled_count='3',approval='SYNTHETIC TEST APPROVAL').items():args+=['-v',key+'='+val]
 script=(root/'admin/bootstrap.sql').read_text();r=run(db,script,args);assert r.returncode==0,r.stderr
 r=run(db,script,args);assert r.returncode!=0 and 'already initialized' in r.stderr
 r=run(db,"select empty_quantity||','||physical_filled||','||(select count(*) from aq_private.profiles)||','||(select count(*) from aq_private.audit_events) from aq_private.inventory;")
 assert r.returncode==0 and r.stdout.strip()=='11,3,1,1',(r.stdout,r.stderr)
 r=run(db,"select setval('aq_private.reference_seq',999999,true);select aq_private.new_reference('TEST');");assert r.returncode==0 and r.stdout.strip().splitlines()[-1].endswith('-1000000'),(r.stdout,r.stderr)
 print('PASS: reference counter expands beyond six digits without truncation.')
 print('PASS: controlled Owner/opening inventory setup once; repeated bootstrap rejected without duplicate profile/stock/audit effects.')
finally:
 r=run('postgres',f'drop database {db};');assert r.returncode==0,r.stderr
