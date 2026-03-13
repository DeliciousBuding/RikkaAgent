# Android Instrumentation Testing

This project keeps instrumentation tests minimal and focused on UI flows that
cannot be covered reliably with pure unit tests.

## Prerequisites

- Android SDK installed (API 34+ emulator image recommended).
- A device or emulator running.

## Recommended Emulator Setup (local)

1. Install a system image: `sdkmanager "system-images;android-34;google_apis;x86_64"`
2. Create an AVD (example): `avdmanager create avd -n rikka-ci -k "system-images;android-34;google_apis;x86_64" -d pixel_6`
3. Boot it: `emulator -avd rikka-ci -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -no-boot-anim`
4. Wait for boot: `adb wait-for-device` and `adb shell getprop sys.boot_completed` (expect `1`)

## Run Connected Tests

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

## Test Provider Notes (SAF Picker)

The SAF picker test uses a lightweight `ContentProvider` registered in the
androidTest manifest:

- Authority: `io.rikka.agent.test.documents`
- Provider class: `io.rikka.agent.test.TestDocumentsProvider`

The provider is intentionally simple and does not grant persistable permissions,
so the test asserts the non-persistable permission warning snackbar.

## Reports

- HTML report: `app/build/reports/androidTests/connected/debug/index.html`
- XML results: `app/build/outputs/androidTest-results/connected/`

## Troubleshooting

- If Gradle cannot find Java, export `JAVA_HOME` to JDK 17.
- If `connectedDevDebugAndroidTest` hangs, ensure the emulator has fully booted
  and is visible under `adb devices`.
