# RikkaAgent Architecture Document

> Updated: 2026-06-23 | Status: Living document -- reflects current codebase state

---

## 1. Clean Architecture Overview

RikkaAgent follows a five-module Clean Architecture with strict dependency direction:

```text
:app  ────> :core:model
:app  ────> :core:ui    ────> :core:model
:app  ────> :core:ssh   ────> :core:model
:app  ────> :core:storage ──> :core:model
```

- `:app` is the sole convergence point; `core:*` modules never depend on each other
- `:core:model` has zero Android framework dependencies (pure Kotlin + kotlinx.serialization)
- `:core:storage` uses Room (Android dependency) but exposes only suspend/Flow interfaces

### 1.1 Module Responsibilities

| Layer | Module | Responsibility | Key Android Deps |
|---|---|---|---|
| **Presentation** | `:app` (screens), `:core:ui` (components) | Render ChatMessage into Compose; collect StateFlow/SharedFlow from ViewModel | Compose, Material3, Navigation |
| **Orchestration** | `:app:vm` | Coordinate SSH execution lifecycle, thread management, auth callbacks | ViewModel, Lifecycle |
| **Data** | `:core:storage` | Persist profiles, threads, messages, preferences | Room, DataStore |
| **Infrastructure** | `:core:ssh` | SSH connection, exec, host-key verification, JSONL parsing | sshj (net.schmizz) |
| **Domain** | `:core:model` | Pure Kotlin data classes: MessagePart, ChatMessage, SshProfile | None |

### 1.2 Module Source Layout

```
rikka-agent/
  settings.gradle.kts          # includes :app, :core:model, :core:ui, :core:ssh, :core:storage
  build.gradle.kts
  app/
    build.gradle.kts
    src/main/java/io/rikka/agent/
      MainActivity.kt           # entry point: Compose setContent + theme wiring
      RikkaAgentApp.kt          # Application class: Koin initialization
      di/AppModule.kt           # Koin: database, repositories, SSH deps
      di/ViewModelModule.kt     # Koin: ViewModel constructors
      nav/Screen.kt             # Type-safe route definitions
      nav/AppNavHost.kt         # NavHost with 6 screens
      vm/ChatViewModel.kt       # SSH execution orchestrator
      vm/OutputFormatter.kt     # Output truncation + formatting
      vm/CommandComposer.kt     # Shell/Codex command wrapping
      vm/ErrorMessageMapper.kt  # SSH error -> user-friendly message
      vm/CancelMessageHelper.kt # Canceled message assembly
      vm/SessionExporter.kt     # Session -> Markdown export
      vm/CodexProgressFormatter.kt  # Codex thread/turn/item progress
      ssh/ContentUriKeyContentProvider.kt  # Encrypted key storage
      ssh/DataStoreKnownHostsStore.kt      # Known hosts persistence
      ssh/KnownHostEndpointParser.kt
      ssh/KnownHostsEntryCodec.kt
      ui/screen/ChatScreen.kt              # Main chat session screen
      ui/screen/ProfilesScreen.kt          # Profile list
      ui/screen/ProfileEditorScreen.kt     # Profile create/edit
      ui/screen/SettingsScreen.kt          # App settings
      ui/screen/KnownHostsScreen.kt        # Known hosts viewer
      ui/screen/AboutScreen.kt             # About / OSS licenses
  core/
    model/src/main/kotlin/io/rikka/agent/model/
      MessagePart.kt      # Sealed class: 8 subtypes
      ChatModels.kt       # ChatMessage, ChatThread, ChatRole, MessageStatus
      SshProfile.kt       # SshProfile, AuthType, HostKeyPolicy
    ssh/src/main/kotlin/io/rikka/agent/ssh/
      SshInterfaces.kt            # SshExecRunner, ExecEvent
      SshExecRunnerFactory.kt     # Factory + DefaultSshExecRunnerFactory
      SshjExecRunner.kt           # sshj-backed SSH exec implementation
      SshConnectionPool.kt        # Connection pool (reuse)
      SshOutputMapper.kt          # ExecEvent -> MessagePart
      SshKeyGenerator.kt          # Ed25519 key pair generation
      JsonlParser.kt              # JSONL stream parser
      KnownHostsStore.kt          # KnownHostsStore + HostKeyCallback + StoredHostKey
      InMemoryKnownHostsStore.kt  # Test fake
    storage/src/main/kotlin/io/rikka/agent/storage/
      ChatRepository.kt    # ChatRepository interface + RoomChatRepository
      ProfileStore.kt      # ProfileStore interface + RoomProfileStore
      AppPreferences.kt    # DataStore-backed preferences
      db/AppDatabase.kt           # Room DB v5
      db/ChatEntities.kt          # ChatThreadEntity, ChatMessageEntity
      db/SshProfileEntity.kt      # SshProfileEntity
      db/SshProfileDao.kt         # Profile DAO
      db/ChatMessageDao.kt        # Thread + message DAO
      db/MessagePartConverter.kt  # TypeConverter: List<MessagePart> <-> JSON
      db/Mappers.kt               # Entity <-> Model mapping
    ui/src/main/kotlin/io/rikka/agent/ui/
      RikkaAgentTheme.kt          # Theme (Light/Dark/Amoled) + Typography + Shapes
      theme/ExtendColors.kt       # 50 semantic color slots
      components/ChatBubble.kt           # Main chat bubble
      components/MessagePartsBlock.kt    # MessagePart type dispatcher
      components/ChatInput.kt            # Text input
      components/MarkdownText.kt         # Markdown renderer
      components/MarkdownBlock.kt        # Markdown block renderer
      components/HighlightCodeBlock.kt   # Syntax-highlighted code
      components/CodeCard.kt             # Collapsible code block
      components/MermaidDiagramCard.kt   # Mermaid diagram (WebView)
      components/MermaidSupport.kt       # Mermaid utilities
      components/MeshGradientBackground.kt  # Animated background
      util/AnsiStripper.kt               # ANSI escape code remover
```

