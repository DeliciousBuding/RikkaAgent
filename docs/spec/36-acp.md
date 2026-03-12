# ACP Support (v2+)

This document specifies how `rikka-agent` can add **Agent Client Protocol (ACP)** support as an optional, parallel integration path.

ACP does **not** replace v1 remote execution. In practice:

- v1: SSH `exec` + streaming rendering (`docs/spec/33-remote-exec.md`)
- v2+: ACP client for richer agent workflows (sessions, tools, terminals, file ops)

## Goals

- Enable a “native chat” agent experience using a standard protocol:
  - session lifecycle
  - streaming updates
  - tool calls / terminal output
  - (optionally) workspace/file operations
- Keep security posture strong:
  - **no public unauthenticated agent ports** by default
  - prefer tunneling ACP through SSH
- Preserve the RikkaHub-level UI polish requirements.

## Non-Goals

- Implementing an ACP server in the Android app.
- Requiring ACP for basic SSH command runs.
- Turning `rikka-agent` into a full IDE.

## ACP Summary (What We Care About)

ACP is a JSON-RPC based protocol between:

- **Client**: our Android app
- **Server**: an agent process (e.g., Copilot CLI in ACP mode, or a Codex-to-ACP bridge)

Key ACP primitives we likely use:

- session initialize / prompt
- server streaming updates
- terminal create/exec/output lifecycle

## Architecture Additions

We add new modules/interfaces without disrupting v1:

- `:core:acp`
  - JSON-RPC codec
  - ACP client state machine
  - transport abstraction

### 1) Transport Options

ACP can run over different transports. For Android + “server-side agent”, the safest default is:

- **ACP over TCP tunneled through SSH**

Transport variants:

1. `SshTunneledTcpTransport` (recommended)
   - ACP server listens on server `127.0.0.1:<port>`
   - app opens an SSH `direct-tcpip` channel to that port
   - ACP JSON-RPC messages flow through that channel
2. `WebSocketTransport` (optional, disabled by default)
   - only acceptable with strong auth + TLS + tight firewall rules
3. `StdioTransport` (desktop-only; not a primary Android target)

### 2) ACP Client Contract

The ACP client layer MUST expose a stream of UI-friendly events:

- `SessionCreated(sessionId)`
- `AssistantDelta(text)`
- `AssistantMessageFinal(text)`
- `ToolCall(name, argsJson)`
- `TerminalOutput(stdout/stderr chunk)`
- `Error(category, message)`

The UI MUST be able to render these events using the same chat components and polish
rules as v1 (see `docs/spec/34-polish.md`).

## Server-Side Options (Examples)

We do not hard-depend on a single server implementation. Documented options:

1. **Copilot CLI in ACP mode**
   - Start an ACP server process on the server and tunnel it.
2. **Codex via ACP bridge**
   - Use a `codex-acp`-style bridge that speaks ACP on one side and drives Codex on the other.

v2 spec deliberately keeps this flexible because ecosystems evolve quickly.

## Security & Deployment Defaults

Normative defaults:

- ACP servers MUST bind to `127.0.0.1` on the server.
- The Android app MUST connect via SSH tunnel (no public ports).
- Authentication relies on SSH key auth + host key verification (see `docs/spec/32-ssh.md`).
- The app MUST clearly warn if the user chooses an “expose ACP over public network” mode.

Operational guidance (future docs):

- systemd unit for ACP server (optional)
- process supervision (restart, logs)
- least-privileged server user

## Migration & Coexistence With v1

`rikka-agent` SHOULD keep a stable “execution facade”:

- `ExecutionEngine` chooses a backend:
  - `SshExecBackend` (v1)
  - `AcpBackend` (v2+)

The chat timeline remains the same:

- user message bubble
- assistant bubble streaming
- optional debug metadata

## Open Questions

- Which ACP server(s) do we officially support first (Copilot vs Codex bridge vs others)?
- How do we package “start/stop ACP server” UX safely (without encouraging bad deployments)?

Track these in `docs/spec/05-open-questions.md`.

