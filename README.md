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

### Implemented (M1 Skeleton)

- [x] Jetpack Compose chat-style UI
- [x] Material 3 theming (light + dark)
- [x] Markdown rendering in output bubbles
- [x] Streaming output simulation (fake data for UI validation)
- [x] ANSI escape sequence stripping utility

### Planned

- [ ] SSH profiles (host/port/user/key)
- [ ] Private key import + encrypted at-rest storage (Android Keystore)
- [ ] Strict `known_hosts` verification (fingerprint confirmation; change warning/block)
- [ ] Real SSH exec channel streaming
- [ ] Code block cards (copy / collapse / syntax highlight)
- [ ] Navigation: Profiles → Editor → Session → Settings
- [ ] Codex integration (remote command execution)

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

Early development. Currently at **M1** (Android skeleton with mock data). See [ROADMAP.md](ROADMAP.md) for the full milestone plan.

| Milestone | Description | Status |
|-----------|-------------|--------|
| M0 | Spec Freeze | 🟡 In Progress |
| M1 | Android Skeleton (UI, no SSH) | 🟡 In Progress |
| M2 | Markdown/Streaming Rendering | Not Started |
| M3 | SSH exec (Mode A) | Not Started |
| M4 | Codex Integration | Not Started |
| M5 | Open-Source Release Quality | Not Started |

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
