# SSH Spec (Mode A)

This spec defines the SSH behavior required to support a “chat-style command runner” on Android.

Scope: **Mode A (non-interactive exec)** (no PTY, no ANSI terminal emulation).

## 1) Supported Authentication Methods (v1)

Must support:

- Public key authentication
  - OpenSSH private keys (including encrypted keys)
  - `ed25519` keys are recommended as the default
- Host key verification (known hosts)

Should support:

- Password authentication (optional, behind an explicit toggle; discouraged for security)

May support later:

- SSH agent forwarding
- Hardware-backed keys / FIDO2 (requires careful UX and library support)

## 2) Host Key Verification (Security-by-Default)

### First Connect

- On first connect to a host+port, the client must surface the server host key fingerprint (SHA256) and require an explicit user trust action.
- On trust, persist the host key in a local `KnownHostsStore`.

### Subsequent Connect

- If host key matches: connect silently.
- If host key mismatches:
  - default: block and show a high-severity warning
  - allow “Replace host key” only with double-confirmation (see microcopy spec)

### Matching Rules

Host identity is at minimum:

- hostname/IP
- port

Note:

- If the library provides canonical host representation (e.g., `[host]:port`), the store should follow that convention to avoid mismatch bugs.

## 3) Connection Reuse & Lifecycle

### Session Reuse

- The app should reuse a single SSH session per profile while the chat screen is active.
- A command exec opens a new exec channel on the reused session.

### Reconnect Strategy

If the session disconnects:

- fail the in-flight command with an explicit “Disconnected” status
- provide a `Reconnect` UI action
- optional: auto-reconnect once with exponential backoff (max 1–2 retries) if the user is at the bottom (to avoid surprising behavior when reviewing logs)

## 4) Keepalive

To reduce “mobile network idle” disconnects:

- enable keepalive at a conservative interval (e.g., 15–30s)
- keepalive failures should transition state to disconnected and stop streaming

Keepalive should be configurable per profile (see Settings spec).

## 5) Exec Channel Semantics

### Command Submission

- Commands are sent exactly as the user typed them.
- Do not automatically wrap commands in `bash -lc` unless the user opts in:
  - wrapping changes semantics and can hide errors

### Exit Status

- If the SSH library provides an exit code, capture it and render in the output bubble footer.
- If unknown, show “Exit status unavailable”.

### Cancellation

When the user taps Cancel:

- close the exec channel
- append a final line indicating cancellation
- mark the run as `Canceled` (distinct from error)

## 6) Output Streaming

### stdout vs stderr

- Capture stdout and stderr separately.
- Render as separate blocks (default) or a combined view (optional user setting).

### Encoding

- Assume UTF-8 by default.
- If decoding fails, replace invalid sequences and show a warning badge (“Some output could not be decoded”).

### Backpressure / Limits

To avoid memory blow-ups:

- cap buffered output per run (bytes or chars)
- when cap is exceeded:
  - continue streaming but keep only last N chars/lines
  - show “truncated” state with export/copy-all affordances

## 7) Security & Privacy Requirements

- Never log raw SSH credentials, private keys, or passphrases.
- Never write host key prompts or fingerprints into analytics.
- Treat command output as potentially sensitive:
  - default retention for session history should be conservative (see `docs/spec/40-security.md`)

## 8) Testing Expectations

Must have automated tests for:

- known hosts: accept first key, reject mismatch, replace flow generates correct store update
- exec runner: stdout/stderr separation, exit code capture, cancellation, timeout

May use:

- local Docker container running `sshd` (for integration tests)
- a mock SSH server or fake transport (for unit tests)
