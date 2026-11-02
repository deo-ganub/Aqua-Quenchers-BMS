import unittest
from aqua_classifier.dev_adapter import solve
class Adapter(unittest.TestCase):
 def test_authorization_and_exact_schema(self):
  body={'request_id':'11111111-1111-4111-8111-111111111111','remarks':'fuel'}
  self.assertEqual(solve(body,'',lambda _:True)[0],403)
  self.assertEqual(solve(body,'staff',lambda _:False)[0],403)
  for field in ['actor_id','model_path','labels','weights','url']:
   self.assertEqual(solve({**body,field:'bad'},'owner',lambda _:True)[0],400)
 def test_missing_model_is_safe_not_save_success(self):
  status,r=solve({'request_id':'11111111-1111-4111-8111-111111111111','remarks':'fuel'},'owner',lambda _:True)
  self.assertEqual(status,200);self.assertEqual(r['status'],'UNAVAILABLE');self.assertIsNone(r['suggested_category']);self.assertIsNone(r['model_version'])
 def test_raw_and_normalized_unicode_limits(self):
  for text in [' ','a'*501,'\ufdfa'*30]:
   self.assertEqual(solve({'request_id':'11111111-1111-4111-8111-111111111111','remarks':text},'owner',lambda _:True)[0],400)
if __name__=='__main__':unittest.main()
