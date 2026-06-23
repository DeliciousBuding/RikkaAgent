# RikkaAgent -- Product Requirements Document

> Version: 2.0 | Updated: 2026-06-23 | Status: Living

---

## 1. Product Vision

RikkaAgent is a **chat-style SSH command executor** for Android. It reimagines remote server management by rendering SSH command input and output as structured conversation bubbles -- making every interaction as readable, searchable, and shareable as a chat conversation.

The product is adapted from the design language of RikkaHub (5.5k stars on GitHub), one of the most polished Material You Android chat apps. While RikkaHub is an AI chat client, RikkaAgent applies the same UX paradigm to SSH execution: commands are user messages, output is assistant messages, and structured content (code blocks, errors, reasoning steps) each gets its own visual treatment.

**Core value proposition**: Replace the wall of scrolling terminal text with a conversation interface that lets operators understand output at a glance.

---

## 2. Target Users

| User Persona | Age Range | Technical Level | Typical Device | Primary Need |
|---|---|---|---|---|
| **DevOps Engineer** | 25-40 | High | Phone (emergency) | Diagnose production incidents from mobile; execute `docker ps`, `journalctl`, `curl` and read results immediately |
| **System Administrator** | 28-50 | High | Phone / Tablet | Routine server checks (`df -h`, `free -m`, `uptime`) across multiple hosts; share outputs with team |
| **Developer** | 22-35 | High | Phone / Tablet | Trigger remote builds, view logs, run Codex/Claude Code for AI-assisted coding tasks |
| **Tech Enthusiast** | 18-35 | Medium | Phone | Manage home server / NAS / Raspberry Pi; need lightweight but readable SSH client |
| **Linux Learner** | 16-25 | Low-Medium | Phone | Practice Linux commands with a chat interface that lowers the terminal barrier |

---

## 3. Core Features

### P0 -- MVP (v1.0)

#### 3.1 SSH Connection Management

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-CONN-01 | Profile CRUD | Create/edit/delete SSH connection profiles (host, port, username, auth type, key reference) | `SshProfile` data model, `ProfileStore` / `RoomProfileStore` for persistence |
| F-CONN-02 | Authentication chain | Password, public key (RSA/Ed25519), and key+passphrase auth | `SshjExecRunner.authenticate()` delegates to `PasswordProvider`, `KeyContentProvider`, `PassphraseProvider` |
| F-CONN-03 | PuTTY .ppk support | Load PuTTY private key format alongside OpenSSH PEM | `detectPrivateKeyFormat()` + `PuTTYKeyFile` / `OpenSSHKeyFile` in `SshjExecRunner.loadKeyProvider()` |
| F-CONN-04 | Connection reuse | Reuse authenticated SSH session across multiple commands for the same profile | `SshjExecRunner` with `reuseConnections = true`; `SshConnectionPool` for multi-profile pooling |
| F-CONN-05 | Connection test | Validate SSH connectivity from profile editor | TCP connect + SSH banner exchange |
| F-CONN-06 | Profile search/filter | Filter profiles by name, host, or tags | `ProfileSearchFilter` utility |

#### 3.2 Command Execution

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-EXEC-01 | Exec channel | Non-interactive SSH command execution (Mode A); no PTY | `SshExecRunner.run(profile, command): Flow<ExecEvent>` |
| F-EXEC-02 | Streaming output | Real-time stdout/stderr streaming into chat bubbles | 4KB read buffer on `Dispatchers.IO`; `SshOutputMapper` accumulates at paragraph boundaries |
| F-EXEC-03 | Exit code display | Exit code shown with green (0) or red (non-zero) badge after command finishes | `MessagePart.Command(exitCode)` rendered in `CommandPartView` with `CircleCheck`/`X` icon |
| F-EXEC-04 | ANSI stripping | Automatic removal of ANSI escape codes from output | `SshOutputMapper.stripAnsi()` via regex `\x1B\[[0-9;]*[a-zA-Z]` |

