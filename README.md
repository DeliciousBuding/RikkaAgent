# RikkaAgent

<div align="center">
  <img src="docs/images/rikkaagent-hero.svg" alt="RikkaAgent Hero" width="600" />

  **The most beautiful SSH client for Android.**

  Execute commands. Run AI tools. Read output like a conversation — not a terminal wall.

  [![Android CI](https://img.shields.io/github/actions/workflow/status/DeliciousBuding/RikkaAgent/ci.yml?branch=master&label=Android%20CI)](https://github.com/DeliciousBuding/RikkaAgent/actions/workflows/ci.yml)
  [![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin)](https://kotlinlang.org/)
  [![Android API](https://img.shields.io/badge/API-24%2B-2ea043)](app/build.gradle.kts)
  [![Material You](https://img.shields.io/badge/Material%20You-dynamic-6750A4)](https://m3.material.io/)
  [![Codex](https://img.shields.io/badge/Codex-integrated-0a7ea4)](docs/design-agent-runner.md)
</div>

## At A Glance

| Focus | What You Get |
|---|---|
| **Beautiful by design** | Material You dynamic color, 5 preset themes, AMOLED dark mode, 50 semantic colors |
| **Chat-style output** | Commands and output rendered as structured chat bubbles, not raw terminal text |
| **Rich Markdown** | IntelliJ MarkdownParser with syntax highlighting, tables, Mermaid diagrams |
| **Security-first** | Known-hosts verification, encrypted key storage, explicit credential confirmation |
| **AI-ready** | Remote Codex/Claude Code execution with ChainOfThought reasoning display |
| **Operator velocity** | Copy, rerun, share, export — every action one tap away |
| **Chinese-first i18n** | Full Chinese + English localization across all screens |

## Capability Matrix

| Domain | Status | Notes |
|---|---|---|
| Compose app shell | Done | Profiles, editor, chat session, settings, known hosts, about |
| SSH engine (Mode A) | Done | sshj exec, stdout/stderr/exit streaming, connection reuse |
| Auth chain | Done | Password, key, passphrase, PuTTY `.ppk` |
| Host key safety | Done | TOFU + mismatch warning + replacement confirmation |
| Codex integration | Done | Profile toggle, workdir, API key injection, JSONL events, thread/turn/item progress summary |
| Rendering pipeline | Done | IntelliJ MarkdownParser + HighlightCodeBlock + CodeCard + truncation/full-output |
| Structured message model | Done | `MessagePart` sealed class: Command, Stdout, Stderr, Text, Code, Reasoning, Error, Mermaid |
| MessagePart rendering | Done | `MessagePartsBlock` dispatches each part type to dedicated composable |
| ChainOfThought | Done | Collapsible reasoning card for Codex reasoning steps |
| Syntax highlighting | Done | `HighlightCodeBlock` with language-aware coloring |
| Mermaid rendering | Done | Feature flag, segmented rendering, WebView card, retry + source fallback |
| Lucide icons | Done | Consistent icon set across all screens (Lucide Icons 1.1.0) |
| Material Expressive Theme | Done | `MaterialExpressiveTheme` with AMOLED + dynamic color + extend colors |
| CI quality gate | Done | Unit test + lint + assemble + artifacts + summary |

## Product Boundary

### Included

- Non-interactive SSH command execution (exec channel)
- Structured streaming into chat bubbles via `MessagePart` model
- Day-to-day operator workflow (rerun, copy, share, export)
- Markdown rendering with syntax highlighting (IntelliJ MarkdownParser)
- Collapsible ChainOfThought reasoning display for Codex sessions

### Not Included (v1)

- PTY terminal emulation (`vim`, `top`, `htop`, cursor control)
- Default server-side HTTP command relay
- Fully autonomous remote action without explicit user command

## Architecture

```text
:app            -> Screens, Navigation, ViewModels, DI wiring
:core:model     -> Domain models (MessagePart sealed class, ChatMessage, ChatThread, SshProfile)
:core:ssh       -> SSH runner, JSONL parser, host key store interfaces, connection pool
:core:storage   -> Room v5 + DataStore persistence, MessagePartConverter, Migrations
:core:ui        -> Reusable Compose components (MessagePartsBlock/HighlightCodeBlock/MarkdownBlock/CodeCard/ReasoningCard/ChainOfThought/DotLoading/ErrorCard/DataTable)
```

### ViewModel Decomposition

The original monolithic `ChatViewModel` has been decomposed into focused collaborators:

```text
ChatViewModel (thin orchestrator)
  -> ChatSessionManager   -- thread CRUD, message persistence, title auto-generation
  -> CommandExecutor      -- SSH connection, command execution, output formatting, Codex JSONL
  -> AuthCallbackBroker   -- host key / password / passphrase callback bridging (SharedFlow)
  -> SessionExporter      -- session export to file
  -> CancelMessageHelper  -- cancellation message assembly
  -> OutputFormatter      -- truncation, exit code, stderr formatting
  -> CommandComposer      -- shell wrapping, Codex env injection
  -> CodexProgressFormatter -- thread/turn/item progress rendering
  -> ErrorMessageMapper   -- SSH error category to user-friendly string
```

### MessagePart Model

`ChatMessage` now carries a `parts: List<MessagePart>` field instead of a flat `content: String`. Each part is a typed sealed class instance:

| Part Type | Purpose |
|---|---|
| `MessagePart.Command` | Executed command with exit code and timestamp |
| `MessagePart.Stdout` | Stdout chunk (streaming-friendly) |
| `MessagePart.Stderr` | Stderr chunk |
| `MessagePart.Text` | Plain text / Markdown content |
| `MessagePart.Code` | Fenced code block with language tag |
| `MessagePart.Reasoning` | AI reasoning step (ChainOfThought) |
| `MessagePart.Error` | Structured error with cause chain |
| `MessagePart.Mermaid` | Mermaid diagram definition |

Backward compatibility: old messages with only `content: String` auto-migrate to `parts = listOf(Text(content))` on deserialization.

### Mode A Runtime Flow

```text
User command
    -> ChatViewModel
    -> CommandExecutor.execute()
    -> SshExecRunner.run(profile, command)
    -> Flow<ExecEvent>
    -> stdout/stderr/structured/exit
    -> MessagePart accumulation
    -> message persistence + UI update via MessagePartsBlock
```

## Quick Start

### Requirements

- JDK 17+
- Android SDK (target API 35)
- Gradle wrapper (included)

Windows note (if `JAVA_HOME` is missing):

```powershell
$env:JAVA_HOME="C:\Path\To\JDK17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### Build, Test, Lint

```bash
./gradlew test
./gradlew :app:lintDevDebug
./gradlew assembleDevDebug
```

Instrumentation (requires device/emulator):

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

Instrumentation tips and emulator setup:

- `docs/testing-android-instrumentation.md`

Debug APK output:

- `app/build/outputs/apk/dev/debug/app-dev-debug.apk`

## Verification Shortcuts

For fast local regression on current core paths:

```bash
./gradlew :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :app:testDevDebugUnitTest
```

For UI-heavy regression before a release candidate:

```bash
./gradlew :app:testDevDebugUnitTest --tests "io.rikka.agent.ui.screen.HostKeyDialogsTest"
./gradlew :app:lintDevDebug :app:assembleDevDebug
```

## UI Gallery

| Chat Session | Profile Editor | Settings |
|---|---|---|
| ![Chat Session](docs/images/screenshot-chat.svg) | ![Profile Editor](docs/images/screenshot-profile.svg) | ![Settings](docs/images/screenshot-settings.svg) |

| MessageParts Rendering | ChainOfThought | Syntax Highlighting |
|---|---|---|
| ![MessageParts](docs/images/screenshot-messageparts.svg) | ![ChainOfThought](docs/images/screenshot-reasoning.svg) | ![Highlight](docs/images/screenshot-highlight.svg) |

> Notes:
> - Current gallery uses placeholder SVG assets generated from project visual direction.
> - Replace with real screenshots before release tag.

## Quick Demo Flow

1. Create a profile in the editor (host/user/auth).
2. Test SSH connection and trust host key fingerprint.
3. Open chat session and run `uname -a`.
4. Re-run, copy, export, and share output from action row.
5. (Optional) Enable Codex mode for JSONL streaming tasks with ChainOfThought reasoning display.

## FAQ

**Q1: Why RikkaAgent over Termux / ConnectBot / JuiceSSH?**

Termux is a full terminal emulator — powerful but clunky for quick tasks. ConnectBot and JuiceSSH are traditional terminal apps. RikkaAgent is designed for **readability and speed**: every command output is rendered with Markdown, syntax highlighting, and structured cards. It's the tool you reach for when something's broken and you need answers fast — not a wall of monospace text.

**Q2: Why no interactive terminal mode (vim, top, htop)?**
Mode A intentionally focuses on exec-channel reliability and readable output. PTY is out of v1 scope. See [design-pty.md](docs/design-pty.md) for the v2 roadmap.

**Q3: Does RikkaAgent store private keys in plaintext?**
No. App-managed keys are encrypted at rest via AndroidX Security Crypto (AES-256-GCM).

**Q4: Why does host key mismatch require confirmation?**
To reduce MITM risk. Replacing trust should only happen after out-of-band verification.

**Q5: Mermaid diagram shows source code instead of rendering. Why?**
Fallback is intentional when parsing or local rendering fails. The source block remains visible so the session stays readable and recoverable.

**Q6: What's the relationship between RikkaAgent and RikkaHub?**
RikkaAgent's design language is aligned with RikkaHub (5.5k ⭐ on GitHub) — MaterialExpressiveTheme, ExtendColors, Lucide Icons, component patterns. They are independent projects: RikkaHub is an AI chat client; RikkaAgent is an SSH command executor that brings RikkaHub's polished UX to remote server management.

## GitHub Ready

- CI validates unit tests, lint, and a dev debug APK on every PR.
- Dependabot monitors Gradle and GitHub Actions dependencies weekly.
- PR template is included for verification notes and rollback planning.
- Issue templates guide bug reports and feature requests with security-safe prompts.

## Milestones

| Milestone | Theme | Status |
|---|---|---|
| M0 | Spec freeze | Done |
| M1 | App core UX | Done |
| M2 | Rendering pipeline | Done |
| M3 | SSH engine | Done |
| M4 | Codex integration | Done |
| M5 | Release quality | Done |
| M6 | MessagePart + ViewModel refactor | Done |
| M7 | UI alignment with RikkaHub | Done |

Track details in [ROADMAP.md](ROADMAP.md).

---

## Design

RikkaAgent's UI is aligned with [RikkaHub](https://github.com/re-ovo/rikkahub) (5.5k ⭐), one of the most polished Material You Android apps in open source. We adopted its design language — MaterialExpressiveTheme, ExtendColors, Lucide Icons, ChainOfThought, MarkdownBlock — and adapted it for the SSH command execution domain.

Built clean-room from scratch under Apache-2.0.

## Security Notes

- Private keys are encrypted at rest via AndroidX Security Crypto.
- Host key mismatch is treated as high-risk and requires explicit confirmation.
- Never commit secrets (keys, tokens, passphrases, real infrastructure identifiers).

Hardening references:

- [docs/server-hardening.md](docs/server-hardening.md)
- [docs/threat-model.md](docs/threat-model.md)
- [docs/privacy-audit.md](docs/privacy-audit.md)

## Docs Hub

| Category | Entry |
|---|---|
| **Product** | [PRD](docs/prd.md) · [Architecture](docs/architecture.md) · [UI Design](docs/design.md) · [API](docs/api.md) |
| **Security** | [Security Design](docs/security.md) · [Threat Model](docs/threat-model.md) · [Privacy Audit](docs/privacy-audit.md) |
| **Testing** | [Testing Strategy](docs/testing.md) · [Conventions](docs/testing-conventions.md) · [Instrumentation](docs/testing-android-instrumentation.md) |
| **Engineering** | [Release Checklist](docs/release-checklist.md) · [Dependency Audit](docs/dependency-audit.md) · [Glossary](docs/glossary.md) |
| **Roadmap** | [Use Cases](docs/design-use-cases.md) · [AgentRunner](docs/design-agent-runner.md) · [PTY Design](docs/design-pty.md) |
| **Analysis** | [Analysis Report](docs/analysis/analysis-report.md) · [Project Overview](docs/analysis/project-overview.md) |

## Contributing

Before opening PRs, read:

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [SECURITY.md](SECURITY.md)

## License

Licensed under [Apache-2.0](LICENSE).
