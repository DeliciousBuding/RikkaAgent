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
  - `app/src/test/java/io/rikka/agent/ui/screen/HostKeyDialogStateMachineTest.kt`
  - `app/src/test/java/io/rikka/agent/ui/screen/HostKeyDialogsTest.kt`
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt`
- Gaps:
  - SAF picker permission persistence / URI lifecycle regression coverage

## 2) Remote Exec / JSONL

- Spec: `docs/spec/33-remote-exec.md`
- Coverage:
  - `JsonlParserTest` (normal JSONL / split chunks / malformed fallback / nested field)
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt` (password / passphrase / host-key request-response mapping, canceled exec state)
- Gaps:
  - Richer UI surfacing for Codex `thread/turn/item` progress events

## 2.5) Chat Session State

- Spec: `docs/spec/29-interaction.md`, `docs/spec/30-architecture.md`
- Coverage:
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt`
  - `app/src/test/java/io/rikka/agent/vm/ProfileEditorViewModelTest.kt`
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
  - End-to-end UI interaction coverage for action-row flows (rerun/share/export)

## 3) Output Formatting & Truncation

- Spec: `docs/spec/30-architecture.md` (memory cap), `docs/spec/34-polish.md`
- Coverage:
  - `app/src/test/java/io/rikka/agent/vm/OutputFormatterTest.kt`
- Gaps:
  - UI-level interaction tests for expand/share full output

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

1. SAF picker permission persistence / URI lifecycle tests
2. UI-level interaction tests for truncated-output expand/share flows
3. Codex `thread/turn/item` progress visualization tests