#### 3.3 Structured Output (MessagePart)

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-MP-01 | MessagePart sealed class | Eight typed subtypes: Command, Stdout, Stderr, Text, Code, Reasoning, Error, Mermaid | `core:model` -- `MessagePart` with `@Serializable` + `@SerialName("type")` for polymorphic JSON |
| F-MP-02 | Type-specific rendering | Each part subtype gets dedicated Compose composable | `MessagePartsBlock` dispatches via `when (part)` to `CommandPartView`, `StdoutPartView`, `ErrorPartView`, etc. |
| F-MP-03 | Backward compatibility | Legacy `content: String` messages auto-migrate to `parts = listOf(Text(content))` | `ChatMessage` keeps `_content` field; `textContent` accessor provides unified view |
| F-MP-04 | Room persistence | `List<MessagePart>` stored as JSON string in `chat_messages.partsJson` column | `MessagePartConverter` (Room `@TypeConverter`) serializes via `kotlinx.serialization` |

#### 3.4 Chat UI

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-UI-01 | Chat bubbles | User messages right-aligned (primary color), assistant output left-aligned | `ChatBubble` composable with role-based colors (`primaryContainer` / `surface` / `errorContainer`) |
| F-UI-02 | Markdown rendering | Headings, paragraphs, lists, blockquotes, links, inline code, tables, strikethrough | `MarkdownText` via `commonmark-java` 0.22.0 + GFM extensions |
| F-UI-03 | Syntax highlighting | 50+ language code blocks with language tags and highlighted syntax | `HighlightCodeBlock` with language-aware coloring |
| F-UI-04 | Action bar | Copy, rerun, share, full output view per message | `ChatBubble` action row with `CopyButton`, `RerunButton`, `ShareButton`, `ExpandButton` |
| F-UI-05 | Streaming indicator | Animated "Typing..." during execution; linear progress bar in top bar | `TypingIndicator` + `LinearProgressIndicator` |
| F-UI-06 | Output truncation | Output capped at 256KB; truncated content shows "View full output" | `OutputFormatter` with `capChars = 256_000`; `FullOutputDialog` for complete view |
| F-UI-07 | Multi-line input | Multi-line text input with Enter-to-send, monospace font | `ChatInput` composable |
| F-UI-08 | Session persistence | All messages persisted to Room; thread list with history | `ChatRepository` + `RoomChatRepository` |

#### 3.5 Security

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-SEC-01 | Known hosts (TOFU) | First connection: show fingerprint, require explicit confirmation; subsequent: verify match | `HostKeyPolicy.TrustFirstUse`; `DataStoreKnownHostsStore` for persistence |
| F-SEC-02 | Host key mismatch warning | Stored fingerprint differs from server -- block with high-risk warning + secondary confirmation | `HostKeyEvent.Mismatch` triggers `HostKeyDialog` then `HostKeyReplacementConfirmDialog` |
| F-SEC-03 | Encrypted key storage | App-managed private keys encrypted at rest via AndroidX Security Crypto | `EncryptedInternalKeyStore` (AES-256-GCM-HKDF-4KB with Android Keystore master key) |
| F-SEC-04 | Password in memory only | SSH passwords and passphrases never persisted; requested via UI callback each connection | `PasswordProvider` / `PassphraseProvider` interfaces; values held as `String` in memory only |
| F-SEC-05 | Command injection protection | Shell special characters escaped in `CommandComposer` | `shellQuote()` replaces `'` with `'\''`; `wrapForCodex()` escapes `"` for task string |

#### 3.6 i18n

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-I18N-01 | Chinese-first bilingual UI | Mandarin Chinese as default locale, English as fallback; 120+ string resources | `values/strings.xml` (en) + `values-zh/strings.xml` (zh-CN) |
| F-I18N-02 | Zero hardcoded strings | All user-visible strings in `@string` resources, no hardcoded text in Compose | CI check via `lintDevDebug` |

### P1 -- Enhancement (v1.1)

| ID | Feature | Description | Implementation |
|---|---|---|---|
| F-CODEX-01 | Profile-level Codex toggle | Per-profile enable/disable Codex mode | `SshProfile.codexMode` boolean field |
| F-CODEX-02 | Codex command wrapping | Auto-wrap user input as `codex exec --json --full-auto` | `CommandComposer.wrapForCodex()` |
| F-CODEX-03 | JSONL streaming | Parse Codex JSONL event stream into structured MessageParts | `JsonlLineBuffer` + `JsonlParser` + `CodexEventMapper` |
| F-CODEX-04 | ChainOfThought display | Collapsible reasoning step cards for Codex AI reasoning | `ReasoningCard` / `ChainOfThought` composable in `MessagePartsBlock` |
| F-CODEX-05 | Progress summary | Real-time thread/turn/item progress from Codex JSONL events | `CodexProgressFormatter` parses `type` fields (`thread.*`, `turn.*`, `item.*`) |
| F-CODEX-06 | Environment injection | `OPENAI_API_KEY` injected into remote environment via shell prefix | `CommandComposer.wrapForCodex()` adds `envPart` |
| F-MERMAID-01 | Mermaid rendering | Auto-detect and render Mermaid diagrams from output (feature-gated) | `MermaidDiagramCard` with WebView; `AppPreferences.enableMermaid` toggle |
| F-SESSION-01 | Session export | Export whole session as Markdown plain text via `Intent.ACTION_SEND` | `SessionExporter.export()`, `ShareIntents.sessionExport()` |
| F-RENDER-01 | Output truncation + expand | 256KB threshold with "View complete output" action | `OutputFormatter.format()` returns `FormattedOutput(display, full, truncated)` |

