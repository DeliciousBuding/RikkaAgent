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
 */
class ChatSessionManager(
  private val profileId: String,
  private val chatRepository: ChatRepository,
  private val scope: CoroutineScope,
) {

  /** Observable list of past threads for this profile. */
  val threads: StateFlow<List<ChatThread>> = chatRepository.observeThreads(profileId)
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /** Current active thread ID, null for unsaved new sessions. */
  var currentThreadId: String? = null
    private set

  /** Start a fresh session (new thread). */
  fun newSession() {
    currentThreadId = null
  }

  /** Switch to an existing thread. */
  fun switchThread(targetThreadId: String) {
    currentThreadId = targetThreadId
  }

  /** Returns true if the given thread is the currently active one. */
  fun isCurrentThread(targetThreadId: String): Boolean =
    currentThreadId == targetThreadId

  /** Delete a thread. Returns true if it was the active thread. */
  suspend fun deleteThread(targetThreadId: String): Boolean {
    chatRepository.deleteThread(targetThreadId)
    val wasActive = currentThreadId == targetThreadId
    if (wasActive) currentThreadId = null
    return wasActive
  }

  /** Load messages for a thread. */
  suspend fun getMessages(threadId: String): List<ChatMessage> =
    chatRepository.getMessages(threadId)

  /**
   * Persist a message. Auto-creates a thread if none exists.
   * Auto-titles the thread from the first user command.
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

  /** Update an existing message's content and status. */
  fun persistUpdate(id: String, content: String, status: MessageStatus) {
    scope.launch {
      val parts = if (content.isNotEmpty()) listOf(MessagePart.Text(content)) else emptyList()
      chatRepository.updateMessage(id, parts, status)
    }
  }
}
