# RikkaAgent Testing Strategy

本文档定义 RikkaAgent 项目的完整测试策略，涵盖测试金字塔、覆盖矩阵、工具链、CI 门禁和关键组件的测试用例设计。

---

## 1. Testing Pyramid

### 1.1 Distribution Target

| 层级 | 占比 | 范围 | 执行环境 |
|------|------|------|----------|
| Unit Tests | 70% | Model、ViewModel、工具类、纯函数、状态机 | JVM (Robolectric for Android deps) |
| Integration Tests | 20% | Room DAO/Migration、SSH exec pipeline、Transformer 管线、DataStore | Instrumented (device/emulator) |
| UI Tests | 10% | Compose screenshot、关键用户流程、Intent dispatch | Robolectric + Compose Test / Instrumented |

### 1.2 Current State

项目已有 **32 个测试文件**，分布如下：

| Module | Test Files | Test Cases (est.) | Coverage Layer |
|--------|-----------|-------------------|----------------|
| `core:model` | 3 | ~35 | Unit |
| `core:ssh` | 4 | ~45 | Unit |
| `core:storage` | 1 (unit) + 1 (instrumented) | ~12 | Unit + Integration |
| `core:ui` | 4 | ~55 | Unit (Compose + Robolectric) |
| `app` (vm) | 14 | ~95 | Unit (Robolectric) |
| `app` (ui/screen) | 7 | ~20 | Unit (Compose + Robolectric) |
| `app` (ssh) | 4 | ~20 | Unit (Robolectric) |
| `app` (storage) | 1 | ~3 | Unit (Robolectric) |
| `app` (androidTest) | 1 | ~5 | Integration |

---

## 2. Coverage Matrix

### 2.1 Component Coverage Status

| Component | Unit | Integration | UI | Priority | Status |
|-----------|------|-------------|-----|----------|--------|
| **core:model** | | | | | |
| MessagePart serialization | Done (10 tests) | -- | -- | P0 | Covered |
| ChatMessage backward compat | Done (5 tests) | -- | -- | P0 | Covered |
| ChatMessage factory methods | Done (4 tests) | -- | -- | P0 | Covered |
| SshProfile defaults + roundtrip | Done (8 tests) | -- | -- | P1 | Covered |
| ChatRole / MessageStatus enums | Done (2 tests) | -- | -- | P2 | Covered |
| ChatThread serialization | Done (1 test) | -- | -- | P1 | Covered |
| **core:ssh** | | | | | |
| SshOutputMapper (full pipeline) | Done (28 tests) | -- | -- | P0 | Covered |
| JsonlParser + LineBuffer | Done (11 tests) | -- | -- | P0 | Covered |
| SshAuthKeyFormat detection | Done (2 tests) | -- | -- | P1 | Covered |
| InMemoryKnownHostsStore | Done (4 tests) | -- | -- | P1 | Covered |
| ExecEvent.Error/Canceled mapping | Done (via SshOutputMapper) | -- | -- | P0 | Covered |
| **core:storage** | | | | | |
| Mappers (entity <-> model) | Done (3 tests) | -- | -- | P0 | Covered |
| Migration v4 -> v5 | -- | Done (6 tests) | -- | P0 | Covered |
| Room DAO CRUD | Missing | Missing | -- | P1 | Gap |
| **core:ui** | | | | | |
| MessagePartsBlock rendering | Done (22 tests) | -- | -- | P0 | Covered |
| MermaidFenceParser | Done | -- | -- | P1 | Covered |
| MermaidRenderSupport | Done | -- | -- | P2 | Covered |
| AnsiStripper | Done | -- | -- | P1 | Covered |
| **app:vm** | | | | | |
| ChatViewModel lifecycle | Done (12 tests) | -- | -- | P0 | Covered |
| CommandExecutor lifecycle | Done (20 tests) | -- | -- | P0 | Covered |
| ChatSessionManager CRUD | Done (14 tests) | -- | -- | P0 | Covered |
| AuthCallbackBroker dispatch | Done (9 tests) | -- | -- | P0 | Covered |
| CommandComposer | Done (8 tests) | -- | -- | P0 | Covered |
| OutputFormatter | Done (6 tests) | -- | -- | P1 | Covered |
| ErrorMessageMapper | Done (2 tests) | -- | -- | P1 | Covered |
| CodexProgressFormatter | Done (2 tests) | -- | -- | P1 | Covered |
| CancelMessageHelper | Done (4 tests) | -- | -- | P1 | Covered |
| SessionExporter | Done (2 tests) | -- | -- | P2 | Covered |
| ProfileEditorViewModel | Done (4 tests) | -- | -- | P1 | Covered |
| SettingsViewModel | Done (2 tests) | -- | -- | P2 | Covered |
| **app:ui** | | | | | |
| HostKeyDialog + state machine | Done (6 tests) | -- | -- | P0 | Covered |
| ChatBubble actions | Done (3 tests) | -- | -- | P0 | Covered |
| FullOutputDialog | Done (1 test) | -- | -- | P1 | Covered |
| ChatScreen share dispatch | Done (2 tests) | -- | -- | P1 | Covered |
| CodexProgressUi | Done (1 test) | -- | -- | P2 | Covered |
| ShareIntents | Done (2 tests) | -- | -- | P2 | Covered |
| KeyImportSupport | Done (4 tests) | -- | -- | P1 | Covered |
| **app:ssh** | | | | | |
| DataStoreKnownHostsStore | Done (3 tests) | -- | -- | P1 | Covered |
| KnownHostsEntryCodec | Done (3 tests) | -- | -- | P1 | Covered |
| KnownHostEndpointParser | Done (3 tests) | -- | -- | P1 | Covered |
| ContentUriKeyContentProvider | Done (4 tests) | -- | -- | P1 | Covered |
| **app:storage** | | | | | |
| AppPreferences | Done (2 tests) | -- | -- | P2 | Covered |

