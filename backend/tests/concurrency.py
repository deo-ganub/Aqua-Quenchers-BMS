"""Real multi-connection last-gallon test on an explicitly isolated local database."""
import concurrent.futures,os,pathlib,subprocess,uuid,json
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
 def shared_request(payload,actor=1):
  sql="begin;select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-00000000000"+str(actor)+"',true);select set_config('request.jwt.claims',jsonb_build_object('session_id','"+str(uuid.uuid4())+"','iat',extract(epoch from clock_timestamp()))::text,true);set local role authenticated;select public.aq_command('"+json.dumps(payload).replace("'","''")+"'::jsonb);commit;"
  return run(sql)
 r=run('update aq_private.inventory set physical_filled=1;');assert r.returncode==0,r.stderr
 payload=dict(operation='create_sale',request_id=str(uuid.uuid4()),customer_name='Synthetic concurrent retry',quantity=1,cash_centavos=3500)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:retries=list(pool.map(shared_request,[payload,payload]))
 assert all(r.returncode==0 for r in retries),[r.stderr for r in retries]
 ids=[json.loads(next(line for line in r.stdout.splitlines() if line.startswith('{') and 'reference' in line))['id'] for r in retries]
 assert ids[0]==ids[1],'Identical concurrent retry returned different sale'
 r=run("select physical_filled||','||(select count(*) from aq_private.sales)||','||(select count(*) from aq_private.payments) from aq_private.inventory")
 assert r.stdout.strip()=='0,2,2',(r.stdout,r.stderr)
 print('PASS: identical concurrent request returns same sale and creates one additional stock/receipt effect.')
 r=run('update aq_private.inventory set physical_filled=1;');assert r.returncode==0,r.stderr
 payload['request_id']=str(uuid.uuid4());a={**payload,'confirm_credit':True};b={**a,'cash_centavos':1000}
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:conflicts=list(pool.map(shared_request,[a,b]))
 assert sum(r.returncode==0 for r in conflicts)==1,[r.stderr for r in conflicts]
 assert any('Request key was already used with different data.' in r.stderr for r in conflicts)
 r=run("select physical_filled||','||(select count(*) from aq_private.sales)||','||(select count(*) from aq_private.payments) from aq_private.inventory")
 assert r.stdout.strip()=='0,3,3',(r.stdout,r.stderr)
 print('PASS: same-key/different-payload concurrent conflict has one sale/receipt effect.')

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
  result=run("select jsonb_build_object('physical',(select physical_filled from aq_private.inventory),'reserved',(select sum(quantity) from aq_private.reservations),'delivered',(select sum(quantity) from aq_private.deliveries),'deliveries',(select count(*) from aq_private.deliveries),'sales',(select count(*) from aq_private.sales),'payments',(select count(*) from aq_private.payments));")
  assert result.returncode==0,result.stderr
  return json.loads(result.stdout)
 def deliver_payload(quantity,order=orders[0]):
  return dict(operation='record_delivery',request_id=str(uuid.uuid4()),order_id=order,quantity=quantity)
 # Two different authenticated actors try 3 of the same remaining5 gallons.
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  attempts=list(pool.map(lambda actor:shared_request(deliver_payload(3),actor),[1,2]))
 assert sum(r.returncode==0 for r in attempts)==1,[r.stderr for r in attempts]
 assert any('Delivered quantity cannot exceed the pending quantity.' in r.stderr for r in attempts),[r.stderr for r in attempts]
 assert state()==dict(physical=5,reserved=5,delivered=3,deliveries=1,sales=3,payments=3),state()
 result=run(f"select status||','||delivered_quantity||','||(select quantity from aq_private.reservations where order_id='{orders[0]}') from aq_private.orders where id='{orders[0]}';")
 assert result.stdout.strip()=='undelivered,3,2',result.stdout
 print('PASS: concurrent Owner/Staff partial deliveries cannot overdeliver; remaining2 and other-order3 stay reserved.')
 final=deliver_payload(2)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:attempts=list(pool.map(shared_request,[final,final]))
 assert all(r.returncode==0 for r in attempts),[r.stderr for r in attempts]
 replies=[json.loads(next(line for line in r.stdout.splitlines() if line.startswith('{') and '"id"' in line)) for r in attempts]
 assert replies[0]==replies[1] and replies[0]['status']=='delivered',replies
 assert state()==dict(physical=3,reserved=3,delivered=5,deliveries=2,sales=3,payments=3),state()
 print('PASS: identical concurrent final-delivery retry returns same result with exactly one new delivery and stock effect.')
 # All remaining physical stock belongs to the other order; POS can never take it.
 sale=dict(operation='create_sale',request_id=str(uuid.uuid4()),customer_name='Synthetic reserved POS race',quantity=3,cash_centavos=10500)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  futures=[pool.submit(shared_request,deliver_payload(3,orders[1]),2),pool.submit(shared_request,sale)]
  delivery,pos=[f.result() for f in futures]
 assert delivery.returncode==0,delivery.stderr
 assert pos.returncode!=0 and 'Not enough filled gallons.' in pos.stderr,pos.stderr
 assert state()==dict(physical=0,reserved=0,delivered=8,deliveries=3,sales=3,payments=3),state()
 print('PASS: competing POS cannot consume reserved stock before/after final delivery; no extra sale/receipt, all reservations reconciled.')

finally:
 r=run(f'drop database {database}',db='postgres');assert r.returncode==0,r.stderr
