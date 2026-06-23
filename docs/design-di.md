# RikkaAgent 依赖注入架构审查与改进方案

日期: 2026-06-23

## 1. 现状审计

### 1.1 当前模块结构

```
appModule (单一巨型模块)
  ├── Room.databaseBuilder → AppDatabase
  ├── DAO 实例 (sshProfileDao, chatMessageDao)
  ├── RoomChatRepository → ChatRepository (接口绑定)
  ├── RoomProfileStore → ProfileStore (接口绑定)
  ├── AppPreferences (具体类，无接口)
  ├── DataStoreKnownHostsStore → KnownHostsStore
  ├── ContentUriKeyContentProvider → KeyContentProvider
  └── DefaultSshExecRunnerFactory → SshExecRunnerFactory

viewModelModule
  ├── ChatViewModel(profileId, get(), get(), get(), get(), get(), get(), androidApplication())
  ├── ProfilesViewModel(get())
  ├── ProfileEditorViewModel(profileId, get(), androidApplication())
  └── SettingsViewModel(get())
```

### 1.2 依赖图（实际）

```
ChatViewModel
  ├── ProfileStore           (接口 ✓)
  ├── KnownHostsStore        (接口 ✓)
  ├── ChatRepository         (接口 ✓)
  ├── AppPreferences         (具体类 ✗ — DIP 违反)
  ├── KeyContentProvider?    (接口 ✓)
  ├── SshExecRunnerFactory   (接口 ✓, 默认参数 DefaultSshExecRunnerFactory ✗)
  └── Application            (Android 框架类 ✗ — 无法脱离 Android 测试)

CommandExecutor
  ├── ProfileStore           (接口 ✓)
  ├── KnownHostsStore        (接口 ✓)
  ├── AppPreferences         (具体类 ✗)
  ├── KeyContentProvider?    (接口 ✓)
  ├── SshExecRunnerFactory   (接口 ✓)
  ├── AuthCallbackBroker     (具体类 ✗ — 但属于内部协调器，可接受)
  ├── Application            (Android 框架类 ✗)
  └── CoroutineScope         (注入 ✓)
```

## 2. 问题清单

### 2.1 依赖反转违反 (DIP)

| 问题 | 位置 | 严重程度 |
|------|------|----------|
| `AppPreferences` 是具体类，无接口 | ChatViewModel, CommandExecutor, SettingsViewModel | **High** — 无法提供 Fake 实现，测试必须依赖 Android DataStore |
| `ProfilesViewModel` 依赖 `RoomProfileStore` 而非 `ProfileStore` | ProfilesViewModel | **High** — `observeProfiles()` 仅存在于具体类，接口缺失此方法 |
| `Application` 直接注入 ViewModel | ChatViewModel, ProfileEditorViewModel | **Medium** — 字符串资源访问阻碍纯 JVM 测试 |
| `ChatViewModel` 构造器默认参数引用具体实现 | `runnerFactory: SshExecRunnerFactory = DefaultSshExecRunnerFactory` | **Low** — Koin 注入时覆盖，但默认值泄漏实现 |

### 2.2 接口隔离 (ISP)

| 接口 | 方法数 | 评估 |
|------|--------|------|
| `ChatRepository` | 8 | **可接受** — 线程 CRUD + 消息 CRUD 天然耦合，拆分增加间接层无实际收益 |
| `ProfileStore` | 4 | 粒度合适 |
| `KnownHostsStore` | 4 | 粒度合适 |
| `SshExecRunner` | 1 | 最小接口 |
| `KeyContentProvider` | 1 | fun interface，最小 |
| `AppPreferences` | N/A | **无接口** — 需要抽取 |

`ChatRepository` 可以拆为 `ThreadRepository` (4 methods) + `MessageRepository` (4 methods)，但消息的生命周期绑定在线程上，拆分后调用方需要同时注入两个 Repository 且手动保证一致性。**不建议拆分**。

### 2.3 Koin 模块组织

当前 `appModule` 混合了三个关注点：
- **data** — Room DAO、Repository 实现、DataStore
- **domain** — 接口绑定（ChatRepository, ProfileStore 等）
- **presentation** — 无（ViewModel 在 viewModelModule）

问题：
1. 模块不可独立替换 — 无法在测试中只替换 data 层
2. `androidContext()` 耦合 — 整个模块依赖 Android 上下文，纯 JVM 测试无法使用

