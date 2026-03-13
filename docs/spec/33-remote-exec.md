# Remote Execution (v1)

This spec defines how `rikka-agent` v1 executes remote work **over SSH** and streams results into a chat timeline.

`rikka-agent` v1 is intentionally **not a terminal emulator**: we use SSH `exec` channels, render output as Markdown/code blocks, and keep everything feeling like a native chat app.

## Scope

v1 includes:

- SSH `exec` (no PTY) for commands and “AI runners”.
- Streaming stdout/stderr into a single chat bubble without UI jank.
- A tolerant parsing layer for structured outputs (starting with `codex exec --json`).

## Non-Goals (v1)

- Interactive shells / full-screen apps (vim, htop) / ANSI terminal UX.
- Public HTTP remote-exec relay service (adds attack surface; not default).
- Remote file browsing/editing protocols (see ACP in `docs/spec/36-acp.md`).

## Concepts

- **Run**: one remote execution, initiated by a user action.
- **Runner**: describes how to start a run and (optionally) parse structured output.
- **Frame**: the normalized streaming event format used internally by UI/persistence.

### Frame Model (Normative)

Runners MUST produce a stream of frames:

- `RunStarted(runId, runnerType, startedAt)`
- `StdoutChunk(runId, bytes)`
- `StderrChunk(runId, bytes)`
- `StructuredEvent(runId, kind, json)`
  - `kind` examples: `json`, `status`, `markdown_delta`, `tool_call`, `trace`
- `RunExit(runId, exitCode, finishedAt)`
- `RunError(runId, category, message, detailsJson?)`
- `RunCancelled(runId, finishedAt)`

Notes:

- Frames MUST be append-only (no “rewrite history” in v1).
- `StructuredEvent` is optional; the UI MUST still be correct with only stdout/stderr.

## Transport: SSH Exec Channel (Normative)

All v1 runs use an SSH `exec` channel:

- NO PTY allocation.
- stdout and stderr MUST be read concurrently.
- exit status MUST be captured when available.
- cancellation is best-effort:
  - closing the exec channel is sufficient for v1
  - if remote process keeps running, that is acceptable in v1 (we document follow-up hardening)

## Built-in Runner Types (v1)

### 1) `shell_exec`

Purpose:

- Run a plain shell command (e.g., `uptime`, `docker ps`).

Request fields:

- `command` (string, required)
- `cwd` (string, optional)
- `envAllowlist` (list of keys, optional; defaults empty)

Execution:

- Run in a login shell is NOT required; prefer predictable behavior:
  - RECOMMENDED: `sh -lc "<command>"`
- If `cwd` is set:
  - RECOMMENDED: `cd "<cwd>" && <command>`

Output:

- Stream raw stdout/stderr into chunks.
- ANSI stripping MAY be applied (best-effort) but MUST NOT corrupt content.

### 2) `codex_exec_jsonl`

Purpose:

- Run OpenAI Codex CLI on the server and render its streaming output as chat.

Server requirements:

- `codex` CLI installed on the server.
- Authentication occurs on the server (no API keys stored on the phone by default).

Recommended command template:

- MUST enable JSONL events:
  - `codex exec --json ...`
- SHOULD avoid coupling to a git repo:
  - `--skip-git-repo-check`
- SHOULD avoid leaving artifacts by default:
  - `--ephemeral` (unless user explicitly asks to change files)

Illustrative example:

```bash
codex exec --json --ephemeral --skip-git-repo-check -m gpt-5.2-codex -- "Explain what this command does: ls -la"
```

### JSONL Parsing (Tolerant-by-Design)

Because upstream event schemas may evolve, the v1 parser MUST be schema-tolerant:

1. Split stdout by newline.
2. For each line:
   - If line is valid JSON:
     - Emit `StructuredEvent(kind="json", json=<object>)`.
     - Attempt best-effort extraction:
       - If the object contains a likely text field (examples: `text`, `content`, `delta`), emit
         `StructuredEvent(kind="markdown_delta", json={"text": <string>})`.
       - If the object contains a likely status/progress field (examples: `status`, `stage`, `progress`),
         emit `StructuredEvent(kind="status", json=<subset>)`.
     - If extraction fails, keep only the `json` event (do not drop data).
   - If line is not JSON:
     - Emit it as `StdoutChunk` (raw text + newline).

UI rules:

- The UI MAY render `markdown_delta` as a streaming assistant bubble.
- The UI SHOULD keep a per-run “Debug” view that shows raw `json` events.
- The UI MUST NOT depend on any single `type` value existing in JSON.

## Streaming & UI Performance Contract

We need “fast” feeling without excessive Compose recomposition.

Normative requirements:

- Collect raw IO on background threads.
- Convert bytes to frames as quickly as possible.
- UI updates MUST be batched:
  - target tick: 50–100ms
  - on each tick, append buffered text to the message model once
- Markdown parsing MUST NOT run on the main thread.
- Markdown MUST NOT be reparsed for every small chunk:
  - parse on tick boundaries
  - allow “plaintext while streaming, parse on settle” as a fallback mode

Truncation:

- In-memory output per run MUST be capped.
- When truncation happens, the message MUST show a clear “Output truncated” affordance and still allow:
  - copy tail
  - export complete output if available (future)

## Timeouts & Cancellation

- Default timeout SHOULD exist (configurable per profile).
- On cancel:
  - close exec channel
  - emit `RunCancelled`
- On timeout:
  - close exec channel
  - emit `RunError(category="timeout", ...)`

## Persistence (v1)

Persist per run:

- runner type + request metadata (excluding secrets)
- stdout/stderr transcript (possibly truncated)
- structured events (optional; behind a debug flag)
- exit code / timestamps

Outputs may contain secrets; deletion controls MUST exist (see `docs/spec/40-security.md`).

## Testing Requirements

- Unit tests:
  - JSONL parser: malformed JSON lines never crash; raw text preserved
  - batching logic does not reorder within a single stream
- Integration tests (SSH to local test server):
  - concurrent stdout/stderr streaming works
  - cancellation updates UI state correctly

## Related Specs

- `docs/spec/32-ssh.md`
- `docs/spec/23-rendering.md`
- `docs/spec/40-security.md`
- `docs/spec/36-acp.md` (v2 direction)
