# RikkaAgent Domain Layer Design

> Clean Architecture Use Case extraction for pure-Kotlin domain layer (zero Android dependency)

## 1. Architecture Overview

### Current State

```
app/
  vm/CommandExecutor.kt      -- mixed: domain logic + Android resources (Application, R.string)
  vm/ChatSessionManager.kt   -- mixed: domain logic + CoroutineScope coupling
core/
  model/                      -- already clean (ChatMessage, SshProfile, MessagePart)
  storage/ChatRepository.kt   -- interface + Room impl co-located
  storage/ProfileStore.kt     -- interface in data layer
  ssh/SshInterfaces.kt        -- SshExecRunner, ExecEvent
  ssh/KnownHostsStore.kt      -- HostKeyCallback, KnownHostsStore
```

### Target State

```
domain/                       -- NEW: pure Kotlin module, zero Android deps
  model/                      -- re-export from core/model (or inline if needed)
  repository/                 -- repository interfaces (Domain defines, Data implements)
  usecase/                    -- one class per use case
  event/                      -- domain events (CommandEvent, HostKeyDecision)
core/
  model/                      -- unchanged (already clean)
  storage/                    -- Room implementations of domain repository interfaces
  ssh/                        -- SSH implementations
app/
  vm/                         -- thin ViewModel delegates to Use Cases
```

---

## 2. Domain Models

Existing models in `core/model` are already clean -- `@Serializable` data classes with no Android dependency. No changes needed.

| Model | Location | Status |
|---|---|---|
| `ChatMessage` | `core/model/ChatModels.kt` | Clean |
| `ChatThread` | `core/model/ChatModels.kt` | Clean |
| `ChatRole` | `core/model/ChatModels.kt` | Clean |
| `MessageStatus` | `core/model/ChatModels.kt` | Clean |
| `MessagePart` (sealed) | `core/model/MessagePart.kt` | Clean |
| `SshProfile` | `core/model/SshProfile.kt` | Clean |
| `AuthType` | `core/model/SshProfile.kt` | Clean |
| `HostKeyPolicy` | `core/model/SshProfile.kt` | Clean |

### New Domain-Only Models

```kotlin
// domain/event/CommandEvent.kt
package io.rikka.agent.domain.event

import io.rikka.agent.model.MessagePart

/**
 * Domain events emitted during SSH command execution.
 *
 * Replaces the raw ExecEvent + callback soup in CommandExecutor with
 * a clean, sealed event stream that Use Case consumers can pattern-match.
 */
sealed class CommandEvent {

    /** Command execution started. */
    data class Started(val executionId: String) : CommandEvent()

    /** Streaming stdout chunk. */
    data class Stdout(val executionId: String, val text: String) : CommandEvent()

    /** Streaming stderr chunk. */
    data class Stderr(val executionId: String, val text: String) : CommandEvent()

    /** Structured Codex JSONL event (reasoning, code, text). */
    data class Structured(val executionId: String, val part: MessagePart) : CommandEvent()

    /** Progress update from Codex (thread/turn/item). */
    data class Progress(
        val executionId: String,
        val thread: String? = null,
        val turn: String? = null,
        val item: String? = null,
        val status: String? = null,
    ) : CommandEvent()

    /** Command completed successfully. */
    data class Completed(
        val executionId: String,
        val exitCode: Int,
        val fullOutput: String,
    ) : CommandEvent()

    /** Command was canceled by the user. */
    data class Canceled(val executionId: String) : CommandEvent()

    /** Command failed with an error. */
    data class Failed(
        val executionId: String,
        val category: String,
        val message: String,
        val partialOutput: String? = null,
    ) : CommandEvent()
}

// domain/event/HostKeyDecision.kt
package io.rikka.agent.domain.event

/**
 * Decision for an unknown or mismatched host key.
 */
enum class HostKeyDecision {
    /** Trust this key (first-use or accept replacement). */
    Trust,
    /** Reject this key (abort connection). */
    Reject,
}

// domain/model/ExecutionHandle.kt
package io.rikka.agent.domain.model

/**
 * Opaque handle for a running command execution.
 * Returned by ExecuteCommandUseCase; passed to CancelExecutionUseCase.
 */
data class ExecutionHandle(
    val executionId: String,
    val profileId: String,
    val command: String,
)
```

