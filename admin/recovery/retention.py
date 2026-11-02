"""Age monitoring and conservative retention from an independently controlled catalog.
No pruning without verified copies AND an administrator-recorded isolated-restore decision.
"""
import argparse,datetime,json,pathlib,re
from backup import sha,private_directory,independent
PATTERN=re.compile(r'aq-\d{8}T\d{6}-[a-f0-9]{8}\.tar\.age')
def inspect(primary,recovery,catalog,now=None):
 dirs=[private_directory(p) for p in (primary,recovery,catalog)];independent(dirs);rows=[];problems=[]
 for f in sorted(dirs[2].glob('aq-*.tar.age.json')):
  if f.with_suffix(f.suffix+'.retired').exists():continue
  try:
   row=json.loads(f.read_text());name=row['filename']
   if not isinstance(name,str) or not PATTERN.fullmatch(name):raise ValueError('Invalid catalog filename')
   if not all((d/name).is_file() and not (d/name).is_symlink() and sha(d/name)==row['ciphertext_sha256'] for d in dirs[:2]):raise ValueError('Encrypted copy unavailable or mismatched')
   created=datetime.datetime.fromisoformat(row['created_at'])
   if created.tzinfo is None:raise ValueError('Missing timezone')
   rows.append((created,row,f))
  except (ValueError,KeyError,OSError,TypeError) as e:problems.append({'entry':f.name,'error':str(e)})
 now=now or datetime.datetime.now(datetime.timezone.utc)
 age=(now-max(r[0] for r in rows)).total_seconds()/3600 if rows else None
 return dirs,rows,{'status':'HEALTHY' if age is not None and 0<=age<24 and not problems else 'ACTION REQUIRED','latest_copy_age_hours':age,'copy_problems':problems,'rpo_target_hours':24,'restore_targets':'NOT established by copy/age checks'}
def prune(primary,recovery,catalog,execute=False):
 dirs,rows,health=inspect(primary,recovery,catalog)
 if health['status']!='HEALTHY':raise ValueError('Do not prune while copies are missing/stale/unverified.')
 daily={}
 for created,row,f in sorted(rows,reverse=True,key=lambda r:r[0]):
  decision=row.get('isolated_restore')
  if not isinstance(decision,dict) or decision.get('status')!='VERIFIED' or not decision.get('evidence_ref'):continue
  daily.setdefault(row['business_date'],(created,row,f))
 if len(daily)<7:raise ValueError('Retain all copies: seven independently verified daily copies with isolated-restore evidence required.')
 keep={r[1]['filename'] for r in sorted(daily.values(),reverse=True,key=lambda r:r[0])[:7]}
 candidates=[row['filename'] for _,row,_ in rows if row['filename'] not in keep]
 if execute:
  for name in candidates:
   for d in dirs[:2]:(d/name).unlink()
  # Keep catalog metadata as evidence. Subsequent checks recognize only currently retained files
  # through explicit retirement receipts; never erase the digest history.
  for _,row,f in rows:
   if row['filename'] in candidates:
    receipt=f.with_suffix(f.suffix+'.retired')
    with receipt.open('x') as out:out.write(datetime.datetime.now(datetime.timezone.utc).isoformat())
 return {'mode':'EXECUTED' if execute else 'DRY RUN','keep':sorted(keep),'retire_candidates':candidates}
def main():
 p=argparse.ArgumentParser();p.add_argument('command',choices=['monitor','prune']);p.add_argument('--primary',required=True);p.add_argument('--recovery',required=True);p.add_argument('--catalog',required=True);p.add_argument('--execute',action='store_true');a=p.parse_args()
 if a.command=='monitor':
  result=inspect(a.primary,a.recovery,a.catalog)[2];print(json.dumps(result,indent=2));raise SystemExit(0 if result['status']=='HEALTHY' else 2)
 print(json.dumps(prune(a.primary,a.recovery,a.catalog,a.execute),indent=2))
if __name__=='__main__':main()
