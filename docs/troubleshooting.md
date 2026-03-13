# Troubleshooting

Common issues and quick fixes for local development and CI.

## Java / Gradle

**`JAVA_HOME is not set`**

- Ensure JDK 17 is installed.
- On Windows:

```powershell
$env:JAVA_HOME="C:\Path\To\JDK17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Android SDK

**`sdk.dir` missing**

- Ensure `local.properties` contains your SDK path:

```text
sdk.dir=C:\Path\To\Android\Sdk
```

## Emulator / Connected Tests

**`connectedDevDebugAndroidTest` hangs**

- Confirm emulator is booted:
  - `adb wait-for-device`
  - `adb shell getprop sys.boot_completed` returns `1`

**No devices found**

- Start an emulator or connect a device.
- Run `adb devices` to confirm visibility.

## Lint / Build Artifacts

**Lint report not found**

- Run `./gradlew :app:lintDevDebug`
- Report path: `app/build/reports/lint-results-devDebug.html`

**APK not found**

- Run `./gradlew assembleDevDebug`
- APK path: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`

## CI Diagnostics

For workflow steps, artifacts, and summary details, see `docs/ci-notes.md`.
