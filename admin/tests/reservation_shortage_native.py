"""Actual native own-reservation shortage error using an explicit owned administrator fixture.

No production/manual-adjustment endpoint. Persists every financial/setup intent;
restores only a guarded synthetic order reservation, and refuses blind mutation retries.
"""
import argparse
import copy
import datetime
import hashlib
import hmac
import json
import os
import re
import secrets
import shlex
import ssl
import subprocess
import time
import urllib.request
import uuid
from zoneinfo import ZoneInfo
from connected_android import ADB, LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser=argparse.ArgumentParser()
    options=parser.parse_args()
    c=json.loads((LOCAL/'private-config.json').read_text())
    assert c['kind']=='SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url']=='https://127.0.0.1:55436'
    assert re.fullmatch(r'aq_http_[0-9a-f]{8}',c['database'])
    assert adb('emu','avd','name').stdout.decode().splitlines()[0]=='aq_api29_software'
    path=LOCAL/'reservation-shortage-native-state.json'
    assert not path.exists(), 'Prior intent exists; reconcile native/order/restoration outcome before any retry'
    env=dict(os.environ,LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(body,**variables):
        args=['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql','-X','-At','-h','/workspace/tooling/pgtest','-p','55432','-d',c['database'],'-v','ON_ERROR_STOP=1']
        for key,value in variables.items():args+=['-v',key+'='+str(value)]
        return subprocess.run(args,input=body,text=True,capture_output=True,check=True,env=env).stdout.strip()
    tables=['customers','profiles','orders','inventory','reservations','deliveries','sales','payments','expenses','requests','audit_events']
    snapshot_sql='select jsonb_build_object('+','.join("'"+t+"',(select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),'[]'::jsonb) from aq_private."+t+' x)' for t in tables)+');'
    def snapshot():return json.loads(sql(snapshot_sql))
    def bucket(kind,value):return hmac.new(c['SUPABASE_SERVICE_ROLE_KEY'].encode(),json.dumps({kind:value},sort_keys=True,separators=(',',':')).encode(),hashlib.sha256).hexdigest()
    for _ in range(35):
        delay=int(sql("select coalesce(max(ceil(greatest(0,extract(epoch from started_at+interval '15 minutes'-clock_timestamp()))))::int,0) from aq_private.login_windows where (bucket=:'user_bucket' and attempts+2>10) or (bucket=:'source_bucket' and attempts+2>60);",user_bucket='u:'+bucket('username',c['owner_username']),source_bucket='s:'+bucket('source','127.0.0.1')))
        if not delay:break
        print('Waiting actual login window',delay,'seconds; no reset',flush=True);time.sleep(min(30,delay+1))
    else:raise RuntimeError('Inspect actual login window')
    context=ssl.create_default_context(cafile=c['ca_cert'])
    def call(route,body,token=None):
        req=urllib.request.Request(c['url']+route,data=json.dumps(body).encode(),headers={'apikey':c['SUPABASE_ANON_KEY'],'Authorization':'Bearer '+(token or c['SUPABASE_ANON_KEY']),'content-type':'application/json'})
        with urllib.request.urlopen(req,context=context,timeout=20) as response:return json.load(response)
    token=call('/functions/v1/username-login',{'username':c['owner_username'],'password':c['owner_password']})['access_token']
    def query(resource,**fields):return call('/rest/v1/rpc/aq_query',{'p_request':{'resource':resource,**fields}},token)
    def command(label,operation,**fields):
        payload={'operation':operation,'request_id':str(uuid.uuid4()),**fields};assert label not in state['commands']
        state['commands'][label]={'payload':payload};private_json(path,state)
        value=call('/rest/v1/rpc/aq_command',{'p_request':payload},token)
        state['commands'][label]['result']=value;private_json(path,state);return value
    original=query('inventory');assert original['reserved_filled']==0 and original['pending_quantity']==0
    customer_state=json.loads((LOCAL/'owner-customer-create-state.json').read_text());assert customer_state['completed']
    customer=query('customer',id=customer_state['customer_id']);assert customer['customer_type']=='dealer'
    day=str(datetime.datetime.now(ZoneInfo('Asia/Manila')).date());report=query('report',start_date=day,end_date=day)
    baseline_rows=snapshot()
    run_id=secrets.token_hex(4)
    state={'kind':'SYNTHETIC_LOCAL_RESERVATION_SHORTAGE_UI','run_id':run_id,'baseline_rows':baseline_rows,'stock_before':original,'report_before':report,'commands':{},'completed':False,'shortage_restored':False}
    private_json(path,state)
    order=command('order','create_order',customer_id=customer['id'],quantity=2,business_date=day);state['order_id']=order['id'];private_json(path,state)
    command('for_pickup','transition_order',order_id=order['id'],status='for_pickup')
    command('pickup','record_pickup',order_id=order['id'],quantity=2)
    command('refilling','transition_order',order_id=order['id'],status='refilling')
    command('ready','transition_order',order_id=order['id'],status='ready_for_delivery')
    seeded=query('inventory');assert seeded['physical_filled']==original['physical_filled']+2
    state['shortage']={'expected_reserved':2,'physical_filled':seeded['physical_filled'],'injected_reserved':1};private_json(path,state)
    def set_reservation(expected,replacement):
        result=sql("update aq_private.reservations set quantity=:'replacement'::bigint where order_id=:'order'::uuid and quantity=:'expected'::bigint and exists(select 1 from aq_private.orders where id=:'order'::uuid and status='ready_for_delivery' and quantity=2 and delivered_quantity=0) and (select physical_filled from aq_private.inventory where singleton)=:'filled'::bigint returning quantity;",order=order['id'],expected=expected,replacement=replacement,filled=seeded['physical_filled'])
        assert result.splitlines()==[str(replacement),'UPDATE 1'],'Unexpected own-reservation state; no unguarded restoration'
    def restore():
        if state['shortage_restored']:return
        state['restoration_intent']=True;private_json(path,state)
        set_reservation(1,2);state['shortage_restored']=True;private_json(path,state)
    process=None
    try:
        set_reservation(2,1);state['shortage_applied']=True;state['before_rejection']=snapshot();private_json(path,state)
        fixture={'kind':state['kind'],'run_id':run_id,'username':c['owner_username'],'password':c['owner_password'],'order_id':order['id'],'reference':order['reference']}
        private_json(LOCAL/'reservation-shortage-native-fixture.json',fixture)
        for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:adb('install','-r','-t',str(REPO/apk))
        adb('reverse','tcp:55436','tcp:55436')
        assert re.fullmatch(r'[0-9a-f]{8}',run_id)
        adb('shell','run-as',PACKAGE,'rm','-f','files/reservation-shortage-result.json','files/reservation-shortage-restored-'+run_id+'.json')
        adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('umask 077; cat > files/reservation-shortage-credentials.json'),input=json.dumps(fixture).encode())
        transcript=LOCAL/'reservation-shortage-native-instrumentation.log'
        with transcript.open('wb') as log:
            transcript.chmod(0o600)
            process=subprocess.Popen(ADB+['shell','am','instrument','-w','-r','-e','class',PACKAGE+'.ReservationShortageDeviceTest',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT)
            end=time.monotonic()+360
            while process.poll() is None:
                if time.monotonic()>end:process.terminate();raise RuntimeError('Bounded test deadline; inspect order before retry')
                probe=subprocess.run(ADB+['exec-out','run-as',PACKAGE,'cat','files/reservation-shortage-result.json'],capture_output=True)
                try:progress=json.loads(probe.stdout)
                except json.JSONDecodeError:progress={}
                if progress.get('run_id')==run_id and progress.get('phase')=='shortage_rejected' and not state['shortage_restored']:
                    assert snapshot()==state['before_rejection'],'Rejected delivery changed protected records'
                    assert query('order',id=order['id'])['status']=='ready_for_delivery'
                    restore()
                    adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('umask 077; cat > files/reservation-shortage-restored-'+run_id+'.json'),input=b'{"restored":true}')
                time.sleep(.2)
        text=transcript.read_text().replace(c['owner_password'],'[private fixture withheld]');evidence=REPO/'evidence/android-reservation-shortage.log';evidence.write_text(text)
        assert 'OK (1 test)' in text and state['shortage_restored'],'Inspect actual native/order outcome before retry'
    finally:
        if process is not None and process.poll() is None:
            adb('shell','am','force-stop',PACKAGE)
            process.terminate()
        if not state['shortage_restored']:
            restore();state['failure_restored_shortage_only']=True;private_json(path,state)
    done=query('order',id=order['id']);after=query('inventory')
    assert (done['status'],done['delivered_quantity'],done['pending_quantity'],done['reserved_quantity'],done['total_centavos'])==('delivered',2,0,0,6000)
    for key in ['physical_filled','empty_quantity','reserved_filled','pending_quantity']:assert after[key]==original[key],key
    report_after=query('report',start_date=day,end_date=day)
    for key in ['payments_received_centavos','expenses_centavos','net_income_centavos']:assert report_after[key]==report[key]
    for key in ['revenue_centavos','outstanding_centavos']:assert report_after[key]==report[key]+6000
    requests=json.loads(sql("select jsonb_agg(jsonb_build_object('payload',payload,'request_id',request_id)) from aq_private.requests where actor_id=:'owner'::uuid and payload->>'order_id'=:'order' and payload->>'operation'='record_delivery';",owner=c['owner_id'],order=order['id']))
    assert len(requests)==1;replay={**requests[0]['payload'],'request_id':requests[0]['request_id']}
    state['delivery_replay_intent']=replay;private_json(path,state);before_replay=snapshot()
    response=call('/rest/v1/rpc/aq_command',{'p_request':replay},token)
    assert response['status']=='delivered' and snapshot()==before_replay
    state.update(delivery_replay_verified=True);private_json(path,state)
    adb('shell','run-as',PACKAGE,'rm','files/reservation-shortage-credentials.json')
    final_rows=snapshot()
    for table,rows in state['baseline_rows'].items():
        if table=='inventory':continue
        assert all(row in final_rows[table] for row in rows),'Preexisting row changed: '+table
    for table,delta in {'customers':0,'profiles':0,'orders':1,'reservations':1,'deliveries':1,'sales':0,'payments':0,'expenses':0}.items():assert len(final_rows[table])-len(state['baseline_rows'][table])==delta,'Unexpected records: '+table
    state['completed']=True;private_json(path,state)
    with evidence.open('a') as log:log.write('\nPASS: actual native insufficient-own-reservation error despite ample physical stock; all protected/request/audit rows unchanged. Explicit owned test-admin reservation-only shortage restored with guards; rotated retry retains2gallon input/previews and saves one final delivery. Stock/reservations return baseline, charge6000only/no receipts/expenses, all preexisting rows unchanged; exact signed delivery replay has no effect. SQL separately verifies server reservation denial for both roles. No product adjustment endpoint or production data.\n')
    print('PASS: native own-reservation guard/checked restoration/rotation/retry/delivery replay and protected-row reconciliation')



if __name__=='__main__':main()
