# Architecture Spec

## 1) Project Type

- Android app (Jetpack Compose)
- Multi-module Gradle project (recommended)

## 2) Modules (Planned)

- `:app`
- `:core:model`
  - `SshProfile`
  - `Command`, `CommandRun`, `ExecEvent`
  - session / timeline state models
- `:core:storage`
  - encrypted key storage abstraction
  - profile store
  - known_hosts store
- `:core:ssh`
  - SSH connection/session manager (reuse)
  - exec runner with streaming events
- `:core:ui`
  - reusable Compose components (command/output cards)
  - Markdown/code rendering adapters

## 3) Key Interfaces (Stability Targets)

### 3.1 `SshSessionManager`

Responsibilities:

- establish authenticated session
- maintain connection reuse across commands
- keepalive
- expose connection state

API (conceptual):

- `connect(profileId)`
- `disconnect(profileId)`
- `getSession(profileId)`

### 3.2 `SshExecRunner`

Responsibilities:

- open exec channel
- stream stdout/stderr events
- cancellation + timeout
- return exit status when available

API (conceptual):

- `run(profileId, command): Flow<ExecEvent>`

### 3.3 `KnownHostsStore`

Responsibilities:

- read and write trusted host keys
- compare fingerprints
- provide "host key mismatch" metadata for UX

## 4) Data Flow (Mode A)

1. UI creates a command message
2. ViewModel calls `execRunner.run(...)`
3. Flow events update a single output message incrementally
4. Final event emits exit code; UI marks command as completed

## 5) Implementation Notes (Non-binding)

- Prefer a library that supports host key verification hooks.
- Avoid holding decrypted private key strings longer than necessary.
- Throttle UI updates to reduce Compose recomposition pressure.