---

## 3. Repository Interfaces

All interfaces live in `domain/repository`. Data layer implements them.

```kotlin
// domain/repository/ChatRepository.kt
package io.rikka.agent.domain.repository

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for chat threads and messages.
 *
 * Domain layer owns this interface. Data layer (Room) implements it.
 */
interface ChatRepository {

    /** Observe all threads for a profile, ordered by most recent. */
    fun observeThreads(profileId: String): Flow<List<ChatThread>>

    /** Create a new thread. Returns the thread ID. */
    suspend fun createThread(profileId: String, title: String): String

    /** Delete a thread and all its messages. */
    suspend fun deleteThread(threadId: String)

    /** Insert a message into a thread. */
    suspend fun insertMessage(threadId: String, message: ChatMessage)

    /** Update an existing message's parts and status. */
    suspend fun updateMessage(id: String, parts: List<MessagePart>, status: MessageStatus)

    /** Observe messages in a thread, ordered by timestamp. */
    fun observeMessages(threadId: String): Flow<List<ChatMessage>>

    /** One-shot load of all messages in a thread. */
    suspend fun getMessages(threadId: String): List<ChatMessage>

    /** Update thread title. */
    suspend fun updateThreadTitle(threadId: String, title: String)
}

// domain/repository/ProfileRepository.kt
package io.rikka.agent.domain.repository

import io.rikka.agent.model.SshProfile

/**
 * Persistence boundary for SSH profiles.
 *
 * Renamed from ProfileStore for consistency.
 */
interface ProfileRepository {

    /** List all profiles. */
    suspend fun listProfiles(): List<SshProfile>

    /** Get a profile by ID, or null. */
    suspend fun getById(id: String): SshProfile?

    /** Create or update a profile. */
    suspend fun upsert(profile: SshProfile)

    /** Delete a profile. */
    suspend fun delete(profileId: String)
}

// domain/repository/HostKeyRepository.kt
package io.rikka.agent.domain.repository

/**
 * Persistence boundary for SSH known-hosts fingerprints.
 *
 * Domain owns the interface. Data layer implements with DataStore/Room.
 */
interface HostKeyRepository {

    /** Get stored fingerprint for host:port, or null. */
    suspend fun getFingerprint(host: String, port: Int): StoredHostKey?

    /** Store a host key. */
    suspend fun store(host: String, port: Int, key: StoredHostKey)

    /** Remove a host key. */
    suspend fun remove(host: String, port: Int)

    /** List all stored host keys. */
    suspend fun getAll(): List<Pair<String, StoredHostKey>>
}

/**
 * A stored host key entry.
 */
data class StoredHostKey(
    val fingerprint: String,
    val keyType: String,
    val addedAtEpochMs: Long,
)

// domain/repository/ExecutionRepository.kt
package io.rikka.agent.domain.repository

import io.rikka.agent.domain.model.ExecutionHandle

/**
 * Tracks running and recent command executions.
 *
 * Enables cancellation by executionId and history queries.
 */
interface ExecutionRepository {

    /** Register a new execution. Returns the handle. */
    suspend fun register(profileId: String, command: String): ExecutionHandle

    /** Mark an execution as completed/canceled/failed. */
    suspend fun finalize(executionId: String, status: ExecutionStatus)

    /** Get the handle for a running execution, or null. */
    suspend fun getRunning(profileId: String): ExecutionHandle?

    /** Get execution history for a profile. */
    suspend fun getHistory(profileId: String, limit: Int = 50): List<ExecutionRecord>
}

enum class ExecutionStatus { Running, Completed, Canceled, Failed }

data class ExecutionRecord(
    val handle: ExecutionHandle,
    val status: ExecutionStatus,
    val exitCode: Int? = null,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
)
```

---

