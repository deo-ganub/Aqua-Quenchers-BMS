"""Offline classifier workflow. No network inference or expense persistence."""
import argparse,json
from .pipeline import prepare_command,train_command,evaluate_command

def main():
 p=argparse.ArgumentParser();s=p.add_subparsers(dest='command',required=True)
 for command in ('prepare','train','evaluate'):
  c=s.add_parser(command);c.add_argument('--dataset',required=True);c.add_argument('--output',required=True);c.add_argument('--synthetic',action='store_true')
  if command=='train':c.add_argument('--model-version',required=True)
 a=p.parse_args()
 if a.command=='prepare':result=prepare_command(a.dataset,a.output,a.synthetic)
 elif a.command=='train':result=train_command(a.dataset,a.output,a.model_version,a.synthetic)
 else:result=evaluate_command(a.dataset,a.output,a.synthetic)
 print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
