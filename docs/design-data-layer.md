# RikkaAgent Data Layer Normalization Design

> Status: Proposed
> Date: 2026-06-23
> Scope: `core/storage`, `core/ssh`, data flow to `app` layer

---

## 1. Current State Analysis

### 1.1 Storage Module

| Component | Type | Issues |
|-----------|------|--------|
| `ChatRepository` | interface | No error handling; no `Result<T>` |
| `RoomChatRepository` | class | No `@Inject`; inline upsert logic; directly calls DAO |
| `ProfileStore` | interface | Missing `observeProfiles()` (exists only on impl) |
| `RoomProfileStore` | class | No `@Inject`; `observeProfiles()` not in interface |
| `AppPreferences` | concrete class | No interface; cannot mock for testing |
| `ChatMessageDao` | DAO | Adequate |
| `SshProfileDao` | DAO | Adequate |
| `Mappers.kt` | extensions | `ChatThread` mapping loses `messages`; no error mapping |
| `MessagePartConverter` | TypeConverter | Adequate |
| `AppDatabase` | Room | Adequate; version 5 |

### 1.2 SSH Module

| Component | Type | Issues |
|-----------|------|--------|
| `SshExecRunner` | interface | Adequate |
| `SshjExecRunner` | class | Internal connection cache duplicates `SshConnectionPool` |
| `SshConnectionPool` | class | Exists but unused — `SshjExecRunner` has its own cache |
| `SshOutputMapper` | class | Good; maps `ExecEvent` → `MessagePart` |
| `KnownHostsStore` | interface | Adequate |
| `InMemoryKnownHostsStore` | class | Not persisted; no TTL |

### 1.3 Key Problems

1. **No DataSource abstraction** — Repositories call DAOs directly, mixing data-origin concerns.
2. **Inconsistent interfaces** — `ProfileStore.observeProfiles()` exists only on the implementation.
3. **No error handling** — All repository methods throw on failure; callers have no typed error path.
4. **Duplicate SSH caching** — `SshjExecRunner` and `SshConnectionPool` both cache connections independently.
5. **No cache strategy** — SSH connections have no TTL; session list has no memory cache.
6. **`AppPreferences` untestable** — No interface, cannot substitute a fake.
7. **Mapper gaps** — `ChatThread` mapping is lossy; no DTO→Model abstraction for SSH output.

---

## 2. Target Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ViewModel / UI                           │
│                   (ChatViewModel, etc.)                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │ depends on interfaces only
┌──────────────────────────▼──────────────────────────────────────┐
│                       Repository Layer                          │
│  ChatRepository          ProfileRepository     PreferencesStore │
│  (interface + impl)      (interface + impl)    (interface + impl)│
└─────────┬──────────────────────┬──────────────────────┬─────────┘
          │                      │                      │
┌─────────▼──────────┐ ┌────────▼───────────┐ ┌───────▼──────────┐
│  LocalDataSource   │ │  RemoteDataSource  │ │  PreferencesDS   │
│  (Room + DAOs)     │ │  (SSH exec)        │ │  (DataStore)     │
└────────────────────┘ └────────────────────┘ └──────────────────┘
```

---

## 3. Normalization Plan

### 3.1 Repository Pattern

**Rule**: Every Repository has a public interface. Implementation uses `@Inject constructor`.

#### 3.1.1 ChatRepository

Current interface is mostly correct. Changes:

- Add `Result<T>` wrapping to all mutation methods.
- Add `observeThread(threadId)` for single-thread observation.

```kotlin
// core/storage/.../ChatRepository.kt

interface ChatRepository {
    // Observation (Flow)
    fun observeThreads(profileId: String): Flow<List<ChatThread>>
    fun observeMessages(threadId: String): Flow<List<ChatMessage>>
    fun observeThread(threadId: String): Flow<ChatThread?>

    // One-shot reads
    suspend fun getMessages(threadId: String): Result<List<ChatMessage>>
    suspend fun getThread(threadId: String): Result<ChatThread?>

