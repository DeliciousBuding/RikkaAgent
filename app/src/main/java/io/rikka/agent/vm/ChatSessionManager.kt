package io.rikka.agent.vm

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.storage.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages chat thread lifecycle: CRUD, title auto-generation, and message persistence.
 *
 * ## Responsibilities
 * - Maintains the current active thread ID and provides thread switching.
 * - Observes all threads for a given profile via [ChatRepository.observeThreads].
 * - Persists messages to the repository, auto-creating a thread on first message.
 * - Auto-generates a thread title from the first user command (truncated to 50 chars).
 * - Provides message loading for thread switching.
 *
 * ## Thread Safety
 * - [currentThreadId] is a simple `var` mutated only from the Main dispatcher
 *   (via `viewModelScope` in [ChatViewModel]), so no concurrent access occurs.
 * - The [threads] flow is created via [StateFlow.stateIn] with [SharingStarted.WhileSubscribed],
 *   which is safe for multi-collector scenarios.
 * - [persistMessage] and [persistUpdate] launch coroutines in [scope]; the underlying
 *   [ChatRepository] is expected to be thread-safe (Room DAO operations).
 *
 * ## Exposed State
 * | StateFlow  | Type                           | Description                                    |
 * |------------|--------------------------------|------------------------------------------------|
 * | [threads]  | `StateFlow<List<ChatThread>>`  | Observable list of past threads for this profile. |
 *
 * @param profileId      The SSH profile ID this session manager is bound to.
 * @param chatRepository Repository for thread and message persistence.
 * @param scope          Coroutine scope for launching persistence jobs (typically [viewModelScope]).
 */
class ChatSessionManager(
  private val profileId: String,
  private val chatRepository: ChatRepository,
  private val scope: CoroutineScope,
) {

  /**
   * Observable list of past threads for this profile.
   *
   * Uses [SharingStarted.WhileSubscribed] with a 5-second replay timeout so the
   * upstream Flow stops collecting when no UI subscribers are active.
   */
  val threads: StateFlow<List<ChatThread>> = chatRepository.observeThreads(profileId)
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Current active thread ID, or `null` for unsaved new sessions.
   *
   * Set to `null` by [newSession], set to an existing ID by [switchThread],
   * and set to a newly created ID by [persistMessage] on first message.
   */
  var currentThreadId: String? = null
    private set

  /**
   * Start a fresh session by clearing the current thread ID.
   * The next call to [persistMessage] will auto-create a new thread.
   */
  fun newSession() {
    currentThreadId = null
  }

  /**
   * Switch to an existing thread by setting [currentThreadId].
   *
   * @param targetThreadId The ID of the thread to switch to.
   */
  fun switchThread(targetThreadId: String) {
    currentThreadId = targetThreadId
  }

  /**
   * Check whether the given thread is the currently active one.
   *
   * @param targetThreadId The thread ID to check.
   * @return `true` if [targetThreadId] matches [currentThreadId].
   */
  fun isCurrentThread(targetThreadId: String): Boolean =
    currentThreadId == targetThreadId

  /**
   * Delete a thread from the repository.
   *
   * If the deleted thread was the active session, [currentThreadId] is reset to `null`.
   *
   * @param targetThreadId The ID of the thread to delete.
   * @return `true` if the deleted thread was the currently active one.
   */
  suspend fun deleteThread(targetThreadId: String): Boolean {
    chatRepository.deleteThread(targetThreadId)
    val wasActive = currentThreadId == targetThreadId
    if (wasActive) currentThreadId = null
    return wasActive
  }

  /**
   * Load all messages for a given thread.
   *
   * @param threadId The thread ID to load messages from.
   * @return A list of [ChatMessage] instances ordered by timestamp.
   */
  suspend fun getMessages(threadId: String): List<ChatMessage> =
    chatRepository.getMessages(threadId)

  /**
   * Persist a message to the repository.
   *
   * If no thread exists yet ([currentThreadId] is `null`), a new thread is created
   * automatically. If this is the first message and it is a user message, the thread
   * title is auto-generated from the first 50 characters of the message content.
   *
   * @param msg The [ChatMessage] to persist.
   */
  fun persistMessage(msg: ChatMessage) {
    scope.launch {
      val isNewThread = currentThreadId == null
      val tid = currentThreadId
        ?: chatRepository.createThread(profileId, "").also { currentThreadId = it }
      chatRepository.insertMessage(tid, msg)
      if (isNewThread && msg.role == ChatRole.User) {
        val title = msg.content.take(50).let {
          if (msg.content.length > 50) "$it…" else it
        }
        chatRepository.updateThreadTitle(tid, title)
      }
    }
  }

  /**
   * Update an existing message's content and status in the repository.
   *
   * Wraps the [content] string into a single [MessagePart.Text] for storage.
   *
   * @param id      The message ID to update.
   * @param content The new content string.
   * @param status  The new [MessageStatus] (e.g. [MessageStatus.Final], [MessageStatus.Canceled]).
   */
  fun persistUpdate(id: String, content: String, status: MessageStatus) {
    scope.launch {
      val parts = if (content.isNotEmpty()) listOf(MessagePart.Text(content)) else emptyList()
      chatRepository.updateMessage(id, parts, status)
    }
  }
}