### 2.2 Coverage Gaps

| Gap | Priority | Rationale | Proposed Action |
|-----|----------|-----------|-----------------|
| Room DAO CRUD | P1 | DAO 层仅通过 Migration 测试间接覆盖，缺少 insert/query/update/delete 的独立验证 | 添加 instrumented test：`ChatDaoTest` |
| Room Migration v5+ | P1 | 仅 v4->v5 有测试，后续 migration 需补测 | 每次 migration 增加对应 `MigrationTest` |
| SSH exec end-to-end | P2 | 当前通过 Fake runner 测试，缺少真实 MINA SSHD 集成测试 | 添加 `core:ssh` 集成测试（Apache MINA SSHD） |
| Compose screenshot | P2 | 当前仅验证节点存在性，未做像素级回归 | 引入 Paparazzi 或 Roborazzi |
| Edge case: large message history | P2 | ChatSessionManager 未测试 1000+ 消息场景 | 添加性能边界测试 |

---

## 3. Test Toolchain

### 3.1 Unit Test Stack

| Tool | Version | Module | Purpose |
|------|---------|--------|---------|
| JUnit 4 | 4.13.2 | all | Test runner, assertions |
| kotlinx-coroutines-test | 1.8.1 | app, core:model, core:ssh | `runTest`, `advanceUntilIdle`, `TestDispatcher` |
| Robolectric | 4.14.1 | app | Android context for ViewModel tests |
| Compose UI Test JUnit4 | via BOM 2024.09.00 | app, core:ui | `createComposeRule` for composable tests |
| Turbine | 1.1.0 | core:ssh | Flow assertion (cold flows) |

### 3.2 Integration Test Stack

| Tool | Version | Module | Purpose |
|------|---------|--------|---------|
| AndroidX Test Core | 1.6.1 | app, core:storage | `ApplicationProvider` |
| AndroidX Test Ext JUnit | 1.2.1 | app | `AndroidJUnit4` runner |
| Espresso Intents | 3.6.1 | app | Intent stubbing (SAF picker) |
| Room Testing | 2.6.1 | core:storage | `MigrationTestHelper` |
| Apache MINA SSHD | -- | core:ssh (proposed) | Embedded SSH server for integration tests |

