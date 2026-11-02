"""Inventory every supplied story criterion and scenario without inheriting paper test claims."""
import json,re
from pathlib import Path
root=Path(__file__).resolve().parents[2];records=[]
for role in ['owner','staff']:
 source=f'handoff/source_readable/{role}_stories.json';data=json.loads((root/source).read_text());requirement=None
 for rownum,row in enumerate(data['values'],1):
  if row and isinstance(row[0],str) and re.fullmatch(r'A-\d+',row[0]):requirement=row[0]
  if requirement is None:continue
  if len(row)>6 and row[6] and row[6] not in ['Passed','Failed','Total']:
   cell=str(row[6])
   for n,part in enumerate(re.split(r'\n\s*\n(?=\d+\.)',cell),1):
    if not re.match(r'\d+\.',part.strip()):continue
    num=re.match(r'(\d+)\.',part.strip()).group(1)
    records.append(dict(id=f'{requirement}-AC{num}',requirement=requirement,kind='acceptance',source=source,row=rownum,text=part.strip(),status='Planned',implementation=[],checks=[],gap='Individual criterion verification pending'))
  if len(row)>5 and row[5] and 'Given' in str(row[5]) and row[5]!='Given When Then':
   for part in re.split(r'(?m)(?=^[A-Z]\. )',str(row[5])):
    m=re.match(r'([A-Z])\. (.+)',part)
    if m:records.append(dict(id=f'{requirement}-SC{m[1]}',requirement=requirement,kind='scenario',source=source,row=rownum,text=part.strip(),status='Planned',implementation=[],checks=[],gap='Full Given-When-Then scenario not yet exercised'))
p=root/'docs/source-requirements.json'
old={r['id']:r for r in json.loads(p.read_text())} if p.exists() else {}
for r in records:
 if r['id'] in old:
  for key in ['status','implementation','checks','gap']:r[key]=old[r['id']][key]
p.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
print(f'Indexed {len(records)} acceptance/scenario records; {len(set(r["requirement"] for r in records))} functional requirements. No paper statuses inherited.')
