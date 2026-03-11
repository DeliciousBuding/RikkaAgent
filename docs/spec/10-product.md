# Product Spec

## 1) Summary

`rikka-agent` is an Android (Jetpack Compose) app providing a chat-style UI for running SSH commands in **Mode A** (non-interactive `exec`), streaming stdout/stderr, and rendering outputs as Markdown/code blocks for readability.

## 2) Primary Goals

- Beautiful UI for command output reading on mobile.
- Low-latency command execution experience.
- Security-by-default:
  - encrypted private keys at rest
  - strict host key verification (`known_hosts`)
  - no server-side HTTP remote-exec relay required
- Open-source, directly usable by others (Apache-2.0).

## 3) Non-Goals

- Terminal emulation (PTY, ANSI cursor control, full-screen apps).
- “Autonomous agents” that execute commands without explicit user intent.
- Bundling a remote execution server / relay service as the default setup.

## 4) Target Users

- Individuals/operators who use SSH regularly and want a clean UI for outputs.
- Developers who want a reusable Compose UI + SSH exec streaming foundation.

## 5) Key Use Cases

- Run a one-off command and read the output comfortably:
  - `uname -a`, `df -h`, `free -m`, `docker ps`, `systemctl status ...`
- Rerun a command later from history or pinned commands.
- Copy/export outputs to share (with warnings about sensitive info).

## 6) UX Success Criteria

- Command outputs render cleanly as code blocks; long outputs remain usable.
- Clear separation of stdout vs stderr.
- “First connection” fingerprint confirmation is understandable.
- Fingerprint changes produce a strong, clear warning and a safe default (block).

## 7) Performance Success Criteria

- Connection reuse reduces repeated latency for sequential commands.
- Streaming output updates feel smooth (no visible UI jank).
- Large outputs do not freeze the UI; truncation/load-more is available.

