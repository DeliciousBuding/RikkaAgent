# Test Mapping (Spec -> Automated Checks)

This document maps key spec requirements to current automated coverage.

## 1) SSH / Auth

- Spec: `docs/spec/32-ssh.md`
- Coverage:
  - `core/ssh/src/test/kotlin/io/rikka/agent/ssh/SshAuthKeyFormatTest.kt`
  - `core/ssh/src/test/kotlin/io/rikka/agent/ssh/JsonlParserTest.kt`
- Gaps:
  - Host-key mismatch callback behavior tests
  - Passphrase retry/cancel behavior tests

## 2) Remote Exec / JSONL

- Spec: `docs/spec/33-remote-exec.md`
- Coverage:
  - `JsonlParserTest` (normal JSONL / split chunks / malformed fallback / nested field)
- Gaps:
  - End-to-end ViewModel event stream mapping tests

## 2.5) Chat Session State

- Spec: `docs/spec/29-interaction.md`, `docs/spec/30-architecture.md`
- Coverage:
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt`
- Covered behaviors:
  - initial profile-ready state
  - persisted thread switch loading
  - new-session reset semantics
  - deleting active vs inactive threads
  - password request response flow
  - passphrase request response flow
  - host key unknown / mismatch event response flow
- Gaps:
  - UI-level double-confirm replacement flow tests for host-key mismatch

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

1. UI-level host-key replacement double-confirm tests
2. KnownHosts persistence tests (store/get/remove semantics)
3. ProfileEditor validation and save mapping tests
