# RikkaAgent

![RikkaAgent Hero](docs/images/rikkaagent-hero.svg)

> 📱 Chat-first SSH runner for Android.  
> Read command output like a conversation, not a terminal wall.

[![Android CI](https://img.shields.io/github/actions/workflow/status/DeliciousBuding/RikkaAgent/ci.yml?branch=master&label=Android%20CI)](https://github.com/DeliciousBuding/RikkaAgent/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Android API](https://img.shields.io/badge/API-24%2B-2ea043)](app/build.gradle.kts)
[![Mode](https://img.shields.io/badge/Mode-A%20(SSH%20exec)-0a7ea4)](docs/spec/32-ssh.md)

Inspired by the UX feel of [RikkaHub](https://github.com/re-ovo/rikkahub), built clean-room from scratch under Apache-2.0.

## 🌟 At A Glance

| Focus | What You Get |
|---|---|
| 💬 Readability-first UX | Command + output timeline, Markdown + code cards, streaming-friendly rendering |
| 🔐 Security-by-default | Known-hosts verification, mismatch warning flow, encrypted key storage |
| ⚡ Ops velocity | Copy / rerun / export session, profile-level shell and auth options |
| 🧠 Codex bridge | Remote `codex exec --json --full-auto` with tolerant JSONL parsing and progress summaries |
| 📈 Diagram rendering | Optional Mermaid detection, card rendering, and fallback path |
| 🌍 i18n | Chinese + English resources, Chinese-first UX wording |

## 🚀 Capability Matrix

| Domain | Status | Notes |
|---|---|---|
| Compose app shell | ✅ | Profiles, editor, chat session, settings, known hosts, about |
| SSH engine (Mode A) | ✅ | sshj exec, stdout/stderr/exit streaming, connection reuse |
| Auth chain | ✅ | Password, key, passphrase, PuTTY `.ppk` |
| Host key safety | ✅ | TOFU + mismatch warning + replacement confirmation |
| Codex integration | ✅ | Profile toggle, workdir, API key injection, JSONL events, thread/turn/item progress summary |
| Rendering pipeline | ✅ | Markdown + CodeCard + truncation/full-output affordances |
| Mermaid rendering | ✅ | Feature flag, segmented rendering, WebView card, retry + source fallback |
| CI quality gate | ✅ | Unit test + lint + assemble + artifacts + summary |

## 🎯 Product Boundary

### Included

- Non-interactive SSH command execution (exec channel)
- Structured streaming into chat bubbles
- Day-to-day operator workflow (rerun, copy, share, export)

### Not Included (v1)

- PTY terminal emulation (`vim`, `top`, `htop`, cursor control)
- Default server-side HTTP command relay
- Fully autonomous remote action without explicit user command

## 🧱 Architecture

```text
:app            -> Screens, Navigation, ViewModels, DI wiring
:core:model     -> Domain models (profile, message, status)
:core:ssh       -> SSH runner, JSONL parser, host key store interfaces
:core:storage   -> Room + DataStore persistence
:core:ui        -> Reusable Compose components (bubble/code/markdown/input)
```

### Mode A Runtime Flow

```text
User command
    -> ChatViewModel
    -> SshExecRunner.run(profile, command)
    -> Flow<ExecEvent>
    -> stdout/stderr/structured/exit
    -> message persistence + UI update
```

## ⚙️ Quick Start

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

## ✅ Verification Shortcuts

For fast local regression on current core paths:

```bash
./gradlew :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :app:testDevDebugUnitTest
```

For UI-heavy regression before a release candidate:

```bash
./gradlew :app:testDevDebugUnitTest --tests "io.rikka.agent.ui.screen.HostKeyDialogsTest"
./gradlew :app:lintDevDebug :app:assembleDevDebug
```

## 🖼️ UI Gallery

| Chat Session | Profile Editor | Settings |
|---|---|---|
| ![Chat Session](docs/images/screenshot-chat.svg) | ![Profile Editor](docs/images/screenshot-profile.svg) | ![Settings](docs/images/screenshot-settings.svg) |

> Notes:
> - Current gallery uses placeholder SVG assets generated from project visual direction.
> - Replace with real screenshots before release tag.

## 🎬 Quick Demo Flow

1. Create a profile in the editor (host/user/auth).
2. Test SSH connection and trust host key fingerprint.
3. Open chat session and run `uname -a`.
4. Re-run, copy, export, and share output from action row.
5. (Optional) Enable Codex mode for JSONL streaming tasks.

## ❓ FAQ

**Q1: Why no interactive terminal mode?**  
Mode A intentionally focuses on exec-channel reliability and readable output. PTY is out of v1 scope.

**Q2: Does RikkaAgent store private keys in plaintext?**  
No. App-managed keys are encrypted at rest via AndroidX Security Crypto.

**Q3: Why does host key mismatch require confirmation?**  
To reduce MITM risk. Replacing trust should only happen after out-of-band verification.

**Q4: Mermaid toggle is enabled but a diagram still falls back to source. Why?**  
Fallback is intentional when parsing or local rendering fails. The source block remains visible so the session stays readable and recoverable.

## 🧭 GitHub Ready

- CI validates unit tests, lint, and a dev debug APK on every PR.
- Dependabot monitors Gradle and GitHub Actions dependencies weekly.
- PR template is included for verification notes and rollback planning.
- Issue templates guide bug reports and feature requests with security-safe prompts.

## 🗺️ Milestones

Track execution details in [ROADMAP.md](ROADMAP.md).

| Milestone | Theme | Status |
|---|---|---|
| M0 | Spec freeze | ✅ Mostly complete |
| M1 | App core UX | ✅ Mostly complete |
| M2 | Rendering pipeline | ✅ Mostly complete |
| M3 | SSH engine | ✅ Mostly complete |
| M4 | Codex integration | ✅ Mostly complete |
| M5 | Release quality | ✅ Mostly complete |

## 🛡️ Security Notes

- Private keys are encrypted at rest via AndroidX Security Crypto.
- Host key mismatch is treated as high-risk and requires explicit confirmation.
- Never commit secrets (keys, tokens, passphrases, real infrastructure identifiers).

Hardening references:

- [docs/server-hardening.md](docs/server-hardening.md)
- [docs/threat-model.md](docs/threat-model.md)
- [docs/privacy-audit.md](docs/privacy-audit.md)

## 📚 Docs Hub

| Category | Entry |
|---|---|
| Spec index | [docs/spec/00-index.md](docs/spec/00-index.md) |
| Instrumentation testing | [docs/testing-android-instrumentation.md](docs/testing-android-instrumentation.md) |
| Verification checklist | [docs/verification.md](docs/verification.md) |
| Test mapping | [docs/spec/71-test-mapping.md](docs/spec/71-test-mapping.md) |
| Release acceptance | [docs/spec/81-release-acceptance-matrix.md](docs/spec/81-release-acceptance-matrix.md) |
| Release checklist | [docs/release-checklist.md](docs/release-checklist.md) |
| Architecture | [docs/architecture.md](docs/architecture.md) |
| Product plan | [docs/plan.md](docs/plan.md) |
| i18n key audit | [docs/i18n-key-audit.md](docs/i18n-key-audit.md) |
| RikkaHub UX study (research) | [docs/research/rikkahub-android-ui-study.md](docs/research/rikkahub-android-ui-study.md) |

## 🤝 Contributing

Before opening PRs, read:

- [CONTRIBUTING.md](CONTRIBUTING.md)
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [SECURITY.md](SECURITY.md)

## 📄 License

Licensed under [Apache-2.0](LICENSE).