---

## 2. Data Flow: SSH Exec -> MessagePart -> ChatBubble

### 2.1 Primary Execution Flow

```
User types command in ChatInput
  |
  v
ChatViewModel.send(text)
  |-- Creates user ChatMessage (role=User, status=Final)
  |-- Persists user message via ChatRepository
  |-- Creates assistant seed ChatMessage (role=Assistant, status=Streaming)
  |-- Creates SshOutputMapper, begins tracking command
  |-- Gets or creates SshjExecRunner (via runnerFactory)
  |-- Wraps command: wrapWithShell() or wrapForCodex()
  |-- Launches coroutine to collect Flow<ExecEvent>
  |
  v
SshjExecRunner.run(profile, command): Flow<ExecEvent>
  |-- acquireClient(): connect + authenticate (or reuse cached connection)
  |-- client.startSession() -> session.exec(command)
  |-- Concurrent reads: stdout (IO) + stderr (IO)
  |-- Emits:
       ExecEvent.StdoutChunk(ByteArray)
       ExecEvent.StderrChunk(ByteArray)
       ExecEvent.Exit(code)
       ExecEvent.Canceled
       ExecEvent.Error(category, message)
  |
  v
ChatViewModel collects ExecEvent
  |
  |-- StdoutChunk -> SshOutputMapper.onStdout() -> List<MessagePart>
  |                 (ANSI stripped, paragraph-boundary flushing)
  |                 -> appendPartsToMessage() -> UI recomposition
  |-- StderrChunk -> SshOutputMapper.onStderr() -> MessagePart.Stderr
  |                 -> appendPartsToMessage()
  |-- Exit(code) -> SshOutputMapper.onExit() -> MessagePart.Command(exitCode)
  |                 -> updateAssistantMessage(status=Final)
  |                 -> persistUpdate()
  |
  v
ChatBubble renders ChatMessage
  |-- User message: Text in primaryContainer bubble, right-aligned
  |-- Assistant message (has parts): MessagePartsBlock dispatches
       |-- Text -> MarkdownText or plain Text
       |-- Command -> CommandPartView (monospace + exit code badge)
       |-- Stdout -> StdoutPartView (monospace)
       |-- Stderr -> StderrPartView (monospace, error color)
       |-- Code -> CodePartView (code block with language tag)
       |-- Reasoning -> ReasoningPartView (collapsible)
       |-- Error -> ErrorPartView (error icon + message)
       |-- Mermaid -> MermaidPartView (diagram or source fallback)
  |-- Action row: copy, rerun, share, expand, share-full
```

### 2.2 Codex JSONL Flow (Codex Mode)