    // Mutations
    suspend fun createThread(profileId: String, title: String): Result<String>
    suspend fun deleteThread(threadId: String): Result<Unit>
    suspend fun insertMessage(threadId: String, message: ChatMessage): Result<Unit>
    suspend fun updateMessage(
        id: String,
        parts: List<MessagePart>,
        status: MessageStatus,
    ): Result<Unit>
    suspend fun updateThreadTitle(threadId: String, title: String): Result<Unit>
}
```

Implementation (`RoomChatRepository`) uses `@Inject constructor(private val dao: ChatMessageDao)` and wraps all DAO calls in `runCatching { ... }`.

#### 3.1.2 ProfileRepository (rename from ProfileStore)

Current `ProfileStore` is missing `observeProfiles()` in the interface. Fix:

```kotlin
// core/storage/.../ProfileRepository.kt  (renamed from ProfileStore.kt)

interface ProfileRepository {
    // Observation
    fun observeProfiles(): Flow<List<SshProfile>>
    fun observeProfile(id: String): Flow<SshProfile?>

    // One-shot reads
    suspend fun listProfiles(): Result<List<SshProfile>>
    suspend fun getById(id: String): Result<SshProfile?>

    // Mutations
    suspend fun upsert(profile: SshProfile): Result<Unit>
    suspend fun delete(profileId: String): Result<Unit>
}
```

Implementation (`RoomProfileRepository`) uses `@Inject constructor(private val dao: SshProfileDao)`.

#### 3.1.3 PreferencesStore (interface for AppPreferences)

```kotlin
// core/storage/.../PreferencesStore.kt

interface PreferencesStore {
    val theme: Flow<String>
    val dynamicColor: Flow<Boolean>
    val defaultShell: Flow<String>
    val lastProfileId: Flow<String?>
    val enableMermaid: Flow<Boolean>

    suspend fun setTheme(value: String)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setDefaultShell(value: String)
    suspend fun setLastProfileId(value: String)
    suspend fun setEnableMermaid(value: Boolean)
}
```

`AppPreferences` implements `PreferencesStore`.

### 3.2 Data Source Abstraction

#### 3.2.1 LocalDataSource

Wraps Room DAOs. Repositories delegate to this instead of calling DAOs directly.

```kotlin
// core/storage/.../local/ChatLocalDataSource.kt

class ChatLocalDataSource @Inject constructor(
    private val chatDao: ChatMessageDao,
    private val profileDao: SshProfileDao,
) {
    // Thread operations
    fun observeThreadsForProfile(profileId: String): Flow<List<ChatThreadEntity>> =
        chatDao.observeThreadsForProfile(profileId)

    suspend fun getThread(threadId: String): ChatThreadEntity? =
        chatDao.getThread(threadId)

    suspend fun insertThread(thread: ChatThreadEntity) =
        chatDao.insertThread(thread)

    suspend fun deleteThread(threadId: String) =
        chatDao.deleteThread(threadId)

    suspend fun updateThreadTitle(id: String, title: String, updatedAtMs: Long) =
        chatDao.updateThreadTitle(id, title, updatedAtMs)

    suspend fun updateThreadTimestamp(id: String, updatedAtMs: Long) =
        chatDao.updateThreadTimestamp(id, updatedAtMs)

    // Message operations
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(threadId)

    suspend fun getMessages(threadId: String): List<ChatMessageEntity> =
        chatDao.getMessages(threadId)

    suspend fun insertMessage(message: ChatMessageEntity): Long =
        chatDao.insertMessage(message)

    suspend fun updateMessageParts(
        id: String, content: String, partsJson: String, status: String,
    ): Int = chatDao.updateMessageParts(id, content, partsJson, status)

    // Profile operations
    fun observeAllProfiles(): Flow<List<SshProfileEntity>> =
        profileDao.observeAll()

    suspend fun getProfileById(id: String): SshProfileEntity? =
        profileDao.getById(id)

    suspend fun upsertProfile(entity: SshProfileEntity) =
        profileDao.upsert(entity)

    suspend fun deleteProfile(id: String) =
        profileDao.delete(id)
}
```

#### 3.2.2 RemoteDataSource

Wraps SSH exec. Converts `ExecEvent` flow into domain-ready `MessagePart` flow.

```kotlin
// core/ssh/.../SshRemoteDataSource.kt

