# Testing Spec

## 1) Test Levels

- Unit tests:
  - known_hosts parsing and matching
  - output truncation logic
  - event stream throttling logic (pure Kotlin)
- Integration tests:
  - SSH connection/auth with a local mock or in-memory substitute (preferred)
  - host key mismatch behavior (simulated)
- UI tests (later):
  - command card rerun/copy
  - output collapse/expand

## 2) What We Must NOT Test In CI (by default)

- Real user servers
- Real private keys

## 3) Deterministic Output Handling

Streaming output tests should:

- feed chunk sequences
- verify merged output and timestamps
- verify truncation triggers at thresholds