## 4. Use Cases

### Design Principles

1. **Single Responsibility**: each Use Case does exactly one thing.
2. **Dependency Inversion**: constructor-injected repository interfaces (DIP).
3. **Coroutine-native**: `suspend` or `Flow` return types.
4. **Zero Android dependency**: no `Application`, `Context`, `R.string`, `Log`.
5. **No state**: Use Cases are stateless functions. Mutable state lives in repositories or domain models.

### 4.1 ExecuteCommandUseCase

```kotlin
// domain/usecase/ExecuteCommandUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.domain.event.CommandEvent
import io.rikka.agent.domain.model.ExecutionHandle
import io.rikka.agent.domain.repository.ExecutionRepository
import io.rikka.agent.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow

/**
 * Execute a command over SSH and stream back events.
 *
 * ## Responsibilities
 * - Load the SSH profile.
 * - Register the execution in ExecutionRepository.
 * - Delegate to SshExecRunner (injected as a domain port).
 * - Map raw ExecEvent to domain CommandEvent.
 *
 * ## Does NOT
 * - Format output for UI (that's presentation concern).
 * - Manage Android resources.
 * - Persist messages (that's SendMessageUseCase).
 */
class ExecuteCommandUseCase(
    private val profileRepository: ProfileRepository,
    private val executionRepository: ExecutionRepository,
    private val commandExecutorPort: CommandExecutorPort,
) {
    /**
     * @param profileId SSH profile to use.
     * @param command   Shell command string.
     * @return Flow of domain events for this execution.
     * @throws IllegalArgumentException if profile not found.
     */
    operator fun invoke(profileId: String, command: String): Flow<CommandEvent> {
        // Actual implementation delegates to commandExecutorPort.execute()
        // which returns Flow<CommandEvent> after mapping ExecEvent -> CommandEvent.
        return commandExecutorPort.execute(profileId, command)
    }
}

/**
 * Domain port for SSH command execution.
 *
 * This is NOT an SSH library interface -- it's a domain-level abstraction
 * that the data/infra layer implements by wrapping SshExecRunner.
 */
interface CommandExecutorPort {
    /**
     * Execute a command and stream domain events.
     *
     * Implementations handle SSH connection, authentication, and raw byte
     * streaming. They emit domain-level [CommandEvent] instances.
     */
    fun execute(profileId: String, command: String): Flow<CommandEvent>

    /** Cancel a running execution. */
    suspend fun cancel(executionId: String)
}
```

### 4.2 GetConversationHistoryUseCase

```kotlin
// domain/usecase/GetConversationHistoryUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

/**
 * Load conversation history for a thread.
 *
 * Returns a reactive Flow so the UI updates automatically when
 * new messages are inserted.
 */
class GetConversationHistoryUseCase(
    private val chatRepository: ChatRepository,
) {
    /**
     * @param threadId The thread to observe.
     * @return Flow of messages ordered by timestamp.
     */
    operator fun invoke(threadId: String): Flow<List<ChatMessage>> =
        chatRepository.observeMessages(threadId)
}
```

### 4.3 CreateSessionUseCase

```kotlin
// domain/usecase/CreateSessionUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.domain.repository.ChatRepository
import java.util.UUID

/**
 * Create a new empty chat session (thread).
 *
 * Returns the newly created ChatThread with an empty message list.
 * The caller is responsible for tracking the current thread ID.
 */
class CreateSessionUseCase(
    private val chatRepository: ChatRepository,
) {
    /**
     * @param profileId The SSH profile this session belongs to.
     * @return The new thread ID.
     */
    suspend operator fun invoke(profileId: String): String =
        chatRepository.createThread(profileId, title = "")
}
```

### 4.4 SendMessageUseCase