class SshRemoteDataSource @Inject constructor(
    private val runnerFactory: SshExecRunnerFactory,
    private val knownHostsStore: KnownHostsStore,
    private val keyContentProvider: KeyContentProvider,
) {
    /**
     * Execute a command and return structured MessageParts.
     * Internally creates an SshExecRunner and uses SshOutputMapper.
     */
    fun execute(
        profile: SshProfile,
        command: String,
        hostKeyCallback: HostKeyCallback,
        passwordProvider: PasswordProvider? = null,
        passphraseProvider: PassphraseProvider? = null,
    ): Flow<MessagePart> {
        val runner = runnerFactory.create(
            knownHostsStore, hostKeyCallback,
            passwordProvider, keyContentProvider, passphraseProvider,
        )
        val execFlow = runner.run(profile, command)
        return SshOutputMapper().map(execFlow, command)
    }
}
```

### 3.3 Mapping Layer

#### 3.3.1 Entity ↔ Model (existing, extend)

Current `Mappers.kt` has:
- `SshProfileEntity.toModel()` / `SshProfile.toEntity()`
- `ChatMessageEntity.toModel()` / `ChatMessage.toEntity(threadId)`

Missing:
- `ChatThreadEntity` → `ChatThread` mapping is inline in `RoomChatRepository` and lossy (`messages = emptyList()`).

Add to `Mappers.kt`:

```kotlin
// ── ChatThread ───────────────────────────────────────────────────────────────

/**
 * Convert [ChatThreadEntity] to a lightweight [ChatThread] (without messages).
 * Messages are loaded separately via [ChatMessageDao.observeMessages].
 */
fun ChatThreadEntity.toModel(): ChatThread = ChatThread(
    id = id,
    title = title,
    messages = emptyList(), // loaded separately
)

/**
 * Convert [ChatThreadEntity] to a [ChatThread] with messages attached.
 */
fun ChatThreadEntity.toModel(messages: List<ChatMessage>): ChatThread = ChatThread(
    id = id,
    title = title,
    messages = messages,
)
```

#### 3.3.2 DTO ↔ Model (SSH output)

`SshOutputMapper` already handles `ExecEvent` → `MessagePart`. This is the DTO→Model mapping for SSH output. No additional abstraction needed, but rename to clarify its role:

- Keep `SshOutputMapper` as-is (it already does the right thing).
- Document that `ExecEvent` is the DTO and `MessagePart` is the domain model.

#### 3.3.3 Error Mapping

Introduce a sealed error type for data layer errors:

```kotlin
// core/storage/.../DataError.kt

sealed class DataError {
    data class Database(val cause: Throwable) : DataError()
    data class NotFound(val id: String) : DataError()
    data class Ssh(val category: String, val message: String) : DataError()
    data class Validation(val field: String, val reason: String) : DataError()
}
```

Repositories return `Result<T>` which wraps either `T` or `DataError`.

### 3.4 Cache Strategy

#### 3.4.1 SSH Connection Cache

Current state: `SshjExecRunner` has internal `cachedClient`/`cachedProfileKey`, and `SshConnectionPool` exists separately but is unused.

**Plan**: Consolidate into `SshConnectionPool` with TTL + LRU.

```kotlin
// core/ssh/.../SshConnectionPool.kt  (refactored)