### 2.4 测试替身重复

| Fake 类 | 定义位置 | 重复次数 |
|---------|----------|----------|
| `FakeProfileStore` | ChatViewModelTest, CommandExecutorTest, ChatScreenShareDispatchTest, ProfileEditorViewModelTest | **4** |
| `FakeChatRepository` | ChatViewModelTest, CommandExecutorTest, ChatScreenShareDispatchTest, ChatSessionManagerTest | **4** |
| `FakeKnownHostsStore` | ChatViewModelTest, CommandExecutorTest, ChatScreenShareDispatchTest | **3** |

每个 Fake 实现逻辑几乎相同（LinkedHashMap + MutableStateFlow），但互相独立、不可复用。

## 3. 改进方案

### 3.1 修复 `ProfileStore` 接口缺失

`RoomProfileStore.observeProfiles(): Flow<List<SshProfile>>` 是 `ProfilesViewModel` 的必需方法，但未定义在 `ProfileStore` 接口中，导致 ViewModel 直接依赖具体类。

在 `ProfileStore` 接口中添加：

```kotlin
// core/storage/src/main/kotlin/io/rikka/agent/storage/ProfileStore.kt
interface ProfileStore {
  fun observeProfiles(): Flow<List<SshProfile>>  // 新增
  suspend fun listProfiles(): List<SshProfile>
  suspend fun getById(id: String): SshProfile?
  suspend fun upsert(profile: SshProfile)
  suspend fun delete(profileId: String)
}
```

`RoomProfileStore` 已有此方法，只需从 `fun` 改为 `override fun`。
`FakeProfileStore` 需补充实现：`override fun observeProfiles() = MutableStateFlow(profiles.values.toList())`。

`ProfilesViewModel` 构造器从 `RoomProfileStore` 改为 `ProfileStore`。

### 3.2 抽取 `PreferencesStore` 接口

将 `AppPreferences` 的读写契约抽取为纯 Kotlin 接口，放在 `core/storage` 模块：

```kotlin
// core/storage/src/main/kotlin/io/rikka/agent/storage/PreferencesStore.kt
package io.rikka.agent.storage

import kotlinx.coroutines.flow.Flow

/**
 * User-configurable preferences. Pure Kotlin interface — no Android dependency.
 * Implementations: AppPreferences (DataStore-backed), FakePreferencesStore (in-memory).
 */
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

`AppPreferences` 实现此接口：

```kotlin
// 改造后的 AppPreferences 签名
class AppPreferences(
  private val dataStore: DataStore<Preferences>,
) : PreferencesStore { ... }
```

所有 ViewModel / Use Case 依赖 `PreferencesStore` 而非 `AppPreferences`。

### 3.3 抽取 `StringProvider` 接口替代 `Application`

```kotlin
// core/model/src/main/kotlin/io/rikka/agent/model/StringProvider.kt
package io.rikka.agent.model

/**
 * Abstraction over Android string resources. Allows pure JVM testing.
 */
fun interface StringProvider {
  fun getString(resId: Int, vararg args: Any): String
}
```

在 DI 层绑定：

```kotlin
single<StringProvider> {
  val app = androidContext() as Application
  StringProvider { resId, args ->
    if (args.isEmpty()) app.getString(resId)
    else app.getString(resId, *args)
  }
}
```

`ChatViewModel` 和 `CommandExecutor` 改为依赖 `StringProvider`。

### 3.4 Koin 模块分层

```
dataModule       — Room DAO, Room 实现, DataStore, Android 特有 Provider
domainModule     — 接口绑定 (Repository/Store/Provider)
presentationModule — ViewModel
```

```kotlin
// dataModule.kt
val dataModule = module {
  // Database
  single {
    Room.databaseBuilder(androidContext(), AppDatabase::class.java, "rikka_agent.db")
      .addMigrations(*Migrations.ALL)
      .fallbackToDestructiveMigration()
      .build()
  }
  single { get<AppDatabase>().sshProfileDao() }
  single { get<AppDatabase>().chatMessageDao() }

  // Repository implementations
  single { RoomChatRepository(get()) }
  single { RoomProfileStore(get()) }

  // Preferences (concrete → interface binding lives in domainModule)

  // SSH infrastructure
  single { DataStoreKnownHostsStore(androidContext()) }
  single { ContentUriKeyContentProvider(androidContext()) }
  single { DefaultSshExecRunnerFactory as SshExecRunnerFactory }
}