```
ChatViewModel.send(text) with profile.codexMode = true
  |-- CommandComposer.wrapForCodex(task, workDir, apiKey)
  |     Result: "cd /workdir && OPENAI_API_KEY='sk-...' codex exec --json --full-auto \"task\""
  |-- Creates JsonlLineBuffer for JSONL stream parsing
  |
  v
ExecEvent.StdoutChunk flows in
  |-- JsonlLineBuffer.feed(bytes): buffer + split on newlines
  |-- For each complete line: JsonlParser.tryParse()
  |     |-- Valid JSON -> ExecEvent.StructuredEvent(kind, rawJson)
  |     |-- Invalid   -> ExecEvent.StdoutChunk (raw text)
  |-- StructuredEvent dispatched:
  |     |-- kind = "json"     -> CodexEventMapper.mapEvent() -> MessagePart.Reasoning
  |     |                        CodexProgressFormatter.update() -> progress state
  |     |-- kind = "markdown_delta" -> append to markdown accumulator
  |     |-- kind = "status"   -> update codexStatus
  |     |-- kind = "code"     -> MessagePart.Code
  |  -> renderCodexContent(): progress header + body
  |  -> updateAssistantContent() -> streaming UI update
  |
  v
ExecEvent.Exit -> flush remaining JSONL -> final renderCodexContent()
  -> updateAssistantMessage(status=Final)
```

### 2.3 Authentication Callback Flow

```
Host key verification (synchronous sshj callback -> async UI):
  sshj HostKeyVerifier.verify() (IO thread)
    -> CompletableDeferred<Boolean>
    -> hostKeyCallback.onUnknownHost() or onHostKeyMismatch()
    -> MutableSharedFlow<HostKeyEvent>.emit()
    -> UI collects: show dialog
    -> User responds: respondToHostKey(accepted)
    -> MutableSharedFlow<Boolean>.tryEmit(decision)
    -> CompletableDeferred completes -> sshj continues/aborts

Password/Passphrase auth:
  PasswordProvider.getPassword(profile)
    -> MutableSharedFlow<String?>.emit(profile description)
    -> UI shows password dialog
    -> User enters -> respondToPassword(password)
    -> flow resumes: password available in memory

Connection reuse:
  SshjExecRunner caches SSHClient keyed by "[host]:port:username"
  -> stale check: isConnected + transport probe
  -> stale => evict + reconnect (one retry)
  -> Auth failures are NOT retried (account lockout prevention)
```

---

## 3. Room Database v5 with MessagePartConverter

### 3.1 Schema

```
rikka_agent.db (version 5)
  |
  +-- ssh_profiles
  |     id: TEXT (PK)
  |     name, host, port, username, authType, keyRef, hostKeyPolicy
  |     keepaliveIntervalSec, codexMode, codexWorkDir, codexApiKey
  |
  +-- chat_threads
  |     id: TEXT (PK)
  |     profileId: TEXT (FK -> ssh_profiles.id)
  |     title, createdAtMs, updatedAtMs
  |
  +-- chat_messages
        id: TEXT (PK)
        threadId: TEXT (FK -> chat_threads.id, CASCADE DELETE)
        role, content, partsJson, timestampMs, status
```

### 3.2 MessagePartConverter

`MessagePartConverter` is a Room `@TypeConverter` that serializes `List<MessagePart>` to/from a JSON string for the `partsJson` column:

```kotlin
class MessagePartConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"  // @SerialName value in MessagePart
    }

    @TypeConverter
    fun fromParts(parts: List<MessagePart>): String = json.encodeToString(parts)

    @TypeConverter
    fun toParts(value: String): List<MessagePart> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString(value)
}
```

The JSON discriminator `"type"` matches the `@SerialName` annotation on each `MessagePart` subtype:

```kotlin
@Serializable
@SerialName("command")
data class Command(...) : MessagePart()

@Serializable
@SerialName("stdout")
data class Stdout(...) : MessagePart()
// ... etc for all 8 subtypes
```

### 3.3 Migration v4 -> v5

