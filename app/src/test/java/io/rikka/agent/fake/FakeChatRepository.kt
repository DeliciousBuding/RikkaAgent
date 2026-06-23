package io.rikka.agent.fake

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.storage.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * In-memory [ChatRepository] for tests.
 *
 * Stores threads and messages in mutable maps. Emits real-time updates via
 * [MutableStateFlow] so collectors see mutations immediately.
 *
 * ## Configurable behavior
 *
 * - [createThreadIdProvider] -- override UUID generation for deterministic tests.
 * - [throwOnInsert] -- simulate storage failures on message insertion.
 */
class FakeChatRepository : ChatRepository {

  // ── Storage ────────────────────────────────────────────────────────────────

  /** profileId -> list of thread metadata (id + title, no messages). */
  private val threads = MutableStateFlow<Map<String, List<ChatThread>>>(emptyMap())

  /** threadId -> ordered message list. */
  private val messages = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

  // ── Configurable hooks ─────────────────────────────────────────────────────

  /** Provides thread IDs. Override for deterministic tests. */
  var createThreadIdProvider: () -> String = { UUID.randomUUID().toString() }

  /** When non-null, [insertMessage] throws this exception. */
  var throwOnInsert: Throwable? = null

  // ── ChatRepository ─────────────────────────────────────────────────────────

  override fun observeThreads(profileId: String): Flow<List<ChatThread>> =
    threads.map { it[profileId].orEmpty() }

  override suspend fun createThread(profileId: String, title: String): String {
    val id = createThreadIdProvider()
    val thread = ChatThread(id = id, title = title, messages = emptyList())
    threads.value = threads.value.toMutableMap().apply {
      val existing = get(profileId).orEmpty()
      put(profileId, existing + thread)
    }
    messages[id] = MutableStateFlow(emptyList())
    return id
  }

  override suspend fun deleteThread(threadId: String) {
    // Remove from all profiles
    threads.value = threads.value.mapValues { (_, list) ->
      list.filter { it.id != threadId }
    }
    messages.remove(threadId)
  }

  override suspend fun insertMessage(threadId: String, message: ChatMessage) {
    throwOnInsert?.let { throw it }
    val flow = messages.getOrPut(threadId) { MutableStateFlow(emptyList()) }
    flow.value = flow.value + message
  }

  override suspend fun updateMessage(
    id: String,
    parts: List<MessagePart>,
    status: MessageStatus,
  ) {
    for ((_, flow) in messages) {
      val list = flow.value
      val idx = list.indexOfFirst { it.id == id }
      if (idx >= 0) {
        flow.value = list.toMutableList().apply {
          set(idx, list[idx].copy(parts = parts, status = status))
        }
        return
      }
    }
  }

  override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
    messages.getOrPut(threadId) { MutableStateFlow(emptyList()) }

  override suspend fun getMessages(threadId: String): List<ChatMessage> =
    messages[threadId]?.value.orEmpty()

  override suspend fun updateThreadTitle(threadId: String, title: String) {
    threads.value = threads.value.mapValues { (_, list) ->
      list.map { if (it.id == threadId) it.copy(title = title) else it }
    }
  }

  // ── Test helpers ───────────────────────────────────────────────────────────

  /** Seed a thread directly (bypasses [createThread] hooks). */
  fun seedThread(profileId: String, thread: ChatThread) {
    threads.value = threads.value.toMutableMap().apply {
      val existing = get(profileId).orEmpty()
      put(profileId, existing + thread)
    }
    messages[thread.id] = MutableStateFlow(thread.messages)
  }

  /** Return a snapshot of all threads for a profile. */
  fun threadSnapshot(profileId: String): List<ChatThread> =
    threads.value[profileId].orEmpty()

  /** Return a snapshot of all messages in a thread. */
  fun messageSnapshot(threadId: String): List<ChatMessage> =
    messages[threadId]?.value.orEmpty()

  /** Clear all data. */
  fun reset() {
    threads.value = emptyMap()
    messages.clear()
  }
}
