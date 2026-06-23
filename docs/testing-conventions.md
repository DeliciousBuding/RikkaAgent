# Testing Conventions

本文档定义 RikkaAgent 项目的测试编写规范。所有新测试必须遵循这些约定。

---

## 1. Naming Conventions

### 1.1 Test Classes

格式：`{ClassUnderTest}Test`

```
ChatViewModelTest          -> tests for ChatViewModel
CommandComposerTest        -> tests for CommandComposer
InMemoryKnownHostsStoreTest -> tests for InMemoryKnownHostsStore
```

规则：
- 每个被测类对应一个测试类，放在同 package 下。
- 测试类名以 `Test` 结尾，不加 `Spec` 或 `Should` 等前缀。
- 测试文件位置与被测源码对齐：
  - `core/model/src/test/kotlin/io/rikka/agent/model/SshProfileTest.kt`
  - `app/src/test/java/io/rikka/agent/vm/ChatViewModelTest.kt`

### 1.2 Test Methods

使用 Kotlin 反引号描述式命名，格式：`` `行为描述` ``

```kotlin
@Test
fun `shell quote escapes single quote`() { ... }

@Test
fun `init loads profile label and ready system message`() = runTest(dispatcher) { ... }

@Test
fun `mismatch dialog shows replace action and triggers accept`() { ... }
```

规则：
- 使用反引号包裹的自然语言句子，描述被测行为而非实现细节。
- 句子以动词开头（`loads`、`returns`、`emits`、`maps`、`streams`）。
- 不使用 `test` 前缀（`testShellQuote` 违反约定）。
- 不使用 camelCase（`shellQuoteEscapesSingleQuote` 违反约定）。

### 1.3 Fake / Stub Classes

格式：`Fake{InterfaceName}` 或 `{描述}ExecRunnerFactory`

```kotlin
private class FakeProfileStore(...) : ProfileStore { ... }
private class FakeChatRepository(...) : ChatRepository { ... }
private class FakeKnownHostsStore : KnownHostsStore { ... }
private class RecordingExecRunnerFactory(...) : SshExecRunnerFactory { ... }
private class HangingExecRunnerFactory : SshExecRunnerFactory { ... }
```

规则：
- Fake 实现接口的最小行为，不做真实 I/O。
- 以 `Fake` 前缀命名简单的桩实现。
- 以描述性名称命名有特定行为的工厂（`Recording...`、`Hanging...`）。
- Fake 类定义在测试类内部（`private class`），不共享跨测试文件。
- 如果多个测试文件需要同一 Fake，提取到 `testFixtures` 或共享 `src/test` 目录。

---

## 2. Test Structure: Given-When-Then

所有测试遵循 Given-When-Then 结构。不要求显式注释，但代码顺序必须清晰可辨。

### 2.1 Standard Layout

```kotlin
@Test
fun `execute maps timeout error to friendly message`() = runTest(dispatcher) {
    // Given
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
        listOf(ExecEvent.Error("timeout", "socket timeout"))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    // When
    executor.execute(
        command = "slow-cmd",
        assistantId = "a-1",
        isCodex = false,
        updateContent = { _, _ -> },
        updateMessage = { _, content, status -> finals += content to status },
        getAssistantContent = { null },
    )
    advanceUntilIdle()

    // Then
    assertEquals(MessageStatus.Error, finals[0].second)
    assertEquals(app.getString(R.string.err_timeout), finals[0].first)
}
```

### 2.2 When Section Is Optional

纯断言测试（模型默认值、序列化 round-trip）可省略 When：

```kotlin
@Test
fun `SshProfile default port is 22`() {
    // Given + Then (implicit When: construction)
    val profile = SshProfile(id = "p1", name = "t", host = "h", username = "u")
    assertEquals(22, profile.port)
}
```

### 2.3 One Behavior Per Test

每个 `@Test` 方法只验证一个行为。多个相关断言属于同一行为是允许的，但多个独立场景必须拆分。

---

## 3. Test Toolchain

### 3.1 Unit Tests