class SshConnectionPool(
    private val clientFactory: suspend (SshProfile) -> SSHClient,
    private val maxIdleMs: Long = 5 * 60 * 1000,   // 5 min TTL
    private val maxEntries: Int = 8,                  // LRU cap
) {
    private data class Entry(
        val client: SSHClient,
        val connectedAtMs: Long,
        var lastUsedAtMs: Long,
    )

    private val mutex = Mutex()
    private val pool = LinkedHashMap<String, Entry>(16, 0.75f, true) // access-order LRU

    suspend fun acquire(profile: SshProfile): SSHClient = mutex.withLock {
        val key = cacheKey(profile)
        val existing = pool[key]

        // Evict if stale (TTL) or disconnected
        if (existing != null) {
            val now = System.currentTimeMillis()
            val expired = (now - existing.lastUsedAtMs) > maxIdleMs
            if (expired || !existing.client.isConnected) {
                safeClose(existing.client)
                pool.remove(key)
            } else {
                existing.lastUsedAtMs = now
                return existing.client
            }
        }

        // LRU eviction if at capacity
        while (pool.size >= maxEntries) {
            val eldest = pool.entries.iterator().next()
            safeClose(eldest.value.client)
            pool.remove(eldest.key)
        }

        val client = clientFactory(profile)
        pool[key] = Entry(client, System.currentTimeMillis(), System.currentTimeMillis())
        return client
    }

    suspend fun release(profile: SshProfile) = mutex.withLock {
        pool.remove(cacheKey(profile))?.let { safeClose(it.client) }
    }

    suspend fun closeAll() = mutex.withLock {
        pool.values.forEach { safeClose(it.client) }
        pool.clear()
    }

    /** Remove idle connections exceeding TTL. Call periodically. */
    suspend fun evictStale() = mutex.withLock {
        val now = System.currentTimeMillis()
        val iterator = pool.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((now - entry.value.lastUsedAtMs) > maxIdleMs) {
                safeClose(entry.value.client)
                iterator.remove()
            }
        }
    }

    private fun cacheKey(profile: SshProfile): String =
        "[${profile.host}]:${profile.port}:${profile.username}"

    private fun safeClose(client: SSHClient) {
        try { client.disconnect() } catch (_: Exception) {}
    }
}
```

**Action**: Remove internal caching from `SshjExecRunner` (delete `cachedClient`, `cachedProfileKey`, `acquireClient`, `evictCachedClient`). Have it receive an `SSHClient` from the pool or create a fresh one per call.

#### 3.4.2 Session List Cache

Session thread lists are already reactive via Room's `Flow`. No additional memory cache needed — Room handles its own internal caching and invalidation.

#### 3.4.3 KnownHostsStore Persistence

Replace `InMemoryKnownHostsStore` with a `DataStoreKnownHostsStore` (referenced in `AppModule.kt` but not yet implemented in the ssh module). Use DataStore's `Preferences` or a JSON-serialized file.

### 3.5 DI Integration

Update Koin module to wire the new layers:

```kotlin
// app/.../di/AppModule.kt  (updated)