```kotlin
// domain/usecase/SendMessageUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.domain.repository.ChatRepository
import java.util.UUID

/**
 * Send a user message: persist it and auto-generate thread title if needed.
 *
 * ## Responsibilities
 * - Create the ChatMessage domain object.
 * - Persist via ChatRepository.
 * - Auto-generate thread title from first user message (first 50 chars).
 *
 * ## Does NOT
 * - Trigger command execution (that's ExecuteCommandUseCase).
 * - Manage thread state (that's the ViewModel/presentation layer).
 */
class SendMessageUseCase(
    private val chatRepository: ChatRepository,
) {
    /**
     * @param threadId  Target thread. Must already exist.
     * @param content   User message text.
     * @return The persisted ChatMessage.
     */
    suspend operator fun invoke(threadId: String, content: String): ChatMessage {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.User,
            parts = listOf(MessagePart.Text(content)),
            timestampMs = System.currentTimeMillis(),
            status = MessageStatus.Final,
        )
        chatRepository.insertMessage(threadId, message)

        // Auto-generate title from first user message
        // The caller should check if this is the first message in the thread
        // and call UpdateThreadTitleUseCase if needed, OR we handle it here.
        // For simplicity, we do it here as a side effect.
        val title = content.take(50).let {
            if (content.length > 50) "$it..." else it
        }
        chatRepository.updateThreadTitle(threadId, title)

        return message
    }
}
```

### 4.5 CancelExecutionUseCase

```kotlin
// domain/usecase/CancelExecutionUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.domain.repository.ExecutionRepository

/**
 * Cancel a running command execution.
 *
 * Delegates to the CommandExecutorPort to send the cancellation signal,
 * then updates the execution record.
 */
class CancelExecutionUseCase(
    private val executionRepository: ExecutionRepository,
    private val commandExecutorPort: CommandExecutorPort,
) {
    /**
     * @param executionId The execution to cancel.
     */
    suspend operator fun invoke(executionId: String) {
        commandExecutorPort.cancel(executionId)
        executionRepository.finalize(executionId, ExecutionStatus.Canceled)
    }
}
```

### 4.6 ExportSessionUseCase

```kotlin
// domain/usecase/ExportSessionUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessagePart
import io.rikka.agent.domain.repository.ChatRepository

/**
 * Export a chat thread as a formatted plain-text string.
 *
 * Pure domain logic -- no Android file I/O. The caller decides
 * where to write the result (share intent, file, clipboard).
 */
class ExportSessionUseCase(
    private val chatRepository: ChatRepository,
) {
    /**
     * @param threadId The thread to export.
     * @return Formatted plain-text representation of the conversation.
     */
    suspend operator fun invoke(threadId: String): String {
        val messages = chatRepository.getMessages(threadId)
        return formatMessages(messages)
    }

    private fun formatMessages(messages: List<ChatMessage>): String = buildString {
        for (msg in messages) {
            val role = when (msg.role) {
                ChatRole.User -> "User"
                ChatRole.Assistant -> "Assistant"
            }
            appendLine("[$role]")
            for (part in msg.parts) {
                when (part) {
                    is MessagePart.Text -> appendLine(part.text)
                    is MessagePart.Command -> {
                        appendLine("$ ${part.command}")
                        if (part.isFinished) appendLine("[exit ${part.exitCode}]")
                    }
                    is MessagePart.Stdout -> append(part.text)
                    is MessagePart.Stderr -> {
                        if (part.text.isNotBlank()) appendLine("[stderr] ${part.text}")
                    }
                    is MessagePart.Reasoning -> {
                        appendLine("[thinking] ${part.text}")
                    }
                    is MessagePart.Code -> {
                        appendLine("``` ${part.language ?: ""}")
                        appendLine(part.code)
                        appendLine("```")
                    }
                    is MessagePart.Error -> appendLine("[error] ${part.message}")
                    is MessagePart.Mermaid -> appendLine("[mermaid] ${part.definition}")
                }
            }
            appendLine()
        }
    }
}
```

### 4.7 ValidateHostKeyUseCase

```kotlin
// domain/usecase/ValidateHostKeyUseCase.kt
package io.rikka.agent.domain.usecase

import io.rikka.agent.domain.event.HostKeyDecision
import io.rikka.agent.domain.repository.HostKeyRepository
import io.rikka.agent.domain.repository.StoredHostKey

