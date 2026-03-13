# Testing Spec

## 1) Test Levels

- Unit tests: known hosts parsing/matching, output truncation logic, event stream throttling (pure Kotlin).
- Integration tests: SSH connection/auth with local mock or in-memory substitute, host key mismatch behavior (simulated).
- UI tests: command card rerun/copy, output collapse/expand.
- Instrumentation tests: SAF picker flow, platform share/intent dispatch, Compose UI regression.

## 2) What We Must NOT Test In CI (by default)

- Real user servers
- Real private keys

## 3) Deterministic Output Handling

Streaming output tests should:

- feed chunk sequences
- verify merged output and timestamps
- verify truncation triggers at thresholds

## 4) Instrumentation Notes

- Instrumentation runs are intentionally minimal and documented in
  `docs/testing-android-instrumentation.md`.
- Local verification shortcuts live in `docs/verification.md`.