// domainModule.kt
val domainModule = module {
  // Interface bindings
  single<ChatRepository> { get<RoomChatRepository>() }
  single<ProfileStore> { get<RoomProfileStore>() }
  single<PreferencesStore> { AppPreferences(androidContext()) }
  single<KnownHostsStore> { get<DataStoreKnownHostsStore>() }
  single<KeyContentProvider> { get<ContentUriKeyContentProvider>() }

  // String resource abstraction
  single<StringProvider> {
    val app = androidContext() as Application
    StringProvider { resId, args ->
      if (args.isEmpty()) app.getString(resId)
      else app.getString(resId, *args)
    }
  }
}

// presentationModule.kt
val presentationModule = module {
  viewModel { (profileId: String) ->
    ChatViewModel(profileId, get(), get(), get(), get(), get(), get(), get())
  }
  viewModel { ProfilesViewModel(get<ProfileStore>()) }
  viewModel { (profileId: String?) ->
    ProfileEditorViewModel(profileId, get(), get<StringProvider>())
  }
  viewModel { SettingsViewModel(get<PreferencesStore>()) }
}
```

应用启动时：

```kotlin
// RikkaAgentApp.kt
startKoin {
  androidContext(this@RikkaAgentApp)
  modules(dataModule, domainModule, presentationModule)
}
```

### 3.5 集中测试替身

在 `core/test-fixtures` 或各 core 模块的 `src/testFixtures` 中提供共享 Fake：

```kotlin
// core/storage/src/testFixtures/kotlin/io/rikka/agent/storage/FakeChatRepository.kt
package io.rikka.agent.storage

import io.rikka.agent.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeChatRepository : ChatRepository {
  private val threads = linkedMapOf<String, ChatThread>()
  private val messagesByThread = linkedMapOf<String, MutableList<ChatMessage>>()
  private val threadFlow = MutableStateFlow(emptyList<ChatThread>())
  private var nextThreadId = 1

  override fun observeThreads(profileId: String): Flow<List<ChatThread>> = threadFlow

  override suspend fun createThread(profileId: String, title: String): String {
    val id = "thread-${nextThreadId++}"
    threads[id] = ChatThread(id = id, title = title, messages = emptyList())
    messagesByThread[id] = mutableListOf()
    emitThreads()
    return id
  }

  override suspend fun deleteThread(threadId: String) {
    threads.remove(threadId)
    messagesByThread.remove(threadId)
    emitThreads()
  }

  override suspend fun insertMessage(threadId: String, message: ChatMessage) {
    messagesByThread.getOrPut(threadId) { mutableListOf() }.add(message)
  }

  override suspend fun updateMessage(id: String, parts: List<MessagePart>, status: MessageStatus) {
    for (list in messagesByThread.values) {
      val idx = list.indexOfFirst { it.id == id }
      if (idx >= 0) { list[idx] = list[idx].copy(parts = parts, status = status); return }
    }
  }

  override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
    kotlinx.coroutines.flow.flowOf(messagesByThread[threadId].orEmpty().toList())

  override suspend fun getMessages(threadId: String): List<ChatMessage> =
    messagesByThread[threadId].orEmpty().toList()

  override suspend fun updateThreadTitle(threadId: String, title: String) {
    val existing = threads[threadId] ?: return
    threads[threadId] = existing.copy(title = title)
    emitThreads()
  }

  private fun emitThreads() { threadFlow.update { threads.values.toList() } }
}
```

```kotlin
// core/storage/src/testFixtures/kotlin/io/rikka/agent/storage/FakeProfileStore.kt
package io.rikka.agent.storage

