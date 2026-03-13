# Test Mapping (Spec -> Automated Checks)

This document maps key spec requirements to current automated coverage.

## 1) SSH / Auth

- Spec: `docs/spec/32-ssh.md`
- Coverage:
  - `core/ssh/src/test/kotlin/io/rikka/agent/ssh/SshAuthKeyFormatTest.kt`
  - `core/ssh/src/test/kotlin/io/rikka/agent/ssh/JsonlParserTest.kt`
  - `core/ssh/src/test/kotlin/io/rikka/agent/ssh/InMemoryKnownHostsStoreTest.kt`
  - `app/src/test/java/io/rikka/agent/ssh/DataStoreKnownHostsStoreTest.kt`
  - `app/src/test/java/io/rikka/agent/ssh/ContentUriKeyContentProviderTest.kt`
  - `app/src/test/java/io/rikka/agent/ssh/KnownHostsEntryCodecTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/screen/KeyImportSupportTest.kt`
  - `app/src/androidTest/java/io/rikka/agent/ui/screen/ProfileEditorSafPickerTest.kt`
  - `app/src/androidTest/java/io/rikka/agent/test/TestDocumentsProvider.java`
  - `app/src/test/java/io/rikka/agent/ui/screen/HostKeyDialogStateMachineTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/screen/HostKeyDialogsTest.kt`
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt`
- Gaps:
  - None

## 2) Remote Exec / JSONL

- Spec: `docs/spec/33-remote-exec.md`
- Coverage:
  - `JsonlParserTest` (normal JSONL / split chunks / malformed fallback / nested field)
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt` (password / passphrase / host-key request-response mapping, canceled exec state, Codex thread/turn/item progress rendering)
  - `app/src/test/java/io/rikka/agent/vm/CodexProgressFormatterTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/components/CodexProgressUiTest.kt`
- Gaps:
  - None

## 2.5) Chat Session State

- Spec: `docs/spec/29-interaction.md`, `docs/spec/30-architecture.md`
- Coverage:
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt`
  - `app/src/test/java/io/rikka/agent/vm/ProfileEditorViewModelTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/components/ChatBubbleActionsTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/screen/FullOutputDialogTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/screen/ShareIntentsTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/screen/ChatScreenShareDispatchTest.kt`
- Covered behaviors:
  - initial profile-ready state
  - persisted thread switch loading
  - new-session reset semantics
  - deleting active vs inactive threads
  - password request response flow
  - passphrase request response flow
  - host key unknown / mismatch event response flow
  - host key replacement confirmation dialogs
  - canceled exec event persistence + cancelRunning semantics
- Gaps:
  - None

## 3) Output Formatting & Truncation

- Spec: `docs/spec/30-architecture.md` (memory cap), `docs/spec/34-polish.md`
- Coverage:
  - `app/src/test/java/io/rikka/agent/vm/OutputFormatterTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/components/ChatBubbleActionsTest.kt` (CodeCard expand + full-output action row)
  - `app/src/test/java/io/rikka/agent/ui/screen/FullOutputDialogTest.kt` (full-output dialog render + share/dismiss callbacks)
  - `app/src/test/java/io/rikka/agent/ui/screen/ShareIntentsTest.kt` (chooser title + text/subject payloads)
  - `app/src/test/java/io/rikka/agent/ui/screen/ChatScreenShareDispatchTest.kt` (share/export dispatch to platform)
- Gaps:
  - None

## 4) CI Enforcement

- Workflow: `.github/workflows/ci.yml`
- Enforced steps:
  - `./gradlew test`
  - `./gradlew :core:model:testDebugUnitTest :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :core:ui:testDebugUnitTest :app:testDevDebugUnitTest`
  - `./gradlew :app:lintDevDebug`
  - `./gradlew assembleDevDebug`
- Artifacts:
  - APK, unit test reports, lint reports
- Diagnostics:
  - CI summary now includes test case count, skipped count, and failure triage hints

## 5) Next Additions

1. None
