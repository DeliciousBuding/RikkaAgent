# ROADMAP -- rikka-agent

> Goal: Chat-style SSH command executor for Android with beautiful message rendering (Mode A: non-interactive exec channel).
>
> Facts/config -> `STATE.md` | History -> `ARCHIVE.md`

## Entry Points

- Spec index: `docs/spec/00-index.md`
- Product plan: `docs/plan.md`
- Security/threat model: `docs/threat-model.md`, `docs/server-hardening.md`
- Repo agent constraints: `AGENTS.md`
- Analysis report: `docs/analysis/analysis-report.md`

## Repository Boundary

- VectorControl docs unrelated to rikka-agent were archived externally on 2026-03-13 to `C:\Users\Ding\docs\vectorcontrol-archive`.

---

## Milestone Overview

| Milestone | Status | Notes |
|-----------|--------|-------|
| M0 Spec freeze | Done | Spec terminology unified, TODO centralized |
| M1 UI skeleton | Done | 8 screens + ProfilesVM/EditorVM/ChatVM all connected to Room |
| M2 Rendering pipeline | Done | Markdown v1 + streaming optimization + CodeCard + Mermaid optional |
| M3 SSH engine | Done | sshj exec + auth + host key + session management + key generation + encrypted storage |
| M4 Codex integration | Done | JSONL parsing + API Key management + exec --json + profile toggle + Markdown rendering |
| M5 Release quality | Done | Server guide + privacy audit + release checklist + output truncation + full test suite |
| i18n | Done | 120+ string resources, Chinese-first, full UI coverage |
| **M6 MessagePart + ViewModel refactor** | **Done** | MessagePart sealed class, ChatViewModel decomposition, HighlightCodeBlock, ReasoningCard, MaterialExpressiveTheme, Lucide icons, Room MIGRATION_4_5 |

---

## M6 Completed (Refactor Phase)

### Data Model

- [x] `MessagePart` sealed class: Command, Stdout, Stderr, Text, Code, Reasoning, Error, Mermaid
- [x] `ChatMessage.parts: List<MessagePart>` replaces flat `content: String`
- [x] Backward-compatible deserialization (old `content` auto-migrates to `parts`)
- [x] Room MIGRATION_4_5: `partsJson` column added, existing content migrated
- [x] `MessagePartConverter` TypeConverter for Room persistence
- [x] `MessagePartTest` unit tests for serialization roundtrip

### ViewModel Decomposition

- [x] `ChatViewModel` reduced to thin orchestrator
- [x] `ChatSessionManager` extracted (thread CRUD, message persistence)
- [x] `CommandExecutor` extracted (SSH execution, output formatting, Codex JSONL)
- [x] `AuthCallbackBroker` extracted (host key/password/passphrase SharedFlow bridging)
- [x] `CodexEventMapper` maps JSONL events to `MessagePart` subtypes

### UI Components

- [x] `MessagePartsBlock` dispatches each `MessagePart` type to dedicated composable
- [x] `HighlightCodeBlock` with IntelliJ MarkdownParser syntax highlighting
- [x] `MarkdownBlock` AST-based Markdown rendering
- [x] `ReasoningCard` collapsible ChainOfThought display
- [x] `ChainOfThought` composable for reasoning step visualization
- [x] `CommandCard` for command parts
- [x] `StreamOutputCard` for stdout/stderr parts
- [x] `ErrorCard` for structured errors
- [x] Lucide Icons adopted across all screens
- [x] `MaterialExpressiveTheme` with AMOLED + dynamic color + extend colors

### Tech Stack Additions

- [x] IntelliJ MarkdownParser 0.7.3 (AST-based Markdown + syntax highlighting)
- [x] Lucide Icons 1.1.0 (`composables:icons-lucide`)
- [x] MaterialExpressiveTheme (Material3 1.3.0)
- [x] Room v5 with proper Migrations (no more `fallbackToDestructiveMigration`)

---

## Next Steps

1. **Storage layer hardening** -- Fix `insertMessage` thread-title-clearing bug; replace `OnConflictStrategy.REPLACE` with upsert; encrypt `codexApiKey` at rest.
2. **SSH runBlocking elimination** -- Replace `runBlocking` calls in `SshjExecRunner.buildVerifier()` with suspend-based callbacks.
3. **Test coverage for core paths** -- `SshjExecRunner` unit tests, `SshConnectionPool` tests, Room DAO tests.
4. **Static analysis** -- Integrate detekt or ktlint.
5. **Markdown link handling** -- Make links clickable, handle image rendering.
6. **Mermaid rendering completion** -- Replace stub with actual WebView-based rendering.

---

## Historical Sprint Records

### 2026-03-14 Sprint (Completed)

All items below are done. See ROADMAP git history for details.

- Connected Android instrumentation tests
- SAF chooser regression tests
- Codex progress UI rendering tests
- CI summary, APK upload, Node24 compatibility
- Instrumentation testing documentation
- Terminology unification (known hosts / complete output / Mode A)
- Spec hygiene and verification checklist
- Architecture docs aligned to implementation
- Troubleshooting, glossary, CI notes docs

### Agent Handoff Snapshot (2026-03-13)

All items below are done.

1. Canceled message semantics (`MessageStatus.Canceled` + `cancelRunning()`)
2. Mermaid toggle + segmented rendering + WebView card + offline fallback
3. README publish-ready refactor (matrix/FAQ/Gallery/Quick Demo)
4. i18n key consistency audit (0 missing in app/core-ui)
5. Codex thread/turn/item progress summary with tests
6. Truncated output CodeCard expand/complete output action row coverage
7. ChatScreen share/export chooser payload test coverage
8. SAF end-to-end picker verification

---

## Verified Build Environment

PowerShell:

```powershell
$env:JAVA_HOME='d:\Code\Projects\rikka-agent\tmp\jdk17\jdk-17.0.18+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Recommended verification:

```powershell
.\gradlew :core:model:testDebugUnitTest :core:ssh:testDebugUnitTest :core:ui:testDebugUnitTest :core:storage:testDebugUnitTest :app:testDevDebugUnitTest --no-daemon
.\gradlew :app:lintDevDebug :app:assembleDevDebug --no-daemon
```