/**
 * Validate an SSH host key against the known-hosts store.
 *
 * Implements the Trust-First-Use policy:
 * - Unknown host -> ask user -> store if trusted.
 * - Known host, key matches -> accept.
 * - Known host, key mismatch -> ask user -> replace if trusted.
 *
 * The "ask user" step is modeled as a callback interface (HostKeyVerifier)
 * so the domain layer stays UI-agnostic.
 */
class ValidateHostKeyUseCase(
    private val hostKeyRepository: HostKeyRepository,
    private val hostKeyVerifier: HostKeyVerifier,
) {
    /**
     * Validate a host key during SSH connection.
     *
     * @param host        Remote hostname or IP.
     * @param port        Remote port.
     * @param fingerprint Host key fingerprint presented by the server.
     * @param keyType     Key algorithm (e.g. "ssh-ed25519").
     * @return Decision to trust or reject.
     */
    suspend operator fun invoke(
        host: String,
        port: Int,
        fingerprint: String,
        keyType: String,
    ): HostKeyDecision {
        val stored = hostKeyRepository.getFingerprint(host, port)

        return if (stored == null) {
            // Unknown host -- ask user
            val trust = hostKeyVerifier.onUnknownHost(host, port, fingerprint, keyType)
            if (trust) {
                hostKeyRepository.store(
                    host, port,
                    StoredHostKey(fingerprint, keyType, System.currentTimeMillis())
                )
                HostKeyDecision.Trust
            } else {
                HostKeyDecision.Reject
            }
        } else if (stored.fingerprint == fingerprint) {
            // Known host, key matches
            HostKeyDecision.Trust
        } else {
            // Key mismatch -- potential MITM
            val replace = hostKeyVerifier.onHostKeyMismatch(
                host, port, stored.fingerprint, fingerprint, keyType
            )
            if (replace) {
                hostKeyRepository.store(
                    host, port,
                    StoredHostKey(fingerprint, keyType, System.currentTimeMillis())
                )
                HostKeyDecision.Trust
            } else {
                HostKeyDecision.Reject
            }
        }
    }
}

/**
 * Domain port for host key verification UI.
 *
 * The presentation layer implements this to show confirmation dialogs.
 * Domain layer calls it but never knows about Android/Compose/etc.
 */
interface HostKeyVerifier {
    suspend fun onUnknownHost(host: String, port: Int, fingerprint: String, keyType: String): Boolean
    suspend fun onHostKeyMismatch(
        host: String, port: Int,
        expectedFingerprint: String, actualFingerprint: String,
        keyType: String,
    ): Boolean
}
```

---

## 5. Module Structure

```
rikkaagent/
  core/
    model/          -- domain models (ChatMessage, SshProfile, MessagePart, etc.)
    domain/         -- NEW: pure Kotlin library module
      build.gradle.kts
      src/main/kotlin/io/rikka/agent/domain/
        event/
          CommandEvent.kt
          HostKeyDecision.kt
        model/
          ExecutionHandle.kt
        repository/
          ChatRepository.kt
          ProfileRepository.kt
          HostKeyRepository.kt
          ExecutionRepository.kt
        usecase/
          ExecuteCommandUseCase.kt
          GetConversationHistoryUseCase.kt
          CreateSessionUseCase.kt
          SendMessageUseCase.kt
          CancelExecutionUseCase.kt
          ExportSessionUseCase.kt
          ValidateHostKeyUseCase.kt
        port/
          CommandExecutorPort.kt
          HostKeyVerifier.kt
    storage/        -- Room implementations of domain repository interfaces
    ssh/            -- SSH implementations of CommandExecutorPort
  app/
    vm/             -- thin ViewModels delegate to Use Cases