import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeProfileStore(
  initialProfile: SshProfile? = null,
) : ProfileStore {
  private val profiles = linkedMapOf<String, SshProfile>().apply {
    initialProfile?.let { put(it.id, it) }
  }
  private val profilesFlow = MutableStateFlow(profiles.values.toList())

  private fun emit() { profilesFlow.update { profiles.values.toList() } }

  override fun observeProfiles(): Flow<List<SshProfile>> = profilesFlow
  override suspend fun listProfiles(): List<SshProfile> = profiles.values.toList()
  override suspend fun getById(id: String): SshProfile? = profiles[id]
  override suspend fun upsert(profile: SshProfile) { profiles[profile.id] = profile; emit() }
  override suspend fun delete(profileId: String) { profiles.remove(profileId); emit() }
}
```

```kotlin
// core/storage/src/testFixtures/kotlin/io/rikka/agent/storage/FakePreferencesStore.kt
package io.rikka.agent.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePreferencesStore : PreferencesStore {
  override val theme = MutableStateFlow("system")
  override val dynamicColor = MutableStateFlow(false)
  override val defaultShell = MutableStateFlow("/bin/bash")
  override val lastProfileId = MutableStateFlow<String?>(null)
  override val enableMermaid = MutableStateFlow(false)

  override suspend fun setTheme(value: String) { theme.value = value }
  override suspend fun setDynamicColor(value: Boolean) { dynamicColor.value = value }
  override suspend fun setDefaultShell(value: String) { defaultShell.value = value }
  override suspend fun setLastProfileId(value: String) { lastProfileId.value = value }
  override suspend fun setEnableMermaid(value: Boolean) { enableMermaid.value = value }
}
```

```kotlin
// core/ssh/src/testFixtures/kotlin/io/rikka/agent/ssh/FakeSshExecRunnerFactory.kt
package io.rikka.agent.ssh

/**
 * Factory that returns a pre-configured [ClosableSshExecRunner].
 * All create() calls return the same runner instance.
 */
class FakeSshExecRunnerFactory(
  private val runner: ClosableSshExecRunner,
) : SshExecRunnerFactory {
  override fun create(
    knownHostsStore: KnownHostsStore,
    hostKeyCallback: HostKeyCallback,
    passwordProvider: PasswordProvider?,
    keyContentProvider: KeyContentProvider?,
    passphraseProvider: PassphraseProvider?,
  ): ClosableSshExecRunner = runner
}
```

`InMemoryKnownHostsStore` 已存在于 `core/ssh/src/main`，可直接复用。

`FakeStringProvider` 用于纯 JVM 测试：

```kotlin
// core/model/src/testFixtures/kotlin/io/rikka/agent/model/FakeStringProvider.kt
package io.rikka.agent.model

/**
 * Returns pattern "res:$resId" for any string resource request.
 * Tests can assert on this deterministic pattern instead of localized strings.
 */
class FakeStringProvider : StringProvider {
  override fun getString(resId: Int, vararg args: Any): String =
    if (args.isEmpty()) "res:$resId" else "res:$resId:${args.joinToString(",")}"
}
```

### 3.6 测试 DI 配置

```kotlin
// app/src/test/java/io/rikka/agent/di/TestModules.kt
package io.rikka.agent.di

import io.rikka.agent.model.FakeStringProvider
import io.rikka.agent.model.StringProvider
import io.rikka.agent.ssh.*
import io.rikka.agent.storage.*
import org.koin.dsl.module

/**
 * Koin modules for pure JVM tests. No Android dependency.
 */
fun testModules(
  profileStore: ProfileStore = FakeProfileStore(),
  chatRepository: ChatRepository = FakeChatRepository(),
  preferencesStore: PreferencesStore = FakePreferencesStore(),
  knownHostsStore: KnownHostsStore = InMemoryKnownHostsStore(),
  keyContentProvider: KeyContentProvider? = null,
  runnerFactory: SshExecRunnerFactory = error("Provide a FakeSshExecRunnerFactory"),
  stringProvider: StringProvider = FakeStringProvider(),
) = module {
  single<ProfileStore> { profileStore }
  single<ChatRepository> { chatRepository }
  single<PreferencesStore> { preferencesStore }
  single<KnownHostsStore> { knownHostsStore }
  single<KeyContentProvider?> { keyContentProvider }
  single<SshExecRunnerFactory> { runnerFactory }
  single<StringProvider> { stringProvider }
}
```

## 4. 重构后完整 Koin 模块代码

### 4.1 `dataModule.kt`

```kotlin
// app/src/main/java/io/rikka/agent/di/DataModule.kt
package io.rikka.agent.di

import androidx.room.Room
import io.rikka.agent.ssh.ContentUriKeyContentProvider
import io.rikka.agent.ssh.DataStoreKnownHostsStore
import io.rikka.agent.ssh.DefaultSshExecRunnerFactory
import io.rikka.agent.ssh.SshExecRunnerFactory
import io.rikka.agent.storage.RoomChatRepository
import io.rikka.agent.storage.RoomProfileStore
import io.rikka.agent.storage.db.AppDatabase
import io.rikka.agent.storage.db.Migrations
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Data layer bindings: Room database, DAOs, Room-backed repository implementations,
 * DataStore-backed stores, and Android-specific SSH infrastructure.
 *
 * This module depends on Android context and cannot be used in pure JVM tests.
 */