### P2 -- Future (v2.0)

| ID | Feature | Description |
|---|---|---|
| F-PTY-01 | PTY terminal support | Optional pseudo-terminal for interactive programs (vim, top, htop) |
| F-PTY-02 | Terminal resize | Dynamic PTY size negotiation |
| F-MULTI-AGENT-01 | Multiple AI backends | Support Claude Code, Aider, and other AI CLI tools via `AgentRunner` abstraction |
| F-PROFILE-01 | Profile import/export | JSON batch import/export (excluding keys) |
| F-PROFILE-02 | Profile grouping | Group profiles by project or environment |
| F-TABLET-01 | Dual-pane layout | Landscape tablet with persistent profile list + chat side-by-side |
| F-SEARCH-01 | Full-text search | Search within session output |

---

## 4. Non-Goals (v1 Scope)

The following are explicitly out of scope for v1.0:

| Area | Non-Goal | Rationale |
|---|---|---|
| **AI model integration** | No built-in AI model, no LLM inference, no API calls to AI providers | RikkaAgent is SSH-only; Codex integration is a remote tool invocation, not local AI |
| **PTY / terminal emulation** | No interactive programs (vim, top, htop, cursor control) | Mode A design decision -- clean exec channel avoids terminal escape complexity |
| **HTTP remote-exec relay** | No server-side HTTP proxy for SSH | Attack surface reduction; SSH is direct from Android to target host |
| **File transfer** | No SCP/SFTP | Scope boundary -- command executor only |
| **Port forwarding** | No SSH tunnel or port forwarding | Requires PTY or channel management beyond exec-only |
| **Root/jailbreak detection** | No device integrity checks | Android Keystore provides hardware binding; root is accepted residual risk |
| **Auto-backup encryption** | No SQLCipher database encryption | Accepts plaintext DB as trade-off for Room simplicity; key material separately encrypted |
| **Multi-device sync** | No cloud sync of profiles or sessions | Privacy-by-design: all data stays on device |

---

## 5. Success Metrics

### 5.1 Connection Reliability

| Metric | Target | Measurement |
|---|---|---|
| SSH connection success rate | > 98% (reachable hosts) | CI integration test with mock SSH server |
| Connection establishment (LAN) | < 3s | Profiling from `connect()` to authenticated |
| Connection establishment (WAN) | < 8s | Manual measurement on production profile |
| Connection reuse hit rate | > 80% of consecutive commands | `SshConnectionPool` hit/miss logging |
| Auth retry rate (password) | < 5% of connections | Count of password re-entry flows |

### 5.2 Output Rendering Quality

| Metric | Target | Measurement |
|---|---|---|
| Markdown render accuracy | 100% of GFM spec elements | `MarkdownText` unit test coverage |
| Syntax highlighting languages | 50+ languages | `HighlightCodeBlock` language registry |
| ANSI stripping correctness | 100% of common escape sequences | `AnsiStripper` / `SshOutputMapper` unit tests |
| Truncation threshold | 256KB (256,000 chars) | `OutputFormatter.format()` test |
| Streaming frame rate | >= 60fps during streaming | Compose frame rate monitoring |

### 5.3 MessagePart Model Coverage

| Metric | Target | Measurement |
|---|---|---|
| MessagePart subtypes | 8 types (Command, Stdout, Stderr, Text, Code, Reasoning, Error, Mermaid) | Code inspection |
| Backward compatibility | Legacy `content` messages render correctly | `ChatModelsTest` round-trip |
| Serialization round-trip | 100% of subtypes survive JSON encode/decode | `MessagePart` serialization tests |

### 5.4 Security Posture

