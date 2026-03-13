# Architecture Spec

This spec is intentionally biased toward a clean separation between:

- SSH transport (network + auth + host key verification)
- execution (exec channels + streaming stdout/stderr)
- UI rendering (chat timeline + code/markdown rendering)

so that we can iterate on UI polish without destabilizing security-sensitive code.

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
  - known hosts store
- `:core:ssh`
  - SSH connection/session manager (reuse)
  - exec runner with streaming events
- `:core:ui`
  - reusable Compose components (command/output cards)
  - Markdown/code rendering adapters

Also read:

- `docs/spec/32-ssh.md`
- `docs/spec/33-remote-exec.md`
- `docs/spec/36-acp.md` (v2+)

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

Note: current implementation uses `SshConnectionPool` to provide the reuse
behavior without a separate public session manager interface.

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

### 3.4 `SshExecRunnerFactory`

Responsibilities:

- create `SshExecRunner` instances bound to a profile
- allow swapping SSH backends without touching UI

API (conceptual):

- `create(profile): SshExecRunner`

### 3.5 `SshConnectionPool`

Responsibilities:

- cache authenticated SSH clients per profile
- evict stale connections and recreate as needed

API (conceptual):

- `acquire(profile): SSHClient`
- `release(profile)`
- `closeAll()`

## 4) Data Flow (Mode A)

1. UI creates a command message
2. ViewModel calls `execRunner.run(...)`
3. Flow events update a single output message incrementally
4. Final event emits exit code; UI marks command as completed

## 5) Streaming Pipeline (Required)

We must make streaming “feel fast” without recomposing the whole list every few milliseconds.

Rules:

- Read stdout and stderr on background threads.
- Convert raw bytes into `ExecEvent` items as soon as possible.
- UI updates are batched (50–100ms tick) to avoid excessive recomposition.
- For long-running commands, keep a stable `OutputBubble` node id and append content into it.

Suggested event model (conceptual):

- `StdoutChunk(text: String)`
- `StderrChunk(text: String)`
- `Exit(code: Int?)`
- `Error(category: ..., message: ...)`
- `StateChange(connecting/connected/disconnected)`

## 6) ViewModel State & Persistence (Planned)

Two layers of state:

1. **Ephemeral runtime state** (in-memory):
   - connection state
   - in-flight command runs
   - streaming buffers (stdout/stderr)
2. **Persistent state** (disk):
   - SSH profiles
   - known hosts
   - session history (optional early; can start with “current session only”)

Guideline:

- Persist only what users explicitly expect to persist.
- Treat outputs as potentially sensitive; default retention should be conservative (see `docs/spec/40-security.md`).

## 7) UI Performance Budgets (Required)

These are “guard rails” so the UI stays smooth:

- Do not run Markdown parsing on the main thread.
- Do not reparse Markdown for every streaming chunk.
- Avoid huge single-string allocations in tight loops:
  - cap in-memory output per message
  - truncate with explicit UI affordance to export/copy-all
- Prefer immutable snapshots for UI state, but keep streaming buffers mutable internally until emitting a tick update.

## 8) Implementation Notes (Non-binding)

- Prefer a library that supports host key verification hooks.
- Avoid holding decrypted private key strings longer than necessary.
- Throttle UI updates to reduce Compose recomposition pressure.
- Current implementation uses `SshConnectionPool` to reuse authenticated sessions.
