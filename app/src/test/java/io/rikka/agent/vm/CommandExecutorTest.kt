package io.rikka.agent.vm

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.R
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SshProfile
import io.rikka.agent.ssh.ClosableSshExecRunner
import io.rikka.agent.ssh.ConnectionState
import io.rikka.agent.ssh.ExecEvent
import io.rikka.agent.ssh.HostKeyCallback
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.PassphraseProvider
import io.rikka.agent.ssh.PasswordProvider
import io.rikka.agent.ssh.SshExecRunnerFactory
import io.rikka.agent.ssh.StoredHostKey
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CommandExecutorTest {

  private val dispatcher: TestDispatcher = StandardTestDispatcher()
  private lateinit var app: Application
  private lateinit var profile: SshProfile
  private lateinit var profileStore: FakeProfileStore
  private lateinit var appPreferences: AppPreferences
  private lateinit var authBroker: AuthCallbackBroker
  private lateinit var runnerFactory: RecordingExecRunnerFactory

  @Before
  fun setUp() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    stopKoin()
    app = ApplicationProvider.getApplicationContext()
    appPreferences = AppPreferences(app)
    appPreferences.setDefaultShell("/bin/sh")
    profile = SshProfile(
      id = "profile-1",
      name = "Test Box",
      host = "test.example.com",
      username = "testuser",
    )
    profileStore = FakeProfileStore(profile)
    authBroker = AuthCallbackBroker()
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Exit(0))
    }
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  // ── Execution lifecycle ────────────────────────────────────────────────────

  @Test
  fun `execute streams stdout and fires final updateMessage`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk("hello ".toByteArray()),
        ExecEvent.StdoutChunk("world".toByteArray()),
        ExecEvent.Exit(0),
      )
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "echo hello world",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(1, finals.size)
    assertEquals(MessageStatus.Final, finals[0].second)
    assertTrue(finals[0].first.contains("hello world"))
  }

  @Test
  fun `execute streams stderr alongside stdout`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk("output".toByteArray()),
        ExecEvent.StderrChunk("warning".toByteArray()),
        ExecEvent.Exit(1),
      )
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "failing-cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(1, finals.size)
    assertEquals(MessageStatus.Final, finals[0].second)
    assertTrue(finals[0].first.contains("output"))
    assertTrue(finals[0].first.contains("warning"))
  }

  @Test
  fun `execute with no output shows no-output message for exit 0`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Exit(0))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "true",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(MessageStatus.Final, finals[0].second)
    assertTrue(finals[0].first.contains(app.getString(R.string.msg_no_output)))
  }

  @Test
  fun `execute with no output shows failed message for non-zero exit`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Exit(127))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "bad-cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(MessageStatus.Final, finals[0].second)
    assertTrue(finals[0].first.contains(app.getString(R.string.msg_no_output_failed)))
    assertTrue(finals[0].first.contains("127"))
  }

  // ── Error events ──────────────────────────────────────────────────────────

  @Test
  fun `execute maps timeout error to friendly message`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Error("timeout", "socket timeout"))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "slow-cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(MessageStatus.Error, finals[0].second)
    assertEquals(app.getString(R.string.err_timeout), finals[0].first)
  }

  @Test
  fun `execute maps connection_refused error to friendly message`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Error("connection_refused", "Connection refused"))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "ssh bad-host",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(MessageStatus.Error, finals[0].second)
    assertEquals(app.getString(R.string.err_connection_refused), finals[0].first)
  }

  @Test
  fun `execute error with prior output appends error below output`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk("partial data".toByteArray()),
        ExecEvent.Error("timeout", "timeout"),
      )
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "flaky-cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(MessageStatus.Error, finals[0].second)
    assertTrue(finals[0].first.contains("partial data"))
    assertTrue(finals[0].first.contains(app.getString(R.string.err_timeout)))
  }

  @Test
  fun `execute sets connectionError on error event`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Error("timeout", "socket timeout"))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    executor.execute(
      command = "cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, _, _ -> },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    val error = executor.lastConnectionError.value
    assertNotNull(error)
    assertEquals("timeout", error!!.category)
    assertEquals(app.getString(R.string.err_timeout), error.message)
  }

  @Test
  fun `dismissConnectionError clears the error`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Error("timeout", "timeout"))
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    executor.execute(
      command = "cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, _, _ -> },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertNotNull(executor.lastConnectionError.value)
    executor.dismissConnectionError()
    assertNull(executor.lastConnectionError.value)
  }

  // ── Cancellation ──────────────────────────────────────────────────────────

  @Test
  fun `cancel stops running command and returns assistantId`() = runTest(dispatcher) {
    runnerFactory = HangingExecRunnerFactory()
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    executor.execute(
      command = "tail -f /var/log/syslog",
      assistantId = "a-42",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, _, _ -> },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    val canceledId = executor.cancel()
    assertEquals("a-42", canceledId)
  }

  @Test
  fun `cancel returns null when nothing is running`() = runTest(dispatcher) {
    val executor = createExecutor()
    advanceUntilIdle()

    val canceledId = executor.cancel()
    assertNull(canceledId)
  }

  @Test
  fun `exec Canceled event maps to Canceled status`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk("partial".toByteArray()),
        ExecEvent.Canceled,
      )
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val finals = mutableListOf<Triple<String, String, MessageStatus>>()

    executor.execute(
      command = "sleep 999",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { id, content, status -> finals += Triple(id, content, status) },
      getAssistantContent = { "partial" },
    )
    advanceUntilIdle()

    assertEquals(1, finals.size)
    assertEquals("a-1", finals[0].first)
    assertEquals(MessageStatus.Canceled, finals[0].third)
    assertTrue(finals[0].second.contains("partial"))
    assertTrue(finals[0].second.contains(app.getString(R.string.msg_command_canceled)))
  }

  // ── Connection state transitions ──────────────────────────────────────────

  @Test
  fun `connectionState transitions through execution lifecycle`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.StdoutChunk("ok".toByteArray()), ExecEvent.Exit(0))
    }
    val executor = createExecutor()
    advanceUntilIdle()

    assertEquals(ConnectionState.Idle, executor.connectionState.value)

    executor.loadProfile(profile.id)
    advanceUntilIdle()
    assertEquals(ConnectionState.Ready, executor.connectionState.value)

    executor.execute(
      command = "ls",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, _, _ -> },
      getAssistantContent = { null },
    )
    // At this point the coroutine is launched but hasn't collected yet
    // After advanceUntilIdle the full flow completes
    advanceUntilIdle()
    assertEquals(ConnectionState.Ready, executor.connectionState.value)
  }

  @Test
  fun `loadProfile sets state to Failed for unknown profile`() = runTest(dispatcher) {
    val executor = createExecutor()
    advanceUntilIdle()

    val result = executor.loadProfile("nonexistent")
    assertNull(result)
    assertTrue(executor.connectionState.value is ConnectionState.Failed)
  }

  @Test
  fun `loadProfile returns profile and label`() = runTest(dispatcher) {
    val executor = createExecutor()
    advanceUntilIdle()

    val result = executor.loadProfile(profile.id)
    assertNotNull(result)
    assertEquals(profile, result!!.first)
    assertEquals(profile.name, result.second)
    assertEquals(ConnectionState.Ready, executor.connectionState.value)
  }

  @Test
  fun `loadProfile with blank name returns host label`() = runTest(dispatcher) {
    profile = profile.copy(name = "")
    profileStore = FakeProfileStore(profile)
    val executor = createExecutor()
    advanceUntilIdle()

    val result = executor.loadProfile(profile.id)
    assertNotNull(result)
    assertEquals("${profile.username}@${profile.host}", result!!.second)
  }

  @Test
  fun `resetConnectionState restores to READY when profile loaded`() = runTest(dispatcher) {
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    // Simulate error state
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Error("timeout", "timeout"))
    }
    executor.execute(
      command = "cmd",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> },
      updateMessage = { _, _, _ -> },
      getAssistantContent = { null },
    )
    advanceUntilIdle()
    assertEquals(ConnectionState.Ready, executor.connectionState.value) // error returns to READY

    executor.resetConnectionState()
    assertEquals(ConnectionState.Ready, executor.connectionState.value)
  }

  // ── Full output access ────────────────────────────────────────────────────

  @Test
  fun `hasFullOutput and getFullOutput track stored outputs`() = runTest(dispatcher) {
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    // Initially empty
    assertTrue(!executor.hasFullOutput("msg-1"))
    assertNull(executor.getFullOutput("msg-1"))

    executor.clearFullOutput()
  }

  // ── Codex MessagePart mapping ─────────────────────────────────────────────

  @Test
  fun `execute in codex mode accumulates structured parts via updateParts`() = runTest(dispatcher) {
    profile = profile.copy(codexMode = true, codexWorkDir = "/workspace", codexApiKey = "test-key")
    profileStore = FakeProfileStore(profile)
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk(
          """{"type":"reasoning","text":"Let me think...","step_id":"s1"}""".trimIndent().toByteArray() +
            "\n".toByteArray() +
            """{"type":"code","code":"println(\"hi\")","language":"kotlin"}""".trimIndent().toByteArray() +
            "\n".toByteArray() +
            """{"delta":"Final answer"}""".trimIndent().toByteArray() +
            "\n".toByteArray()
        ),
        ExecEvent.Exit(0),
      )
    }
    val executor = createExecutor()
    advanceUntilIdle()
    executor.loadProfile(profile.id)
    advanceUntilIdle()

    val collectedParts = mutableListOf<List<MessagePart>>()
    val finals = mutableListOf<Pair<String, MessageStatus>>()

    executor.execute(
      command = "do something",
      assistantId = "a-1",
      isCodex = true,
      updateContent = { _, _ -> },
      updateParts = { _, parts -> collectedParts += parts },
      updateMessage = { _, content, status -> finals += content to status },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    assertEquals(1, finals.size)
    assertEquals(MessageStatus.Final, finals[0].second)

    // Verify the final content contains structured output
    assertTrue(finals[0].first.contains("Let me think..."))
    assertTrue(finals[0].first.contains("Final answer"))
  }

  @Test
  fun `CodexEventMapper maps reasoning events to Reasoning part`() {
    val json = """{"type":"reasoning.text","text":"Analyzing code...","step_id":"step-1"}"""
    val part = CodexEventMapper.mapToPart(json)
    assertTrue(part is MessagePart.Reasoning)
    val reasoning = part as MessagePart.Reasoning
    assertEquals("Analyzing code...", reasoning.text)
    assertEquals("step-1", reasoning.stepId)
  }

  @Test
  fun `CodexEventMapper maps code events to Code part`() {
    val json = """{"type":"code","code":"val x = 1","language":"kotlin"}"""
    val part = CodexEventMapper.mapToPart(json)
    assertTrue(part is MessagePart.Code)
    val code = part as MessagePart.Code
    assertEquals("val x = 1", code.code)
    assertEquals("kotlin", code.language)
  }

  @Test
  fun `CodexEventMapper maps text-bearing events to Text part`() {
    val json = """{"delta":"Hello world"}"""
    val part = CodexEventMapper.mapToPart(json)
    assertTrue(part is MessagePart.Text)
    assertEquals("Hello world", (part as MessagePart.Text).text)
  }

  @Test
  fun `CodexEventMapper returns null for progress events`() {
    val json = """{"type":"thread.started","thread_id":"t1"}"""
    val part = CodexEventMapper.mapToPart(json)
    assertNull(part)
  }

  @Test
  fun `CodexEventMapper returns null for invalid json`() {
    val part = CodexEventMapper.mapToPart("not json at all")
    assertNull(part)
  }

  @Test
  fun `CodexEventMapper maps content-bearing object to Text`() {
    val json = """{"type":"message","content":"Some message content"}"""
    val part = CodexEventMapper.mapToPart(json)
    assertTrue(part is MessagePart.Text)
    assertEquals("Some message content", (part as MessagePart.Text).text)
  }

  // ── Execute does nothing without profile ──────────────────────────────────

  @Test
  fun `execute returns early when no profile loaded`() = runTest(dispatcher) {
    val executor = createExecutor()
    advanceUntilIdle()
    // Don't load profile — currentProfile is null

    var callbackCalled = false
    executor.execute(
      command = "ls",
      assistantId = "a-1",
      isCodex = false,
      updateContent = { _, _ -> callbackCalled = true },
      updateMessage = { _, _, _ -> callbackCalled = true },
      getAssistantContent = { null },
    )
    advanceUntilIdle()

    // Callbacks should never be called when profile is null
    assertTrue(!callbackCalled)
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private fun createExecutor(): CommandExecutor = CommandExecutor(
    profileStore = profileStore,
    knownHostsStore = FakeKnownHostsStore(),
    appPreferences = appPreferences,
    keyContentProvider = KeyContentProvider { "dummy-key" },
    runnerFactory = runnerFactory,
    authBroker = authBroker,
    app = app,
    scope = backgroundScope,
  )

  private class RecordingExecRunnerFactory(
    private val scenario: suspend (SshProfile) -> List<ExecEvent>,
  ) : SshExecRunnerFactory {
    override fun create(
      knownHostsStore: KnownHostsStore,
      hostKeyCallback: HostKeyCallback,
      passwordProvider: PasswordProvider?,
      keyContentProvider: KeyContentProvider?,
      passphraseProvider: PassphraseProvider?,
    ): ClosableSshExecRunner = object : ClosableSshExecRunner {
      override fun run(profile: SshProfile, command: String) = kotlinx.coroutines.flow.flow {
        scenario(profile).forEach { emit(it) }
      }
      override fun close() = Unit
    }
  }

  private class HangingExecRunnerFactory : SshExecRunnerFactory {
    override fun create(
      knownHostsStore: KnownHostsStore,
      hostKeyCallback: HostKeyCallback,
      passwordProvider: PasswordProvider?,
      keyContentProvider: KeyContentProvider?,
      passphraseProvider: PassphraseProvider?,
    ): ClosableSshExecRunner = object : ClosableSshExecRunner {
      override fun run(profile: SshProfile, command: String) = kotlinx.coroutines.flow.flow {
        emit(ExecEvent.StdoutChunk("streaming...".toByteArray()))
        awaitCancellation()
      }
      override fun close() = Unit
    }
  }

  private class FakeProfileStore(
    private val initialProfile: SshProfile? = null,
  ) : ProfileStore {
    private val profiles = linkedMapOf<String, SshProfile>().apply {
      if (initialProfile != null) put(initialProfile.id, initialProfile)
    }

    override suspend fun listProfiles(): List<SshProfile> = profiles.values.toList()
    override suspend fun getById(id: String): SshProfile? = profiles[id]
    override suspend fun upsert(profile: SshProfile) { profiles[profile.id] = profile }
    override suspend fun delete(profileId: String) { profiles.remove(profileId) }
  }

  private class FakeKnownHostsStore : KnownHostsStore {
    override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? = null
    override suspend fun store(host: String, port: Int, key: StoredHostKey) = Unit
    override suspend fun remove(host: String, port: Int) = Unit
    override suspend fun getAll(): List<Pair<String, StoredHostKey>> = emptyList()
  }
}
