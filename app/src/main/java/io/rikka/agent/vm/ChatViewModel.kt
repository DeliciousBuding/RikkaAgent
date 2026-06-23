package io.rikka.agent.vm

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.R
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SessionStats
import io.rikka.agent.ssh.ConnectionState
import io.rikka.agent.ssh.DefaultSshExecRunnerFactory
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.SshExecRunnerFactory
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.CoroutineExceptionHandler
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
 *   Also bridges session management features: search, pin, archive, tags, stats, and
 *   multi-format export.
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
 * | [activeThreads]              | `StateFlow<List<ChatThread>>` | Non-archived threads, pinned first.              |
 * | [archivedThreads]            | `StateFlow<List<ChatThread>>` | Archived threads.                                |
 * | [searchResults]              | `StateFlow<List<ChatThread>>` | FTS search results.                              |
 * | [searchQuery]                | `StateFlow<String>`           | Current search query.                            |
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

  /** Manages thread CRUD, title generation, message persistence, search, pin/archive, tags. */
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

  /** Global exception handler for viewModelScope.launch calls outside command execution.
   *  Logs the error and appends an error system message so the user sees feedback. */
  private val errorHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("ChatViewModel", "Unhandled coroutine exception", throwable)
    appendSystemMessage(
      app.getString(R.string.err_ssh_generic, throwable.message ?: throwable.javaClass.simpleName),
    )
  }

  // ── UI State ───────────────────────────────────────────────────────────

  /** Backing list of messages for the current thread. Mutated via [MutableStateFlow.update]. */
  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  /** Observable message list for the chat UI. */
  val messages: StateFlow<List<ChatMessage>> = _messages

  /** Backing field for the profile display label. */
  private val _profileLabel = MutableStateFlow("")
  /** Human-readable label for the loaded SSH profile (e.g. "root@host"). */
  val profileLabel: StateFlow<String> = _profileLabel

  /** Current active thread ID, exposed for UI. */
  private val _currentThreadId = MutableStateFlow<String?>(null)
  val currentThreadId: StateFlow<String?> = _currentThreadId

  // ── Session management state (forwarded from sessionManager) ────────────

  /** Observable list of active (non-archived) threads, pinned first. */
  val activeThreads: StateFlow<List<ChatThread>> = sessionManager.activeThreads
  /** Observable list of archived threads. */
  val archivedThreads: StateFlow<List<ChatThread>> = sessionManager.archivedThreads
  /** FTS search results for the current search query. */
  val searchResults: StateFlow<List<ChatThread>> = sessionManager.searchResults
  /** Current search query text. */
  val searchQuery: StateFlow<String> = sessionManager.searchQuery

  /** Observable list of past chat threads for the thread-switcher drawer. */
  val threads: StateFlow<List<ChatThread>> = sessionManager.activeThreads
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
    viewModelScope.launch(errorHandler) {
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
    _currentThreadId.value = null
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
    _currentThreadId.value = targetThreadId
    commandExecutor.resetConnectionState()
    viewModelScope.launch(errorHandler) {
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
    viewModelScope.launch(errorHandler) {
      val wasActive = sessionManager.deleteThread(targetThreadId)
      if (wasActive) {
        commandExecutor.clearFullOutput()
        _currentThreadId.value = null
        newSession()
      }
    }
  }

  // ── Search ─────────────────────────────────────────────────────────────

  /**
   * Update the search query for full-text session search.
   *
   * @param query The search text. Empty string clears the search.
   */
  fun setSearchQuery(query: String) {
    sessionManager.setSearchQuery(query)
  }

  // ── Pin / Archive ──────────────────────────────────────────────────────

  /**
   * Toggle the pinned state of a thread.
   *
   * @param threadId The thread to toggle.
   */
  fun togglePin(threadId: String) {
    sessionManager.togglePin(threadId)
  }

  /**
   * Archive a thread, moving it from the active list to the archived list.
   *
   * @param threadId The thread to archive.
   */
  fun archiveThread(threadId: String) {
    sessionManager.archiveThread(threadId)
  }

  /**
   * Unarchive a thread, restoring it to the active list.
   *
   * @param threadId The thread to unarchive.
   */
  fun unarchiveThread(threadId: String) {
    sessionManager.unarchiveThread(threadId)
  }

  // ── Tags ───────────────────────────────────────────────────────────────

  /**
   * Add a tag to a thread.
   *
   * @param threadId The thread to tag.
   * @param tag The tag name (will be trimmed).
   */
  fun addTag(threadId: String, tag: String) {
    sessionManager.addTag(threadId, tag)
  }

  /**
   * Remove a tag from a thread.
   *
   * @param threadId The thread to remove the tag from.
   * @param tag The tag name to remove.
   */
  fun removeTag(threadId: String, tag: String) {
    sessionManager.removeTag(threadId, tag)
  }

  // ── Stats ──────────────────────────────────────────────────────────────

  /**
   * Compute session statistics for a thread.
   *
   * @param threadId The thread to compute stats for.
   * @param callback Callback to receive the computed stats (called on Main dispatcher).
   */
  fun computeThreadStats(threadId: String, callback: (SessionStats) -> Unit) {
    viewModelScope.launch(errorHandler) {
      val stats = sessionManager.getThreadStats(threadId)
      callback(stats)
    }
  }

  // ── Output access ──────────────────────────────────────────────────────

  /**
   * Check whether full (untruncated) output is available for a given message.
   *
   * @param messageId The assistant message ID to check.
   * @return `true` if the full output is stored in memory.
   */
  fun hasFullOutput(messageId: String): Boolean =
    commandExecutor.hasFullOutput(messageId)

  /**
   * Retrieve full (untruncated) output for a given message.
   *
   * @param messageId The assistant message ID.
   * @return The full output string, or null if not available.
   */
  fun getFullOutput(messageId: String): String? =
    commandExecutor.getFullOutput(messageId)

  // ── Command execution ──────────────────────────────────────────────────

  /**
   * Send a command for execution over SSH.
   *
   * Creates a user [ChatMessage] and an assistant seed message, persists both,
   * then delegates to [CommandExecutor.execute] which streams output back
   * via callbacks that update the assistant message in [_messages].
   *
   * If no profile is loaded, appends an error system message instead.
   *
   * @param text The raw command text from the user input.
   */
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

  /**
   * Cancel the currently running command.
   *
   * If a command was running, marks its assistant message as [MessageStatus.Canceled],
   * merges a cancellation notice into the content, and resets the connection state.
   */
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

  /**
   * Export the current session in the given format.
   *
   * For non-PLAIN formats, includes computed session statistics.
   *
   * @param format The export format (default: [ExportFormat.PLAIN]).
   * @return A formatted string containing the profile label, messages, and optionally stats.
   */
  fun exportSession(format: ExportFormat = ExportFormat.PLAIN): String {
    val stats = if (format != ExportFormat.PLAIN) {
      val msgs = _messages.value
      val commandCount = msgs.count { it.role == ChatRole.User }
      val outputLineCount = msgs
        .filter { it.role == ChatRole.Assistant && it.content.isNotEmpty() }
        .sumOf { it.content.lines().size }
      var totalTime = 0L
      for (i in 0 until msgs.size - 1) {
        if (msgs[i].role == ChatRole.User && msgs[i + 1].role == ChatRole.Assistant) {
          val delta = msgs[i + 1].timestampMs - msgs[i].timestampMs
          if (delta > 0) totalTime += delta
        }
      }
      SessionStats(commandCount, totalTime, outputLineCount)
    } else null

    return SessionExporter.export(_profileLabel.value, _messages.value, stats, format)
  }

  /**
   * Called when the ViewModel is being cleared (Activity/Fragment destroyed).
   * Releases SSH runner resources held by [CommandExecutor].
   */
  override fun onCleared() {
    super.onCleared()
    commandExecutor.cleanUp()
  }

  // ── Private helpers ────────────────────────────────────────────────────

  /** Update only the content of an assistant message by [id], preserving its current status. */
  private fun updateAssistantContent(id: String, content: String) {
    _messages.update { list ->
      list.map { if (it.id == id) it.copy(content = content) else it }
    }
  }

  /** Update both content and status of an assistant message by [id]. */
  private fun updateAssistantMessage(id: String, content: String, status: MessageStatus) {
    _messages.update { list ->
      list.map { if (it.id == id) it.copy(content = content, status = status) else it }
    }
  }

  /** Retrieve the current content of an assistant message by [id], or null if not found. */
  private fun getAssistantContent(id: String): String? =
    _messages.value.firstOrNull { it.id == id }?.content

  /** Create and append a system-role [ChatMessage] with the given [text] to [_messages]. */
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

/**
 * Sealed class representing SSH host-key verification events.
 *
 * Collected from [AuthCallbackBroker.hostKeyEvent] and responded to via
 * [ChatViewModel.respondToHostKey].
 */
sealed class HostKeyEvent {
  /**
   * The SSH host is not in the known-hosts file.
   *
   * @param host The hostname or IP.
   * @param port The SSH port.
   * @param fingerprint The server's public-key fingerprint.
   * @param keyType The key algorithm (e.g. "ssh-ed25519").
   */
  data class UnknownHost(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
  ) : HostKeyEvent()

  /**
   * The SSH host's key has changed since it was last recorded.
   *
   * @param host The hostname or IP.
   * @param port The SSH port.
   * @param expectedFingerprint The fingerprint stored in known-hosts.
   * @param actualFingerprint The fingerprint presented by the server.
   * @param keyType The key algorithm (e.g. "ssh-ed25519").
   */
  data class Mismatch(
    val host: String,
    val port: Int,
    val expectedFingerprint: String,
    val actualFingerprint: String,
    val keyType: String,
  ) : HostKeyEvent()
}
