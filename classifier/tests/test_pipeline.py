import json,tempfile,unittest
from pathlib import Path
from aqua_classifier import LABELS
from aqua_classifier.data import load_dataset,prepare,validate_manifest,group_records
from aqua_classifier.pipeline import prepare_command,train_command,evaluate_command
class Pipeline(unittest.TestCase):
 def rows(self):
  return [dict(id=f'{i}-{j}',remarks=f'{label.lower()} station {word}',category=label,reviewed=True,reviewer_id='synthetic-reviewer',group_id=f'{i}-{j}',synthetic=True) for i,label in enumerate(LABELS) for j,word in enumerate(['amber','birch','cedar','delta','elm','fern'])]
 def test_end_to_end_freeze_evaluate_once(self):
  with tempfile.TemporaryDirectory() as t:
   dataset=Path(t)/'synthetic.jsonl';dataset.write_text('\n'.join(json.dumps(r) for r in self.rows()));output=Path(t)/'experiment'
   manifest=prepare_command(dataset,output,True);validate_manifest(self.rows(),manifest)
   training=train_command(dataset,output,'TEST-SYNTHETIC',True,max_epochs=5)
   self.assertEqual(training['evidence_kind'],'SYNTHETIC ONLY');self.assertEqual(len(training['primary_candidates']),9);self.assertEqual(len(training['comparison_candidates']),3)
   results=evaluate_command(dataset,output,True);self.assertEqual(results['evidence_kind'],'SYNTHETIC ONLY');self.assertFalse(results['release_approved']);self.assertEqual(results['device_benchmark'],'NOT RUN')
   with self.assertRaises(FileExistsError):evaluate_command(dataset,output,True)
   with self.assertRaises(ValueError):train_command(dataset,output,'TEST-SYNTHETIC',True,max_epochs=5)
 def test_ingestion_rejects_unreviewed_and_leakage(self):
  rows=self.rows();rows[0]['reviewed']=False
  with tempfile.TemporaryDirectory() as t:
   p=Path(t)/'data.jsonl';p.write_text('\n'.join(json.dumps(r) for r in rows))
   with self.assertRaises(ValueError):load_dataset(p,True)
  rows=self.rows();manifest=prepare(rows);manifest['partitions']['test'].append(manifest['partitions']['train'][0])
  with self.assertRaises(ValueError):validate_manifest(rows,manifest)
 def test_near_duplicates_and_receipt_families_stay_together(self):
  rows=self.rows()[:3];rows[0]['receipt_id']=rows[2]['receipt_id']='same-synthetic-receipt'
  groups=group_records(rows);self.assertTrue(any({rows[0]['id'],rows[2]['id']}<=set(g) for g in groups))
if __name__=='__main__':unittest.main()
