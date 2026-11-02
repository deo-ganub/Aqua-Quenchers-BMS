"""Synthetic local encryption/files test; not a hosted recovery/RPO/RTO drill."""
import importlib.util,json,os,pathlib,subprocess,tempfile,sys
sys.path.insert(0,str(pathlib.Path(__file__).parents[1]/'recovery'))
from retention import inspect,prune
spec=importlib.util.spec_from_file_location('backup',pathlib.Path(__file__).parents[1]/'recovery/backup.py');backup=importlib.util.module_from_spec(spec);spec.loader.exec_module(backup)
def main():
 with tempfile.TemporaryDirectory(prefix='aq-recovery-test-') as t:
  root=pathlib.Path(t);root.chmod(0o700);bundle=root/'bundle';bundle.mkdir(mode=0o700)
  key=root/'identity';subprocess.run(['age-keygen','-o',str(key)],check=True,capture_output=True);key.chmod(0o600)
  public=subprocess.run(['age-keygen','-y',str(key)],check=True,capture_output=True,text=True).stdout
  recipient=root/'recipients';recipient.write_text(public)
  subprocess.run(['pg_dump','-h','/workspace/tooling/pgtest','-p','55432','-d','postgres','--format=custom','--file',str(bundle/'database.dump')],check=True,capture_output=True)
  (bundle/'roles.sql').write_text('-- Synthetic local roles inventory only; restore not performed.\n')
  for name in ['project-config.json','auth-config.json']:(bundle/name).write_text('{"environment":"synthetic-test"}')
  (bundle/'storage-inventory.json').write_text('{"uses_storage":false}')
  primary,recovery,catalog=[root/x for x in ['primary','independent','catalog']]
  entry=backup.package(bundle,recipient,primary,recovery,catalog)
  encrypted=primary/entry['filename'];record=catalog/(entry['filename']+'.json')
  assert inspect(primary,recovery,catalog)[2]['status']=='HEALTHY'
  try:prune(primary,recovery,catalog);raise AssertionError('Unverified retention allowed')
  except ValueError:pass
  result=backup.restore(encrypted,record,key,root/'restored')
  assert result['status']=='FILES VERIFIED' and result['database_restore']=='NOT RUN'
  assert backup.sha(encrypted)==backup.sha(recovery/entry['filename'])
  corrupt=root/'corrupt.age';data=bytearray(encrypted.read_bytes());data[-1]^=1;corrupt.write_bytes(data)
  r=subprocess.run(['age','--decrypt','--identity',str(key),str(corrupt)],capture_output=True)
  assert r.returncode!=0,'Authenticated decryption accepted tampering'
  try:backup.independent([primary,primary/'nested',catalog]);raise AssertionError('Overlapping destinations accepted')
  except ValueError:pass
  print('PASS: age round trip, independent copy digests, restored file hashes, authenticated tamper rejection, destination separation. Database/hosted restore NOT RUN.')

if __name__=="__main__":main()
