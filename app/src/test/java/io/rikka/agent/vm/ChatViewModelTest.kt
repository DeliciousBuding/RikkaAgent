package io.rikka.agent.vm

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.R
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SshProfile
import io.rikka.agent.ssh.ClosableSshExecRunner
import io.rikka.agent.ssh.ExecEvent
import io.rikka.agent.ssh.HostKeyCallback
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.PassphraseProvider
import io.rikka.agent.ssh.PasswordProvider
import io.rikka.agent.ssh.SshExecRunnerFactory
import io.rikka.agent.ssh.StoredHostKey
import io.rikka.agent.storage.AppPreferences
import kotlinx.coroutines.awaitCancellation
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

  private val dispatcher: TestDispatcher = StandardTestDispatcher()
  private lateinit var app: Application
  private lateinit var profile: SshProfile
  private lateinit var profileStore: FakeProfileStore
  private lateinit var chatRepository: FakeChatRepository
  private lateinit var appPreferences: AppPreferences
  private lateinit var runnerFactory: SshExecRunnerFactory

  @Before
  fun setUp() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    stopKoin()
    app = ApplicationProvider.getApplicationContext()
    appPreferences = AppPreferences(app)
    appPreferences.setDefaultShell("/bin/sh")
    profile = SshProfile(
      id = "profile-1",
      name = "Prod Box",
      host = "example.com",
      username = "ding",
    )
    profileStore = FakeProfileStore(profile)
    chatRepository = FakeChatRepository(profile.id)
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Exit(0))
    }
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  @Test
  fun `init loads profile label and ready system message`() = runTest(dispatcher) {
    val viewModel = createViewModel()

    advanceUntilIdle()

    assertEquals(ConnectionState.READY, viewModel.connectionState.value)
    assertEquals(profile.name, viewModel.profileLabel.value)
    assertTrue(viewModel.messages.value.single().content.contains(profile.username))
    assertTrue(viewModel.messages.value.single().content.contains(profile.host))
  }

  @Test
  fun `switchThread loads persisted thread messages`() = runTest(dispatcher) {
    val originalThreadId = chatRepository.createThread(profile.id, "ls")
    val expected = listOf(
      ChatMessage(
        id = "u-1",
        role = ChatRole.User,
        content = "ls -la",
        timestampMs = 1L,
        status = MessageStatus.Final,
      ),
      ChatMessage(
        id = "a-1",
        role = ChatRole.Assistant,
        content = "total 8",
        timestampMs = 2L,
        status = MessageStatus.Final,
      ),
    )
    expected.forEach { chatRepository.insertMessage(originalThreadId, it) }

    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.switchThread(originalThreadId)
    advanceUntilIdle()

    assertEquals(expected, viewModel.messages.value)
  }

  @Test
  fun `newSession clears current thread messages and appends fresh status`() = runTest(dispatcher) {
    val originalThreadId = chatRepository.createThread(profile.id, "pwd")
    chatRepository.insertMessage(
      originalThreadId,
      ChatMessage(
        id = "u-1",
        role = ChatRole.User,
        content = "pwd",
        timestampMs = 1L,
        status = MessageStatus.Final,
      ),
    )
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.switchThread(originalThreadId)
    advanceUntilIdle()

    viewModel.newSession()
    advanceUntilIdle()

    assertEquals(ConnectionState.READY, viewModel.connectionState.value)
    assertEquals(1, viewModel.messages.value.size)
    assertEquals(app.getString(R.string.msg_new_session, profile.name, profile.username, profile.host), viewModel.messages.value.single().content)
  }

  @Test
  fun `deleteThread resets active session but keeps other threads available`() = runTest(dispatcher) {
    val activeThreadId = chatRepository.createThread(profile.id, "active")
    val idleThreadId = chatRepository.createThread(profile.id, "idle")
    chatRepository.insertMessage(
      activeThreadId,
      ChatMessage(
        id = "u-1",
        role = ChatRole.User,
        content = "whoami",
        timestampMs = 1L,
        status = MessageStatus.Final,
      ),
    )

    val viewModel = createViewModel()
    val collector = launch { viewModel.threads.collect { } }
    advanceUntilIdle()
    try {
      viewModel.switchThread(activeThreadId)
      advanceUntilIdle()

      viewModel.deleteThread(activeThreadId)
      advanceUntilIdle()

      assertEquals(listOf(idleThreadId), viewModel.threads.value.map(ChatThread::id))
      assertEquals(1, viewModel.messages.value.size)
      assertEquals(app.getString(R.string.msg_new_session, profile.name, profile.username, profile.host), viewModel.messages.value.single().content)
    } finally {
      collector.cancel()
    }
  }

  @Test
  fun `deleteThread leaves current messages untouched when deleting inactive thread`() = runTest(dispatcher) {
    val activeThreadId = chatRepository.createThread(profile.id, "active")
    val inactiveThreadId = chatRepository.createThread(profile.id, "inactive")
    val activeMessage = ChatMessage(
      id = "u-1",
      role = ChatRole.User,
      content = "hostname",
      timestampMs = 1L,
      status = MessageStatus.Final,
    )
    chatRepository.insertMessage(activeThreadId, activeMessage)

    val viewModel = createViewModel()
    val collector = launch { viewModel.threads.collect { } }
    advanceUntilIdle()
    try {
      viewModel.switchThread(activeThreadId)
      advanceUntilIdle()

      viewModel.deleteThread(inactiveThreadId)
      advanceUntilIdle()

      assertFalse(viewModel.threads.value.any { it.id == inactiveThreadId })
      assertEquals(listOf(activeMessage), viewModel.messages.value)
    } finally {
      collector.cancel()
    }
  }

  @Test
  fun `password auth emits request and resumes after response`() = runTest(dispatcher) {
    profile = profile.copy(authType = io.rikka.agent.model.AuthType.Password)
    profileStore = FakeProfileStore(profile)
    runnerFactory = RecordingExecRunnerFactory { ctx, profile ->
      val password = ctx.passwordProvider?.getPassword(profile)
      listOf(
        ExecEvent.StdoutChunk("password:$password".toByteArray()),
        ExecEvent.Exit(0),
      )
    }
    val requests = mutableListOf<String>()
    val viewModel = createViewModel()
    val requestJob = launch { viewModel.passwordRequest.collect { requests += it } }
    advanceUntilIdle()

    viewModel.send("whoami")
    advanceUntilIdle()

    assertEquals(listOf("${profile.username}@${profile.host}:${profile.port}"), requests)

    viewModel.respondToPassword("s3cr3t")
    advanceUntilIdle()

    assertTrue(viewModel.messages.value.last().content.contains("password:s3cr3t"))
    assertEquals(MessageStatus.Final, viewModel.messages.value.last().status)
    requestJob.cancel()
  }

  @Test
  fun `passphrase request emits for encrypted key flow`() = runTest(dispatcher) {
    profileStore = FakeProfileStore(profile)
    runnerFactory = RecordingExecRunnerFactory { ctx, profile ->
      val passphrase = ctx.passphraseProvider?.getPassphrase(profile)
      listOf(
        ExecEvent.StdoutChunk("passphrase:$passphrase".toByteArray()),
        ExecEvent.Exit(0),
      )
    }
    val requests = mutableListOf<String>()
    val viewModel = createViewModel()
    val requestJob = launch { viewModel.passphraseRequest.collect { requests += it } }
    advanceUntilIdle()

    viewModel.send("check-key")
    advanceUntilIdle()

    assertEquals(listOf("${profile.username}@${profile.host}:${profile.port}"), requests)

    viewModel.respondToPassphrase("letmein")
    advanceUntilIdle()

    assertTrue(viewModel.messages.value.last().content.contains("passphrase:letmein"))
    assertEquals(MessageStatus.Final, viewModel.messages.value.last().status)
    requestJob.cancel()
  }

  @Test
  fun `unknown host emits event and resumes after trust decision`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { ctx, profile ->
      val accepted = ctx.hostKeyCallback.onUnknownHost(
        profile.host,
        profile.port,
        "SHA256:test",
        "ED25519",
      )
      listOf(
        ExecEvent.StdoutChunk("trusted:$accepted".toByteArray()),
        ExecEvent.Exit(0),
      )
    }
    val events = mutableListOf<HostKeyEvent>()
    val viewModel = createViewModel()
    val hostKeyJob = launch { viewModel.hostKeyEvent.collect { events += it } }
    advanceUntilIdle()

    viewModel.send("hostname")
    advanceUntilIdle()

    assertEquals(
      listOf(HostKeyEvent.UnknownHost(profile.host, profile.port, "SHA256:test", "ED25519")),
      events,
    )

    viewModel.respondToHostKey(true)
    advanceUntilIdle()

    assertTrue(viewModel.messages.value.last().content.contains("trusted:true"))
    assertEquals(MessageStatus.Final, viewModel.messages.value.last().status)
    hostKeyJob.cancel()
  }

  @Test
  fun `host key mismatch emits event and resumes after replacement decision`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { ctx, profile ->
      val accepted = ctx.hostKeyCallback.onHostKeyMismatch(
        profile.host,
        profile.port,
        "SHA256:old",
        "SHA256:new",
        "ED25519",
      )
      listOf(
        ExecEvent.StdoutChunk("replaced:$accepted".toByteArray()),
        ExecEvent.Exit(0),
      )
    }
    val events = mutableListOf<HostKeyEvent>()
    val viewModel = createViewModel()
    val hostKeyJob = launch { viewModel.hostKeyEvent.collect { events += it } }
    advanceUntilIdle()

    viewModel.send("hostname")
    advanceUntilIdle()

    assertEquals(
      listOf(HostKeyEvent.Mismatch(profile.host, profile.port, "SHA256:old", "SHA256:new", "ED25519")),
      events,
    )

    viewModel.respondToHostKey(false)
    advanceUntilIdle()

    assertTrue(viewModel.messages.value.last().content.contains("replaced:false"))
    assertEquals(MessageStatus.Final, viewModel.messages.value.last().status)
    hostKeyJob.cancel()
  }

  @Test
  fun `error events use friendly localized messages`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(ExecEvent.Error("timeout", "socket timeout"))
    }
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.send("long-run")
    advanceUntilIdle()

    val last = viewModel.messages.value.last()
    assertEquals(MessageStatus.Error, last.status)
    assertEquals(app.getString(R.string.err_timeout), last.content)
    assertEquals(ConnectionState.READY, viewModel.connectionState.value)
  }

  @Test
  fun `cancelRunning marks assistant message as canceled and preserves prior output`() = runTest(dispatcher) {
    val hangingFactory = object : SshExecRunnerFactory {
      override fun create(
        knownHostsStore: KnownHostsStore,
        hostKeyCallback: HostKeyCallback,
        passwordProvider: PasswordProvider?,
        keyContentProvider: KeyContentProvider?,
        passphraseProvider: PassphraseProvider?,
      ): ClosableSshExecRunner = object : ClosableSshExecRunner {
        override fun run(profile: SshProfile, command: String) = kotlinx.coroutines.flow.flow {
          emit(ExecEvent.StdoutChunk("partial output".toByteArray()))
          awaitCancellation()
        }

        override fun close() = Unit
      }
    }
    runnerFactory = hangingFactory
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.send("tail -f /var/log/app.log")
    advanceUntilIdle()
    viewModel.cancelRunning()
    advanceUntilIdle()

    val last = viewModel.messages.value.last()
    assertEquals(MessageStatus.Canceled, last.status)
    assertTrue(last.content.contains("partial output"))
    assertTrue(last.content.contains(app.getString(R.string.msg_command_canceled)))
    assertEquals(ConnectionState.READY, viewModel.connectionState.value)
  }

  @Test
  fun `canceled exec event maps to canceled message state`() = runTest(dispatcher) {
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk("partial output".toByteArray()),
        ExecEvent.Canceled,
      )
    }
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.send("sleep 10")
    advanceUntilIdle()

    val last = viewModel.messages.value.last()
    assertEquals(MessageStatus.Canceled, last.status)
    assertTrue(last.content.contains("partial output"))
    assertTrue(last.content.contains(app.getString(R.string.msg_command_canceled)))
    assertEquals(ConnectionState.READY, viewModel.connectionState.value)
  }

  @Test
  fun `codex json progress events render structured thread turn item summary`() = runTest(dispatcher) {
    profile = profile.copy(codexMode = true)
    profileStore = FakeProfileStore(profile)
    runnerFactory = RecordingExecRunnerFactory { _, _ ->
      listOf(
        ExecEvent.StdoutChunk(
          (
            """{"type":"thread.started","thread_id":"thread-1","status":"running"}""" + "\n" +
              """{"type":"turn.completed","turn":{"id":"turn-2"}}""" + "\n" +
              """{"type":"item.started","item":{"id":"item-9","type":"tool_call"}}""" + "\n" +
              """{"delta":"Hello from Codex"}""" + "\n"
            ).toByteArray()
        ),
        ExecEvent.Exit(0),
      )
    }
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.send("summarize the repo")
    advanceUntilIdle()

    val last = viewModel.messages.value.last()
    assertEquals(MessageStatus.Final, last.status)
    assertTrue(last.content.contains(app.getString(R.string.msg_codex_status_prefix, "running")))
    assertTrue(last.content.contains("${app.getString(R.string.msg_codex_progress_thread)}: Started • #thread-1"))
    assertTrue(last.content.contains("${app.getString(R.string.msg_codex_progress_turn)}: Completed • #turn-2"))
    assertTrue(last.content.contains("${app.getString(R.string.msg_codex_progress_item)}: Started • tool_call • #item-9"))
    assertTrue(last.content.contains("Hello from Codex"))
  }

  @Test
  fun `exportSession returns plain text transcript for current thread`() = runTest(dispatcher) {
    val targetThreadId = chatRepository.createThread(profile.id, "ls -la")
    chatRepository.insertMessage(
      targetThreadId,
      ChatMessage(
        id = "u-1",
        role = ChatRole.User,
        content = "ls -la",
        timestampMs = 1L,
        status = MessageStatus.Final,
      ),
    )
    chatRepository.insertMessage(
      targetThreadId,
      ChatMessage(
        id = "a-1",
        role = ChatRole.Assistant,
        content = "total 8",
        timestampMs = 2L,
        status = MessageStatus.Final,
      ),
    )

    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.switchThread(targetThreadId)
    advanceUntilIdle()

    val exported = viewModel.exportSession()

    val expected = """
      # Session: ${profile.name}

      $ ls -la

      total 8

    """.trimIndent() + "\n"
    assertEquals(expected, exported)
  }

  private fun createViewModel(): ChatViewModel = ChatViewModel(
    profileId = profile.id,
    profileStore = profileStore,
    knownHostsStore = FakeKnownHostsStore(),
    chatRepository = chatRepository,
    appPreferences = appPreferences,
    keyContentProvider = KeyContentProvider { "dummy-key" },
    runnerFactory = runnerFactory,
    app = app,
  )

  private data class RunnerContext(
    val hostKeyCallback: HostKeyCallback,
    val passwordProvider: PasswordProvider?,
    val passphraseProvider: PassphraseProvider?,
    val keyContentProvider: KeyContentProvider?,
  )

  private class RecordingExecRunnerFactory(
    private val scenario: suspend (RunnerContext, SshProfile) -> List<ExecEvent>,
  ) : SshExecRunnerFactory {
    override fun create(
      knownHostsStore: KnownHostsStore,
      hostKeyCallback: HostKeyCallback,
      passwordProvider: PasswordProvider?,
      keyContentProvider: KeyContentProvider?,
      passphraseProvider: PassphraseProvider?,
    ): ClosableSshExecRunner = object : ClosableSshExecRunner {
      override fun run(profile: SshProfile, command: String) = kotlinx.coroutines.flow.flow {
        scenario(
          RunnerContext(
            hostKeyCallback = hostKeyCallback,
            passwordProvider = passwordProvider,
            passphraseProvider = passphraseProvider,
            keyContentProvider = keyContentProvider,
          ),
          profile,
        ).forEach { emit(it) }
      }

      override fun close() = Unit
    }
  }

  private class FakeProfileStore(
    private val initialProfile: SshProfile? = null,
  ) : ProfileStore {
    private val profiles = linkedMapOf<String, SshProfile>().apply {
      if (initialProfile != null) {
        put(initialProfile.id, initialProfile)
      }
    }

    override suspend fun listProfiles(): List<SshProfile> = profiles.values.toList()

    override suspend fun getById(id: String): SshProfile? = profiles[id]

    override suspend fun upsert(profile: SshProfile) {
      profiles[profile.id] = profile
    }

    override suspend fun delete(profileId: String) {
      profiles.remove(profileId)
    }
  }

  private class FakeChatRepository(
    private val profileId: String,
  ) : ChatRepository {
    private val threads = linkedMapOf<String, ChatThread>()
    private val messagesByThread = linkedMapOf<String, MutableList<ChatMessage>>()
    private val threadFlow = MutableStateFlow(emptyList<ChatThread>())
    private var nextThreadId = 1

    override fun observeThreads(profileId: String): Flow<List<ChatThread>> {
      require(profileId == this.profileId)
      return threadFlow
    }

    override suspend fun createThread(profileId: String, title: String): String {
      require(profileId == this.profileId)
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

    override suspend fun updateMessage(id: String, content: String, status: MessageStatus) {
      messagesByThread.values.forEach { list ->
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
          list[index] = list[index].copy(content = content, status = status)
          return
        }
      }
    }

    override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
      flowOf(messagesByThread[threadId].orEmpty().toList())

    override suspend fun getMessages(threadId: String): List<ChatMessage> =
      messagesByThread[threadId].orEmpty().toList()

    override suspend fun updateThreadTitle(threadId: String, title: String) {
      val existing = threads[threadId] ?: return
      threads[threadId] = existing.copy(title = title)
      emitThreads()
    }

    private fun emitThreads() {
      threadFlow.update { threads.values.toList() }
    }
  }

  private class FakeKnownHostsStore : KnownHostsStore {
    override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? = null

    override suspend fun store(host: String, port: Int, key: StoredHostKey) = Unit

    override suspend fun remove(host: String, port: Int) = Unit

    override suspend fun getAll(): List<Pair<String, StoredHostKey>> = emptyList()
  }
}