`MIGRATION_4_5` adds the `partsJson` column with a default empty array:

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN partsJson TEXT NOT NULL DEFAULT '[]'")
    }
}
```

The `ChatMessage` model provides backward compatibility: when `parts` is empty but `content` has legacy text, the accessor returns `content` as a `MessagePart.Text` wrapper.

---

## 4. Dependency Injection via Koin

### 4.1 Koin Module Registration

`RikkaAgentApp.onCreate()` starts Koin once:

```kotlin
class RikkaAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@RikkaAgentApp)
                modules(appModule, viewModelModule)
            }
        }
    }
}
```

### 4.2 AppModule (`di/AppModule.kt`)

Provides singletons for the app's dependency graph:

| Bean | Binding | Scope |
|---|---|---|
| `AppDatabase` | Room builder with MIGRATION_4_5 + fallbackToDestructiveMigration | single |
| `SshProfileDao` | `get<AppDatabase>().sshProfileDao()` | single |
| `ChatMessageDao` | `get<AppDatabase>().chatMessageDao()` | single |
| `RoomChatRepository` | `RoomChatRepository(get())` | single |
| `ChatRepository` | alias for `RoomChatRepository` | single |
| `RoomProfileStore` | `RoomProfileStore(get())` | single |
| `ProfileStore` | alias for `RoomProfileStore` | single |
| `AppPreferences` | DataStore-backed preferences | single |
| `KnownHostsStore` | `DataStoreKnownHostsStore(androidContext())` | single |
| `ContentUriKeyContentProvider` | `ContentUriKeyContentProvider(androidContext())` | single |
| `KeyContentProvider` | alias for `ContentUriKeyContentProvider` | single |
| `SshExecRunnerFactory` | `DefaultSshExecRunnerFactory` (produces `SshjExecRunner` with `reuseConnections = true`) | single |

### 4.3 ViewModelModule (`di/ViewModelModule.kt`)

Provides ViewModels with Koin's `viewModel` DSL:

```kotlin
viewModel { (profileId: String) ->
    ChatViewModel(profileId, get(), get(), get(), get(), get(), get(), androidApplication())
}
viewModel { ProfilesViewModel(get()) }
viewModel { (profileId: String?) -> ProfileEditorViewModel(profileId, get(), androidApplication()) }
viewModel { SettingsViewModel(get()) }
```

### 4.4 Injection at Screen Level

`ChatScreen` uses `koinViewModel` with profile ID parameter, and `koinInject` for preferences:

```kotlin
val vm: ChatViewModel = koinViewModel { parametersOf(profileId) }
val prefs: AppPreferences = koinInject()
```

---

## 5. Theme System

RikkaAgent's theme system has three layers:

### 5.1 RikkaAgentTheme (`core:ui`)

The root composable that wraps all content. Supports four modes:

```kotlin
enum class ThemeMode { Light, Dark, Amoled, System }
```

Each mode provides:
- **ColorScheme**: 30+ Material 3 color tokens for both light and dark
  - Light: warm paper tones (background `#FFF8F5F0`), blue primary (`#FF3491FA`)
  - Dark: charcoal surfaces (background `#FF0B0B0B`), light blue primary (`#FF47A8FF`)
  - Amoled: pure black background (`#FF000000`), minimal surface elevation
- **Shapes**: `RoundedCornerShape` at 5 levels (6dp extraSmall -> 16dp extraLarge)
- **Typography**: 7 text styles with `SansSerif` and `Monospace` font families
- **LocalCodeFontFamily**: `CompositionLocal` for code block font override

### 5.2 ExtendColors (`theme/ExtendColors.kt`)

A 50-slot semantic color palette (5 hues x 10 levels) for precise UI coloring, aligned with RikkaHub's design:

```
Red: 9 levels   (#FFEBEE -> #B71C1C)
Orange: 9 levels (#FFF3E0 -> #E65100)
Green: 9 levels  (#E8F5E9 -> #1B5E20)
Blue: 9 levels   (#E3F2FD -> #0D47A1)
Gray: 9 levels   (#FAFAFA -> #212121)
```

Two presets: `lightExtendColors()` and `darkExtendColors()` (AMOLED reuses dark). Provided via `CompositionLocal<ExtendColors>`.

### 5.3 MeshGradientBackground (`components/MeshGradientBackground.kt`)

An animated aurora/mesh gradient background inspired by Gemini. Uses four drifting radial blobs:

- **Blue blob**: slow horizontal drift, top position
- **Teal blob**: upper left quadrant
- **Light blue blob**: upper right quadrant
- **Warm blob**: motion contrast, lower center

Each blob follows independent sine/cosine trajectories with different periods (5.5s-8.5s). Dark mode: deep blue tones; Light mode: soft pastel blues. No external blur dependency -- uses `Brush.radialGradient` with color-to-transparent fade.

