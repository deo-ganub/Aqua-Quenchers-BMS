"""Execute the actual notebook's synthetic workflow; no real data, network or device claims."""
import ast,contextlib,hashlib,io,json,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
nb=json.loads((ROOT/'docs/academic/Aqua_Quenchers_Benchmark.ipynb').read_text())
code=[''.join(c['source']) for c in nb['cells'] if c['cell_type']=='code']
for cell in code:ast.parse(cell)
assert all(c.get('outputs',[])==[] and c.get('execution_count') is None for c in nb['cells'] if c['cell_type']=='code')
# Default run must neither create an experiment nor print private data.
ns={}
with contextlib.redirect_stdout(io.StringIO()):
    for cell in code:exec(compile(cell,'<benchmark-notebook>','exec'),ns)
assert ns['CONTEXT'] is None
with tempfile.TemporaryDirectory(prefix='aq-notebook-fixture-') as directory:
    ns={};exec(code[0],ns);ns.update(PROJECT_ROOT=ROOT,PRIVATE_WORKSPACE=directory,
         RUN_EXPERIMENT=True,RUN_HELD_OUT_ONCE=True,RUN_FIVE_PROCESS_TIMING=True)
    with contextlib.redirect_stdout(io.StringIO()) as captured:
        for cell in code[1:]:exec(compile(cell,'<benchmark-notebook>','exec'),ns)
    context=ns['CONTEXT'];summary=ns['QUALITY_SUMMARY'];timing=ns['TIMING_SUMMARY']
    assert summary['evidence'].startswith('SYNTHETIC ONLY') and not summary['release_approved']
    assert summary['device_benchmark']=='NOT RUN' and summary['test_examples']>0
    assert timing['warmup_processes']==1 and timing['fresh_timed_processes']==5
    assert len(timing['runs'])==5 and 'python' in timing['environment']
    assert 'remarks' not in captured.getvalue() and 'demo-' not in captured.getvalue()
    # A second held-out command must fail without changing the first result or marker.
    result=context['experiment']/'test-results.json';before=result.read_bytes()
    try:ns['run_command'](context,['evaluate','--dataset',str(context['dataset']),'--output',str(context['experiment'])],'repeat-denied.log')
    except RuntimeError:pass
    else:raise AssertionError('Repeated held-out use unexpectedly succeeded.')
    assert result.read_bytes()==before
    # Reviewed mode cannot silently invent data or bypass Owner review/clean-source checks.
    ns['MODE']='reviewed';ns['OWNER_REVIEW_CONFIRMED']=False
    try:ns['configure']()
    except ValueError:pass
    else:raise AssertionError('Reviewed mode must require its actual prerequisites.')
    evidence={'evidence_kind':'SYNTHETIC NOTEBOOK EXECUTION ONLY','notebook_code_cells':len(code),'code_cells_sha256':hashlib.sha256(json.dumps(code).encode()).hexdigest(),
      'default_run':'PASS: no experiment created','full_demo':'PASS: actual prepare/train/evaluate/timing cells',
      'repeat_held_out':'PASS: rejected; original result unchanged','reviewed_mode_guard':'PASS',
      'privacy_display':'PASS: aggregate-only output','training_summary':ns['TRAINING_SUMMARY'],'quality_summary':summary,'timing_summary':timing,
      'source_note':'Actual source_commit/source_dirty and environment are recorded; synthetic results do not meet station/device release gates.'}
    out=ROOT/'evidence/notebook-synthetic-execution.json';out.write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS: notebook default and opt-in synthetic execution; frozen repeat rejection; reviewed-mode guard; aggregate-only summaries.')