### 3.3 Fake vs Mock Strategy

项目**禁止使用 Mock 框架**（Mockito/MockK）。所有测试替身通过手写 Fake 实现：

| Fake Class | Replaces | Pattern |
|------------|----------|---------|
| `FakeProfileStore` | `ProfileStore` | `linkedMapOf` in-memory store |
| `FakeChatRepository` | `ChatRepository` | `MutableStateFlow` + `linkedMapOf` |
| `FakeKnownHostsStore` | `KnownHostsStore` | No-op / null returns |
| `FakeSshExecRunner` | `ClosableSshExecRunner` | Configurable event list |
| `RecordingExecRunnerFactory` | `SshExecRunnerFactory` | Captures scenario callbacks |
| `HangingExecRunnerFactory` | `SshExecRunnerFactory` | Emits then `awaitCancellation()` |

规则：
- Fake 实现接口的完整方法签名，不留空实现。
- 使用 `linkedMapOf` / `mutableListOf` 保持插入顺序，便于断言。
- Fake 定义在测试类内部（`private class`），不跨文件共享。
- 多文件共用的 Fake 提取到 `testFixtures` 或共享 `src/test` 目录。

### 3.4 Coroutine Test Pattern

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExampleViewModelTest {

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
        // Given
        // When
        advanceUntilIdle()
        // Then
    }
}
```

### 3.5 Compose UI Test Pattern

```kotlin
@RunWith(RobolectricTestRunner::class)
class ExampleComposableTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders expected content`() {
        composeRule.setContent {
            MaterialTheme {
                TargetComposable(...)
            }
        }
        composeRule.onNodeWithText("expected").assertIsDisplayed()
    }
}
```

---

## 4. CI Quality Gates

### 4.1 Pipeline Stages

```yaml
# .github/workflows/ci.yml
stages:
  - static_analysis:    detekt + ktlint
  - unit_tests:         ./gradlew test
  - focused_tests:      ./gradlew :core:model:testDebugUnitTest :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :core:ui:testDebugUnitTest :app:testDevDebugUnitTest
  - lint:               ./gradlew :app:lintDevDebug
  - coverage:           ./gradlew :app:testDevDebugUnitTest jacocoTestReportDevDebugUnitTest
  - build:              ./gradlew assembleDevDebug
```

### 4.2 Gate Criteria

| Gate | Threshold | Enforcement | Current |
|------|-----------|-------------|---------|
| Unit tests | 100% pass | Block merge | Pass |
| detekt findings | 0 errors | Block merge | Pass (advisory `|| true`) |
| Android Lint | 0 errors | Block merge | Pass |
| JaCoCo instruction coverage | 60%+ (app) | Advisory (reported in summary) | Active |
| Build | assembleDevDebug success | Block merge | Pass |

### 4.3 Coverage Thresholds by Module

| Module | Instruction Target | Branch Target | Rationale |
|--------|--------------------|---------------|-----------|
| `:core:model` | 90%+ | 85%+ | Pure data classes, zero Android deps |
| `:core:ssh` | 85%+ | 80%+ | Parsers + state machines must be high |
| `:core:storage` | 80%+ | 75%+ | Room DAO tests require instrumented |
| `:core:ui` | 70%+ | 60%+ | Compose tests focus on key interactions |
| `:app` (vm) | 85%+ | 80%+ | Core business orchestration |
| `:app` (screen) | 60%+ | 50%+ | Integration-level screen tests |

### 4.4 CI Summary Report

CI 自动生成 Step Summary，包含：
- APK size (devDebug)
- Test case count / failures / skipped
- JaCoCo instruction coverage percentage
- Lint issue count
- Detekt finding count

Artifacts uploaded: `app-dev-debug`, `unit-test-reports`, `lint-reports`, `detekt-reports`, `jacoco-coverage` (14-day retention).

---

## 5. Test Case Design

### 5.1 MessagePart Serialization/Deserialization

**File**: `core/model/src/test/kotlin/io/rikka/agent/model/MessagePartTest.kt`