| Tool | Version | Module | Purpose |
|------|---------|--------|---------|
| JUnit 4 | 4.13.2 | all | Test runner, assertions |
| kotlinx-coroutines-test | 1.8.1 | app, core:model, core:ssh | `runTest`, `advanceUntilIdle`, `TestDispatcher` |
| Robolectric | 4.14.1 | app | Android context for ViewModel tests |
| Compose UI Test JUnit4 | via BOM 2024.09.00 | app | `createComposeRule` for composable tests |
| Turbine | 1.1.0 | core:ssh | Flow assertion (available, use when testing cold flows) |

**Key dependency declarations** (from `gradle/libs.versions.toml`):

```toml
junit4 = { module = "junit:junit", version.ref = "junit4" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines-test" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
```

### 3.2 Instrumentation Tests

| Tool | Version | Module | Purpose |
|------|---------|--------|---------|
| AndroidX Test Core | 1.6.1 | app, core:storage | `ApplicationProvider` |
| AndroidX Test Ext JUnit | 1.2.1 | app | `AndroidJUnit4` runner |
| Espresso Intents | 3.6.1 | app | Intent stubbing (SAF picker) |
| Room Testing | 2.6.1 | core:storage | Migration testing |

### 3.3 Test Execution Commands

```bash
# Fast baseline (core + app unit tests)
./gradlew :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :app:testDevDebugUnitTest

# Full unit test sweep
./gradlew test

# Instrumentation (requires device/emulator)
./gradlew :app:connectedDevDebugAndroidTest

# Coverage report (app module, devDebug)
./gradlew :app:jacocoTestReportDevDebugUnitTest
```

### 3.4 Coroutine Test Pattern

