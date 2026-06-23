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
 * Top-level ViewModel for the chat screen.
 *
 * ## Responsibilities
 * - Orchestrates [ChatSessionManager] (thread lifecycle), [CommandExecutor] (SSH execution),
 *   and [AuthCallbackBroker] (authentication callbacks) to drive the chat UI.
 * - Owns the observable message list and exposes it as a [StateFlow] for Compose collection.
 * - Delegates all domain logic (SSH connection, command execution, persistence) to sub-components,
 *   keeping itself as a thin coordination layer.
 *
 * ## Thread Safety
 * - All mutable state is held in [MutableStateFlow] instances, which are safe for concurrent
 *   emission and collection.
 * - The [viewModelScope] is used for all coroutines; mutations to [_messages] happen on the
 *   Main dispatcher via [MutableStateFlow.update].
 * - [CommandExecutor] launches its own coroutine in the provided scope for SSH streaming;
 *   its callbacks (`updateContent`, `updateMessage`) are invoked from that coroutine and
 *   must be thread-safe (they are, via `StateFlow.update`).
 *
 * ## Exposed State
 * | StateFlow / SharedFlow       | Type                          | Description                                      |
 * |------------------------------|-------------------------------|--------------------------------------------------|
 * | [messages]                   | `StateFlow<List<ChatMessage>>`| Full message list for the current thread.        |
 * | [profileLabel]               | `StateFlow<String>`           | Display label for the loaded SSH profile.        |
 * | [threads]                    | `StateFlow<List<ChatThread>>` | Past thread list for thread-switcher UI.         |
 * | [connectionState]            | `StateFlow<ConnectionState>`  | IDLE / READY / EXECUTING / ERROR.                |
 * | [lastConnectionError]        | `StateFlow<ConnectionError?>` | Most recent connection-level error, dismissable. |
 *
 * ## Exposed Events (SharedFlows)
 * | SharedFlow                   | Type                          | Description                                      |
 * |------------------------------|-------------------------------|--------------------------------------------------|
 * | [hostKeyEvent]               | `SharedFlow<HostKeyEvent>`    | Unknown-host or host-key-mismatch prompt.        |
 * | [passwordRequest]            | `SharedFlow<String>`          | Password auth prompt (description string).       |
 * | [passphraseRequest]          | `SharedFlow<String>`          | Key passphrase prompt (description string).      |
 *
 * @param profileId  The SSH profile ID this session is bound to.
 * @param app        Android [Application] for string resource access.
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

  /** Broker that bridges SSH authentication callbacks to UI-side SharedFlows. */
  private val authBroker = AuthCallbackBroker()

  /** Manages thread CRUD, title generation, and message persistence. */
  private val sessionManager = ChatSessionManager(
    profileId = profileId,
    chatRepository = chatRepository,
    scope = viewModelScope,
  )

  /** Handles SSH connection lifecycle, command execution, and output streaming. */
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

  /** Backing list of messages for the current thread. Mutated via [MutableStateFlow.update]. */
  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  /** Observable message list for the chat UI. */
  val messages: StateFlow<List<ChatMessage>> = _messages

  /** Backing field for the profile display label. */
  private val _profileLabel = MutableStateFlow("")
  /** Human-readable label for the loaded SSH profile (e.g. "root@host"). */
  val profileLabel: StateFlow<String> = _profileLabel

  /** Observable list of past chat threads for the thread-switcher drawer. */
  val threads: StateFlow<List<ChatThread>> = sessionManager.threads
  /** Current SSH connection lifecycle state. */
  val connectionState: StateFlow<ConnectionState> = commandExecutor.connectionState
  /** Most recent connection-level error, or null if none / dismissed. */
  val lastConnectionError: StateFlow<ConnectionError?> = commandExecutor.lastConnectionError

  /** Dismiss the current [lastConnectionError]. */
  fun dismissConnectionError() = commandExecutor.dismissConnectionError()

  // ── Auth flows (forwarded from broker) ─────────────────────────────────

  /** Emits [HostKeyEvent] when the SSH host is unknown or its key has changed. */
  val hostKeyEvent: SharedFlow<HostKeyEvent> = authBroker.hostKeyEvent
  /** Emits a description string when password authentication is requested. */
  val passwordRequest: SharedFlow<String> = authBroker.passwordRequest
  /** Emits a description string when a private-key passphrase is requested. */
  val passphraseRequest: SharedFlow<String> = authBroker.passphraseRequest

  init {
    loadProfile()
  }

  /**
   * Load the SSH profile for [profileId] and emit a system message with the result.
   * Runs on [viewModelScope]; updates [_profileLabel] and appends a system [ChatMessage].
   */
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

  /** Respond to a host-key verification prompt. [accepted] = true to trust. */
  fun respondToHostKey(accepted: Boolean) = authBroker.respondToHostKey(accepted)
  /** Respond to a password prompt. Pass null to cancel authentication. */
  fun respondToPassword(password: String?) = authBroker.respondToPassword(password)
  /** Respond to a passphrase prompt. Pass null to cancel authentication. */
  fun respondToPassphrase(passphrase: String?) = authBroker.respondToPassphrase(passphrase)

  // ── Session management ─────────────────────────────────────────────────

  /**
   * Start a fresh session (new thread).
   *
   * Cancels any running command, resets the session manager and connection state,
   * clears the message list, and appends a system message with the current profile info.
   */
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

  /**
   * Switch to an existing thread and load its messages.
   *
   * Cancels any running command, switches the session manager's active thread,
   * resets connection state, and loads messages from the repository into [_messages].
   *
   * @param targetThreadId The ID of the thread to switch to.
   */
  fun switchThread(targetThreadId: String) {
    commandExecutor.cancel()
    sessionManager.switchThread(targetThreadId)
    commandExecutor.resetConnectionState()
    viewModelScope.launch {
      _messages.value = sessionManager.getMessages(targetThreadId)
    }
  }

  /**
   * Delete a thread and clear UI if it was the current one.
   *
   * If the deleted thread was the active session, automatically starts a new session.
   *
   * @param targetThreadId The ID of the thread to delete.
   */
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