| # | Test Case | Input | Expected | Type |
|---|-----------|-------|----------|------|
| 1 | Text roundtrip | `MessagePart.Text("Hello")` | Decode equals original | Unit |
| 2 | Command with exitCode roundtrip | `MessagePart.Command("ls", exitCode=0)` | `isFinished=true`, `isSuccess=true` | Unit |
| 3 | Command with null exitCode | `MessagePart.Command("top", exitCode=null)` | `isFinished=false` | Unit |
| 4 | Stdout roundtrip | `MessagePart.Stdout("total 42\n")` | Decode preserves text | Unit |
| 5 | Stderr roundtrip | `MessagePart.Stderr("Permission denied\n")` | Decode preserves text | Unit |
| 6 | Reasoning with stepId | `MessagePart.Reasoning("think", stepId="s1")` | stepId preserved | Unit |
| 7 | Code with language | `MessagePart.Code("println()", language="kotlin")` | language preserved | Unit |
| 8 | Code with null language | `MessagePart.Code("echo hi", language=null)` | null language | Unit |
| 9 | Error with all fields | `Error(msg, cause, code)` | All fields preserved | Unit |
| 10 | Mermaid with caption | `Mermaid("graph TD;", caption="Arch")` | Both fields preserved | Unit |
| 11 | JSON type discriminator | Encode Stdout | Contains `"type":"stdout"` | Unit |
| 12 | Mixed list roundtrip | `[Command, Stdout, Stderr, Command]` | 4 items, correct types | Unit |
| 13 | Legacy ChatMessage deserialization | JSON with `content` field | `_content` populated | Unit |
| 14 | New ChatMessage deserialization | JSON with `parts` array | `parts` list populated | Unit |
| 15 | `migrateToParts` legacy -> parts | `_content="old"` | Single Text part | Unit |
| 16 | `migrateToParts` idempotent | Already-migrated message | Parts unchanged | Unit |
| 17 | `textContent` extracts text only | Mixed parts | Only Text parts concatenated | Unit |
| 18 | `commands` extracts commands | Mixed parts | Only Command parts returned | Unit |
| 19 | `stdoutText` concatenates | Multiple Stdout parts | Joined text | Unit |
| 20 | `stderrText` concatenates | Multiple Stderr parts | Joined text | Unit |
| 21 | `ChatMessage.text` factory | `text("id", User, "hello")` | Single Text part | Unit |
| 22 | `ChatMessage.command` factory with output | `command("id", "whoami", stdout="root")` | Command + Stdout, status=Final | Unit |
| 23 | `ChatMessage.command` running | `command("id", "sleep", exitCode=null)` | status=Streaming | Unit |
| 24 | Full ChatMessage roundtrip | Complex parts list | All parts preserved | Unit |
| 25 | Extension `toTextPart` | `"hello".toTextPart()` | `MessagePart.Text("hello")` | Unit |

### 5.2 Room Migration v4 -> v5

**File**: `core/storage/src/androidTest/java/io/rikka/agent/storage/db/Migration4To5Test.kt`

| # | Test Case | Setup | Expected | Type |
|---|-----------|-------|----------|------|
| 1 | Adds partsJson column | v4 DB with data | `partsJson` column exists and non-empty | Integration |
| 2 | Migrates content to Text part | v4 message "Hello world" | `partsJson` contains `{"type":"text","text":"Hello world"}` | Integration |
| 3 | Handles quotes and newlines | v4 message with `"quoted"` and `\n` | JSON properly escaped: `\"quoted\"`, `\n` | Integration |
| 4 | Empty content gets default partsJson | v4 message with `content=""` | `partsJson = "[]"` | Integration |
| 5 | Preserves thread data | v4 thread with title | Thread title unchanged after migration | Integration |
| 6 | v5 insert/read with partsJson | Insert message with partsJson in v5 | `partsJson` roundtrips correctly | Integration |

### 5.3 ChatSessionManager CRUD

**File**: `app/src/test/java/io/rikka/agent/vm/ChatSessionManagerTest.kt`