val dataModule = module {
  // ── Room ───────────────────────────────────────────────────────────────
  single {
    Room.databaseBuilder(
      androidContext(),
      AppDatabase::class.java,
      "rikka_agent.db",
    ).addMigrations(*Migrations.ALL)
     .fallbackToDestructiveMigration()
     .build()
  }
  single { get<AppDatabase>().sshProfileDao() }
  single { get<AppDatabase>().chatMessageDao() }

  // ── Room-backed implementations ────────────────────────────────────────
  single { RoomChatRepository(get()) }
  single { RoomProfileStore(get()) }

  // ── SSH infrastructure (Android-specific) ──────────────────────────────
  single { DataStoreKnownHostsStore(androidContext()) }
  single { ContentUriKeyContentProvider(androidContext()) }
  single<SshExecRunnerFactory> { DefaultSshExecRunnerFactory }
}
```

### 4.2 `domainModule.kt`

```kotlin
// app/src/main/java/io/rikka/agent/di/DomainModule.kt
package io.rikka.agent.di

import android.app.Application
import io.rikka.agent.model.StringProvider
import io.rikka.agent.ssh.ContentUriKeyContentProvider
import io.rikka.agent.ssh.DataStoreKnownHostsStore
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.PreferencesStore
import io.rikka.agent.storage.ProfileStore
import io.rikka.agent.storage.RoomChatRepository
import io.rikka.agent.storage.RoomProfileStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Domain layer bindings: interface-to-implementation mappings.
 *
 * Presentation layer depends only on these interfaces.
 * Swap this module with [testDomainModule] in tests to inject fakes.
 */
val domainModule = module {
  // ── Repository interfaces ──────────────────────────────────────────────
  single<ChatRepository> { get<RoomChatRepository>() }
  single<ProfileStore> { get<RoomProfileStore>() }

  // ── Preferences ────────────────────────────────────────────────────────
  single<PreferencesStore> { AppPreferences(androidContext()) }

  // ── SSH interfaces ─────────────────────────────────────────────────────
  single<KnownHostsStore> { get<DataStoreKnownHostsStore>() }
  single<KeyContentProvider> { get<ContentUriKeyContentProvider>() }

  // ── String resources ───────────────────────────────────────────────────
  single<StringProvider> {
    val app = androidContext() as Application
    StringProvider { resId, args ->
      if (args.isEmpty()) app.getString(resId)
      else app.getString(resId, *args)
    }
  }
}
```

### 4.3 `presentationModule.kt`

```kotlin
// app/src/main/java/io/rikka/agent/di/PresentationModule.kt
package io.rikka.agent.di

import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.vm.ProfileEditorViewModel
import io.rikka.agent.vm.ProfilesViewModel
import io.rikka.agent.vm.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Presentation layer bindings: ViewModels.
 *
 * All ViewModels depend on interfaces from [domainModule], never on data-layer implementations.
 */
