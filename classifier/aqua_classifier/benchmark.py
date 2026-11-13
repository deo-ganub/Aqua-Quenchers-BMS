"""Five fresh training processes after one warmup. Never runs held-out evaluation."""
import argparse,json,os,pathlib,resource,statistics,subprocess,sys,tempfile,time
from .pipeline import prepare_command,train_command,environment

def child(dataset,output,synthetic):
 started=time.perf_counter();prepare_command(dataset,output,synthetic);training=train_command(dataset,output,'TIMING-CANDIDATE',synthetic)
 elapsed=time.perf_counter()-started;rss=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
 return {'seconds':elapsed,'peak_rss_bytes':rss if sys.platform=='darwin' else rss*1024,'primary_candidates':training['primary_candidates'],'model_bytes':training['model_bytes']}

def main():
 p=argparse.ArgumentParser();p.add_argument('--dataset',required=True);p.add_argument('--output',required=True);p.add_argument('--synthetic',action='store_true');p.add_argument('--child',action='store_true');a=p.parse_args()
 if a.child:print(json.dumps(child(a.dataset,a.output,a.synthetic)));return
 if pathlib.Path(a.output).exists():raise ValueError('Use a new benchmark report path; preserve previous evidence.')
 rows=[]
 with tempfile.TemporaryDirectory(prefix='aq-timing-') as t:
  for i in range(6):
   cmd=[sys.executable,'-m','aqua_classifier.benchmark','--child','--dataset',str(pathlib.Path(a.dataset).resolve()),'--output',str(pathlib.Path(t)/str(i))]
   if a.synthetic:cmd+=['--synthetic']
   process=subprocess.run(cmd,check=True,capture_output=True,text=True)
   row=json.loads(process.stdout)
   if i:rows.append(row)
 report={'evidence_kind':'SYNTHETIC ONLY' if a.synthetic else 'OWNER-REVIEWED TRAINING TIMING','warmup_processes':1,'fresh_timed_processes':5,'median_seconds':statistics.median(r['seconds'] for r in rows),'range_seconds':[min(r['seconds'] for r in rows),max(r['seconds'] for r in rows)],'peak_rss_bytes':max(r['peak_rss_bytes'] for r in rows),'runs':rows,'environment':environment(),'device_benchmark':'NOT RUN','held_out_evaluation':'NOT RUN; timing is not independent accuracy evidence'}
 with open(a.output,'x') as f:json.dump(report,f,indent=2)
 print(json.dumps(report,indent=2))
if __name__=='__main__':main()