Applied in `ChatScreen` as the outermost container:

```kotlin
MeshGradientBackground {
    Scaffold(...) { ... }
}
```

### 5.4 Theme Preferences (`AppPreferences` via DataStore)

Persistent theme configuration:

```kotlin
val theme: Flow<String>        // "system", "light", "dark", "amoled"
```

`MainActivity` collects `prefs.theme` and maps to `ThemeMode`:

```kotlin
val themeName by prefs.theme.collectAsState(initial = "system")
RikkaAgentTheme(themeMode = when (themeName) {
    "light" -> ThemeMode.Light
    "dark" -> ThemeMode.Dark
    "amoled" -> ThemeMode.Amoled
    else -> ThemeMode.System
}) { ... }
```

---

## 6. Core Model: MessagePart Sealed Class

### 6.1 Type Hierarchy

```kotlin
@Serializable
sealed class MessagePart {
    // SSH-specific
    data class Command(command: String, exitCode: Int?, startedAtEpochMs: Long?)
    data class Stdout(text: String)
    data class Stderr(text: String)

    // AI / Codex
    data class Reasoning(text: String, stepId: String?)
    data class Code(code: String, language: String?)

    // General
    data class Text(text: String)
    data class Error(message: String, cause: String?, code: Int?)

    // Rendering
    data class Mermaid(definition: String, caption: String?)
}
```

### 6.2 Serialization

Polymorphic JSON via `kotlinx.serialization`:

```json
[{"type": "command", "command": "df -h", "exitCode": 0},
 {"type": "stdout",  "text": "Filesystem      Size  Used Avail Use% Mounted on"},
 {"type": "text",    "text": "## Disk Usage Summary\nAll volumes normal."}]
```

- Discriminator field: `"type"` (set via `classDiscriminator = "type"` in `Json` config)
- Backward-compatible: legacy `content: String`-only messages render as `Text(content)` on read

### 6.3 SshOutputMapper (ExecEvent -> MessagePart)

The bridge between raw SSH byte streams and structured parts:

```
ExecEvent.StdoutChunk(bytes)
  -> stripAnsi() -> UTF-8 decode
  -> accumulate in stdoutBuffer
  -> flush at: double newline (paragraph boundary) OR >4KB buffer
  -> emit MessagePart.Stdout(text)

ExecEvent.StderrChunk(bytes)
  -> stripAnsi() -> UTF-8 decode
  -> emit MessagePart.Stderr(text) immediately (no buffering)

ExecEvent.Exit(exitCode)
  -> flush remaining stdoutBuffer
  -> emit MessagePart.Command(command, exitCode, startedAtEpochMs)

ExecEvent.Error(category, message)
  -> flush buffers
  -> emit MessagePart.Error(message, cause=category)
```

### 6.4 ChatMessage Model

```kotlin
data class ChatMessage(
    val id: String,
    val role: ChatRole,           // User | Assistant
    val content: String = "",      // Legacy flat content
    val parts: List<MessagePart> = emptyList(),  // Structured parts (canonical)
    val timestampMs: Long,
    val status: MessageStatus,    // Streaming | Final | Error | Canceled
) {
    val textContent: String       // Best-effort from parts Text + content fallback
}
```

---

## 7. ViewModel Decomposition

The original monolithic `ChatViewModel` (529 lines) was split into focused collaborators:

```
ChatViewModel (thin orchestrator, ~269 lines)
  |
  |-- Owns: profile loading, message list state, connection state
  |-- Wires: send() -> CommandExecutor -> SSH execution
  |-- Wires: auth callbacks -> SharedFlow -> UI dialogs
  |-- Delegates: persistence, export, cancel logic

Supporting pure-logic objects (all independently testable):
  OutputFormatter       -- truncation, exit code formatting
  CommandComposer       -- shell wrapping, Codex env injection, shellQuote
  ErrorMessageMapper    -- SSH error category -> user-friendly string
  SessionExporter       -- session -> Markdown export
  CancelMessageHelper   -- canceled message assembly
  CodexProgressFormatter -- Codex thread/turn/item progress parsing
  SshOutputMapper       -- ExecEvent -> MessagePart (in core:ssh)
```

---

## 8. Key Interface Contracts

### 8.1 SshExecRunner

