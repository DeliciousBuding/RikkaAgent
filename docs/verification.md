# Verification Notes

This checklist keeps local regression runs consistent before release or CI triage.

## Baseline (fast)

```bash
./gradlew :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :app:testDevDebugUnitTest
```

## Full Unit Test Sweep

```bash
./gradlew test
```

## Lint + APK

```bash
./gradlew :app:lintDevDebug
./gradlew assembleDevDebug
```

## Instrumentation (device/emulator)

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

## Windows JDK Hint

```powershell
$env:JAVA_HOME="C:\Path\To\JDK17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```
