# PTY Interactive Mode Support — Design Document

> Status: **Proposed** (not scheduled)
> Author: agent-generated, 2026-06-23
> Scope: RikkaAgent v1.x / v2 feasibility analysis

---

## 1. Current Mode A (exec channel) — Limitations

### What Mode A Does

Mode A opens an SSH `exec` channel (`session.exec(command)` in sshj), streams
stdout and stderr as raw byte chunks, captures the exit code, and renders the
result inside a chat bubble. This is implemented in `SshjExecRunner` (lines 138–164)
and surfaced through the `ExecEvent` sealed class.

### Limitations

| # | Limitation | Impact |
|---|-----------|--------|
| L1 | **No PTY allocation** — the remote shell has no controlling terminal. | Programs that check `isatty()` behave differently: `ls` defaults to one-column output, `git log` has no pager, color is off. |
| L2 | **No stdin after command start** — `session.exec()` is fire-and-forget on input. | Interactive programs (`sudo` password prompt, `mysql` REPL, `python` REPL) are impossible. |
| L3 | **No terminal size negotiation** — no `winsize` is communicated to the remote. | Programs that query `$COLUMNS`/`$LINES` (top, less, vim) get defaults or fail. |
| L4 | **No ANSI escape code handling** — raw bytes are rendered as plain text. | Programs that emit ANSI sequences (colors, cursor movement) produce garbled output. |
| L5 | **No signal forwarding** — closing the exec channel is the only cancellation. | Cannot send `Ctrl-C`, `Ctrl-Z`, `SIGWINCH`. |
| L6 | **No environment variable injection** — `TERM`, `LANG`, `SHELL` are unset. | Some programs refuse to start or fall back to dumb mode. |

