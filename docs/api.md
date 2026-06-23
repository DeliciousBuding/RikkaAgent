# RikkaAgent Core API Documentation

> Generated from source. Package root: `io.rikka.agent`

---

## Table of Contents

1. [Core Data Models](#1-core-data-models)
   - [MessagePart](#11-messagepart-sealed-class)
   - [ChatMessage](#12-chatmessage)
   - [ChatThread](#13-chatthread)
   - [SshProfile](#14-sshprofile)
   - [ExecEvent](#15-execevent-sealed-class)
2. [Core Interfaces](#2-core-interfaces)
   - [SshExecRunner](#21-sshexecrunner)
   - [KnownHostsStore](#22-knownhostsstore)
   - [ChatRepository](#23-chatrepository)
   - [ProfileStore](#24-profilestore)
3. [Usage Examples](#3-usage-examples)
4. [Extension Guide](#4-extension-guide)

---

## 1. Core Data Models

### 1.1 MessagePart (sealed class)

**Package:** `io.rikka.agent.model`

Polymorphic message part for RikkaAgent. Serialized with `kotlinx.serialization` using the `"type"` discriminator field.

```kotlin
@Serializable
sealed class MessagePart
```

#### Subtypes

| SerialName   | Class        | Fields                                              | Description                                    |
|-------------|-------------|-----------------------------------------------------|------------------------------------------------|
| `"command"`  | `Command`    | `command: String`, `exitCode: Int?`, `startedAtEpochMs: Long?` | An SSH command executed or to be executed.      |
| `"stdout"`   | `Stdout`     | `text: String`                                      | A chunk of stdout from a running/finished cmd. |
| `"stderr"`   | `Stderr`     | `text: String`                                      | A chunk of stderr from a running/finished cmd. |
| `"reasoning"`| `Reasoning`  | `text: String`, `stepId: String?`                   | AI model reasoning step (Codex JSONL).         |
| `"code"`     | `Code`       | `code: String`, `language: String?`                 | Fenced code block, optionally with language.   |
| `"text"`     | `Text`       | `text: String`                                      | Plain text content (default message type).     |
| `"error"`    | `Error`      | `message: String`, `cause: String?`, `code: Int?`   | Structured error with optional cause chain.    |
| `"mermaid"`  | `Mermaid`    | `definition: String`, `caption: String?`            | Mermaid diagram DSL for rendering.             |

#### Command convenience properties

```kotlin
val isFinished: Boolean  // true when exitCode != null
val isSuccess: Boolean   // true when exitCode == 0
```

#### JSON serialization example

```json
{
  "type": "command",
  "command": "ls -la /tmp",
  "exitCode": 0,
  "startedAtEpochMs": 1719100000000
}
```

---

### 1.2 ChatMessage

**Package:** `io.rikka.agent.model`

```kotlin
@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val parts: List<MessagePart> = emptyList(),
    val timestampMs: Long,
    val status: MessageStatus = MessageStatus.Final,
    @Deprecated("Use parts.") private val _content: String = "",
)
```

#### ChatRole enum

| Value       | Description      |
|------------|-----------------|
| `User`      | Human user      |
| `Assistant` | AI assistant    |

#### MessageStatus enum

| Value       | Description                              |
|------------|------------------------------------------|
| `Streaming` | Message is still being streamed/assembled |
| `Final`     | Message is complete                      |
| `Error`     | Error occurred during processing         |
| `Canceled`  | Generation was canceled by user          |

#### Properties

| Property       | Type                   | Description                                        |
|---------------|------------------------|----------------------------------------------------|
| `content`      | `String`               | Backward-compatible plain text (reads `_content` or `textContent`). |
| `textContent`  | `String`               | Concatenated text of all `Text` parts.             |
| `commands`     | `List<MessagePart.Command>` | All `Command` parts in this message.          |
| `lastCommand`  | `MessagePart.Command?` | Last `Command` part, or null.                      |
| `stdoutText`   | `String`               | Concatenated stdout from all `Stdout` parts.       |
| `stderrText`   | `String`               | Concatenated stderr from all `Stderr` parts.       |

#### Factory methods

```kotlin
// Simple text message
ChatMessage.text(id, role, text, timestampMs, status)

// Command message with optional output
ChatMessage.command(id, command, stdout, stderr, exitCode, timestampMs)
```

#### JSON instance

```kotlin
ChatMessage.json  // Json { classDiscriminator = "type"; ignoreUnknownKeys = true; ... }
```

#### Backward compatibility

Old JSON with `"content": "hello"` and no `parts` field deserializes correctly. The `content` property falls back to `_content` when `parts` is empty. Call `migrateToParts()` to explicitly convert legacy messages.

#### Extension functions

```kotlin
fun String.toTextPart(): MessagePart.Text
fun String.toTextParts(): List<MessagePart.Text>
fun ChatMessage.migrateToParts(): ChatMessage
```

---

### 1.3 ChatThread

**Package:** `io.rikka.agent.model`

```kotlin
@Serializable
data class ChatThread(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
)
```

A chat thread containing an ordered list of messages. The `messages` list may be empty when returned from lightweight queries (e.g. `observeThreads`).

---

### 1.4 SshProfile

**Package:** `io.rikka.agent.model`

```kotlin
@Serializable
data class SshProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PublicKey,
    val keyRef: String? = null,
    val hostKeyPolicy: HostKeyPolicy = HostKeyPolicy.TrustFirstUse,
    val keepaliveIntervalSec: Int = 60,
    val codexMode: Boolean = false,
    val codexWorkDir: String? = null,
    val codexApiKey: String? = null,
)
```

| Field                  | Type            | Default               | Description                              |
|-----------------------|-----------------|-----------------------|------------------------------------------|
| `id`                  | `String`        | (required)            | Unique profile identifier                |
| `name`                | `String`        | (required)            | Display name                             |
| `host`                | `String`        | (required)            | SSH hostname or IP                       |
| `port`                | `Int`           | `22`                  | SSH port                                 |
| `username`            | `String`        | (required)            | SSH username                             |
| `authType`            | `AuthType`      | `PublicKey`           | Authentication method                    |
| `keyRef`              | `String?`       | `null`                | Reference to stored private key          |
| `hostKeyPolicy`       | `HostKeyPolicy` | `TrustFirstUse`       | Host key verification policy             |
| `keepaliveIntervalSec`| `Int`           | `60`                  | SSH keepalive interval in seconds        |
| `codexMode`           | `Boolean`       | `false`               | Enable Codex integration mode            |
| `codexWorkDir`        | `String?`       | `null`                | Working directory for Codex sessions     |
| `codexApiKey`         | `String?`       | `null`                | API key for Codex mode                   |

#### AuthType enum

| Value       | Description              |
|------------|--------------------------|
| `PublicKey` | Public key authentication |
| `Password`  | Password authentication   |

#### HostKeyPolicy enum

| Value             | Description                                           |
|------------------|-------------------------------------------------------|
| `TrustFirstUse`   | Trust on first connect; prompt on mismatch (TOFU)    |
| `RejectUnknown`   | Reject any host not already in known_hosts            |
| `AcceptAll`       | Accept all host keys (insecure, for testing only)     |

---

### 1.5 ExecEvent (sealed class)

**Package:** `io.rikka.agent.ssh`

Events emitted by `SshExecRunner.run()` as a `Flow<ExecEvent>`.

```kotlin
@Serializable
sealed class ExecEvent
```

| Subtype          | Fields                              | Description                              |
|-----------------|-------------------------------------|------------------------------------------|
| `StdoutChunk`   | `bytes: ByteArray`                  | Raw stdout bytes from remote command     |
| `StderrChunk`   | `bytes: ByteArray`                  | Raw stderr bytes from remote command     |
| `StructuredEvent`| `kind: String`, `rawJson: String`  | Parsed JSONL event from Codex output     |
| `Exit`          | `code: Int?`                        | Command finished with exit code          |
| `Canceled`      | (none)                              | Execution was canceled                   |
| `Error`         | `category: String`, `message: String`| Error with category for programmatic handling |

#### Error categories

| Category               | Condition                         |
|-----------------------|-----------------------------------|
| `"connection_refused"` | `ConnectException`               |
| `"timeout"`            | `SocketTimeoutException`         |
| `"unknown_host"`       | `UnknownHostException`           |
| `"auth_failed"`        | Authentication failure           |
| `"host_key_rejected"`  | Host key verification rejected   |
| `"ssh_error"`          | All other SSH errors             |

---

## 2. Core Interfaces

### 2.1 SshExecRunner

**Package:** `io.rikka.agent.ssh`

The primary interface for executing commands over SSH. Intentionally minimal (exec only, no PTY) to allow swapping SSH library implementations without touching UI code.

```kotlin
interface SshExecRunner {
    fun run(profile: SshProfile, command: String): Flow<ExecEvent>
}
```

**Parameters:**
- `profile` -- SSH connection configuration
- `command` -- Shell command string to execute remotely

**Returns:** A `Flow<ExecEvent>` that emits stdout/stderr chunks during execution and finishes with an `Exit` or `Error` event.

#### ClosableSshExecRunner

Extends `SshExecRunner` with connection lifecycle management:

```kotlin
interface ClosableSshExecRunner : SshExecRunner {
    fun close()
}
```

#### SshExecRunnerFactory

Factory for creating runner instances with required dependencies:

```kotlin
fun interface SshExecRunnerFactory {
    fun create(
        knownHostsStore: KnownHostsStore,
        hostKeyCallback: HostKeyCallback,
        passwordProvider: PasswordProvider?,
        keyContentProvider: KeyContentProvider?,
        passphraseProvider: PassphraseProvider?,
    ): ClosableSshExecRunner
}
```

`DefaultSshExecRunnerFactory` creates `SshjExecRunner` instances with connection reuse enabled.

#### HostKeyCallback

```kotlin
interface HostKeyCallback {
    suspend fun onUnknownHost(host: String, port: Int, fingerprint: String, keyType: String): Boolean
    suspend fun onHostKeyMismatch(host: String, port: Int, expectedFingerprint: String, actualFingerprint: String, keyType: String): Boolean
}
```

#### Credential providers

```kotlin
fun interface PasswordProvider {
    suspend fun getPassword(profile: SshProfile): String
}

fun interface KeyContentProvider {
    suspend fun getKeyContent(profile: SshProfile): String?
}

fun interface PassphraseProvider {
    suspend fun getPassphrase(profile: SshProfile): String?
}
```

---

### 2.2 KnownHostsStore

**Package:** `io.rikka.agent.ssh`

Persistent store for SSH host keys. Keyed by `"host:port"` pair.

```kotlin
interface KnownHostsStore {
    suspend fun getFingerprint(host: String, port: Int): StoredHostKey?
    suspend fun store(host: String, port: Int, key: StoredHostKey)
    suspend fun remove(host: String, port: Int)
    suspend fun getAll(): List<Pair<String, StoredHostKey>>
}
```

#### StoredHostKey

```kotlin
data class StoredHostKey(
    val fingerprint: String,
    val keyType: String,
    val addedAtMs: Long = System.currentTimeMillis(),
)
```

---

### 2.3 ChatRepository

**Package:** `io.rikka.agent.storage`

Repository for persisting chat threads and messages. Supports reactive observation via `Flow`.

```kotlin
interface ChatRepository {
    fun observeThreads(profileId: String): Flow<List<ChatThread>>
    suspend fun createThread(profileId: String, title: String): String
    suspend fun deleteThread(threadId: String)
    suspend fun insertMessage(threadId: String, message: ChatMessage)
    suspend fun updateMessage(id: String, parts: List<MessagePart>, status: MessageStatus)
    fun observeMessages(threadId: String): Flow<List<ChatMessage>>
    suspend fun getMessages(threadId: String): List<ChatMessage>
    suspend fun updateThreadTitle(threadId: String, title: String)
}
```

| Method              | Returns                     | Description                                      |
|--------------------|-----------------------------|--------------------------------------------------|
| `observeThreads`   | `Flow<List<ChatThread>>`    | Reactive stream of threads for a profile (messages list is empty). |
| `createThread`     | `String` (thread ID)        | Creates a new thread with UUID.                  |
| `deleteThread`     | `Unit`                      | Deletes thread and all its messages.             |
| `insertMessage`    | `Unit`                      | Inserts message; upserts on conflict.            |
| `updateMessage`    | `Unit`                      | Updates parts and status of an existing message. |
| `observeMessages`  | `Flow<List<ChatMessage>>`   | Reactive stream of messages in a thread.         |
| `getMessages`      | `List<ChatMessage>`         | One-shot fetch of all messages in a thread.      |
| `updateThreadTitle`| `Unit`                      | Updates thread title and timestamp.              |

**Implementation:** `RoomChatRepository` backed by Room DAO (`ChatMessageDao`).

---

### 2.4 ProfileStore

**Package:** `io.rikka.agent.storage`

Storage for SSH connection profiles.

```kotlin
interface ProfileStore {
    suspend fun listProfiles(): List<SshProfile>
    suspend fun getById(id: String): SshProfile?
    suspend fun upsert(profile: SshProfile)
    suspend fun delete(profileId: String)
}
```

| Method         | Returns             | Description                        |
|---------------|---------------------|------------------------------------|
| `listProfiles` | `List<SshProfile>`  | All stored profiles.               |
| `getById`      | `SshProfile?`       | Profile by ID, or null.            |
| `upsert`       | `Unit`              | Insert or update a profile.        |
| `delete`       | `Unit`              | Delete profile by ID.              |

---

## 3. Usage Examples

### 3.1 Sending a text message

```kotlin
val message = ChatMessage.text(
    id = UUID.randomUUID().toString(),
    role = ChatRole.User,
    text = "Hello, Rikka!",
)
```

### 3.2 Creating a command message with output

```kotlin
val cmdMsg = ChatMessage.command(
    id = UUID.randomUUID().toString(),
    command = "uname -a",
    stdout = "Linux server 6.1.0 #1 SMP x86_64 GNU/Linux\n",
    exitCode = 0,
)
// Access parts:
println(cmdMsg.lastCommand?.isSuccess)  // true
println(cmdMsg.stdoutText)              // "Linux server 6.1.0 ..."
```

### 3.3 Building a multi-part message manually

```kotlin
val parts = listOf(
    MessagePart.Reasoning("The user wants to check disk usage.", stepId = "s1"),
    MessagePart.Code("df -h", language = "bash"),
    MessagePart.Command("df -h", exitCode = 0),
    MessagePart.Stdout("Filesystem      Size  Used Avail Use% Mounted on\n/dev/sda1       100G   45G   55G  45% /\n"),
)
val msg = ChatMessage(
    id = UUID.randomUUID().toString(),
    role = ChatRole.Assistant,
    parts = parts,
    timestampMs = System.currentTimeMillis(),
)
```

### 3.4 Serializing / deserializing ChatMessage

```kotlin
val json = ChatMessage.json

// Serialize
val jsonString = json.encodeToString(message)

// Deserialize (handles legacy format automatically)
val restored = json.decodeFromString<ChatMessage>(jsonString)
```

### 3.5 Executing a command over SSH

```kotlin
val runner: SshExecRunner = DefaultSshExecRunnerFactory.create(
    knownHostsStore = myKnownHostsStore,
    hostKeyCallback = myHostKeyCallback,
    passwordProvider = null,
    keyContentProvider = myKeyProvider,
    passphraseProvider = null,
)

runner.run(profile, "uptime").collect { event ->
    when (event) {
        is ExecEvent.StdoutChunk -> print(String(event.bytes))
        is ExecEvent.StderrChunk -> System.err.print(String(event.bytes))
        is ExecEvent.Exit -> println("Exit code: ${event.code}")
        is ExecEvent.Error -> println("Error [${event.category}]: ${event.message}")
        is ExecEvent.Canceled -> println("Canceled")
        is ExecEvent.StructuredEvent -> println("Structured: ${event.kind}")
    }
}
```

### 3.6 Observing chat threads

```kotlin
repository.observeThreads(profileId = "ssh-prod").collect { threads ->
    threads.forEach { thread ->
        println("${thread.id}: ${thread.title}")
    }
}
```

### 3.7 Streaming message updates

```kotlin
repository.observeMessages(threadId).collect { messages ->
    // Re-render UI on every change
    adapter.submitList(messages)
}
```

### 3.8 Managing SSH profiles

```kotlin
val store: ProfileStore = // ... inject implementation

// List all profiles
val profiles = store.listProfiles()

// Create or update
store.upsert(SshProfile(
    id = "prod-1",
    name = "Production Server",
    host = "10.0.0.1",
    port = 22,
    username = "deploy",
    authType = AuthType.PublicKey,
    hostKeyPolicy = HostKeyPolicy.TrustFirstUse,
))

// Look up by ID
val profile = store.getById("prod-1")
```

---

## 4. Extension Guide

### 4.1 Adding a new MessagePart type

1. Add a new `data class` inside the `MessagePart` sealed class:

```kotlin
@Serializable
@SerialName("image")
data class Image(
    val url: String,
    val alt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) : MessagePart()
```

2. The `@SerialName` value becomes the `"type"` discriminator in JSON. No registration is needed -- `kotlinx.serialization` handles sealed class polymorphism automatically.

3. Add convenience accessors to `ChatMessage` if the new type benefits from them:

```kotlin
val images: List<MessagePart.Image>
    get() = parts.filterIsInstance<MessagePart.Image>()
```

4. Update any renderer/UI code to handle the new type in `when` expressions. The sealed class ensures the compiler warns about missing branches.

**Serialization example:**

```json
{
  "type": "image",
  "url": "https://example.com/chart.png",
  "alt": "Disk usage chart",
  "width": 800,
  "height": 600
}
```

### 4.2 Adding a new Agent Runner (SSH library backend)

1. Implement `ClosableSshExecRunner`:

```kotlin
class ApacheSshdExecRunner(
    private val knownHostsStore: KnownHostsStore,
    // ... other dependencies
) : ClosableSshExecRunner {

    override fun run(profile: SshProfile, command: String): Flow<ExecEvent> = callbackFlow {
        // 1. Establish connection using your SSH library
        // 2. Execute the command
        // 3. Read stdout/stderr concurrently, emit StdoutChunk/StderrChunk
        // 4. Wait for exit, emit Exit(code)
        // 5. On error, emit Error(category, message)
        // 6. Close channel when done
        awaitClose { /* cleanup */ }
    }

    override fun close() {
        // Release pooled connections
    }
}
```

2. Create a factory:

```kotlin
object ApacheSshdRunnerFactory : SshExecRunnerFactory {
    override fun create(
        knownHostsStore: KnownHostsStore,
        hostKeyCallback: HostKeyCallback,
        passwordProvider: PasswordProvider?,
        keyContentProvider: KeyContentProvider?,
        passphraseProvider: PassphraseProvider?,
    ): ClosableSshExecRunner = ApacheSshdExecRunner(
        knownHostsStore = knownHostsStore,
        // ... wire dependencies
    )
}
```

3. Swap the factory in DI / composition root. No UI code changes needed.

**Key contracts to honor:**
- Emit `Exit` exactly once on normal completion.
- Emit `Error` exactly once on failure (use the category constants for programmatic handling).
- Emit `Canceled` if the coroutine is canceled.
- Never throw from `run()` -- always emit an `Error` event and close the channel.
- Use `callbackFlow` + `awaitClose` for proper lifecycle management.

### 4.3 Implementing KnownHostsStore

```kotlin
class RoomKnownHostsStore(private val dao: KnownHostsDao) : KnownHostsStore {
    override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? =
        dao.get("$host:$port")?.toModel()

    override suspend fun store(host: String, port: Int, key: StoredHostKey) =
        dao.upsert(KnownHostEntity("$host:$port", key.fingerprint, key.keyType, key.addedAtMs))

    override suspend fun remove(host: String, port: Int) =
        dao.delete("$host:$port")

    override suspend fun getAll(): List<Pair<String, StoredHostKey>> =
        dao.getAll().map { it.hostPort to it.toModel() }
}
```

### 4.4 Implementing ChatRepository

The reference implementation `RoomChatRepository` wraps a Room DAO. Key behaviors:

- `insertMessage` uses `IGNORE` conflict strategy and falls back to update if the row exists.
- `updateMessage` serializes `parts` to JSON via `ChatMessage.json` and extracts `textContent` for the FTS/search column.
- `observeThreads` returns threads with an empty `messages` list (lightweight); use `observeMessages` for full content.
- Thread timestamps are updated on message insert but titles are preserved.

---

## Appendix: Package structure

```
io.rikka.agent.model       -- Data models (MessagePart, ChatMessage, ChatThread, SshProfile)
io.rikka.agent.ssh         -- SSH execution (SshExecRunner, ExecEvent, KnownHostsStore, providers)
io.rikka.agent.storage     -- Persistence (ChatRepository, ProfileStore)
io.rikka.agent.storage.db  -- Room DAOs and entities (internal)
```
