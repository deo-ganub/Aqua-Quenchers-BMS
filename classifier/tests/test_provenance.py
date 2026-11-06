"""Source identity must come from the classifier checkout, never caller CWD."""
import contextlib,os,subprocess,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from aqua_classifier import pipeline

class Provenance(unittest.TestCase):
    def test_alternate_clean_repository_cannot_supply_source_commit(self):
        expected=subprocess.check_output(['git','-C',str(Path(pipeline.__file__).resolve().parents[2]),'rev-parse','HEAD'],text=True).strip()
        with tempfile.TemporaryDirectory() as directory:
            env=dict(os.environ,GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL='/dev/null')
            subprocess.run(['git','init','-q',directory],env=env,check=True)
            subprocess.run(['git','-C',directory,'-c','user.name=Fixture','-c','user.email=fixture@example.invalid',
                            'commit','-qm','Synthetic unrelated provenance fixture','--allow-empty'],env=env,check=True)
            other=subprocess.check_output(['git','-C',directory,'rev-parse','HEAD'],env=env,text=True).strip()
            with contextlib.chdir(directory):record=pipeline.environment()
        self.assertEqual(record['source_commit'],expected);self.assertNotEqual(record['source_commit'],other)
    def test_unversioned_source_does_not_borrow_parent_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(pipeline,'__file__',str(Path(directory)/'classifier/aqua_classifier/pipeline.py')):
                record=pipeline.environment()
        self.assertEqual(record['source_commit'],'UNCOMMITTED');self.assertTrue(record['source_dirty'])

if __name__=='__main__':unittest.main()
