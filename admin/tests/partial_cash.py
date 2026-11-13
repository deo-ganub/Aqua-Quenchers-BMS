"""Actual some-cash POS credit + final receipt; refuses any prior mutation intent."""
import argparse
import copy
import hashlib
import hmac
import json
import os
import secrets
import shlex
import subprocess
import time
from connected_android import LOCAL,REPO,PACKAGE,adb,private_json


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--owner',action='store_true');args=parser.parse_args();role='owner' if args.owner else 'staff'
    c=json.loads((LOCAL/'private-config.json').read_text());assert c['kind']=='SYNTHETIC_LOCAL_INTEGRATION_ONLY' and c['url']=='https://127.0.0.1:55436'
    assert adb('emu','avd','name').stdout.decode().splitlines()[0]=='aq_api29_software'
    path=LOCAL/('partial-cash-'+role+'-state.json');assert not path.exists(),'Inspect sale/receipts/progress; never blindly repeat a financial intent'
    env=dict(os.environ,LD_LIBRARY_PATH='/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu')
    def sql(body,**variables):
        command=['/workspace/tooling/sysroot/usr/lib/postgresql/17/bin/psql','-X','-At','-h','/workspace/tooling/pgtest','-p','55432','-d',c['database'],'-v','ON_ERROR_STOP=1']
        for key,value in variables.items():command+=['-v',key+'='+str(value)]
        return subprocess.run(command,input=body,text=True,capture_output=True,check=True,env=env).stdout.strip()
    staff_id=json.loads((LOCAL/'http-test-state.json').read_text())['staff_id'];actor=c['owner_id'] if args.owner else staff_id
    person=json.loads(sql("select to_jsonb(p) from aq_private.profiles p where user_id=:'actor'::uuid;",actor=actor));assert person['role']==role and person['active']
    def bucket(kind,value):return hmac.new(c['SUPABASE_SERVICE_ROLE_KEY'].encode(),json.dumps({kind:value},sort_keys=True,separators=(',',':')).encode(),hashlib.sha256).hexdigest()
    for _ in range(35):
        delay=int(sql("select coalesce(max(ceil(greatest(0,extract(epoch from started_at+interval '15 minutes'-clock_timestamp()))))::int,0) from aq_private.login_windows where (bucket=:'user_bucket' and attempts+1>10) or (bucket=:'source_bucket' and attempts+1>60);",user_bucket='u:'+bucket('username',person['username']),source_bucket='s:'+bucket('source','127.0.0.1')))
        if not delay:break
        print('Waiting actual login window',delay,'seconds; no reset',flush=True);time.sleep(min(30,delay+1))
    else:raise RuntimeError('Inspect login window')
    tables=['customers','profiles','orders','inventory','reservations','deliveries','sales','payments','expenses']
    snapshot_sql='select jsonb_build_object('+','.join("'"+t+"',(select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),'[]'::jsonb) from aq_private."+t+' x)' for t in tables)+") || jsonb_build_object('auth_ids',(select jsonb_agg(jsonb_build_object('id',id,'email',email) order by id) from auth.users));"
    before=json.loads(sql(snapshot_sql));stock=json.loads(sql('select aq_private.stock_json();'));assert stock['filled_available']>=1
    day=sql('select aq_private.today();');report=json.loads(sql("select aq_private.financial_summary(:'day'::date,:'day'::date);",day=day));nonce=secrets.token_hex(4)
    fixture={'kind':'SYNTHETIC_LOCAL_PARTIAL_CASH_UI','run_id':nonce,'owner':args.owner,'username':person['username'],'password':c[role+'_password'],'customer_name':'LOCAL PARTIAL CASH '+role+' '+nonce}
    state={'kind':fixture['kind'],'run_id':nonce,'actor_id':actor,'customer_name':fixture['customer_name'],'quantity':1,'total_centavos':3500,'cash_centavos':1000,'final_receipt_centavos':2500,'before':before,'report_before':report,'started_at':sql('select clock_timestamp();'),'completed':False}
    private_json(path,state);private_json(LOCAL/('partial-cash-'+role+'-fixture.json'),fixture)
    for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:adb('install','-r','-t',str(REPO/apk))
    adb('reverse','tcp:55436','tcp:55436');adb('shell','run-as',PACKAGE,'rm','-f','files/partial-cash-result.json')
    adb('shell','run-as',PACKAGE,'sh','-c',shlex.quote('umask 077; cat > files/partial-cash-credentials.json'),input=json.dumps(fixture).encode())
    try:
        run=adb('shell','am','instrument','-w','-r','-e','class',PACKAGE+'.PartialCashDeviceTest',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner',timeout=360)
        text=run.stdout.decode().replace(fixture['password'],'[private fixture withheld]')
    except subprocess.TimeoutExpired:
        adb('shell','am','force-stop',PACKAGE);state['native_timeout_uncertain']=True;raise
    finally:
        after=json.loads(sql(snapshot_sql));prior_sales={s['id'] for s in before['sales']};prior_payments={p['id'] for p in before['payments']}
        sales=[s for s in after['sales'] if s['id'] not in prior_sales];payments=[p for p in after['payments'] if p['id'] not in prior_payments]
        state.update(observed_sales=sales,observed_payments=payments);private_json(path,state)
    evidence=REPO/('evidence/android-partial-cash-'+role+'.log');evidence.write_text(text)
    progress=json.loads(adb('exec-out','run-as',PACKAGE,'cat','files/partial-cash-result.json').stdout);private_json(LOCAL/('partial-cash-'+role+'-result.json'),progress)
    assert 'OK (1 test)' in text and progress.get('run_id')==nonce and progress.get('completed'),'Inspect persisted financial intent/outcome before retry'
    assert len(sales)==1 and len(payments)==2;sale=sales[0]
    assert sale['id']==progress['sale_id'] and sale['customer_name']==fixture['customer_name'] and sale['customer_id'] is None and sale['quantity']==1 and sale['recorded_by']==actor
    assert sorted(p['amount_centavos'] for p in payments)==[1000,2500] and len({p['reference'] for p in payments})==2
    for payment in payments:
        assert payment['sale_id']==sale['id'] and payment['order_id'] is None and payment['recorded_by']==actor
        assert sql("select received_at>=:'start'::timestamptz and received_at<=clock_timestamp() and (received_at at time zone 'Asia/Manila')::date=:'day'::date from aq_private.payments where id=:'id'::uuid;",start=state['started_at'],day=day,id=payment['id'])=='t'
    projected=json.loads(sql("select aq_private.sale_json(:'id'::uuid);",id=sale['id']));assert (projected['total_centavos'],projected['balance_centavos'],projected['payment_status'])==(3500,0,'paid')
    new_sales={s['id'] for s in sales};new_payments={p['id'] for p in payments};after['sales']=[s for s in after['sales'] if s['id'] not in new_sales];after['payments']=[p for p in after['payments'] if p['id'] not in new_payments]
    current_stock=after['inventory'][0];old_stock=before['inventory'][0]
    assert current_stock['physical_filled']==old_stock['physical_filled']-1
    assert {k:v for k,v in current_stock.items() if k not in ['physical_filled','updated_at']}=={k:v for k,v in old_stock.items() if k not in ['physical_filled','updated_at']}
    after['inventory']=copy.deepcopy(before['inventory']);assert after==before,'Unrelated records/Auth identity changed'
    report_after=json.loads(sql("select aq_private.financial_summary(:'day'::date,:'day'::date);",day=day))
    for key in ['revenue_centavos','payments_received_centavos','net_income_centavos']:assert report_after[key]==report[key]+3500,key
    for key in ['expenses_centavos','outstanding_centavos']:assert report_after[key]==report[key],key
    state.update(completed=True,verified_sale_id=sale['id'],verified_payment_ids=sorted(new_payments));private_json(path,state)
    adb('shell','run-as',PACKAGE,'rm','files/partial-cash-credentials.json')
    with evidence.open('a') as log:log.write('\nPASS: actual '+role+' nonnumeric cash/credit Cancel no effects;1000centavo initial cash on3500sale yields Partially Paid/balance2500, then actual final2500receipt yields Paid/0 and history2unique refs. Exactly1sale/2receipts,3500charge+cash+net/stock-1, other balances/expenses/empty/reservations/all other records/Auth identities unchanged. Server receipt dates/actors exact; intent completed, no blind replay.\n')
    print('PASS:',role,'some-cash credit/final settlement and exact ledger/stock reconciliation')


if __name__=='__main__':main()
