"""Authenticated course exercise only; Android production inference is local Java."""
import json,os,uuid,urllib.request,urllib.error
from http.server import BaseHTTPRequestHandler,HTTPServer
from .contract import predict,validate_result,load_model,result
from .text import normalize

def validate_request(body):
 if not isinstance(body,dict) or set(body)!={'request_id','remarks'}:raise ValueError('Invalid request.')
 if not isinstance(body['request_id'],str):raise ValueError('Invalid request.')
 try:uuid.UUID(body['request_id'])
 except (ValueError,AttributeError):raise ValueError('Invalid request.') from None
 if not isinstance(body['remarks'],str) or not normalize(body['remarks']).strip():raise ValueError('Enter remarks to request a suggestion.')
 return body

def solve(body,token,verify_owner,model=None):
 if not token or not verify_owner(token):return 403,{'error':'Access denied.'}
 try:validate_request(body)
 except ValueError:return 400,{'error':'Invalid suggestion request.'}
 try:value=predict(body['remarks'],model);validate_result(value)
 except Exception:value=result('UNAVAILABLE',reason='MODEL_UNAVAILABLE')
 return 200,value

def owner_verifier(url,key):
 if not url.startswith('https://') or not key:raise ValueError('HTTPS development Supabase public configuration required.')
 def verify(token):
  request=urllib.request.Request(url.rstrip('/')+'/rest/v1/rpc/aq_me',data=b'{}',headers={'Authorization':'Bearer '+token,'apikey':key,'Content-Type':'application/json'})
  try:
   with urllib.request.urlopen(request,timeout=15) as r:profile=json.loads(r.read(16384))
   return profile.get('active') is True and profile.get('role')=='owner'
  except Exception:return False
 return verify

def main():
 import argparse
 p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=8765);p.add_argument('--model');p.add_argument('--trusted-sha256');a=p.parse_args()
 model=None
 if a.model:
  with open(a.model,'rb') as f:model=load_model(f.read(2*1024*1024+1),a.trusted_sha256,False)
 verify=owner_verifier(os.environ.get('AQ_SUPABASE_URL',''),os.environ.get('AQ_SUPABASE_ANON_KEY',''))
 class Handler(BaseHTTPRequestHandler):
  def log_message(self,*args):pass # No remarks, tokens, account IDs or request bodies in logs.
  def do_POST(self):
   if self.path!='/api/v1/algorithm/solve':status,body=404,{'error':'Not found.'}
   else:
    try:
     size=int(self.headers.get('Content-Length','0'))
     if not 0<size<=16384:raise ValueError()
     if self.headers.get_content_type()!='application/json':raise ValueError()
     payload=json.loads(self.rfile.read(size));auth=self.headers.get('Authorization','');token=auth[7:] if auth.startswith('Bearer ') else ''
     status,body=solve(payload,token,verify,model)
    except (ValueError,UnicodeError):status,body=400,{'error':'Invalid suggestion request.'}
   encoded=json.dumps(body,allow_nan=False).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(encoded)));self.end_headers();self.wfile.write(encoded)
 HTTPServer(('127.0.0.1',a.port),Handler).serve_forever()
if __name__=='__main__':main()
