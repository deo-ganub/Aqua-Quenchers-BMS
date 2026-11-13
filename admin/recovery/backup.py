"""Private recovery packaging with age authenticated encryption; no custom cryptography.
Supply a reviewed export bundle; this program never connects to or changes a database.
"""
import argparse,datetime,hashlib,json,os,pathlib,shutil,subprocess,tarfile,tempfile
from zoneinfo import ZoneInfo
REQUIRED={'database.dump','roles.sql','project-config.json','auth-config.json','storage-inventory.json'}
def sha(path):
 h=hashlib.sha256()
 with open(path,'rb') as f:
  for chunk in iter(lambda:f.read(65536),b''):h.update(chunk)
 return h.hexdigest()
def private_directory(path):
 p=pathlib.Path(path).resolve();p.mkdir(parents=True,exist_ok=True,mode=0o700)
 if p.stat().st_mode & 0o077:raise ValueError('Recovery directories must have mode 0700.')
 return p
def independent(paths):
 for i,a in enumerate(paths):
  for b in paths[i+1:]:
   if a==b or a in b.parents or b in a.parents:raise ValueError('Primary, independent copy and digest catalog must be separate paths/permissions.')
def validate_bundle(bundle):
 p=pathlib.Path(bundle).resolve()
 if not REQUIRED<=set(x.name for x in p.iterdir()):raise ValueError('Incomplete bundle: schema/data, roles, project/Auth config and Storage inventory required.')
 for f in p.rglob('*'):
  if f.is_symlink() or not (f.is_dir() or f.is_file()):raise ValueError('Bundle must contain only ordinary files/directories.')
 storage=json.loads((p/'storage-inventory.json').read_text())
 if not isinstance(storage.get('uses_storage'),bool):raise ValueError('Declare actual Storage use.')
 if storage['uses_storage'] and not (p/'storage-objects').is_dir():raise ValueError('Actual Storage objects missing.')
 subprocess.run(['pg_restore','--list',str(p/'database.dump')],check=True,stdout=subprocess.DEVNULL)
 return p
def package(bundle,recipients,primary,recovery,catalog):
 bundle=validate_bundle(bundle);dirs=[private_directory(p) for p in (primary,recovery,catalog)];independent(dirs)
 stamp=datetime.datetime.now(datetime.timezone.utc);name=stamp.strftime('aq-%Y%m%dT%H%M%S')+'-'+os.urandom(4).hex()+'.tar.age'
 with tempfile.TemporaryDirectory(prefix='aq-backup-') as scratch:
  os.chmod(scratch,0o700);archive=pathlib.Path(scratch)/'export.tar';encrypted=pathlib.Path(scratch)/name
  manifest={'created_at':stamp.isoformat(),'business_date':stamp.astimezone(ZoneInfo('Asia/Manila')).date().isoformat(),'files':{str(f.relative_to(bundle)):sha(f) for f in sorted(bundle.rglob('*')) if f.is_file()},'isolated_restore':'NOT RUN'}
  with tarfile.open(archive,'w') as tar:tar.add(bundle,arcname='bundle')
  subprocess.run(['age','--encrypt','--recipients-file',str(recipients),'--output',str(encrypted),str(archive)],check=True)
  digest=sha(encrypted)
  for target in dirs[:2]:
   tmp=target/(name+'.partial');shutil.copyfile(encrypted,tmp);tmp.chmod(0o600)
   if sha(tmp)!=digest:raise ValueError('Encrypted copy verification failed; originals retained.')
   os.replace(tmp,target/name)
  manifest.update(ciphertext_sha256=digest,filename=name,copy_verification='SHA256 MATCHED (not an isolated restore)')
  dest=dirs[2]/(name+'.json')
  with open(dest,'x') as f:os.chmod(dest,0o600);json.dump(manifest,f,indent=2)
 return manifest
def restore(archive,entry,identity,destination):
 entry=json.loads(pathlib.Path(entry).read_text());archive=pathlib.Path(archive)
 if archive.name!=entry['filename'] or sha(archive)!=entry['ciphertext_sha256']:raise ValueError('Independent ciphertext digest mismatch.')
 destination=pathlib.Path(destination).resolve()
 if destination.exists():raise ValueError('Restore only into a new isolated directory.')
 with tempfile.TemporaryDirectory(prefix='aq-restore-') as scratch:
  decrypted=pathlib.Path(scratch)/'bundle.tar';subprocess.run(['age','--decrypt','--identity',str(identity),'--output',str(decrypted),str(archive)],check=True)
  with tarfile.open(decrypted) as tar:
   members=tar.getmembers()
   for m in members:
    path=pathlib.PurePosixPath(m.name)
    if path.is_absolute() or '..' in path.parts or not (m.isdir() or m.isfile()):raise ValueError('Unsafe archive entry.')
   destination.mkdir(mode=0o700);tar.extractall(destination,members=members,filter='data')
 for name,digest in entry['files'].items():
  if sha(destination/'bundle'/name)!=digest:raise ValueError('Restored file digest mismatch.')
 return {'status':'FILES VERIFIED','database_restore':'NOT RUN','reconciliation':'NOT RUN','erasure_ledger':'MUST APPLY BEFORE REOPENING'}
def main():
 p=argparse.ArgumentParser();s=p.add_subparsers(dest='command',required=True)
 c=s.add_parser('package')
 for arg in ['bundle','recipients','primary','recovery','catalog']:c.add_argument('--'+arg,required=True)
 c=s.add_parser('restore-files')
 for arg in ['archive','entry','identity','destination']:c.add_argument('--'+arg,required=True)
 a=vars(p.parse_args());command=a.pop('command');print(json.dumps(package(**a) if command=='package' else restore(**a),indent=2))
if __name__=='__main__':main()