val appModule = module {
    // Database
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "rikka_agent.db")
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<AppDatabase>().sshProfileDao() }
    single { get<AppDatabase>().chatMessageDao() }

    // Local Data Source
    single { ChatLocalDataSource(get(), get()) }

    // Remote Data Source
    single { SshConnectionPool(clientFactory = { /* ... */ }) }
    single { SshRemoteDataSource(get(), get(), get()) }

    // Repositories
    single<ChatRepository> { RoomChatRepository(get()) }       // ChatLocalDataSource
    single<ProfileRepository> { RoomProfileRepository(get()) } // ChatLocalDataSource
    single<PreferencesStore> { AppPreferences(androidContext()) }

    // SSH infra
    single<KnownHostsStore> { DataStoreKnownHostsStore(androidContext()) }
    single<KeyContentProvider> { ContentUriKeyContentProvider(androidContext()) }
    single<SshExecRunnerFactory> { DefaultSshExecRunnerFactory }
}
```

---

## 4. Migration Checklist

### Phase 1: Interface Normalization (non-breaking)

- [ ] Add `observeProfiles()` and `observeProfile()` to `ProfileRepository` interface
- [ ] Rename `ProfileStore` → `ProfileRepository`
- [ ] Create `PreferencesStore` interface; make `AppPreferences` implement it
- [ ] Add `observeThread(threadId)` to `ChatRepository`
- [ ] Add `Result<T>` return types to all mutation methods

### Phase 2: DataSource Extraction

- [ ] Create `ChatLocalDataSource` wrapping both DAOs
- [ ] Create `SshRemoteDataSource` wrapping `SshExecRunner` + `SshOutputMapper`
- [ ] Refactor `RoomChatRepository` to use `ChatLocalDataSource` instead of raw DAO
- [ ] Refactor `RoomProfileRepository` to use `ChatLocalDataSource`

### Phase 3: Mapper Completion

- [ ] Add `ChatThreadEntity.toModel()` and `toModel(messages)` to `Mappers.kt`
- [ ] Create `DataError` sealed class
- [ ] Move error mapping from `SshjExecRunner` catch blocks to a shared utility

### Phase 4: Cache Consolidation

- [ ] Refactor `SshConnectionPool` with TTL + LRU
- [ ] Remove internal caching from `SshjExecRunner`
- [ ] Implement `DataStoreKnownHostsStore` (replacing `InMemoryKnownHostsStore`)
- [ ] Add periodic `evictStale()` call (e.g., on app foreground)

### Phase 5: DI Update

- [ ] Update Koin module with new bindings
- [ ] Verify all ViewModel injection points compile

---

## 5. Files to Modify

| File | Action |
|------|--------|
| `core/storage/.../storage/ProfileStore.kt` | Rename to `ProfileRepository.kt`; add `observeProfiles()`, `observeProfile()` |
| `core/storage/.../storage/RoomProfileStore.kt` | Rename to `RoomProfileRepository.kt`; use `@Inject`; delegate to `ChatLocalDataSource` |
| `core/storage/.../storage/ChatRepository.kt` | Add `Result<T>`, `observeThread()`; refactor impl to use `ChatLocalDataSource` |
| `core/storage/.../storage/AppPreferences.kt` | Extract `PreferencesStore` interface; implement it |
| `core/storage/.../storage/db/Mappers.kt` | Add `ChatThreadEntity.toModel()` variants |
| `core/storage/.../storage/local/ChatLocalDataSource.kt` | **New**: wraps DAOs |
| `core/ssh/.../ssh/SshConnectionPool.kt` | Refactor with TTL + LRU |
| `core/ssh/.../ssh/SshjExecRunner.kt` | Remove internal caching |
| `core/ssh/.../ssh/SshRemoteDataSource.kt` | **New**: wraps exec + mapper |
| `core/storage/.../storage/DataError.kt` | **New**: sealed error type |
| `app/.../di/AppModule.kt` | Update bindings |

---

## 6. Design Decisions

| Decision | Rationale |
|----------|-----------|
| `Result<T>` over sealed class returns | Kotlin stdlib `Result` is idiomatic, composable, and supports `runCatching`. A `DataError` sealed class provides typed errors inside the `Result`. |
| Keep `ChatRepository` separate from `ProfileRepository` | They serve different domains (chat threads vs. SSH profiles). Single Responsibility. |
| `LocalDataSource` wraps multiple DAOs | One data source per storage origin. Both DAOs live in Room, so one `ChatLocalDataSource` is sufficient. |
| `SshOutputMapper` stays in `core/ssh` | It maps SSH-specific `ExecEvent` to domain `MessagePart`. Belongs with the SSH module. |
| `SshConnectionPool` as the single cache | Eliminate duplication. `SshjExecRunner` becomes stateless (no cached client). |
| Memory cache unnecessary for thread lists | Room + Flow already provides reactive caching with automatic invalidation. |