The current spec explicitly acknowledges these as non-goals for v1
(`docs/spec/33-remote-exec.md`: "Interactive shells / full-screen apps /
ANSI terminal UX").

---

## 2. PTY Mode Technical Design

### 2.1 SSH Protocol Layer

sshj provides `Session.startShell()` which allocates a PTY on the remote side
and opens a bidirectional shell channel. The key API surface:

```
val session: Session = client.startSession()
val shell: Session.Shell = session.startShell()
// shell.inputStream  — remote stdout (OutputStream to write stdin)
// shell.outputStream — remote stdout (InputStream to read)
// shell.errorStream  — remote stderr (InputStream)
// shell.allocatePTY(type, cols, rows, width, height, modes)
```

PTY allocation must happen **before** `startShell()`:

```
session.allocatePTY("xterm-256color", cols, rows, 0, 0, PTYModes.EMPTY)
```

### 2.2 New Interface: `SshPtyRunner`

```kotlin
interface SshPtyRunner {
  /**
   * Open an interactive PTY session.
   *
   * @param profile   SSH connection profile
   * @param cols      initial terminal columns (default 80)
   * @param rows      initial terminal rows (default 24)
   * @param term      TERM value (default "xterm-256color")
   * @return a bidirectional flow:
   *   - downstream: PtyEvent from the remote
   *   - upstream:   PtyInput written by the UI
   */
  fun open(profile: SshProfile, cols: Int = 80, rows: Int = 24, term: String = "xterm-256color"): PtySession
}

class PtySession(
  val events: Flow<PtyEvent>,     // remote → UI
  val input: suspend (PtyInput) -> Unit,  // UI → remote
  val resize: suspend (cols: Int, rows: Int) -> Unit,  // SIGWINCH
  val close: suspend () -> Unit,
)

sealed class PtyEvent {
  data class Output(val bytes: ByteArray) : PtyEvent()  // combined stdout
  data class Exit(val code: Int?) : PtyEvent()
  data class Error(val category: String, val message: String) : PtyEvent()
  data object Closed : PtyEvent()
}

sealed class PtyInput {
  data class Bytes(val data: ByteArray) : PtyInput()
  data class CtrlKey(val code: Int) : PtyInput()  // e.g. Ctrl-C = 0x03
}
```

### 2.3 Implementation Sketch: `SshjPtyRunner`

```kotlin
class SshjPtyRunner(
  private val knownHostsStore: KnownHostsStore,
  private val hostKeyCallback: HostKeyCallback,
  // ... same auth providers as SshjExecRunner
) : ClosableSshPtyRunner {

  override fun open(profile: SshProfile, cols: Int, rows: Int, term: String): PtySession {
    // 1. Acquire SSHClient (reuse SshjExecRunner's acquireClient logic)
    // 2. Start session, allocate PTY
    // 3. Start shell
    // 4. Launch coroutine to read outputStream → PtyEvent.Output
    // 5. Return PtySession with input/resize/close lambdas
  }
}
```

Key differences from `SshjExecRunner`:

| Aspect | Mode A (exec) | PTY mode (shell) |
|--------|--------------|------------------|
| Channel open | `session.exec(cmd)` | `session.allocatePTY(...)` + `session.startShell()` |
| stdin | none after start | continuous `shell.outputStream.write()` |
| stdout+stderr | two separate streams | merged into one stream (PTY merges them) |
| resize | N/A | `session.sendWindowChange(cols, rows)` |
| exit | `cmd.exitStatus` after join | `shell.join()` + exit status |

### 2.4 Connection Reuse

PTY sessions are long-lived (user keeps terminal open). Connection pooling
must change:

- Mode A: pool `SSHClient` per profile, open/close exec channels freely.
- PTY mode: one `SSHClient` → one PTY session at a time per profile.
  Concurrent exec channels (Mode A) can coexist on the same `SSHClient`
  because SSH multiplexes channels.

Strategy: `SshConnectionPool` returns a shared `SSHClient`. Mode A opens
exec channels on it. PTY mode opens a shell channel on it. Both can run
concurrently because sshj supports multiple channels per connection.

---

## 3. Terminal Emulation Requirements

### 3.1 Why We Need a Terminal Emulator

Remote programs assume a terminal. Without emulation:

- `\r\n` vs `\n` handling is wrong.
- ANSI escape sequences (`\e[31m` red, `\e[1;1H` cursor home) render as garbage.
- Programs that use cursor positioning (progress bars, spinners, `htop`) are unusable.
- Backspace/delete key handling differs.

### 3.2 Minimal Viable Emulator

For v1.x PTY support, we need a **minimal VT100/xterm subset**:

| Feature | Priority | Notes |
|---------|----------|-------|
| Basic text output | P0 | Print characters, handle `\r`, `\n`, `\t` |
| SGR (colors, bold, italic, underline) | P0 | `\e[...m` — map to Compose `AnnotatedString` spans |
| Cursor movement (up/down/left/right, home) | P1 | `\e[nA/B/C/D`, `\e[H` — needed for progress bars |
| Erase (line, screen) | P1 | `\e[2J`, `\e[K` — needed for `git push` progress |
| Scroll regions | P2 | `\e[r` — needed by `less`, `man` |
| Alternate screen buffer | P2 | `\e[?1049h/l` — `vim`, `htop`, `less` |
| Mouse tracking | P3 | `\e[?1000h` — optional, for `mc`, `htop` mouse support |

### 3.3 Library Options

| Option | Pros | Cons |
|--------|------|------|
| **Custom Kotlin emulator** | Full control, minimal deps, Compose-native output | Significant effort (VT100 is non-trivial), maintenance burden |
| **JTerm (JVM terminal emulator)** | Existing VT100 implementation | Designed for Swing/AWT, needs Compose adapter; maturity unclear |
| **Telnet/SSH lib with built-in terminal** | None found with good Android + Kotlin support | — |
| **WebView + xterm.js** | Battle-tested terminal emulator | WebView overhead, security surface, IPC complexity |
| **Minimal ANSI parser (custom)** | Strips/colors only, no cursor control | Covers P0 features only; insufficient for full-screen apps |

**Recommendation:** Start with a **minimal ANSI parser** for P0 features (colors + basic text), then build cursor movement (P1) incrementally. Full VT100 emulation (P2+) is a separate effort and may justify using xterm.js in a WebView for "advanced terminal" mode.

### 3.4 ANSI → Compose Mapping

```kotlin
// Parsed ANSI state → AnnotatedString spans
data class TerminalLine(
  val spans: List<StyledSpan>,
  val attributes: LineAttributes,  // dim, reverse, etc.
)

data class StyledSpan(
  val text: String,
  val foreground: Color?,   // null = default
  val background: Color?,
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val dim: Boolean = false,
)
```

The emulator maintains a **screen buffer** (2D grid of cells) and a **cursor
position.** On each batch of incoming bytes, it updates the buffer and emits
a snapshot for the UI to render.

---

## 4. UI Adaptation

### 4.1 Two Rendering Modes

The app needs two distinct rendering surfaces:

| Mode | Rendered as | Use case |
|------|-------------|----------|
| **Chat bubble** (Mode A) | Code block in chat timeline | `ls`, `docker ps`, one-shot commands |
| **Terminal view** (PTY) | Full-screen or inline terminal widget | Interactive shells, `vim`, `htop`, REPLs |

### 4.2 Terminal View Component

```
┌─────────────────────────────────┐
│ [Profile] bash - connected  [×] │  ← header bar
├─────────────────────────────────┤
│ user@host:~$ ls                 │
│ file1.txt  file2.txt            │  ← terminal canvas
│ user@host:~$ █                  │     (Compose Canvas or
│                                 │      LazyColumn of TerminalLine)
│                                 │
├─────────────────────────────────┤
│ [Ctrl] [Tab] [↑] [↓] [←] [→]  │  ← virtual keyboard toolbar
└─────────────────────────────────┘
```

Implementation options:

1. **Compose Canvas** — render the cell grid directly onto a `Canvas`
   composable. Best for performance, full control over cell placement.
   Requires implementing text measurement and hit-testing.

2. **LazyColumn of AnnotatedString** — each terminal row is a `Text`
   composable with `AnnotatedString` spans. Simpler implementation, but
   recomposition overhead for rapid updates (scrolling `htop`).

3. **WebView + xterm.js** — delegate all terminal rendering to a WebView.
   Best fidelity, worst integration with Compose navigation and theming.

**Recommendation:** Option 1 (Compose Canvas) for the primary terminal view.
It avoids recomposition overhead, supports precise cursor rendering, and
integrates naturally with the app's theming.

### 4.3 Virtual Keyboard Toolbar

Mobile keyboards lack terminal-critical keys. A toolbar row provides:

- **Ctrl** toggle (sends Ctrl+next key)
- **Tab** key
- **Arrow keys** (up/down/left/right)
- **Escape** key
- **Pipe** `|`, **Redirect** `>`, **Backtick** `` ` `` shortcuts

### 4.4 Resize Handling

When the terminal view resizes (orientation change, split-screen,
soft keyboard appear/disappear):

1. Measure new pixel dimensions.
2. Calculate cols/rows from font metrics: `cols = pixelWidth / charWidth`.
3. Call `ptySession.resize(cols, rows)`.
4. Remote shell receives `SIGWINCH` and redraws.

### 4.5 Mode Switching

Users should not have to choose mode manually. Detection heuristics:

| Signal | Mode |
|--------|------|
| Command returns immediately (exit < 1s) | Chat bubble |
| Command is known interactive (`bash`, `python`, `mysql`, `ssh`) | Offer PTY |
| Remote sends ANSI escape sequences | Auto-switch to terminal view |
| User explicitly requests "Open terminal" | PTY mode |

A manual toggle should always be available: the input bar can offer
"Run in terminal" as a checkbox or long-press action.

---

## 5. Coexistence Strategy: exec + PTY

### 5.1 Architecture

```
                   ┌──────────────────────┐
                   │   SshConnectionPool   │
                   │  (shared SSHClient)   │
                   └──────┬───────┬───────┘
                          │       │
              ┌───────────┘       └───────────┐
              │                               │
    ┌─────────▼─────────┐         ┌───────────▼───────────┐
    │  SshjExecRunner    │         │   SshjPtyRunner       │
    │  (Mode A: exec)    │         │   (Mode B: PTY)       │
    │  Flow<ExecEvent>   │         │   PtySession          │
    └─────────┬─────────┘         └───────────┬───────────┘
              │                               │
    ┌─────────▼─────────┐         ┌───────────▼───────────┐
    │  Chat Bubble       │         │  Terminal View        │
    │  ViewModel         │         │  ViewModel            │
    └───────────────────┘         └───────────────────────┘
```

### 5.2 Rules

1. **Both modes share the same `SSHClient`** from `SshConnectionPool`. SSH
   multiplexes channels, so an exec channel and a shell channel can coexist
   on one TCP connection.

2. **PTY sessions are one-at-a-time per profile.** Opening a second PTY
   session on the same profile should either close the first one or be
   blocked with a message ("Terminal already open").

3. **Exec commands can run concurrently with an open PTY session.** The user
   can type a quick `uptime` in the chat input while a terminal session is
   active. The exec result appears as a chat bubble; the terminal is
   unaffected.

4. **Lifecycle:** PTY sessions must be closed when the user navigates away
   from the terminal view (or the app goes to background) to avoid orphaned
   remote shells. A "keep alive in background" option can be added later.

### 5.3 Event Model Extension

```kotlin
// Mode A events (existing, unchanged)
sealed class ExecEvent {
  data class StdoutChunk(val bytes: ByteArray) : ExecEvent()
  data class StderrChunk(val bytes: ByteArray) : ExecEvent()
  data class StructuredEvent(val kind: String, val rawJson: String) : ExecEvent()
  data class Exit(val code: Int?) : ExecEvent()
  data object Canceled : ExecEvent()
  data class Error(val category: String, val message: String) : ExecEvent()
}

// PTY events (new)
sealed class PtyEvent {
  data class Output(val bytes: ByteArray) : PtyEvent()   // merged stream
  data class Exit(val code: Int?) : PtyEvent()
  data class Error(val category: String, val message: String) : PtyEvent()
  data object Closed : PtyEvent()
}
```

No changes to `ExecEvent` — the two event streams are independent.

---

## 6. Priority Assessment

### 6.1 Effort Estimate

| Component | Effort | Notes |
|-----------|--------|-------|
| `SshjPtyRunner` | 2–3 days | Thin wrapper, reuses auth/hostkey infra |
| Minimal ANSI parser (P0: colors + text) | 3–5 days | State machine, SGR mapping |
| Cursor movement (P1) | 3–5 days | Screen buffer, erase sequences |
| Compose Canvas terminal view | 5–7 days | Cell grid, cursor blink, selection |
| Virtual keyboard toolbar | 1–2 days | Key buttons, Ctrl toggle |
| Resize handling | 1–2 days | Measure + SIGWINCH |
| Mode detection / switching | 2–3 days | Heuristics + manual toggle |
| Tests (unit + integration) | 3–5 days | Mock PTY server, ANSI parser tests |
| **Total (P0+P1)** | **~20–30 days** | Does not include full VT100 |

### 6.2 User Value Analysis

**High value scenarios unlocked by PTY:**

- Interactive shell sessions (`bash`, `zsh`) — the single most requested
  feature after basic exec.
- `sudo` commands that require password input.
- REPL access (`python`, `node`, `irb`) for quick server-side prototyping.
- `git rebase -i`, `git add -p` — interactive git workflows.
- Database CLIs (`psql`, `mysql`, `redis-cli`).

**Low value / deferred:**

- Full-screen TUI apps (`vim`, `htop`, `mc`) — these need P2 terminal
  features (alternate screen, scroll regions) and are better served by a
  dedicated terminal app or SSH client.
- Mouse tracking — niche, high complexity.

### 6.3 Recommendation

**Do NOT include PTY in v1.0.** Ship v1.0 with Mode A only (exec channel).
The exec channel covers 80% of the "run a command on my server" use case
and is much simpler to test, secure, and polish.

**Include minimal PTY (P0+P1) in v1.x** as a fast-follow after v1.0 ships.
Target:

- Minimal ANSI parser (colors, basic text).
- Compose Canvas terminal view with virtual keyboard toolbar.
- Manual "Open terminal" toggle (no auto-detection yet).
- No full-screen TUI apps (documented limitation).

**Rationale:**

- Interactive shell access is the natural next step after "run commands."
- The effort is bounded (~3 weeks) and mostly independent of Mode A code.
- Full VT100 emulation (for vim/htop) is a separate v2+ effort and should
  not block the initial PTY release.

---

## 7. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| ANSI parser bugs cause garbled output | Medium | Comprehensive test suite with real-world escape sequences from common tools |
| PTY session leaks (orphaned remote shells) | High | Close PTY on view destroy; implement keepalive timeout; document behavior |
| Connection pool contention between Mode A and PTY | Medium | SSH channel multiplexing is well-defined; test concurrent usage |
| Compose Canvas performance for rapid updates (htop-like) | Medium | Batch updates at 30fps cap; skip frames if behind; benchmark early |
| Security: PTY exposes interactive shell → wider attack surface | Medium | Same auth/hostkey model as Mode A; no additional credential handling; document that PTY gives full shell access |

---

## 8. Related Specs

- `docs/spec/32-ssh.md` — SSH transport (Mode A)
- `docs/spec/33-remote-exec.md` — Remote execution (Mode A)
- `docs/spec/30-architecture.md` — Module structure and interfaces
- `docs/spec/23-rendering.md` — Output rendering (chat bubbles)
- `docs/spec/29-interaction.md` — Gesture and input behavior
- `docs/spec/20-ux.md` — UX flows

This document does NOT modify any existing spec. It is a standalone design
proposal for future implementation.