ViewModel 和 CommandExecutor 测试使用 `StandardTestDispatcher` + `runTest`：

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        // ... setup
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `some behavior`() = runTest(dispatcher) {
        // ...
        advanceUntilIdle()
        // assertions
    }
}
```

规则：
- 必须在 `@Before` 中 `Dispatchers.setMain(dispatcher)`。
- 必须在 `@After` 中 `Dispatchers.resetMain()`。
- 使用 `advanceUntilIdle()` 推进所有挂起的协程。
- 使用 `backgroundScope` 作为需要独立 scope 的组件的注入参数。

### 3.5 Compose UI Test Pattern

Robolectric 环境下的 Compose 测试：

```kotlin
@RunWith(RobolectricTestRunner::class)
class HostKeyDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `mismatch dialog shows replace action and triggers accept`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var accepted = false

        composeRule.setContent {
            MaterialTheme {
                HostKeyDialog(
                    event = HostKeyEvent.Mismatch(...),
                    onAccept = { accepted = true },
                    onReject = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.btn_replace_trust))
            .assertIsDisplayed()
            .performClick()

        assertTrue(accepted)
    }
}
```

Instrumentation 环境下的 Compose 测试（需设备/模拟器）：

```kotlin
@RunWith(AndroidJUnit4::class)
class ProfileEditorSafPickerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun safPickerPersistsPermissionAndStoresKeyRef() {
        // Espresso intent stubbing + compose interaction
    }
}
```

### 3.6 Flow Testing with Turbine

Turbine 在 `:core:ssh` 中可用，适用于冷 Flow 的逐值断言：

```kotlin
@Test
fun `some flow emits expected values`() = runTest {
    val flow = someFunctionReturningFlow()

    flow.test {
        assertEquals(expected1, awaitItem())
        assertEquals(expected2, awaitItem())
        awaitComplete()
    }
}
```

当前项目中 Turbine 使用频率较低，大部分 Flow 测试通过 `launch { flow.collect {} }` + 手动断言完成。两种方式均可接受，但新测试优先使用 Turbine 以提高可读性。

---

## 4. Coverage Requirements

### 4.1 JaCoCo Configuration

JaCoCo 已在 `:app` 模块配置，报告通过以下命令生成：

```bash
./gradlew :app:jacocoTestReportDevDebugUnitTest
```

报告位置：
- XML: `app/build/reports/jacoco/jacocoTestReportDevDebugUnitTest/jacocoTestReportDevDebugUnitTest.xml`
- HTML: `app/build/reports/jacoco/jacocoTestReportDevDebugUnitTest/`
- CSV: `app/build/reports/jacoco/jacocoTestReportDevDebugUnitTest/jacocoTestReportDevDebugUnitTest.csv`

排除的类（不计入覆盖率）：
- `**/R.class`, `**/R$*.class`
- `**/BuildConfig.class`
- `**/Manifest*.*`

### 4.2 Coverage Targets

| Layer | Target | Rationale |
|-------|--------|-----------|
| `:core:model` | 90%+ | 纯数据类，无 Android 依赖，测试成本低 |
| `:core:ssh` | 85%+ | SSH 协议交互难以完全覆盖，但解析器和状态机必须高覆盖 |
| `:core:storage` | 80%+ | Room DAO 测试依赖 instrumented tests，成本较高 |
| `:core:ui` | 70%+ | Compose 组件测试以关键交互为主，不追求行覆盖 |
| `:app` (ViewModel) | 85%+ | ViewModel 是核心业务编排层 |
| `:app` (Screen) | 60%+ | 屏幕级测试以集成场景为主 |

### 4.3 What Must Be Tested

优先级从高到低：

1. **纯函数和转换器**：`CommandComposer`、`JsonlParser`、`OutputFormatter`、`CodexEventMapper`、`Mappers`
2. **状态机和生命周期**：`CommandExecutor`、`ChatSessionManager`、`HostKeyDialogStateMachine`
3. **接口契约**：`KnownHostsStore`、`ProfileStore`、`ChatRepository` 的各实现
4. **ViewModel 编排**：`ChatViewModel` 的完整用户场景
5. **UI 交互**：关键对话框（Host Key、密码输入）的点击流
6. **数据库迁移**：Room migration 必须有 instrumented test

### 4.4 What Does Not Need Testing

- Android Framework 类（`Application`、`ContentProvider` 的系统回调路径）
- Koin DI 模块定义本身
- Compose 预览函数（`@Preview`）
- 生成代码（Room DAO 实现、KSP 产物）

---

## 5. Test Data Management

### 5.1 Inline Test Data

简单的输入数据直接内联在测试方法中：

```kotlin
@Test
fun `shell quote escapes single quote`() {
    assertEquals("'a'\\''b'", CommandComposer.shellQuote("a'b"))
}
```

### 5.2 Shared Fixtures via setUp

当多个测试方法共享相同的初始状态时，通过 `@Before` / `setUp()` 初始化：

```kotlin
@Before
fun setUp() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    app = ApplicationProvider.getApplicationContext()
    profile = SshProfile(
        id = "profile-1",
        name = "Test Box",
        host = "test.example.com",
        username = "testuser",
    )
    profileStore = FakeProfileStore(profile)
}
```

规则：
- `setUp()` 中只放通用的、所有测试都需要的初始化。
- 测试特定的场景数据（如不同的 `SshProfile` 配置）在各测试方法内构造。
- 不使用 `@JvmField` companion object 中的 `const` 测试数据（除非是真正的常量如端口号）。

### 5.3 Fake 对象的构造策略

Fake 对象通过构造函数接受初始状态：

```kotlin
private class FakeProfileStore(
    private val initialProfile: SshProfile? = null,
) : ProfileStore {
    private val profiles = linkedMapOf<String, SshProfile>().apply {
        if (initialProfile != null) put(initialProfile.id, initialProfile)
    }
    // ...
}
```

规则：
- Fake 必须实现接口的所有方法，不留空实现。
- 使用 `linkedMapOf` 或 `mutableListOf` 保持插入顺序，便于断言。
- 包含 `require()` 校验前置条件（如 `require(profileId == this.profileId)`），及早发现误用。

### 5.4 Sensitive Data

参照 `AGENTS.md` 安全约束：

- 测试中不得使用真实的 SSH 密钥、密码、主机名或 IP。
- 使用虚构数据：`example.com`、`test.example.com`、`dummy-key`、`SHA256:abc`。
- 密码和 passphrase 使用明显的占位值：`s3cr3t`、`letmein`。
- API key 使用格式明确的假值：`sk-123`、`test-key`。

### 5.5 Room Database Test Data

Instrumentation 测试中的数据库测试使用 `Room.inMemoryDatabaseBuilder`：

```kotlin
// Pattern from Migration4To5Test
val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
    .addMigrations(MIGRATION_4_5)
    .build()