| # | Test Case | Setup | Expected | Type |
|---|-----------|-------|----------|------|
| 1 | `newSession` clears currentThreadId | Active thread selected | `currentThreadId == null` | Unit |
| 2 | `switchThread` sets currentThreadId | Thread exists | `currentThreadId == tid`, `isCurrentThread(tid) == true` | Unit |
| 3 | `switchThread` replaces previous | Thread A then Thread B | Only B is current | Unit |
| 4 | `deleteThread` active returns true | Active thread deleted | `wasActive=true`, `currentThreadId=null` | Unit |
| 5 | `deleteThread` inactive returns false | Inactive thread deleted | `wasActive=false`, active unchanged | Unit |
| 6 | `deleteThread` removes from repo | Thread deleted | Not in `getThreads()` | Unit |
| 7 | `persistMessage` creates thread + auto-title | First user message | Thread created, title = command text | Unit |
| 8 | `persistMessage` truncates long title | 60-char command | Title = 50 chars + "..." | Unit |
| 9 | `persistMessage` no title overwrite for assistant | User msg then assistant msg | Title stays as user command | Unit |
| 10 | `persistMessage` reuses thread | Two messages | Same threadId, 2 messages stored | Unit |
| 11 | `getMessages` returns persisted | Insert message to thread | Returned in `getMessages(tid)` | Unit |
| 12 | `isCurrentThread` false when none selected | No thread selected | Always false | Unit |
| 13 | `isCurrentThread` true only for current | Two threads, one selected | True for current, false for other | Unit |
| 14 | `threads` StateFlow reflects changes | Create threads | StateFlow updates | Unit |
| 15 | `persistUpdate` updates content+status | Streaming message | Updated to Final with new content | Unit |
| 16 | `persistUpdate` empty content | Update with "" | Empty parts list | Unit |

### 5.4 CommandExecutor Lifecycle

**File**: `app/src/test/java/io/rikka/agent/vm/CommandExecutorTest.kt`

| # | Test Case | Scenario | Expected | Type |
|---|-----------|----------|----------|------|
| 1 | Streams stdout + fires final | StdoutChunks then Exit(0) | `updateMessage` called with Final status | Unit |
| 2 | Streams stderr alongside stdout | Stdout + Stderr + Exit(1) | Content contains both | Unit |
| 3 | No output + exit 0 | Exit(0) only | Shows "no output" message | Unit |
| 4 | No output + non-zero exit | Exit(127) | Shows "no output, failed" with exit code | Unit |
| 5 | Maps timeout error | Error("timeout") | Friendly timeout message, Error status | Unit |
| 6 | Maps connection_refused error | Error("connection_refused") | Friendly message, Error status | Unit |
| 7 | Error with prior output | Stdout then Error | Output + error message combined | Unit |
| 8 | Sets connectionError on error | Error event | `lastConnectionError` populated | Unit |
| 9 | `dismissConnectionError` clears | Error then dismiss | `lastConnectionError` null | Unit |
| 10 | `cancel` stops running command | Hanging runner | Returns assistantId | Unit |
| 11 | `cancel` returns null when idle | No running command | Returns null | Unit |
| 12 | Canceled event maps to Canceled status | Canceled event | MessageStatus.Canceled | Unit |
| 13 | ConnectionState lifecycle | IDLE -> READY -> execute -> READY | State transitions correct | Unit |
| 14 | `loadProfile` unknown -> ERROR | Non-existent profile | ConnectionState.ERROR | Unit |
| 15 | `loadProfile` returns profile+label | Valid profile | Profile + name returned | Unit |
| 16 | `loadProfile` blank name -> host label | Profile with empty name | Returns `user@host` | Unit |
| 17 | Codex mode accumulates structured parts | Codex JSON events | Reasoning/Code/Text parts emitted | Unit |
| 18 | CodexEventMapper reasoning | JSON with type=reasoning.text | Reasoning part | Unit |
| 19 | CodexEventMapper code | JSON with type=code | Code part | Unit |
| 20 | CodexEventMapper text | JSON with delta | Text part | Unit |
| 21 | CodexEventMapper progress event | JSON thread.started | Returns null | Unit |
| 22 | CodexEventMapper invalid JSON | "not json" | Returns null | Unit |
| 23 | Execute without profile | No profile loaded | Callbacks never called | Unit |

