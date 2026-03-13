# RikkaAgent

**A beautiful Android SSH command runner** — execute commands over SSH, stream stdout/stderr, and render outputs as Markdown / code blocks.

Inspired by the UX of [RikkaHub](https://github.com/re-ovo/rikkahub). Not a fork — clean-room implementation under Apache-2.0.

## What is this?

Mobile terminals work, but they're not always pleasant for reading command outputs. RikkaAgent provides a polished, chat-style UI for:

- **Run a command** → see streaming output → copy / rerun / export
- **SSH profiles** with encrypted key storage
- **Markdown & code block rendering** of command outputs
- **Host key verification** with first-use confirmation

The primary use case is connecting an Android phone to a desktop running [Codex](https://github.com/openai/codex) or similar tools via SSH, giving you a beautiful mobile interface for remote command execution.

## Features

### Implemented

- [x] Jetpack Compose chat-style UI
- [x] Material 3 theming (light + dark + AMOLED)
- [x] Markdown rendering in output bubbles
- [x] Real SSH exec streaming (stdout/stderr/exit)
- [x] ANSI escape sequence stripping
- [x] Host key verification (TOFU + mismatch warning)
- [x] SSH profiles persisted with Room
- [x] Encrypted private key storage (AndroidX Security Crypto)
- [x] Private key auth + password auth + `.ppk` support
- [x] Codex mode (`codex exec --json --full-auto`) with JSONL parsing
- [x] Session export/share + rerun/copy actions
- [x] i18n (English/Chinese)

### Planned

- [ ] Mermaid rendering (optional)
- [ ] More tests (unit + integration)
- [ ] Output expand/download for truncated long logs

## Non-Goals

- Interactive terminal emulation (PTY/ANSI cursor control)
- Server-side HTTP relays (avoids expanding attack surface)
- Fully autonomous AI agents executing commands without user intent

## Architecture

```
:app                  → Main app (Activity, ViewModel, Screens)
:core:model           → Data models (SshProfile, ChatMessage, etc.)
:core:ssh             → SSH exec interface + event model
:core:storage         → Profile persistence interface
:core:ui              → Reusable Compose components (Theme, ChatBubble, ChatInput)
```

Mode A data flow:

```
User types command → ViewModel → SshExecRunner.run(profile, cmd)
                                       ↓
                                 Flow<ExecEvent>
                                       ↓
                          StdoutChunk / StderrChunk / Exit / Error
                                       ↓
                          ChatMessage updated incrementally → UI recomposes
```

## Building

### Prerequisites

- JDK 17+
- Android SDK (API 35)
- Gradle 8.10.2 (wrapper included)

### Build

```bash
./gradlew assembleDevDebug
```

The APK will be at `app/build/outputs/apk/dev/debug/app-dev-debug.apk`.

## Project Status

Core milestones are substantially implemented (M1-M5 mainline complete, polish/test work ongoing). See [ROADMAP.md](ROADMAP.md) for live progress.

| Milestone | Description | Status |
|-----------|-------------|--------|
| M0 | Spec Freeze | ✅ Mostly Complete |
| M1 | Android App Core UX | ✅ Mostly Complete |
| M2 | Markdown/Streaming Rendering | ✅ Mostly Complete |
| M3 | SSH exec (Mode A) | ✅ Mostly Complete |
| M4 | Codex Integration | ✅ Mostly Complete |
| M5 | Release Quality | ✅ Mostly Complete |

## Documentation

- [Spec Index](docs/spec/00-index.md) — Product, UX, architecture, security specs
- [Architecture](docs/architecture.md) — Module design and data flow
- [Threat Model](docs/threat-model.md) — Security analysis
- [Server Hardening](docs/server-hardening.md) — SSH server configuration guide
- [RikkaHub UI Study](docs/research/rikkahub-android-ui-study.md) — Reference UI analysis (read-only observations)

## Clean-Room Note

This project is inspired by the UX ideas of [RikkaHub](https://github.com/re-ovo/rikkahub) and other chat UIs, but is implemented entirely from scratch. No code has been copied from copyleft-licensed repositories.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: see [SECURITY.md](SECURITY.md).

## License

[Apache-2.0](LICENSE)
