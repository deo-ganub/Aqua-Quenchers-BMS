"""Custodian-only independent privacy-decision export/replay. No app route or live default.

Use a reviewed libpq service; secrets remain in its approved private configuration.
Replay defaults to dry-run. Execution requires the exact destination database name.
Keep this ledger independently controlled and newer than ordinary restore sets.
"""
import argparse,datetime,json,os,re,stat,subprocess,uuid
from pathlib import Path
MAX_BYTES=32*1024*1024
FIELDS={'decision_id','customer_id','approval_ref','reason','cutoff'}

def query(service,body,variables=None):
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,100}',service):raise ValueError('Use a reviewed libpq service name.')
    argv=['psql','-X','-qAt','--dbname','service='+service,'-v','ON_ERROR_STOP=1']
    for key,value in (variables or {}).items():argv+=['-v',key+'='+str(value)]
    result=subprocess.run(argv,input="set statement_timeout='30s'; set timezone='UTC';\n"+body,
                          text=True,capture_output=True,timeout=40,check=False)
    if result.returncode:raise RuntimeError('Database operation failed. Review private administrator evidence; no success is assumed.')
    return result.stdout.strip()

def private_parent(path):
    path=Path(path).absolute()
    for item in [path.parent,*path.parent.parents]:
        if item.is_symlink():raise ValueError('Symlinked ledger parents are not accepted.')
    fd=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    metadata=os.fstat(fd)
    if metadata.st_mode&0o077 or metadata.st_uid!=os.getuid():
        os.close(fd);raise ValueError('Ledger parent must be custodian-owned and0700.')
    return path,fd

def export_ledger(service,output):
    text=query(service,"""select jsonb_build_object('schema_version',1,'entries',coalesce(jsonb_agg(
      jsonb_build_object('decision_id',decision_id,'customer_id',customer_id,'approval_ref',approval_ref,
      'reason',reason,'cutoff',cutoff) order by executed_at,decision_id),'[]'::jsonb))
      from aq_private.privacy_decisions;""")
    value=json.loads(text);validate(value)
    data=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
    if len(data)>MAX_BYTES:raise ValueError('Ledger exceeds32MiB.')
    path,parent=private_parent(output)
    try:
        fd=os.open(path.name,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=parent)
        with os.fdopen(fd,'wb') as stream:stream.write(data);stream.flush();os.fsync(stream.fileno())
    finally:os.close(parent)
    return {'status':'EXPORTED','decisions':len(value['entries'])}

def validate(value):
    if not isinstance(value,dict) or set(value)!={'schema_version','entries'} or value['schema_version']!=1:
        raise ValueError('Unsupported ledger schema.')
    if not isinstance(value['entries'],list) or len(value['entries'])>10000:raise ValueError('Invalid ledger entries.')
    ids=set()
    for row in value['entries']:
        if not isinstance(row,dict) or set(row)!=FIELDS:raise ValueError('Invalid decision fields.')
        for key in ['decision_id','customer_id']:
            if not isinstance(row[key],str) or str(uuid.UUID(row[key]))!=row[key]:raise ValueError('Use canonical UUIDs.')
        if row['decision_id'] in ids:raise ValueError('Duplicate decision ID.')
        ids.add(row['decision_id'])
        for key,bound in [('approval_ref',160),('reason',500)]:
            if not isinstance(row[key],str) or not row[key].strip() or len(row[key])>bound:raise ValueError('Invalid approval metadata.')
        stamp=datetime.datetime.fromisoformat(row['cutoff'].replace('Z','+00:00'))
        if stamp.tzinfo is None or stamp>datetime.datetime.now(datetime.timezone.utc):raise ValueError('Use an original timezone-aware cutoff, not a future value.')
    return value

def read_ledger(path):
    path,parent=private_parent(path)
    try:fd=os.open(path.name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=parent)
    finally:os.close(parent)
    with os.fdopen(fd,'rb') as stream:
        info=os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_nlink!=1 or info.st_size>MAX_BYTES or info.st_mode&0o077:
            raise ValueError('Use an ordinary restricted ledger at most32MiB, without hard links.')
        data=stream.read(MAX_BYTES+1);after=os.fstat(stream.fileno())
        if len(data)!=info.st_size or (info.st_ino,info.st_size,info.st_mtime_ns)!=(after.st_ino,after.st_size,after.st_mtime_ns):
            raise ValueError('Ledger changed during reading.')
    return validate(json.loads(data))

def replay(service,ledger,execute=False,confirm_database=None):
    value=read_ledger(ledger)
    if execute and (not confirm_database or query(service,'select current_database();')!=confirm_database):
        raise ValueError('Execution requires the exact reviewed destination database name.')
    # One transaction: a changed decision or unsettled restored record rolls back the entire batch.
    variables={};calls=[]
    for i,row in enumerate(value['entries']):
        for key in FIELDS:variables[f'{key}_{i}']=row[key]
        calls.append("select aq_private.erase_customer_contact(:'customer_id_%d'::uuid,:'decision_id_%d'::uuid,"
                     ":'approval_ref_%d',:'reason_%d',%s,:'cutoff_%d'::timestamptz);" % (i,i,i,i,'true' if execute else 'false',i))
    query(service,'begin;\n'+'\n'.join(calls)+'\ncommit;',variables)
    return {'status':'EXECUTED' if execute else 'DRY_RUN','decisions':len(value['entries']),
            'reopened_access':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    export=sub.add_parser('export');export.add_argument('--service',required=True);export.add_argument('--output',required=True)
    restore=sub.add_parser('replay');restore.add_argument('--service',required=True);restore.add_argument('--ledger',required=True)
    restore.add_argument('--execute',action='store_true');restore.add_argument('--confirm-database')
    args=parser.parse_args()
    result=export_ledger(args.service,args.output) if args.command=='export' else replay(args.service,args.ledger,args.execute,args.confirm_database)
    print(json.dumps(result))
if __name__=='__main__':main()