| Metric | Target | Measurement |
|---|---|---|
| Zero plaintext credentials | No password/passphrase persisted, keys encrypted | `EncryptedInternalKeyStore` audit |
| Host key verification | 100% of connections verify; mismatch blocks + requires explicit confirmation | `HostKeyDialogStateMachineTest` |
| Command injection | Zero shell escape bypasses in `CommandComposer` | `CommandComposerTest` with edge cases |
| Log sanitization | No credentials in logcat output | Manual debug build inspection |

---

## 6. Architecture Philosophy

RikkaAgent follows **Clean Architecture** with five modules:

```
:app            -> Application shell: DI, ViewModel, Navigation
:core:model     -> Domain models (pure Kotlin, zero Android dependencies)
:core:ssh       -> SSH execution: connection, auth, host-key verification, JSONL parsing
:core:storage   -> Persistence: Room v5 database, DataStore preferences
:core:ui        -> Compose components: ChatBubble, MessagePartsBlock, Theme, MarkdownRenderer
```

Key architectural decisions:
- **Structured message model**: `MessagePart` sealed class replaces monolithic `content: String`, enabling type-specific rendering for SSH output, code blocks, errors, and AI reasoning steps
- **Exec-only (Mode A)**: Non-interactive SSH exec channel; no PTY in v1
- **TOFU host-key verification**: Trust On First Use with explicit mismatch warnings
- **Encrypted key storage**: Android Keystore-backed AES-256-GCM for app-managed private keys
- **No HTTP relay**: Direct SSH from device to target; no server-side proxy

---

## 7. Competitive Landscape

| Dimension | RikkaAgent | Termux | ConnectBot | JuiceSSH |
|---|---|---|---|---|
| Paradigm | Chat bubbles | Terminal emulator | Terminal emulator | Terminal emulator |
| Positioning | Chat-first SSH runner | Local Linux environment | Lightweight SSH client | Commercial SSH client |
| PTY support | No (design decision) | Full | Full | Full |
| Output readability | High (Markdown + syntax highlight + code folding) | Low (plain text) | Low (plain text) | Medium (basic ANSI color) |
| AI integration | Codex / Claude Code (first-class) | None (manual install) | None | None |
| Mermaid rendering | Yes | No | No | No |
| Session export | Markdown / plain text | No | No | Limited |
| Key storage | Android Keystore encrypted | Filesystem | Android Keystore | Android Keystore |
| Host key verification | TOFU + explicit confirm | TOFU | TOFU + explicit confirm | Weak (auto-accept) |
| License | Apache-2.0 | GPL-3.0 | Apache-2.0 | Partially open |
| Minimum API | 24 | 24 | 21 | 21 |

---

## 8. Release Roadmap

| Version | Theme | Features |
|---|---|---|
| **v1.0** | Core SSH + Chat MVP | SSH exec, chat UI, Profile CRUD, Markdown rendering, TOFU host keys, encrypted key storage, i18n, 3 themes |
| **v1.1** | Codex + Rendering | Codex integration, ChainOfThought, Mermaid, session export, output truncation |
| **v2.0** | Platform Evolution | PTY support, multi-agent runner, profile import/export, full-text search, tablet layout |

---

## Appendix A: Terminology

| Term | Definition |
|---|---|
| **Exec Channel** | SSH protocol non-interactive command channel; no PTY allocated |
| **PTY** | Pseudo-terminal for interactive programs (cursor control, vim, top) |
| **TOFU** | Trust On First Use -- host key verification model |
| **JSONL** | JSON Lines -- one JSON object per line, used by Codex for streaming |
| **Mode A** | RikkaAgent's exec-only interaction mode (non-interactive) |
| **MessagePart** | Typed sealed class: Command, Stdout, Stderr, Text, Code, Reasoning, Error, Mermaid |
| **ChainOfThought** | Collapsible AI reasoning step display |
| **Profile** | A set of SSH connection parameters (host, port, user, auth) |

## Appendix B: Related Documents

| Document | Path |
|---|---|
| Architecture | [architecture.md](architecture.md) |
| Security Review | [security-review.md](security-review.md) |
| Threat Model | [threat-model.md](threat-model.md) |
| Privacy Audit | [privacy-audit.md](privacy-audit.md) |
| Spec Index | [spec/00-index.md](spec/00-index.md) |
| Use Cases | [design-use-cases.md](design-use-cases.md) |
