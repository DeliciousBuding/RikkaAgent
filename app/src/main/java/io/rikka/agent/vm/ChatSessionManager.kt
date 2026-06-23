package io.rikka.agent.vm

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SessionStats
import io.rikka.agent.storage.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages chat thread lifecycle: CRUD, title auto-generation, message persistence,
 * and session management features (search, pin, archive, tags, statistics).
 *
 * ## Responsibilities
 * - Maintains the current active thread ID and provides thread switching.
 * - Observes active/archived threads for a given profile via [ChatRepository].
 * - Persists messages to the repository, auto-creating a thread on first message.
 * - Auto-generates a thread title from the first user command (truncated to 50 chars).
 * - Provides message loading for thread switching.
 * - Supports full-text search across session messages.
 * - Supports pin/archive operations for session organization.
 * - Supports user-defined tags for session categorization.
 * - Computes and exposes session statistics (command count, execution time, output lines).
 *
 * ## Thread Safety
 * - [currentThreadId] is a simple `var` mutated only from the Main dispatcher
 *   (via `viewModelScope` in [ChatViewModel]), so no concurrent access occurs.
 * - All flows are created via [StateFlow.stateIn] with [SharingStarted.WhileSubscribed],
 *   which is safe for multi-collector scenarios.
 * - [persistMessage] and [persistUpdate] launch coroutines in [scope]; the underlying
 *   [ChatRepository] is expected to be thread-safe (Room DAO operations).
 *
 * ## Exposed State
 * | StateFlow        | Type                           | Description                                    |
 * |------------------|--------------------------------|------------------------------------------------|
 * | [activeThreads]  | `StateFlow<List<ChatThread>>`  | Non-archived threads, pinned first.            |
 * | [archivedThreads]| `StateFlow<List<ChatThread>>`  | Archived threads.                              |
 * | [searchResults]  | `StateFlow<List<ChatThread>>`  | FTS search results (empty when no query).      |
 * | [searchQuery]    | `StateFlow<String>`            | Current search query text.                     |
 *
 * @param profileId      The SSH profile ID this session manager is bound to.
 * @param chatRepository Repository for thread and message persistence.
 * @param scope          Coroutine scope for launching persistence jobs (typically [viewModelScope]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionManager(
  private val profileId: String,
  private val chatRepository: ChatRepository,
  private val scope: CoroutineScope,
) {

  // ── Search state ────────────────────────────────────────────────────────

  /** Current search query. Empty string means no search active. */
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery

  /**
   * Search results for the current [_searchQuery].
   *
   * When the query is empty, emits an empty list immediately.
   * When non-empty, delegates to [ChatRepository.searchThreads] which uses FTS4.
   */
  val searchResults: StateFlow<List<ChatThread>> = _searchQuery.flatMapLatest { query ->
    if (query.isBlank()) flowOf(emptyList())
    else chatRepository.searchThreads(profileId, query)
  }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  // ── Thread lists ────────────────────────────────────────────────────────

  /**
   * Observable list of active (non-archived) threads for this profile.
   *
   * Pinned threads appear first, then sorted by most recent activity.
   */
  val activeThreads: StateFlow<List<ChatThread>> =
    chatRepository.observeActiveThreads(profileId)
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Observable list of archived threads for this profile.
   */
  val archivedThreads: StateFlow<List<ChatThread>> =
    chatRepository.observeArchivedThreads(profileId)
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  // ── Current thread tracking ─────────────────────────────────────────────

  /**
   * Current active thread ID, or `null` for unsaved new sessions.
   *
   * Set to `null` by [newSession], set to an existing ID by [switchThread],
   * and set to a newly created ID by [persistMessage] on first message.
   */
  var currentThreadId: String? = null
    private set

  // ── Session lifecycle ───────────────────────────────────────────────────

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

  // ── Search ──────────────────────────────────────────────────────────────

  /**
   * Update the search query for full-text session search.
   *
   * @param query The search text. Empty string clears the search.
   */
  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  // ── Pin / Archive ───────────────────────────────────────────────────────

  /**
   * Toggle the pinned state of a thread.
   *
   * @param threadId The thread to toggle.
   */
  fun togglePin(threadId: String) {
    scope.launch {
      val thread = activeThreads.value.find { it.id == threadId }
        ?: archivedThreads.value.find { it.id == threadId }
        ?: return@launch
      if (thread.isPinned) chatRepository.unpinThread(threadId)
      else chatRepository.pinThread(threadId)
    }
  }

  /**
   * Archive a thread, moving it from the active list to the archived list.
   *
   * @param threadId The thread to archive.
   */
  fun archiveThread(threadId: String) {
    scope.launch { chatRepository.archiveThread(threadId) }
  }

  /**
   * Unarchive a thread, restoring it to the active list.
   *
   * @param threadId The thread to unarchive.
   */
  fun unarchiveThread(threadId: String) {
    scope.launch { chatRepository.unarchiveThread(threadId) }
  }

  // ── Tags ────────────────────────────────────────────────────────────────

  /**
   * Add a tag to a thread.
   *
   * @param threadId The thread to tag.
   * @param tag The tag name (will be trimmed).
   */
  fun addTag(threadId: String, tag: String) {
    if (tag.isBlank()) return
    scope.launch { chatRepository.addTag(threadId, tag) }
  }

  /**
   * Remove a tag from a thread.
   *
   * @param threadId The thread to remove the tag from.
   * @param tag The tag name to remove.
   */
  fun removeTag(threadId: String, tag: String) {
    scope.launch { chatRepository.removeTag(threadId, tag) }
  }

  // ── Stats ───────────────────────────────────────────────────────────────

  /**
   * Compute session statistics for a given thread.
   *
   * This reads all messages and computes: command count, total execution time,
   * and output line count. Should be called on a background dispatcher.
   *
   * @param threadId The thread to compute stats for.
   * @return A [SessionStats] instance.
   */
  suspend fun getThreadStats(threadId: String): SessionStats =
    chatRepository.getThreadStats(threadId)

  // ── Message persistence ─────────────────────────────────────────────────

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

  /**
   * Delete all messages that appear after the given message in the current thread.
   *
   * Used during message editing: when a user edits a command, the old assistant
   * response (and any subsequent messages) are removed so the new response can
   * take its place.
   *
   * @param messageId The ID of the message after which all messages should be deleted.
   */
  fun deleteMessagesAfter(messageId: String) {
    val tid = currentThreadId ?: return
    scope.launch {
      chatRepository.deleteMessagesAfter(tid, messageId)
    }
  }
}
