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
  - `./gradlew :app:lintDevDebug`
  - `./gradlew assembleDevDebug`
- Artifacts:
  - APK, unit test reports, lint reports

## 5) Next Additions

1. ChatViewModel command wrapping tests (`shell`, `codex`, `workDir`, env injection)
2. KnownHosts persistence tests (store/get/remove semantics)
3. ProfileEditor validation and save mapping tests
