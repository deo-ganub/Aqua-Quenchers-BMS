"""Compile/run real native transport against deterministic host fixtures, no network.

The JSON dependency is the same pinned org.json artifact declared by app/build.gradle.kts.
Not an Android UI/device test. All generated classes live in a temporary directory.
"""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
JDK=Path('/workspace/tooling/gradle-home/jdks/eclipse_adoptium-25-amd64-linux.2/bin')
if not JDK.is_dir():JDK=Path('/workspace/tooling/jdk21/bin')
FLAGS=['--release','11'] if (JDK.parent/'lib/ct.sym').is_file() else ['-source','11','-target','11']
JSON=Path('/workspace/tooling/aq-algoias-java/json.jar')
if not JSON.is_file():raise SystemExit('Provide the declared org.json:json:20250517 test JAR at '+str(JSON))
src=ROOT/'app/src/main/java/com/example/aquaquencherspos'
sources=list((ROOT/'app/src/hostTest/java').rglob('*.java'))+[src/(name+'.java') for name in ['Api','Session','SupabaseConfig']]
with tempfile.TemporaryDirectory(prefix='aq-native-host-') as folder:
    subprocess.run([str(JDK/'javac'),*FLAGS,'-cp',str(JSON),'-d',folder,*map(str,sources)],check=True,timeout=30)
    subprocess.run([str(JDK/'java'),'-Xmx128m','-cp',folder+':'+str(JSON),
                    'com.example.aquaquencherspos.TransportHostTest'],check=True,timeout=30)