```

### build.gradle.kts for domain module

```kotlin
// core/domain/build.gradle.kts
plugins {
    id("rikkaagent.android.library")  // or pure kotlin if no Android deps
    // Actually: use kotlin("jvm") since domain has ZERO Android deps
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version libs.versions.kotlinSerialization
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    // No Android dependencies!
}
```

---

## 6. Dependency Graph

```
app (ViewModel)
  |
  +---> domain/usecase (Use Cases)
  |       |
  |       +---> domain/repository (interfaces)
  |       +---> domain/event (events)
  |       +---> domain/port (ports)
  |       +---> core/model (domain models)
  |
  +---> core/storage (Room implementations)
  |       |
  |       +---> domain/repository (implements)
  |
  +---> core/ssh (SSH implementations)
          |
          +---> domain/port (implements CommandExecutorPort)
```

**Key invariant**: `domain` module depends ONLY on `core:model`. It never imports `storage`, `ssh`, or `app`.

---

## 7. Migration Path

### Phase 1: Create domain module (non-breaking)
1. Create `core/domain` module with `build.gradle.kts`.
2. Define repository interfaces in `domain/repository/`.
3. Define events in `domain/event/`.
4. Define ports in `domain/port/`.
5. Write use cases in `domain/usecase/`.

### Phase 2: Adapt data layer
1. Make `RoomChatRepository` implement `domain.repository.ChatRepository` (already matches).
2. Make `ProfileStore` impl implement `domain.repository.ProfileRepository`.
3. Create `RoomExecutionRepository` or in-memory impl.
4. Create adapter from `KnownHostsStore` to `domain.repository.HostKeyRepository`.

### Phase 3: Adapt infra layer
1. Create `SshCommandExecutorPort` that wraps `SshExecRunner` and maps `ExecEvent` -> `CommandEvent`.
2. Create `HostKeyVerifierAdapter` that bridges `HostKeyCallback` -> `HostKeyVerifier`.

### Phase 4: Refactor ViewModels
1. Replace `CommandExecutor` usage with `ExecuteCommandUseCase` + `CancelExecutionUseCase`.
2. Replace `ChatSessionManager` usage with `CreateSessionUseCase` + `GetConversationHistoryUseCase` + `SendMessageUseCase`.
3. Move output formatting (`OutputFormatter`, `renderCodexContent`) to presentation layer.
4. Move Android string resources to presentation layer (ViewModel or Compose).

### Phase 5: Delete old code
1. Remove `CommandExecutor.kt` from `app/vm/`.
2. Remove `ChatSessionManager.kt` from `app/vm/`.
3. Remove `ProfileStore` interface from `core/storage` (replaced by `domain.repository.ProfileRepository`).

---

## 8. Verification Checklist

- [ ] `core/domain` module has ZERO Android imports (grep for `android.`).
- [ ] Every Use Case has exactly one public `operator fun invoke()`.
- [ ] All Use Case dependencies are interfaces (no concrete classes).
- [ ] Repository interfaces have no Room/SQL annotations.
- [ ] `CommandEvent` sealed class has no `ExecEvent` dependency.
- [ ] `HostKeyVerifier` interface has no Android `Dialog` dependency.
- [ ] Domain models (`core/model`) unchanged -- no new Android leaks.
- [ ] ViewModel code reduced to thin delegation (no business logic).

---

## 9. Appendix: Full Use Case Summary

| Use Case | Input | Output | Repos Used |
|---|---|---|---|
| `ExecuteCommandUseCase` | `profileId, command` | `Flow<CommandEvent>` | ProfileRepository, ExecutionRepository, CommandExecutorPort |
| `GetConversationHistoryUseCase` | `threadId` | `Flow<List<ChatMessage>>` | ChatRepository |
| `CreateSessionUseCase` | `profileId` | `String` (threadId) | ChatRepository |
| `SendMessageUseCase` | `threadId, content` | `ChatMessage` | ChatRepository |
| `CancelExecutionUseCase` | `executionId` | `Unit` | ExecutionRepository, CommandExecutorPort |
| `ExportSessionUseCase` | `threadId` | `String` | ChatRepository |
| `ValidateHostKeyUseCase` | `host, port, fingerprint, keyType` | `HostKeyDecision` | HostKeyRepository, HostKeyVerifier |