val presentationModule = module {
  viewModel { (profileId: String) ->
    ChatViewModel(
      profileId = profileId,
      profileStore = get(),
      knownHostsStore = get(),
      chatRepository = get(),
      preferencesStore = get(),
      keyContentProvider = get(),
      runnerFactory = get(),
      stringProvider = get(),
    )
  }

  viewModel { ProfilesViewModel(get<ProfileStore>()) }

  viewModel { (profileId: String?) ->
    ProfileEditorViewModel(profileId, get(), get<StringProvider>())
  }

  viewModel { SettingsViewModel(get<PreferencesStore>()) }
}
```

### 4.4 改造后的 `ChatViewModel` 构造器

```kotlin
class ChatViewModel(
  private val profileId: String,
  profileStore: ProfileStore,
  knownHostsStore: KnownHostsStore,
  chatRepository: ChatRepository,
  preferencesStore: PreferencesStore,  // 接口，非具体类
  keyContentProvider: KeyContentProvider? = null,
  runnerFactory: SshExecRunnerFactory,
  private val stringProvider: StringProvider,  // 接口，非 Application
) : ViewModel() {
  // ...
  // 替换所有 app.getString(R.string.xxx) 为 stringProvider.getString(R.string.xxx)
  // 替换所有 appPreferences 为 preferencesStore
}
```

### 4.5 改造后的 `RikkaAgentApp`

```kotlin
class RikkaAgentApp : Application() {
  override fun onCreate() {
    super.onCreate()
    if (GlobalContext.getOrNull() == null) {
      startKoin {
        androidContext(this@RikkaAgentApp)
        modules(dataModule, domainModule, presentationModule)
      }
    }
  }
}
```

## 5. 变更影响矩阵

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `core/storage/.../PreferencesStore.kt` | **新增** | 纯 Kotlin 接口 |
| `core/storage/.../ProfileStore.kt` | **修改** | 添加 `observeProfiles()` 方法 |
| `core/storage/.../RoomProfileStore.kt` | **修改** | `observeProfiles` 添加 `override` |
| `core/storage/.../AppPreferences.kt` | **修改** | implements PreferencesStore |
| `core/model/.../StringProvider.kt` | **新增** | fun interface |
| `app/.../di/DataModule.kt` | **新增** | Room + DataStore 绑定 |
| `app/.../di/DomainModule.kt` | **新增** | 接口绑定 |
| `app/.../di/PresentationModule.kt` | **新增** | ViewModel 绑定 |
| `app/.../di/AppModule.kt` | **删除** | 拆分为上述三个模块 |
| `app/.../di/ViewModelModule.kt` | **删除** | 合并到 presentationModule |
| `app/.../RikkaAgentApp.kt` | **修改** | modules(appModule, viewModelModule) → modules(dataModule, domainModule, presentationModule) |
| `app/.../vm/ChatViewModel.kt` | **修改** | AppPreferences → PreferencesStore, Application → StringProvider |
| `app/.../vm/CommandExecutor.kt` | **修改** | AppPreferences → PreferencesStore, Application → StringProvider |
| `app/.../vm/ProfilesViewModel.kt` | **修改** | RoomProfileStore → ProfileStore |
| `app/.../vm/SettingsViewModel.kt` | **修改** | AppPreferences → PreferencesStore |
| `app/.../vm/ProfileEditorViewModel.kt` | **修改** | Application → StringProvider |
| `core/storage/testFixtures/.../FakeChatRepository.kt` | **新增** | 共享测试替身 |
| `core/storage/testFixtures/.../FakeProfileStore.kt` | **新增** | 共享测试替身 |
| `core/storage/testFixtures/.../FakePreferencesStore.kt` | **新增** | 共享测试替身 |
| `core/model/testFixtures/.../FakeStringProvider.kt` | **新增** | 共享测试替身 |
| `app/src/test/.../di/TestModules.kt` | **新增** | 测试用 Koin 模块工厂 |
| 各 test 文件 | **修改** | 删除内联 Fake，改用共享版 |

## 6. 迁移步骤

1. **新增接口**：`PreferencesStore`、`StringProvider` — 零风险，纯增量
2. **修复 `ProfileStore` 接口**：添加 `observeProfiles()` — 同步修改 `RoomProfileStore` 的 `override` 标记
3. **修改 `AppPreferences`**：添加 `: PreferencesStore` — 现有代码不受影响（具体类仍然可用）
4. **新增共享 Fake**：在 `testFixtures` source set 中创建 — 纯增量
5. **拆分 Koin 模块**：新建 `dataModule`/`domainModule`/`presentationModule`，修改 `RikkaAgentApp` — 原子切换
6. **改造 ViewModel 构造器**：逐个替换 `AppPreferences` → `PreferencesStore`、`Application` → `StringProvider`、`RoomProfileStore` → `ProfileStore`
7. **清理测试**：删除各 test 文件中的内联 Fake，改用 `testFixtures` 共享版

每步独立可回滚。

## 7. 不建议的变更

| 提议 | 理由 |
|------|------|
| 拆分 `ChatRepository` → `ThreadRepository` + `MessageRepository` | 消息生命周期绑定在线程上，拆分后调用方需手动保证一致性，增加复杂度 |
| 将 `AuthCallbackBroker` 抽象为接口 | 它是 ViewModel 内部协调器，不跨层传递，保持具体类即可 |
| 为 `SshConnectionPool` 抽象接口 | 仅在 `SshjExecRunner` 内部使用，不影响 DI 边界 |
