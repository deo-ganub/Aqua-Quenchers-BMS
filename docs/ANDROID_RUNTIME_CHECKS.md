# Local Android runtime checks

Official emulator37.2.13 (build16740729) and API29 default x86_64 image8.0.0 were installed with the official Android CLI using the inherited proxy/TLS trust. Both retained under `/workspace/tooling/android-sdk`; CLI under `/workspace/tooling/android-user/bin/android-cli`. KVM/CPU vmx/svm unavailable, so this uses software emulation. This is neither the declared Owner tablet/Staff phone nor valid performance evidence.

Isolated AVD `aq_api29_software` lives under `/workspace/tooling/android-avd/aq_api29`. Check existing `adb devices` and this AVD before starting another emulator; never stop an unrelated device. Processes do not survive environment snapshots. If this AVD is stopped:

```sh
export ANDROID_HOME=/workspace/tooling/android-sdk
export ANDROID_USER_HOME=/workspace/tooling/android-user
export ANDROID_AVD_HOME=/workspace/tooling/android-avd
$ANDROID_HOME/emulator/emulator -avd aq_api29_software -accel off -gpu swiftshader -no-snapshot -no-audio -no-window -no-boot-anim -memory 2048 -cores 2 -port 5554 -no-metrics
```

Keep emulator startup logs private (0600). Check `adb -s emulator-5554 shell getprop sys.boot_completed` returns1; a running PID/offline ADB entry is insufficient. Use `/workspace/tooling/android-sdk/platform-tools/adb`. Build with the verified Gradle commands in README, then:

```sh
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class com.aquaquenchers.bms.SignInDeviceTest,com.aquaquenchers.classifier.ClassifierParityDeviceTest,com.aquaquenchers.bms.ReportPdfDeviceTest com.aquaquenchers.bms.test/androidx.test.runner.AndroidJUnitRunner
```

Reusable runner: `python3 admin/tests/android_runtime.py` checks the expected AVD before replacing APKs, verifies asset isolation and refuses zero/failed test success.

Actual result: `OK (3 tests)`, evidence/android-api29-instrumentation.log. Sign-in test verifies missing fields and absence of protected navigation. Parity test verifies8 synthetic Python golden cases: tokens/TFIDF/7-class scores within1e-6 and status/category/reason; synthetic package rejected for release loading. The third test creates a synthetic report PDF and opens/renders its595×842 page on Android. Independent host pdftotext verification matches station/period/Manila generation time, all5 exact measures and calculation (evidence/pdf-synthetic-text.log). It is not a saved business report or SAF workflow proof. APK inspection confirms golden.json/synthetic-model.json only in instrumentation APK. No station data is bundled or quality/performance claimed.

The separate `ClassifierDeviceBenchmark` deliberately requires an approved release model and explicit declared `owner_tablet_id`; it was NOT RUN here. Actual local connected saves, real Auth/TOTP, rotation, SAF/PDF, refresh/expiry and lost-response retry now pass in separate targeted suites; see LOCAL_REAL_SERVICES.md and current WORK_STATE. Additional scenario variants, Figma fidelity and declared physical-device gates remain outstanding.

Published snapshot cold boot may leave a SystemUI ANR overlay even after sys.boot_completed=1. Check window state before diagnosing inaccessible picker controls. On this owned AVD only, identify the actual android:id/aerr_wait/Wait control before selecting Wait; never tap coordinates blindly or alter app data. Actual PDF cancellation runner: python3 admin/tests/pdf_cancel.py (read-only, real Auth, private fixture and exact snapshot reconciliation). Evidence android-pdf-cancel.log passes after startup overlay resolution.

Resume recovery (2026-11-04 Manila): ADB absent and refused5555; prior owned qemu was a zombie. Startup refused stale multiple-instance locks. After checking there were no active qemu processes and both locks belonged to the current user, archived only `hardware-qemu.ini.lock` and `multiinstance.lock` from the owned AVD into the private runtime directory. Relaunched the documented retained AVD without wiping data/read-only mode; boot completed in113149ms. Do not remove locks while any emulator is active. Retained backend health passed without fixture reset.