```

规则：
- 使用内存数据库，不写入磁盘。
- 测试完成后在 `@After` 中 `db.close()`。
- Migration 测试必须覆盖 forward 和 edge cases（空表、大量数据）。

---

## 6. Test Organization

### 6.1 File Layout

```
core/model/src/test/kotlin/io/rikka/agent/model/
    SshProfileTest.kt
    ChatModelsTest.kt
    MessagePartTest.kt

core/ssh/src/test/kotlin/io/rikka/agent/ssh/
    InMemoryKnownHostsStoreTest.kt
    JsonlParserTest.kt
    SshAuthKeyFormatTest.kt
    SshOutputMapperTest.kt

core/storage/src/test/kotlin/io/rikka/agent/storage/db/
    MappersTest.kt

core/ui/src/test/kotlin/io/rikka/agent/ui/components/
    MermaidFenceParserTest.kt
    MessagePartsBlockTest.kt
core/ui/src/test/kotlin/io/rikka/agent/ui/util/
    AnsiStripperTest.kt

app/src/test/java/io/rikka/agent/vm/
    ChatViewModelTest.kt
    CommandExecutorTest.kt
    CommandComposerTest.kt
    OutputFormatterTest.kt
    ErrorMessageMapperTest.kt
    CodexProgressFormatterTest.kt
    ...

app/src/test/java/io/rikka/agent/ui/screen/
    HostKeyDialogsTest.kt
    HostKeyDialogStateMachineTest.kt
    FullOutputDialogTest.kt
    ...

app/src/test/java/io/rikka/agent/ssh/
    DataStoreKnownHostsStoreTest.kt
    KnownHostEndpointParserTest.kt
    KnownHostsEntryCodecTest.kt
    ContentUriKeyContentProviderTest.kt

app/src/androidTest/java/io/rikka/agent/ui/screen/
    ProfileEditorSafPickerTest.kt

core/storage/src/androidTest/java/io/rikka/agent/storage/db/
    Migration4To5Test.kt
```

### 6.2 Test Scope Mapping

| Source File | Test File | Scope |
|-------------|-----------|-------|
| `vm/CommandComposer.kt` | `vm/CommandComposerTest.kt` | Pure unit |
| `vm/ChatViewModel.kt` | `vm/ChatViewModelTest.kt` | Robolectric + coroutines |
| `vm/CommandExecutor.kt` | `vm/CommandExecutorTest.kt` | Robolectric + coroutines |
| `ssh/JsonlParser.kt` | `ssh/JsonlParserTest.kt` | Pure unit |
| `ssh/InMemoryKnownHostsStore.kt` | `ssh/InMemoryKnownHostsStoreTest.kt` | Pure unit + coroutines |
| `storage/db/Mappers.kt` | `storage/db/MappersTest.kt` | Pure unit |
| `ui/components/HostKeyDialog.kt` | `ui/screen/HostKeyDialogsTest.kt` | Compose + Robolectric |
| `storage/db/Migrations.kt` | `storage/db/Migration4To5Test.kt` | Instrumented (Room) |
| `ui/screen/ProfileEditorScreen.kt` | `ui/screen/ProfileEditorSafPickerTest.kt` | Instrumented (Espresso) |

---

## 7. Anti-Patterns

以下做法在本项目中被明确禁止：

1. **Mock 框架**：不使用 Mockito/MockK。通过手写 Fake 对象替代，保持测试可读性和编译期安全。
2. **Thread.sleep**：不使用 `Thread.sleep` 等待异步操作。使用 `runTest` + `advanceUntilIdle()`。
3. **共享可变状态**：测试类之间不共享可变的 companion object 或 static 字段。
4. **测试依赖顺序**：每个测试方法必须独立运行，不依赖其他测试的执行结果或顺序。
5. **过度断言**：不在一个测试中断言不相关的行为。如果发现一个测试有 5+ 个 `assert` 且覆盖不同逻辑路径，应拆分。
6. **真实网络**：测试中不得发起真实的 SSH 连接或 HTTP 请求。
