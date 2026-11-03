"""Expected rejection of a synthetic private-key-shaped build input; no real credential."""
import os,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[2]
environment={**os.environ,'JAVA_HOME':'/workspace/tooling/jdk21','GRADLE_USER_HOME':'/workspace/tooling/gradle-home'}
r=subprocess.run(['./gradlew','--no-daemon','--offline','--max-workers=2','help','-PAQ_SUPABASE_ANON_KEY=sb_secret_SYNTHETIC_TEST_ONLY'],cwd=root,env=environment,capture_output=True,text=True)
if r.returncode==0 or 'must be a public anon/publishable client key' not in r.stdout+r.stderr:
 raise RuntimeError('Expected key rejection did not execute/pass; inspect Gradle runtime prerequisites before retry.')
print('PASS: expected build rejection for a synthetic privileged-key-shaped fixture; no real credential used.')
