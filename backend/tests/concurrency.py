"""Real multi-connection walk-in/delivery race tests on an explicitly isolated local database."""
import concurrent.futures,os,pathlib,subprocess,uuid,json,threading
root=pathlib.Path(__file__).resolve().parents[2]
psql='/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql'
base=[psql,'-h','/workspace/tooling/pgtest','-p','55432','-v','ON_ERROR_STOP=1']
database='aq_test_'+uuid.uuid4().hex

def run(sql,db=database):return subprocess.run(base+['-d',db,'-At','-c',sql],capture_output=True,text=True)
r=run(f"create database {database} encoding 'UTF8' template template0",db='postgres');assert r.returncode==0,r.stderr
try:
 # Roles already exist on the local cluster; schemas/functions are isolated per database.
 stub=(root/'backend/tests/auth_stub.sql').read_text();stub='\n'.join(x for x in stub.splitlines() if not x.startswith('create role '))
 for sql in [stub]+[p.read_text() for p in sorted((root/'supabase/migrations').glob('*.sql'))]:
  r=run(sql);assert r.returncode==0,r.stderr
 seed="""insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid'),('10000000-0000-4000-8000-000000000002','staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner'),('10000000-0000-4000-8000-000000000002','staff.test','Synthetic Staff','09000000002','staff');"""
 r=run(seed);assert r.returncode==0,r.stderr
 def shared_request(payload,actor=1,method="aq_command",aal="aal1"):
  assert method in ("aq_command","aq_admin_prepare_staff") and aal in ("aal1","aal2")
  sql="begin;select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-00000000000"+str(actor)+"',true);select set_config('request.jwt.claims',jsonb_build_object('session_id','"+str(uuid.uuid4())+"','iat',extract(epoch from clock_timestamp()),'aal','"+aal+"')::text,true);set local role authenticated;select public."+method+"('"+json.dumps(payload).replace("'","''")+"'::jsonb);commit;"
  return run(sql)
 payload=dict(operation='create_counter_order',request_id=str(uuid.uuid4()),customer_name='Synthetic concurrent retry',quantity=1,cash_centavos=3500)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:retries=list(pool.map(shared_request,[payload,payload]))
 assert all(r.returncode==0 for r in retries),[r.stderr for r in retries]
 ids=[json.loads(next(line for line in r.stdout.splitlines() if line.startswith('{') and 'reference' in line))['id'] for r in retries]
 assert ids[0]==ids[1],'Identical concurrent retry returned different walk-in order'
 r=run("select (select count(*) from aq_private.orders where channel='counter')||','||(select count(*) from aq_private.payments)")
 assert r.stdout.strip()=='1,1',(r.stdout,r.stderr)
 print('PASS: identical concurrent walk-in request returns the same order with one order/receipt effect.')
 payload['request_id']=str(uuid.uuid4());a={**payload,'confirm_credit':True};b={**a,'cash_centavos':1000}
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:conflicts=list(pool.map(shared_request,[a,b]))
 assert sum(r.returncode==0 for r in conflicts)==1,[r.stderr for r in conflicts]
 assert any('Request key was already used with different data.' in r.stderr for r in conflicts)
 r=run("select (select count(*) from aq_private.orders where channel='counter')||','||(select count(*) from aq_private.payments)")
 assert r.stdout.strip()=='2,2',(r.stdout,r.stderr)
 print('PASS: same-key/different-payload concurrent conflict has one walk-in order/receipt effect.')

 def command(operation,**fields):
  result=shared_request(dict(operation=operation,request_id=str(uuid.uuid4()),**fields))
  assert result.returncode==0,result.stderr
  return json.loads(next(line for line in result.stdout.splitlines() if line.startswith('{') and '"id"' in line))
 customer=command('create_customer',name='Synthetic delivery race',customer_type='household',contact_number='09000000003',address='Synthetic test')
 orders=[]
 for quantity in [5,3]:
  order=command('create_order',customer_id=customer['id'],quantity=quantity)
  command('transition_order',order_id=order['id'],status='for_pickup')
  command('record_pickup',order_id=order['id'],quantity=quantity)
  command('transition_order',order_id=order['id'],status='refilling')
  command('transition_order',order_id=order['id'],status='ready_for_delivery')
  orders.append(order['id'])
 def state():
  result=run("select jsonb_build_object('delivered',(select sum(quantity) from aq_private.deliveries),'deliveries',(select count(*) from aq_private.deliveries),'walk_ins',(select count(*) from aq_private.orders where channel='counter'),'payments',(select count(*) from aq_private.payments));")
  assert result.returncode==0,result.stderr
  return json.loads(result.stdout)
 def deliver_payload(quantity,order=orders[0]):
  return dict(operation='record_delivery',request_id=str(uuid.uuid4()),order_id=order,quantity=quantity)
 # Two different authenticated actors try 3 of the same remaining5 gallons.
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  attempts=list(pool.map(lambda actor:shared_request(deliver_payload(3),actor),[1,2]))
 assert sum(r.returncode==0 for r in attempts)==1,[r.stderr for r in attempts]
 assert any('Delivered quantity cannot exceed the pending quantity.' in r.stderr for r in attempts),[r.stderr for r in attempts]
 assert state()==dict(delivered=3,deliveries=1,walk_ins=2,payments=2),state()
 result=run(f"select status||','||delivered_quantity from aq_private.orders where id='{orders[0]}';")
 assert result.stdout.strip()=='undelivered,3',result.stdout
 print('PASS: concurrent Owner/Staff partial deliveries cannot overdeliver; remaining 2 stay pending.')
 final=deliver_payload(2)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:attempts=list(pool.map(shared_request,[final,final]))
 assert all(r.returncode==0 for r in attempts),[r.stderr for r in attempts]
 replies=[json.loads(next(line for line in r.stdout.splitlines() if line.startswith('{') and '"id"' in line)) for r in attempts]
 assert replies[0]==replies[1] and replies[0]['status']=='delivered',replies
 assert state()==dict(delivered=5,deliveries=2,walk_ins=2,payments=2),state()
 print('PASS: identical concurrent final-delivery retry returns same result with exactly one new delivery.')
 # Two actors race to deliver the other order's 3 gallons with different keys: only one lands.
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  attempts=list(pool.map(lambda actor:shared_request(deliver_payload(3,orders[1]),actor),[1,2]))
 assert sum(r.returncode==0 for r in attempts)==1,[r.stderr for r in attempts]
 assert state()==dict(delivered=8,deliveries=3,walk_ins=2,payments=2),state()
 print('PASS: competing full deliveries of one order record exactly one delivery.')
 # Pending account aliases and ordinary self-profile edits use the same lock.
 before_alias=state();gate=threading.Barrier(2)
 rename=dict(operation='update_profile',request_id=str(uuid.uuid4()),username='race.alias',full_name='Synthetic Staff',contact_number='09000000002')
 prepare=dict(operation='create',request_id=str(uuid.uuid4()),payload_hash='d'*64,username='race.alias',full_name='Synthetic New Staff',contact_number='09000000003',active=True)
 def compete_alias(args):gate.wait(timeout=10);return shared_request(*args)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  outcomes=list(pool.map(compete_alias,[(rename,2,'aq_command','aal1'),(prepare,1,'aq_admin_prepare_staff','aal2')]))
 assert sum(r.returncode==0 for r in outcomes)==1,[r.stderr for r in outcomes]
 assert any('Username already exists.' in r.stderr for r in outcomes),[r.stderr for r in outcomes]
 result=run("select (select count(*) from aq_private.profiles where username='race.alias')+(select count(*) from aq_private.account_operations where state='pending' and payload->>'username'='race.alias');")
 assert result.returncode==0 and result.stdout.strip()=='1',(result.stdout,result.stderr)
 assert state()==before_alias,'Identity race changed financial state'
 print('PASS: concurrent Staff self-profile rename versus Owner AAL2 account preparation has exactly one alias owner/reservation; loser rejected, financial state unchanged (JWT stubs).')


finally:
 r=run(f'drop database {database}',db='postgres');assert r.returncode==0,r.stderr
