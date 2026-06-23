package io.rikka.agent.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.R
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ssh.DefaultSshExecRunnerFactory
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.SshExecRunnerFactory
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Thin orchestrator that composes [ChatSessionManager], [CommandExecutor],
 * and [AuthCallbackBroker] to drive the chat UI.
 *
 * Owns the message list and delegates all domain logic to sub-components.
 */
class ChatViewModel(
  private val profileId: String,
  profileStore: ProfileStore,
  knownHostsStore: KnownHostsStore,
  chatRepository: ChatRepository,
  appPreferences: AppPreferences,
  keyContentProvider: KeyContentProvider? = null,
  runnerFactory: SshExecRunnerFactory = DefaultSshExecRunnerFactory,
  private val app: Application,
) : ViewModel() {

  // ── Sub-components ─────────────────────────────────────────────────────

  private val authBroker = AuthCallbackBroker()

  private val sessionManager = ChatSessionManager(
    profileId = profileId,
    chatRepository = chatRepository,
    scope = viewModelScope,
  )

  private val commandExecutor = CommandExecutor(
    profileStore = profileStore,
    knownHostsStore = knownHostsStore,
    appPreferences = appPreferences,
    keyContentProvider = keyContentProvider,
    runnerFactory = runnerFactory,
    authBroker = authBroker,
    app = app,
    scope = viewModelScope,
  )

  // ── UI State ───────────────────────────────────────────────────────────

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages

  private val _profileLabel = MutableStateFlow("")
  val profileLabel: StateFlow<String> = _profileLabel

  val threads: StateFlow<List<ChatThread>> = sessionManager.threads
  val connectionState: StateFlow<ConnectionState> = commandExecutor.connectionState

  // ── Auth flows (forwarded from broker) ─────────────────────────────────

  val hostKeyEvent: SharedFlow<HostKeyEvent> = authBroker.hostKeyEvent
  val passwordRequest: SharedFlow<String> = authBroker.passwordRequest
  val passphraseRequest: SharedFlow<String> = authBroker.passphraseRequest

  init {
    loadProfile()
  }

  private fun loadProfile() {
    viewModelScope.launch {
      val result = commandExecutor.loadProfile(profileId)
      if (result != null) {
        val (profile, label) = result
        _profileLabel.value = label
        appendSystemMessage(
          app.getString(R.string.msg_profile_ready, profile.name, profile.username, profile.host),
        )
      } else {
        appendSystemMessage(app.getString(R.string.msg_profile_not_found))
      }
    }
  }

  // ── Auth responses (forwarded to broker) ───────────────────────────────

  fun respondToHostKey(accepted: Boolean) = authBroker.respondToHostKey(accepted)
  fun respondToPassword(password: String?) = authBroker.respondToPassword(password)
  fun respondToPassphrase(passphrase: String?) = authBroker.respondToPassphrase(passphrase)

  // ── Session management ─────────────────────────────────────────────────

  /** Start a fresh session (new thread). */
  fun newSession() {
    commandExecutor.cancel()
    sessionManager.newSession()
    commandExecutor.clearFullOutput()
    _messages.value = emptyList()
    commandExecutor.resetConnectionState()
    val profile = commandExecutor.currentProfile ?: return
    appendSystemMessage(
      app.getString(R.string.msg_new_session, profile.name, profile.username, profile.host),
    )
  }

  /** Switch to an existing thread and load its messages. */
  fun switchThread(targetThreadId: String) {
    commandExecutor.cancel()
    sessionManager.switchThread(targetThreadId)
    commandExecutor.resetConnectionState()
    viewModelScope.launch {
      _messages.value = sessionManager.getMessages(targetThreadId)
    }
  }

  /** Delete a thread and clear UI if it was the current one. */
  fun deleteThread(targetThreadId: String) {
    viewModelScope.launch {
      val wasActive = sessionManager.deleteThread(targetThreadId)
      if (wasActive) {
        commandExecutor.clearFullOutput()
        newSession()
      }
    }
  }

  // ── Output access ──────────────────────────────────────────────────────

  fun hasFullOutput(messageId: String): Boolean =
    commandExecutor.hasFullOutput(messageId)

  fun getFullOutput(messageId: String): String? =
    commandExecutor.getFullOutput(messageId)

  // ── Command execution ──────────────────────────────────────────────────

  fun send(text: String) {
    val command = text.trim()
    if (command.isEmpty()) return

    if (commandExecutor.currentProfile == null) {
      appendSystemMessage(app.getString(R.string.msg_not_connected))
      return
    }

    // User message
    val userMsg = ChatMessage(
      id = "u-${UUID.randomUUID()}",
      role = ChatRole.User,
      content = command,
      timestampMs = System.currentTimeMillis(),
      status = MessageStatus.Final,
    )
    _messages.update { it + userMsg }
    sessionManager.persistMessage(userMsg)

    // Assistant seed
    val assistantId = "a-${UUID.randomUUID()}"
    val assistantSeed = ChatMessage(
      id = assistantId,
      role = ChatRole.Assistant,
      content = "",
      timestampMs = System.currentTimeMillis(),
      status = MessageStatus.Streaming,
    )
    _messages.update { it + assistantSeed }
    sessionManager.persistMessage(assistantSeed)

    val profile = commandExecutor.currentProfile!!
    commandExecutor.execute(
      command = command,
      assistantId = assistantId,
      isCodex = profile.codexMode,
      updateContent = ::updateAssistantContent,
      updateMessage = { id, content, status ->
        updateAssistantMessage(id, content, status)
        sessionManager.persistUpdate(id, content, status)
      },
      getAssistantContent = ::getAssistantContent,
    )
  }

  fun cancelRunning() {
    val canceledId = commandExecutor.cancel()
    if (canceledId != null) {
      val canceledText = app.getString(R.string.msg_command_canceled)
      val current = _messages.value.firstOrNull { it.id == canceledId }
      val newContent = CancelMessageHelper.mergeCanceledContent(current?.content, canceledText)
      updateAssistantMessage(canceledId, newContent, MessageStatus.Canceled)
      sessionManager.persistUpdate(canceledId, newContent, MessageStatus.Canceled)
    }
    commandExecutor.resetConnectionState()
  }

  /** Export current session as plain text. */
  fun exportSession(): String =
    SessionExporter.export(_profileLabel.value, _messages.value)

  override fun onCleared() {
    super.onCleared()
    commandExecutor.cleanUp()
  }

  // ── Private helpers ────────────────────────────────────────────────────

  private fun updateAssistantContent(id: String, content: String) {
    _messages.update { list ->
      list.map { if (it.id == id) it.copy(content = content) else it }
    }
  }

  private fun updateAssistantMessage(id: String, content: String, status: MessageStatus) {
    _messages.update { list ->
      list.map { if (it.id == id) it.copy(content = content, status = status) else it }
    }
  }

  private fun getAssistantContent(id: String): String? =
    _messages.value.firstOrNull { it.id == id }?.content

  private fun appendSystemMessage(text: String) {
    val msg = ChatMessage(
      id = "sys-${UUID.randomUUID()}",
      role = ChatRole.Assistant,
      content = text,
      timestampMs = System.currentTimeMillis(),
      status = MessageStatus.Final,
    )
    _messages.update { it + msg }
  }
}

enum class ConnectionState {
  IDLE,
  READY,
  EXECUTING,
  ERROR,
}

sealed class HostKeyEvent {
  data class UnknownHost(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
  ) : HostKeyEvent()

  data class Mismatch(
    val host: String,
    val port: Int,
    val expectedFingerprint: String,
    val actualFingerprint: String,
    val keyType: String,
  ) : HostKeyEvent()
}
