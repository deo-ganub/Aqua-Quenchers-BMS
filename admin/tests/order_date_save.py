"""Actual past+future native saves per role, private intent before mutation.

Refuses any prior role intent, including an uncertain partial save. No replay of
creation on retry; reconcile observed UUIDs and device progress first.
"""
import argparse
import datetime
import hashlib
import hmac
import json
import os
import secrets
import shlex
import subprocess
import time
from connected_android import LOCAL, REPO, PACKAGE, adb, private_json


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--owner',action='store_true');parser.add_argument('--reconcile-only',action='store_true');options=parser.parse_args();role='owner' if options.owner else 'staff'
    c=json.loads((LOCAL/'private-config.json').read_text());assert c['kind']=='SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url']=='https://127.0.0.1:55436'
    if not options.reconcile_only:assert adb('emu','avd','name').stdout.decode().splitlines()[0]=='aq_api29_software'
    path=LOCAL/('order-date-save-'+role+'-state.json');previous=json.loads(path.read_text()) if path.exists() else None
    if previous:assert options.reconcile_only and not previous['completed'] and previous['kind']=='SYNTHETIC_LOCAL_ORDER_DATE_SAVE_UI','Inspect prior outcome; never replay creation'
    else:assert not options.reconcile_only,'No saved intent to reconcile'
    env=dict(os.environ,LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(body,**variables):
        args=['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql','-X','-At','-h','/workspace/tooling/pgtest','-p','55432','-d',c['database'],'-v','ON_ERROR_STOP=1']
        for key,value in variables.items():args+=['-v',key+'='+str(value)]
        return subprocess.run(args,input=body,text=True,capture_output=True,check=True,env=env).stdout.strip()
    staff_id=json.loads((LOCAL/'http-test-state.json').read_text())['staff_id'];actor=c['owner_id'] if options.owner else staff_id
    person=json.loads(sql("select to_jsonb(p) from aq_private.profiles p where user_id=:'actor'::uuid;",actor=actor));assert person['role']==role and person['active']
    def bucket(kind,value):return hmac.new(c['SUPABASE_SERVICE_ROLE_KEY'].encode(),json.dumps({kind:value},sort_keys=True,separators=(',',':')).encode(),hashlib.sha256).hexdigest()
    if not previous:
        for _ in range(35):
            delay=int(sql("select coalesce(max(ceil(greatest(0,extract(epoch from started_at+interval '15 minutes'-clock_timestamp()))))::int,0) from aq_private.login_windows where (bucket=:'user_bucket' and attempts+1>10) or (bucket=:'source_bucket' and attempts+1>60);",user_bucket='u:'+bucket('username',person['username']),source_bucket='s:'+bucket('source','127.0.0.1')))
            if not delay:break
            print('Waiting actual login window',delay,'seconds; no reset',flush=True);time.sleep(min(30,delay+1))
        else:raise RuntimeError('Inspect login window')
    creation=json.loads((LOCAL/'owner-customer-create-state.json').read_text());assert creation['completed']
    customer=json.loads(sql("select to_jsonb(c) from aq_private.customers c where id=:'customer'::uuid;",customer=creation['customer_id']));assert customer['customer_type']=='dealer'
    today=datetime.date.fromisoformat(sql('select aq_private.today();'));past=today-datetime.timedelta(days=1);future=today+datetime.timedelta(days=1)
    tables=['customers','profiles','orders','inventory','reservations','deliveries','sales','payments','expenses']
    snapshot_sql='select jsonb_build_object('+','.join("'"+t+"',(select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),'[]'::jsonb) from aq_private."+t+' x)' for t in tables)+") || jsonb_build_object('auth_ids',(select jsonb_agg(jsonb_build_object('id',id,'email',email) order by id) from auth.users));"
    if previous:
        state=previous;assert state['actor_id']==actor and state['customer_id']==customer['id']
        fixture=json.loads((LOCAL/('order-date-save-'+role+'-fixture.json')).read_text());before=state['before'];reports=state['reports_before'];run_id=state['run_id']
        today=datetime.date.fromisoformat(fixture['today']);future=max(fixture['dates'])
        after=json.loads(sql(snapshot_sql));old_ids={o['id'] for o in before['orders']};created=[o for o in after['orders'] if o['id'] not in old_ids]
        text=(REPO/('evidence/android-order-date-save-'+role+'.log')).read_text()
    else:
        before=json.loads(sql(snapshot_sql));reports={str(d):json.loads(sql("select aq_private.financial_summary(:'day'::date,:'day'::date);",day=d)) for d in [past,today,future]}
        run_id=secrets.token_hex(4);fixture={'kind':'SYNTHETIC_LOCAL_ORDER_DATE_SAVE_UI','run_id':run_id,'owner':options.owner,'username':person['username'],'password':c[role+'_password'],'full_name':person['full_name'],'customer_name':customer['name'],'today':str(today),'dates':[str(past),str(future)]}
        state={'kind':fixture['kind'],'run_id':run_id,'actor_id':actor,'customer_id':customer['id'],'dates':fixture['dates'],'quantity':2,'unit_centavos':3000,'before':before,'reports_before':reports,'started_at':sql('select clock_timestamp();'),'completed':False}
        private_json(path,state);private_json(LOCAL/('order-date-save-'+role+'-fixture.json'),fixture)
        for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:adb('install','-r','-t',str(REPO/apk))
        adb('reverse','tcp:55436','tcp:55436');adb('shell','run-as',PACKAGE,'rm','-f','files/order-date-save-result.json')
        adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('umask 077; cat > files/order-date-save-credentials.json'),input=json.dumps(fixture).encode())
        text=''
        try:
            run=adb('shell','am','instrument','-w','-r','-e','class',PACKAGE+'.OrderDateSaveDeviceTest',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=360)
            text=run.stdout.decode().replace(fixture['password'],'[private fixture withheld]')
        except subprocess.TimeoutExpired:
            adb('shell','am','force-stop',PACKAGE);state['native_timeout_uncertain']=True;raise
        finally:
            after=json.loads(sql(snapshot_sql));old_ids={o['id'] for o in before['orders']};created=[o for o in after['orders'] if o['id'] not in old_ids]
            state['observed_orders']=created;private_json(path,state)
    evidence=REPO/('evidence/android-order-date-save-'+role+'.log');evidence.write_text(text)
    result_path=LOCAL/('order-date-save-'+role+'-result.json')
    progress=json.loads(result_path.read_text()) if previous else json.loads(adb('exec-out','run-as',PACKAGE,'cat','files/order-date-save-result.json').stdout)
    private_json(result_path,progress)
    assert 'OK (1 test)' in text and progress.get('run_id')==run_id and progress.get('completed'),'Inspect observed orders/intent before retry'
    assert len(created)==2 and {o['business_date'] for o in created}==set(fixture['dates'])
    for o in created:
        assert (o['customer_id'],o['recorded_by'],o['quantity'],o['unit_price_centavos'],o['quantity']*o['unit_price_centavos'],o['status'])==(customer['id'],actor,2,3000,6000,'ordered')
        assert sql("select created_at>=:'start'::timestamptz and created_at<=clock_timestamp() and (created_at at time zone 'Asia/Manila')::date=:'today'::date and business_date<>(created_at at time zone 'Asia/Manila')::date from aq_private.orders where id=:'order'::uuid;",start=state['started_at'],today=today,order=o['id'])=='t'
    new_ids={o['id'] for o in created};after['orders']=[o for o in after['orders'] if o['id'] not in new_ids];assert after==before,'Dated order save changed unrelated records/cash/stock/Auth IDs'
    for date,prior in reports.items():
        report=json.loads(sql("select aq_private.financial_summary(:'day'::date,:'day'::date);",day=date))
        for key in ['payments_received_centavos','expenses_centavos','net_income_centavos']:assert report[key]==prior[key],key
        assert report['revenue_centavos']==prior['revenue_centavos']+(6000 if date in fixture['dates'] else 0)
        assert report['outstanding_centavos']==prior['outstanding_centavos']+(12000 if date==str(future) else 6000)
    state.update(completed=True,verified_order_ids=sorted(new_ids));private_json(path,state)
    if not previous:adb('shell','run-as',PACKAGE,'rm','files/order-date-save-credentials.json')
    with evidence.open('a') as log:log.write('\nPASS: actual '+role+' past+future picker/save acknowledgements, next new form defaults today,2server orders/6000centavos each with correct stable actor/customer IDs and immutable real encoding day. Business-date revenue and as-of balances exact; cash/expenses/net/stock/all preexisting records/Auth identities unchanged. Persisted intent completed; no blind creation replay.\n')
    print('PASS:',role,'actual past+future order saves and exact date/financial reconciliation')


if __name__=='__main__':main()