### 5.5 AuthCallbackBroker Dispatch

**File**: `app/src/test/java/io/rikka/agent/vm/AuthCallbackBrokerTest.kt`

| # | Test Case | Trigger | Expected | Type |
|---|-----------|---------|----------|------|
| 1 | Unknown host -> event + accept | `onUnknownHost` | `HostKeyEvent.UnknownHost` emitted, `respondToHostKey(true)` resumes with `true` | Unit |
| 2 | Unknown host -> event + reject | `onUnknownHost` | `respondToHostKey(false)` resumes with `false` | Unit |
| 3 | Host key mismatch -> event | `onHostKeyMismatch` | `HostKeyEvent.Mismatch` with expected/actual fingerprints | Unit |
| 4 | Mismatch accept -> replacement confirm | `onHostKeyMismatch` then accept | Resumes with `true` | Unit |
| 5 | Mismatch reject | `onHostKeyMismatch` then reject | Resumes with `false` | Unit |
| 6 | Password request + response | `getPassword` | `passwordRequest` emits label, `respondToPassword` returns value | Unit |
| 7 | Password cancelled | `respondToPassword(null)` | Throws `IllegalStateException("Authentication cancelled")` | Unit |
| 8 | Passphrase request + response | `getPassphrase` | `passphraseRequest` emits label, returns value | Unit |
| 9 | Passphrase cancelled | `respondToPassphrase(null)` | Returns null | Unit |
| 10 | Sequential password requests | Two password flows | Each gets independent response | Unit |
| 11 | Interleaved host key + password | Host key then password | Both resolve correctly | Unit |

### 5.6 SshOutputMapper Mapping

**File**: `core/ssh/src/test/kotlin/io/rikka/agent/ssh/SshOutputMapperTest.kt`

| # | Test Case | Input Events | Expected Output | Type |
|---|-----------|-------------|-----------------|------|
| 1 | Plain stdout -> Command + Text | `StdoutChunk("hello\n")` | Command part + Text("hello") | Unit |
| 2 | Multiline stdout | `StdoutChunk("a\nb\n")` | Multiple Text parts | Unit |
| 3 | Markdown preserved | `StdoutChunk("# Title\n**bold**\n")` | Text contains markdown | Unit |
| 4 | ANSI color stripped | `StdoutChunk("[31mred[0m\n")` | Text("red") | Unit |
| 5 | ANSI cursor codes stripped | `StdoutChunk("[2K[1Aclean\n")` | Text("clean") | Unit |
| 6 | ANSI OSC title stripped | `StdoutChunk("]0;titlevisible\n")` | Text("visible") | Unit |
| 7 | `stripAnsi` complex nested | `"[1;32;40mGreen[0m normal"` | "Green on black normal" | Unit |
| 8 | `stripAnsi` empty string | `""` | `""` | Unit |
| 9 | `stripAnsi` plain text | `"no escapes"` | `"no escapes"` | Unit |
| 10 | stderr -> Stderr part | `StderrChunk("error\n")` | Stderr("error") | Unit |
| 11 | ANSI stripped from stderr | `StderrChunk("[31merr[0m\n")` | Stderr("err") | Unit |
| 12 | Exit code 0 -> final Command | `StdoutChunk + Exit(0)` | Command(exitCode=0, isSuccess=true) | Unit |
| 13 | Non-zero exit code | `Exit(1)` | Command(exitCode=1, isSuccess=false) | Unit |
| 14 | Exit code 127 | `StderrChunk + Exit(127)` | Command(exitCode=127) | Unit |
| 15 | Initial + final Command share timestamp | `StdoutChunk + Exit(0)` | Same command string and startedAtEpochMs | Unit |
| 16 | stdout-stderr-stdout ordering | Mixed events | Correct interleaved order | Unit |
| 17 | Buffer flushed before stderr | `StdoutChunk("partial") + StderrChunk` | "partial" emitted as Text before Stderr | Unit |
| 18 | Multiple stderr chunks | Two StderrChunk events | Two Stderr parts | Unit |
| 19 | Long output size flush | 17000 chars without newline | Split into multiple Text parts | Unit |
| 20 | Long line-based output | 500 lines | Multiple Text parts | Unit |
| 21 | Streaming chunk accumulation | `StdoutChunk("Hel") + StdoutChunk("lo\n")` | Text("Hello") | Unit |
| 22 | Empty output + exit 0 | `Exit(0)` only | Only Command parts, no Text/Stderr | Unit |
| 23 | Empty stdout chunk ignored | `StdoutChunk("")` | No Text emitted | Unit |
| 24 | Empty stderr chunk ignored | `StderrChunk("")` | No Stderr emitted | Unit |
| 25 | Whitespace-only stdout emitted | `StdoutChunk("   \n")` | Text("   ") | Unit |
| 26 | Fenced code block detected | `` ```python\nprint('hi')\n``` `` | Code(code="print('hi')", language="python") | Unit |
| 27 | Code block without language | `` ```\ncode\n``` `` | Code(code="code", language=null) | Unit |
| 28 | Error event -> Error part | `ExecEvent.Error("connection", "refused")` | Error(message="refused", cause="connection") | Unit |
| 29 | Canceled event | `StdoutChunk + Canceled` | Command(exitCode=null) | Unit |
| 30 | Mapper reusable | Two separate calls | No cross-contamination | Unit |
| 31 | StructuredEvent ignored | `StructuredEvent` between stdout | No extra parts, stdout preserved | Unit |

