"""Restricted administrator ledger export/replay. Default replay is dry run; no app route."""
import argparse,datetime,json,os,pathlib,subprocess,uuid

def query(sql,variables=None):
 args=['psql','-X','-At','-v','ON_ERROR_STOP=1']
 for name,value in (variables or {}).items():args+=['-v',name+'='+str(value)]
 r=subprocess.run(args,input=sql,text=True,capture_output=True)
 if r.returncode:raise ValueError('Administrator database operation rejected; inspect restricted database logs.')
 return r.stdout.strip()
def validate(entry):
 if not isinstance(entry,dict) or set(entry)!={'decision_id','customer_id','approval','reason','cutoff'}:raise ValueError('Invalid ledger fields.')
 for name in ['decision_id','customer_id']:uuid.UUID(entry[name])
 for name,maxlen in [('approval',160),('reason',500)]:
  if not isinstance(entry[name],str) or not 1<=len(entry[name].strip())<=maxlen:raise ValueError('Invalid approval/reason.')
 date=datetime.datetime.fromisoformat(entry['cutoff'])
 if date.tzinfo is None:raise ValueError('Cutoff timezone required.')
 return entry

def main():
 p=argparse.ArgumentParser();p.add_argument('command',choices=['export','replay']);p.add_argument('--ledger',required=True);p.add_argument('--execute',action='store_true');p.add_argument('--confirm-database');a=p.parse_args();path=pathlib.Path(a.ledger)
 if a.command=='export':
  if path.exists():raise ValueError('Use a new protected ledger snapshot path.')
  if path.parent.stat().st_mode & 0o077:raise ValueError('Ledger parent must be restricted0700.')
  raw=query("select coalesce(jsonb_agg(jsonb_build_object('decision_id',decision_id,'customer_id',customer_id,'approval',payload->>'owner_approval_ref','reason',payload->>'reason','cutoff',effective_cutoff) order by recorded_at),'[]') from aq_private.privacy_decisions;")
  rows=json.loads(raw)
  for row in rows:validate(row)
  descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
  with os.fdopen(descriptor,'w') as out:json.dump(rows,out,indent=2)
  print(json.dumps({'status':'EXPORTED','decisions':len(rows),'independent_storage':'Custodian must verify restricted destination/custody'}));return
 if path.stat().st_size>32*1024*1024:raise ValueError('Ledger too large for this procedure.')
 rows=json.loads(path.read_text())
 if not isinstance(rows,list):raise ValueError('Ledger must be a list.')
 for row in rows:validate(row)
 if a.execute and (not a.confirm_database or query('select current_database();')!=a.confirm_database):raise ValueError('Confirm the exact isolated restore database before execution.')
 result=[]
 for row in rows:
  variables={**row,'execute':'true' if a.execute else 'false'}
  sql="select aq_private.erase_eligible_contact(:'customer_id'::uuid,:'decision_id'::uuid,:'approval',:'reason',:'execute'::boolean,:'cutoff'::timestamptz);"
  result.append(json.loads(query(sql,variables)))
 print(json.dumps({'mode':'EXECUTED' if a.execute else 'DRY RUN','decisions':len(result),'access':'Keep app access closed until reconciliation/Owner review'}))
if __name__=='__main__':main()