```kotlin
interface SshExecRunner {
    fun run(profile: SshProfile, command: String): Flow<ExecEvent>
}
interface ClosableSshExecRunner : SshExecRunner {
    fun close()
}
interface SshExecRunnerFactory {
    fun create(
        knownHostsStore: KnownHostsStore,
        hostKeyCallback: HostKeyCallback,
        passwordProvider: PasswordProvider?,
        keyContentProvider: KeyContentProvider?,
        passphraseProvider: PassphraseProvider?,
    ): ClosableSshExecRunner
}
```

### 8.2 KnownHostsStore

```kotlin
interface KnownHostsStore {
    suspend fun getFingerprint(host: String, port: Int): StoredHostKey?
    suspend fun store(host: String, port: Int, key: StoredHostKey)
    suspend fun remove(host: String, port: Int)
    suspend fun getAll(): List<Pair<String, StoredHostKey>>
}
```

### 8.3 ChatRepository

```kotlin
interface ChatRepository {
    fun observeThreads(profileId: String): Flow<List<ChatThread>>
    suspend fun createThread(profileId: String, title: String): String
    suspend fun deleteThread(threadId: String)
    suspend fun insertMessage(threadId: String, message: ChatMessage)
    suspend fun updateMessage(id: String, content: String, status: MessageStatus)
    fun observeMessages(threadId: String): Flow<List<ChatMessage>>
    suspend fun getMessages(threadId: String): List<ChatMessage>
    suspend fun updateThreadTitle(threadId: String, title: String)
}
```

### 8.4 HostKeyCallback

```kotlin
interface HostKeyCallback {
    suspend fun onUnknownHost(host: String, port: Int, fingerprint: String, keyType: String): Boolean
    suspend fun onHostKeyMismatch(
        host: String, port: Int,
        expectedFingerprint: String, actualFingerprint: String, keyType: String
    ): Boolean
}
```

---

## 9. State Machine: ConnectionState

```text
[*] -> IDLE
IDLE -> READY   (loadProfile() success)
IDLE -> ERROR   (loadProfile() failure)
READY -> EXECUTING  (send() command)
EXECUTING -> READY  (Exit / Error / Canceled)
EXECUTING -> READY  (Canceled by user)
ERROR -> READY      (loadProfile() retry success)
READY -> IDLE       (newSession() with no profile)
```

---

## Appendix A: Dependency Summary

| Dependency | Purpose | License |
|---|---|---|
| Jetpack Compose (BOM) | Declarative UI | Apache-2.0 |
| Material 3 | Material You design system | Apache-2.0 |
| Kotlin Coroutines + Flow | Structured concurrency | Apache-2.0 |
| Room 2.6+ | SQLite ORM | Apache-2.0 |
| DataStore Preferences | Key-value settings | Apache-2.0 |
| Koin 3.x | Dependency injection | Apache-2.0 |
| sshj 0.39.0 | SSH2 client library | BSD-2-Clause |
| kotlinx.serialization 1.6+ | JSON polymorphism | Apache-2.0 |
| commonmark-java 0.22.0 | Markdown parser | BSD-2-Clause |
| Lucide Icons 1.1.0 | Icon set | ISC |
| Coil | Image loading | Apache-2.0 |
| AndroidX Security Crypto | EncryptedFile / EncryptedSharedPreferences | Apache-2.0 |

## Appendix B: File Size Summary

| Module | Source Files | Lines of Code (approx) |
|---|---|---|
| `:core:model` | 3 | 180 |
| `:core:ssh` | 10 | 700 |
| `:core:storage` | 10 | 550 |
| `:core:ui` | 12 | 950 |
| `:app` | 22 | 2800 |
| **Total** | **57** | **~5200** |

## Appendix C: Performance Targets

| Metric | Target | Mechanism |
|---|---|---|
| Message rendering | >= 60fps | Compose lazy column + `remember` caching |
| SSH connect (LAN) | < 3s | Connection pool reuse on second command |
| SSH connect (WAN) | < 8s | Keepalive + stale eviction + one retry |
| First byte latency | < 500ms (LAN) | 4KB read buffer on IO dispatcher |
| Streaming throughput | 10K lines no jank | Virtualized scroll + paragraph-boundary flushing |
| Memory (idle) | < 80MB RSS | Activity lifecycle + ViewModel-onCleared cleanup |
| APK size | < 15MB | No PTY lib, no heavy WebView bundles |