### 5.7 MarkdownBlock Rendering

**File**: `core/ui/src/test/kotlin/io/rikka/agent/ui/components/MessagePartsBlockTest.kt`

| # | Test Case | MessagePart | Expected Render | Type |
|---|-----------|-------------|-----------------|------|
| 1 | Text renders content | `Text("Hello")` | Node with "Hello" displayed | Unit |
| 2 | Empty Text renders nothing | `Text("")` | No node with empty text | Unit |
| 3 | Text with markdown-like content | `Text("## Heading\n**bold**")` | "Heading" exists in tree | Unit |
| 4 | Code renders content | `Code("println()", "kotlin")` | "println()" displayed | Unit |
| 5 | Code with language label | `Code("val x=1", "kotlin")` | "kotlin" label displayed | Unit |
| 6 | Code without language fallback | `Code("echo hi", null)` | "output" fallback label | Unit |
| 7 | Command renders text | `Command("ls -la", exitCode=0)` | "ls -la" displayed | Unit |
| 8 | Command exit 0 success badge | `Command("whoami", exitCode=0)` | "exit 0" displayed | Unit |
| 9 | Command exit 1 failure badge | `Command("cat /x", exitCode=1)` | "exit 1" displayed | Unit |
| 10 | Command running indicator | `Command("sleep", exitCode=null)` | "..." displayed | Unit |
| 11 | Command dollar prompt | `Command("uname", exitCode=0)` | "$" displayed | Unit |
| 12 | Stdout renders output | `Stdout("total 42\n")` | "total 42" exists | Unit |
| 13 | Empty Stdout renders nothing | `Stdout("")` | No "stdout" node | Unit |
| 14 | Stderr renders error | `Stderr("Permission denied\n")` | "Permission denied" exists | Unit |
| 15 | Empty Stderr renders nothing | `Stderr("")` | No "stderr" node | Unit |
| 16 | Stdout label shown | `Stdout("output")` | "stdout" label displayed | Unit |
| 17 | Stderr label shown | `Stderr("error")` | "stderr" label displayed | Unit |
| 18 | Error renders message | `Error("Connection refused")` | Message displayed | Unit |
| 19 | Error with cause | `Error("refused", cause="ECONNREFUSED")` | Cause text exists | Unit |
| 20 | Error with code badge | `Error("SSH failed", code=255)` | "code: 255" displayed | Unit |
| 21 | Error without cause | `Error("oops", cause=null)` | No "Cause:" section | Unit |
| 22 | Error without code | `Error("oops", code=null)` | No "code:" badge | Unit |
| 23 | Reasoning collapsed by default | `Reasoning("think", stepId="s1")` | "Step s1" visible, content hidden | Unit |
| 24 | Reasoning expanded when streaming | `Reasoning("think")` + streaming | Content visible | Unit |
| 25 | Reasoning toggle on click | Click header | Content becomes visible | Unit |
| 26 | Reasoning without stepId | `Reasoning("think", stepId=null)` | "Thinking" generic label | Unit |
| 27 | Mixed parts all render | Text+Command+Stdout+Code+Reasoning | All elements displayed | Unit |
| 28 | Command + stdout + stderr | `ChatMessage.command(...)` | All parts rendered | Unit |
| 29 | Error after command | Command + Error | Both cards rendered | Unit |
| 30 | Empty parts list | No parts | Empty block, no crash | Unit |
| 31 | isStreaming flag | Text + isStreaming=true | Text still displayed | Unit |
| 32 | Streaming status auto-expands reasoning | Message with Streaming status | Reasoning content visible | Unit |

