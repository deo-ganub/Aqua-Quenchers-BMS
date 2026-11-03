import json,pathlib,unittest
from aqua_classifier.contract import validate_model
class ReleaseSchema(unittest.TestCase):
 def test_nonsynthetic_requires_full_commit_reference(self):
  model=json.loads((pathlib.Path(__file__).parents[1]/'fixtures/synthetic-model.json').read_text());model['provenance']['synthetic']=False
  with self.assertRaises(ValueError):validate_model(model)
  model['provenance']['source_commit']='a'*40;validate_model(model)
