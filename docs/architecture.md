# Architecture (Draft)

This document describes the planned architecture for `rikka-agent`.

## Goals

- Beautiful, chat-style rendering of SSH command outputs
- Low-latency UX (connection reuse + streaming updates)
- Security by default (encrypted keys, strict host verification)
- Modular design so apps can reuse the SSH core without adopting the full UI

## Suggested Module Layout

- `:app`
- `:core:model`
  - Command / output event types
  - Session state model
- `:core:storage`
  - Profile storage
  - `known_hosts` store
  - Encrypted key storage abstractions
- `:core:ssh`
  - Connection manager (reuse / lifecycle)
  - Exec runner (`exec` channel)
  - Streaming stdout/stderr
- `:core:ui`
  - Compose components: command cards, output cards
  - Markdown/code rendering adapters

## Data Flow (Exec Mode)

1. UI triggers `runCommand(profileId, command)`
2. `SshSessionManager` ensures an authenticated SSH session exists
3. `SshExecRunner` opens an exec channel and emits a stream of events:
   - stdout chunk
   - stderr chunk
   - exit code
   - error / cancelled
4. UI collects the stream and updates an output message incrementally (with throttling)

