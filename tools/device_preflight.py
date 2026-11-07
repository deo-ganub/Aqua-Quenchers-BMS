"""Read-only metadata preparation for the requester-declared Xiaomi Pad 8.

No network pairing, installation, credentials, business data or performance claims.
Run locally on the computer with the authorized tablet connection.
"""
import argparse,json,shutil,subprocess
from pathlib import Path
TARGET='Xiaomi Pad 8'

def capture(adb='adb',serial=None):
    listing=subprocess.run([adb,'devices'],text=True,capture_output=True,check=True,timeout=15).stdout
    connected=[line.split()[0] for line in listing.splitlines()[1:] if len(line.split())==2 and line.split()[1]=='device']
    if not connected:raise ValueError('Xiaomi Pad 8 is not connected. Use the trusted computer with its authorized USB debugging connection.')
    if serial is None:
        if len(connected)!=1:raise ValueError('Select the target tablet with --serial; no device is chosen automatically.')
        serial=connected[0]
    if serial not in connected:raise ValueError('The selected device is not connected and authorized.')
    def prop(name):
        value=subprocess.run([adb,'-s',serial,'shell','getprop',name],text=True,capture_output=True,check=True,timeout=15).stdout.strip()
        if len(value)>256 or any(ord(c)<32 for c in value):raise ValueError('Unexpected device property.')
        return value
    if prop('ro.kernel.qemu')=='1':raise ValueError('Use the physical Xiaomi Pad 8; emulator measurements cannot satisfy its device gate.')
    manufacturer=prop('ro.product.manufacturer');model=prop('ro.product.model');api=prop('ro.build.version.sdk')
    if not api.isdigit() or int(api)<29:raise ValueError('The approved app requires Android API29 or newer.')
    if manufacturer.lower()!='xiaomi':raise ValueError('The connected manufacturer does not match the declared Xiaomi target.')
    if not model:raise ValueError('Device model could not be observed.')
    return {'declared_target':TARGET,'observed_manufacturer':manufacturer,'observed_model':model,
            'android_version':prop('ro.build.version.release'),'api_level':int(api),
            'security_patch':prop('ro.build.version.security_patch'),
            'identity_note':'Observed product code retained; retail model selection is user-declared.',
            'status':'ENVIRONMENT CAPTURED','benchmark_status':'NOT RUN','release_model_status':'REQUIRES APPROVED MODEL',
            'serial_recorded':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--serial');parser.add_argument('--adb')
    args=parser.parse_args();adb=args.adb or shutil.which('adb')
    if not adb:
        prepared=Path('/workspace/tooling/android-sdk/platform-tools/adb')
        adb=str(prepared) if prepared.is_file() else None
    if not adb:parser.exit(2,'Install Android SDK Platform-Tools on the trusted development computer.\n')
    try:report=capture(adb,args.serial)
    except (ValueError,subprocess.SubprocessError) as e:parser.exit(2,str(e)+'\n')
    print(json.dumps(report,indent=2))
if __name__=='__main__':main()
