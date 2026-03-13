package io.rikka.agent.ui.screen

import android.app.Application
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.ProfileStore
import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.ui.R as UiR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatScreenShareDispatchTest {

  @get:Rule
  val composeRule = createComposeRule()

  private val dispatcher = StandardTestDispatcher()
  private lateinit var app: Application
  private lateinit var prefs: AppPreferences
  private lateinit var profile: SshProfile
  private lateinit var profileStore: FakeProfileStore
  private lateinit var chatRepository: FakeChatRepository
  private lateinit var viewModel: ChatViewModel

  @Before
  fun setUp() {
    stopKoin()
    Dispatchers.setMain(dispatcher)
    app = ApplicationProvider.getApplicationContext()
    prefs = AppPreferences(app)
    runBlocking {
      prefs.setDefaultShell("/bin/sh")
    }
    profile = SshProfile(
      id = "profile-1",
      name = "Prod Box",
      host = "example.com",
      username = "ding",
    )
    profileStore = FakeProfileStore(profile)
    chatRepository = FakeChatRepository(profile.id)
    viewModel = ChatViewModel(
      profileId = profile.id,
      profileStore = profileStore,
      knownHostsStore = FakeKnownHostsStore(),
      chatRepository = chatRepository,
      appPreferences = prefs,
      keyContentProvider = KeyContentProvider { "dummy-key" },
      runnerFactory = RecordingExecRunnerFactory { _, _ ->
        listOf(
          ExecEvent.StdoutChunk("hello output".toByteArray()),
          ExecEvent.Exit(0),
        )
      },
      app = app,
    )
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  @Test
  fun `chat screen share output dispatches chooser intent`() = runTest(dispatcher) {
    val started = mutableListOf<Intent>()
    val context = ApplicationProvider.getApplicationContext<Application>()

    composeRule.setContent {
      MaterialTheme {
        ChatScreen(
          profileId = profile.id,
          vmOverride = viewModel,
          prefsOverride = prefs,
          startActivityOverride = { started += it },
        )
      }
    }

    val threadId = chatRepository.createThread(profile.id, "Session")
    chatRepository.insertMessage(
      threadId,
      ChatMessage(
        id = "assistant-1",
        role = ChatRole.Assistant,
        content = "hello output",
        timestampMs = System.currentTimeMillis(),
        status = MessageStatus.Final,
      ),
    )
    viewModel.switchThread(threadId)
    advanceUntilIdle()
    composeRule.waitForIdle()

    composeRule.onNodeWithContentDescription(context.getString(UiR.string.cd_share))
      .assertIsDisplayed()
    composeRule.onNodeWithContentDescription(context.getString(UiR.string.cd_share))
      .performClick()
    composeRule.waitUntil(5_000) { started.isNotEmpty() }

    val chooser = started.lastOrNull()
    assertNotNull(chooser)
    assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)

    @Suppress("DEPRECATION")
    val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
    assertNotNull(target)
    assertEquals(Intent.ACTION_SEND, target!!.action)
    assertEquals("text/plain", target.type)
    assertTrue(target.getStringExtra(Intent.EXTRA_TEXT)!!.contains("hello output"))
  }

  @Test
  fun `chat screen export session dispatches chooser intent`() = runTest(dispatcher) {
    val started = mutableListOf<Intent>()
    val context = ApplicationProvider.getApplicationContext<Application>()

    composeRule.setContent {
      MaterialTheme {
        ChatScreen(
          profileId = profile.id,
          vmOverride = viewModel,
          prefsOverride = prefs,
          startActivityOverride = { started += it },
        )
      }
    }

    val threadId = chatRepository.createThread(profile.id, "Session")
    chatRepository.insertMessage(
      threadId,
      ChatMessage(
        id = "assistant-2",
        role = ChatRole.Assistant,
        content = "uptime output",
        timestampMs = System.currentTimeMillis(),
        status = MessageStatus.Final,
      ),
    )
    viewModel.switchThread(threadId)
    advanceUntilIdle()
    composeRule.waitForIdle()

    composeRule.onNodeWithContentDescription(context.getString(R.string.export_session))
      .performClick()

    val chooser = started.lastOrNull()
    assertNotNull(chooser)
    assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)

    @Suppress("DEPRECATION")
    val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
    assertNotNull(target)
    assertEquals(Intent.ACTION_SEND, target!!.action)
    assertEquals(
      context.getString(R.string.ssh_session_subject, viewModel.profileLabel.value),
      target.getStringExtra(Intent.EXTRA_SUBJECT),
    )
  }

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
    private val messageFlows = linkedMapOf<String, MutableStateFlow<List<ChatMessage>>>()
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
      val list = messagesByThread.getOrPut(threadId) { mutableListOf() }
      list.add(message)
      messageFlow(threadId).value = list.toList()
    }

    override suspend fun updateMessage(id: String, content: String, status: MessageStatus) {
      messagesByThread.values.forEach { list ->
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
          list[index] = list[index].copy(content = content, status = status)
          messageFlowForList(list).value = list.toList()
          return
        }
      }
    }

    override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
      messageFlow(threadId)

    override suspend fun getMessages(threadId: String): List<ChatMessage> =
      messagesByThread[threadId].orEmpty().toList()

    override suspend fun updateThreadTitle(threadId: String, title: String) {
      val existing = threads[threadId] ?: return
      threads[threadId] = existing.copy(title = title)
      emitThreads()
    }

    private fun messageFlow(threadId: String): MutableStateFlow<List<ChatMessage>> =
      messageFlows.getOrPut(threadId) { MutableStateFlow(messagesByThread[threadId].orEmpty().toList()) }

    private fun messageFlowForList(list: MutableList<ChatMessage>): MutableStateFlow<List<ChatMessage>> {
      val threadId = messagesByThread.entries.firstOrNull { it.value === list }?.key
      return if (threadId != null) messageFlow(threadId) else MutableStateFlow(list.toList())
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
