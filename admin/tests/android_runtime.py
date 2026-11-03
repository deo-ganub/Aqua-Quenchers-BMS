"""Two explicit local API29 runtime checks; never runs declared-tablet benchmarks.

Requires the existing isolated aq_api29_software AVD and built app/test APKs.
"""
import pathlib
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
ADB = ['/workspace/tooling/android-sdk/platform-tools/adb', '-s', 'emulator-5554']
CLASSES = 'com.aquaquenchers.bms.SignInDeviceTest,com.aquaquenchers.classifier.ClassifierParityDeviceTest'


def run(arguments, timeout=120):
    return subprocess.run(ADB + arguments, cwd=ROOT, check=True, capture_output=True, text=True, timeout=timeout)


def main():
    avd = run(['emu', 'avd', 'name']).stdout.splitlines()
    if not avd or avd[0] != 'aq_api29_software':
        raise RuntimeError('Refusing to replace APKs on an unrelated device.')
    if run(['shell', 'getprop', 'sys.boot_completed']).stdout.strip() != '1':
        raise RuntimeError('Isolated emulator is not booted; inspect readiness first.')
    with (ROOT / 'evidence/android-api29-instrumentation.log').open('w') as log:
        log.write('LOCAL SOFTWARE API29 ONLY; no real-data accuracy, managed Auth or declared-device performance evidence.\n')
        for prop in ['ro.build.version.sdk', 'ro.product.cpu.abi', 'sys.boot_completed']:
            value = run(['shell', 'getprop', prop]).stdout.strip()
            if prop == 'ro.build.version.sdk' and value != '29':
                raise RuntimeError('Expected isolated API29 target.')
            log.write(prop + '=' + value + '\n')
        for path, count in [('app/build/outputs/apk/debug/app-debug.apk', 0),
                            ('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk', 2)]:
            with zipfile.ZipFile(ROOT / path) as archive:
                actual = sum(name in ['assets/golden.json', 'assets/synthetic-model.json'] for name in archive.namelist())
                if actual != count:
                    raise RuntimeError('Synthetic fixture assets are not isolated to the instrumentation APK.')
            log.write(path + ': synthetic fixture assets=' + str(count) + '\n')
            log.write(run(['install', '-r', '-t', path]).stdout)
            log.flush()
        result = run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', CLASSES,
                      'com.aquaquenchers.bms.test/androidx.test.runner.AndroidJUnitRunner'], timeout=180)
        log.write(result.stdout + result.stderr)
        if 'OK (2 tests)' not in result.stdout or 'FAILURES!!!' in result.stdout:
            raise RuntimeError('Instrumentation did not pass both tests; inspect evidence log.')
    print('PASS: two actual API29 tests; synthetic fixtures absent from app APK.')


if __name__ == '__main__':
    main()