### 5.8 ChatBubble State Display

**File**: `app/src/test/java/io/rikka/agent/ui/components/ChatBubbleActionsTest.kt`

| # | Test Case | Message State | Expected Behavior | Type |
|---|-----------|--------------|-------------------|------|
| 1 | Long output expandable | 20-line output | "Show all lines (20)" button displayed, click expands | Unit |
| 2 | Assistant action row callbacks | Final assistant message | "Expand" + "Share full" buttons trigger callbacks | Unit |
| 3 | User rerun button | User message | Rerun button returns command text | Unit |
| 4 | Codex progress markdown | Progress list items | All list items rendered | Unit |
| 5 | Full output dialog | Full text | Title + text displayed, share + dismiss callbacks work | Unit |
| 6 | Share dispatches chooser intent | Click share | `ACTION_CHOOSER` with `ACTION_SEND` text/plain | Integration |
| 7 | Export session dispatches intent | Click export | Chooser with subject and transcript | Integration |

---

## 6. Test Execution Reference

### 6.1 Quick Commands

```bash
# Fast baseline (core + app unit tests)
./gradlew :core:model:testDebugUnitTest :core:ssh:testDebugUnitTest :core:storage:testDebugUnitTest :core:ui:testDebugUnitTest :app:testDevDebugUnitTest --no-daemon

# Full unit test sweep
./gradlew test --no-daemon

# Instrumented tests (requires device/emulator)
./gradlew :core:storage:connectedAndroidTest :app:connectedDevDebugAndroidTest

# Coverage report (app module, devDebug)
./gradlew :app:testDevDebugUnitTest jacocoTestReportDevDebugUnitTest --no-daemon

# Static analysis
detekt-cli --input "$(pwd)" --build-upon-default-config --all-rules --excludes '**/build/**,**/resources/**,**/.gradle/**'

# Lint
./gradlew :app:lintDevDebug --no-daemon
```

### 6.2 Report Locations

| Report | Path |
|--------|------|
| Unit test HTML | `**/build/reports/tests/` |
| Unit test XML | `**/build/test-results/` |
| JaCoCo HTML | `app/build/reports/jacoco/jacocoTestReportDevDebugUnitTest/` |
| JaCoCo CSV | `app/build/reports/jacoco/jacocoTestReportDevDebugUnitTest/jacocoTestReportDevDebugUnitTest.csv` |
| Lint HTML | `app/build/reports/lint-results-devDebug.html` |
| Detekt HTML | `build/reports/detekt/detekt.html` |
| Detekt SARIF | `build/reports/detekt/detekt.sarif` |

---

## 7. Anti-Patterns

1. **No Mock Frameworks**: Mockito/MockK prohibited. Use hand-written Fakes.
2. **No Thread.sleep**: Use `runTest` + `advanceUntilIdle()`.
3. **No Shared Mutable State**: No companion object statics across tests.
4. **No Test Order Dependencies**: Each test method is independent.
5. **No Over-Assertion**: Max 5 related asserts per test; split unrelated checks.
6. **No Real Network**: No real SSH connections or HTTP requests in tests.
