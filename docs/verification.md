# Verification Notes

This checklist keeps local regression runs consistent before release or CI triage.

## Baseline (fast)

```bash
./gradlew :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :app:testDevDebugUnitTest
```

Notes:

- Use this when only docs or UI copy changed and no core logic moved.

## Full Unit Test Sweep

```bash
./gradlew test
```

## Lint + APK

```bash
./gradlew :app:lintDevDebug
./gradlew assembleDevDebug
```

Artifacts:

- APK: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`
- Lint: `app/build/reports/lint-results-devDebug.html`

## Instrumentation (device/emulator)

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

Reports:

- Unit tests: `app/build/reports/tests/`
- Instrumentation HTML: `app/build/reports/androidTests/connected/debug/index.html`

## Windows JDK Hint

```powershell
$env:JAVA_HOME="C:\Path\To\JDK17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```
