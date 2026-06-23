# STATE -- rikka-agent

> Static facts, architecture, and infrastructure. No progress or history.
>
> Progress -> `ROADMAP.md` | History -> `ARCHIVE.md`

---

## 1. Key Decisions (SSOT)

| Decision | Value |
|----------|-------|
| Product form | Android App (Kotlin + Jetpack Compose) |
| Product positioning | SSH command executor (not an AI chat client) |
| Interaction mode | Mode A (non-interactive exec channel; no PTY/ANSI rendering) |
| Connection | Android native SSH direct; no server-side HTTP relay |
| Security | Known-hosts enabled by default; host key mismatch blocks; encrypted key storage |
| License | Apache-2.0 |
| Clean-room | UX inspiration only; no code copied from reference projects |
| SSH library | sshj 0.39.0 (BSD-2-Clause), Ed25519/RSA/ECDSA |
| Private key format | OpenSSH PEM + PuTTY `.ppk` |
| Markdown rendering | IntelliJ MarkdownParser 0.7.3 (AST-based, syntax highlighting) + commonmark-java 0.22.0 (GFM tables/strikethrough) |
| Icon system | Lucide Icons 1.1.0 (`composables:icons-lucide`) |
| Theme | MaterialExpressiveTheme + AMOLED + dynamic color + extend colors |
| Message model | `MessagePart` sealed class (Command/Stdout/Stderr/Text/Code/Reasoning/Error/Mermaid) |
| Key storage | SSH private keys: EncryptedFile (AES-256-GCM via AndroidX Security Crypto); Codex API Key: Room DB plaintext (known debt, see ROADMAP) |
| Codex integration | `codex exec --full-auto` via SSH (exec channel) |
| Output strategy | Truncation display + message-level complete output view/share |
| i18n | Chinese + English (values/strings.xml + values-zh/strings.xml), Chinese-first |
| Persistence | Room DB v5 (chat/config/Codex fields/partsJson) + DataStore (preferences) |
| DI | Koin |
| CI | GitHub Actions: test + lint + assemble + artifacts + step summary |
| Spec index | `docs/spec/00-index.md` |

---

## 2. Module Status

| Module | Status | Notes |
|--------|--------|-------|
| `:app` | High completion | Navigation, ViewModel decomposition (6 sub-components), Codex mode, i18n, Lucide icons throughout |
| `:core:model` | Stable | `MessagePart` sealed class (8 types), `ChatMessage` with parts + backward compat, `ChatThread`, `SshProfile`, Room MIGRATION_4_5 |
| `:core:ssh` | Stable | SSH exec, JSONL parser, HostKey verification, `.ppk` support, `SshConnectionPool`, `SshOutputMapper` |
| `:core:storage` | Stable | Room v5, DataStore, Profile/Chat persistence, `MessagePartConverter`, proper Migrations |
| `:core:ui` | High completion | MaterialExpressiveTheme, `MessagePartsBlock`, `HighlightCodeBlock`, `MarkdownBlock`, `ReasoningCard`, `ChainOfThought`, `CodeCard`, `CommandCard`, `StreamOutputCard`, `ErrorCard`, `DataTable`, `DotLoading`, `MermaidDiagramCard` |

---

## 3. ViewModel Decomposition

The original monolithic `ChatViewModel` (529 lines, 8 parameters) has been split into focused collaborators:

| Component | Responsibility |
|-----------|---------------|
| `ChatViewModel` | Thin orchestrator: message list state, composes sub-components |
| `ChatSessionManager` | Thread CRUD, message persistence, title auto-generation |
| `CommandExecutor` | SSH connection lifecycle, command execution, output formatting, Codex JSONL processing |
| `AuthCallbackBroker` | Bridges sshj synchronous callbacks (host key / password / passphrase) to Compose SharedFlows |
| `SessionExporter` | Session export to file |
| `CancelMessageHelper` | Cancellation message assembly |
| `OutputFormatter` | Truncation, exit code, stderr formatting |
| `CommandComposer` | Shell wrapping, Codex env injection |
| `CodexProgressFormatter` | thread/turn/item progress rendering |
| `ErrorMessageMapper` | SSH error category to user-friendly string |
| `CodexEventMapper` | Maps Codex JSONL events to `MessagePart` subtypes |

---

## 4. MessagePart Model

`ChatMessage.parts: List<MessagePart>` replaces the flat `content: String` field.

| Part Type | SerialName | Purpose |
|-----------|-----------|---------|
| `MessagePart.Command` | `"command"` | Executed command with exit code and timestamp |
| `MessagePart.Stdout` | `"stdout"` | Stdout chunk (streaming-friendly) |
| `MessagePart.Stderr` | `"stderr"` | Stderr chunk |
| `MessagePart.Text` | `"text"` | Plain text / Markdown content |
| `MessagePart.Code` | `"code"` | Fenced code block with language tag |
| `MessagePart.Reasoning` | `"reasoning"` | AI reasoning step (ChainOfThought) |
| `MessagePart.Error` | `"error"` | Structured error with cause chain |
| `MessagePart.Mermaid` | `"mermaid"` | Mermaid diagram definition |

Serialization: kotlinx.serialization polymorphism with `"type"` discriminator. Forward-compatible (`ignoreUnknownKeys = true`).

Backward compatibility: old `content`-only messages auto-migrate to `parts = listOf(Text(content))` on deserialization.

---

## 5. Local Build Prerequisites (Windows)

- If terminal lacks JDK, set first:
  - `JAVA_HOME=d:\Code\Projects\rikka-agent\tmp\jdk17\jdk-17.0.18+8`
  - Add `%JAVA_HOME%\bin` to `Path`
- The above path is verified for Gradle test, lint, and assemble in the current environment.

Common environment troubleshooting:

- `docs/troubleshooting.md`
