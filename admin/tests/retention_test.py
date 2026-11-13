"""Retention policy logic fixtures; dates/restore approvals below are synthetic test inputs."""
import datetime,hashlib,json,pathlib,sys,tempfile,unittest
sys.path.insert(0,str(pathlib.Path(__file__).parents[1]/'recovery'))
from retention import inspect,prune
class Retention(unittest.TestCase):
 def test_keep_seven_daily_copies_and_retain_retirement_history(self):
  with tempfile.TemporaryDirectory() as t:
   root=pathlib.Path(t);dirs=[root/n for n in ['primary','recovery','catalog']]
   for d in dirs:d.mkdir(mode=0o700)
   now=datetime.datetime.now(datetime.timezone.utc);names=[]
   for i in range(8):
    created=now-datetime.timedelta(days=i);name=created.strftime('aq-%Y%m%dT%H%M%S')+f'-{i:08x}.tar.age';names.append(name);payload=b'SYNTHETIC RETENTION LOGIC FIXTURE, NOT AN ENCRYPTED BACKUP'
    for d in dirs[:2]:(d/name).write_bytes(payload)
    row=dict(filename=name,ciphertext_sha256=hashlib.sha256(payload).hexdigest(),created_at=created.isoformat(),business_date=created.date().isoformat(),isolated_restore={'status':'VERIFIED','evidence_ref':'SYNTHETIC TEST APPROVAL ONLY'})
    (dirs[2]/(name+'.json')).write_text(json.dumps(row))
   result=prune(*dirs);self.assertEqual(result['retire_candidates'],[names[-1]]);self.assertTrue((dirs[0]/names[-1]).exists())
   result=prune(*dirs,execute=True);self.assertFalse((dirs[0]/names[-1]).exists());self.assertTrue((dirs[2]/(names[-1]+'.json')).exists());self.assertEqual(inspect(*dirs)[2]['status'],'HEALTHY')
   (dirs[1]/names[0]).write_bytes(b'tampered');self.assertEqual(inspect(*dirs)[2]['status'],'ACTION REQUIRED')
   with self.assertRaises(ValueError):prune(*dirs,execute=True)
 def test_empty_or_stale_catalog_needs_action(self):
  with tempfile.TemporaryDirectory() as t:
   dirs=[pathlib.Path(t)/n for n in ['a','b','c']]
   self.assertEqual(inspect(*dirs)[2]['status'],'ACTION REQUIRED')
if __name__=='__main__':unittest.main()
