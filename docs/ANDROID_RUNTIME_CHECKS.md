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
adb -s emulator-5554 shell am instrument -w -r -e class com.aquaquenchers.bms.SignInDeviceTest,com.aquaquenchers.classifier.ClassifierParityDeviceTest com.aquaquenchers.bms.test/androidx.test.runner.AndroidJUnitRunner
```

Reusable runner: `python3 admin/tests/android_runtime.py` checks the expected AVD before replacing APKs, verifies asset isolation and refuses zero/failed test success.

Actual result: `OK (2 tests)`, evidence/android-api29-instrumentation.log. Sign-in test verifies missing fields and absence of protected navigation. Parity test verifies8 synthetic Python golden cases: tokens/TFIDF/7-class scores within1e-6 and status/category/reason; synthetic package rejected for release loading. APK inspection confirms golden.json/synthetic-model.json only in instrumentation APK. No station data is bundled or quality/performance claimed.

The separate `ClassifierDeviceBenchmark` deliberately requires an approved release model and explicit declared `owner_tablet_id`; it was NOT RUN here. Connected saves, managed Auth/MFA, full screen/rotation/network/PDF flows, Figma fidelity and physical-device gates still require their documented prerequisites.
