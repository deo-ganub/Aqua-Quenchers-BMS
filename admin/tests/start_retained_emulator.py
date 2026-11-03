"""Start only the owned retained API29 AVD, detached like the local services.

Never wipes data, stops another device, or reports PID presence as boot readiness.
"""
import fcntl
import json
import os
from pathlib import Path
import subprocess
import time

ROOT=Path('/workspace/tooling')
LOCAL=ROOT/'aq-auth-rest-local'
NAME='aq_api29_software'
ADB=ROOT/'android-sdk/platform-tools/adb'


def main():
    assert (ROOT/'android-avd/aq_api29').is_dir()
    with (LOCAL/'emulator-start.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        probe=subprocess.run([str(ADB),'-s','emulator-5554','emu','avd','name'],capture_output=True,text=True,timeout=10)
        if probe.returncode==0:
            assert probe.stdout.splitlines()[0]==NAME,'Unrelated device on selected serial; preserve it'
            print('Owned AVD already available; verify actual boot/window readiness separately.');return
        active=[]
        for line in subprocess.check_output(['ps','-eo','pid=,stat=,comm='],text=True).splitlines():
            pid,state,command=line.split(None,2)
            if 'qemu-system' in command and not state.startswith('Z'):active.append(int(pid))
        if active:
            for pid in active:
                args=(Path('/proc')/str(pid)/'cmdline').read_bytes().split(b'\0')
                if NAME.encode() in args:
                    print('Owned qemu already starting; verify actual ADB/boot readiness separately.');return
            raise RuntimeError('Other active emulator exists; inspect before starting a second device')
        for name in ['hardware-qemu.ini.lock','multiinstance.lock']:
            path=ROOT/'android-avd/aq_api29'/name
            if path.exists():
                assert path.stat().st_uid==os.getuid(),'Unowned lock; preserve it'
                path.rename(LOCAL/(name+'.stale-'+str(time.time_ns())))
        env=dict(os.environ,ANDROID_HOME=str(ROOT/'android-sdk'),ANDROID_USER_HOME=str(ROOT/'android-user'),ANDROID_AVD_HOME=str(ROOT/'android-avd'))
        args=[str(ROOT/'android-sdk/emulator/emulator'),'-avd',NAME,'-accel','off','-gpu','swiftshader','-no-snapshot','-no-audio','-no-window','-no-boot-anim','-memory','2048','-cores','2','-port','5554','-no-metrics']
        fd=os.open(LOCAL/'emulator-resume.log',os.O_WRONLY|os.O_CREAT|os.O_APPEND,0o600)
        with os.fdopen(fd,'ab') as output:
            process=subprocess.Popen(args,env=env,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
        marker=LOCAL/'emulator-daemon.json';temporary=marker.with_suffix('.tmp')
        fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as output:json.dump({'kind':'OWNED_SYNTHETIC_AVD','name':NAME,'pid':process.pid,'started_at':time.time()},output)
        temporary.replace(marker)
        print('Started retained owned AVD detached; PID',process.pid,'is not a readiness claim. No data reset.')


if __name__=='__main__':main()
