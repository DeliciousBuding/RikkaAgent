package io.rikka.agent.storage

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SessionStats
import io.rikka.agent.storage.db.ChatMessageDao
import io.rikka.agent.storage.db.ChatThreadEntity
import io.rikka.agent.storage.db.ThreadTagEntity
import io.rikka.agent.storage.db.computeStats
import io.rikka.agent.storage.db.toEntity
import io.rikka.agent.storage.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

interface ChatRepository {
  // ── Thread observation ──────────────────────────────────────────────────
  fun observeThreads(profileId: String): Flow<List<ChatThread>>
  fun observeActiveThreads(profileId: String): Flow<List<ChatThread>>
  fun observeArchivedThreads(profileId: String): Flow<List<ChatThread>>
  fun searchThreads(profileId: String, query: String): Flow<List<ChatThread>>

  // ── Thread CRUD ─────────────────────────────────────────────────────────
  suspend fun createThread(profileId: String, title: String): String
  suspend fun deleteThread(threadId: String)
  suspend fun updateThreadTitle(threadId: String, title: String)

  // ── Pin / Archive ───────────────────────────────────────────────────────
  suspend fun pinThread(threadId: String)
  suspend fun unpinThread(threadId: String)
  suspend fun archiveThread(threadId: String)
  suspend fun unarchiveThread(threadId: String)

  // ── Tags ────────────────────────────────────────────────────────────────
  suspend fun addTag(threadId: String, tag: String)
  suspend fun removeTag(threadId: String, tag: String)
  suspend fun getTags(threadId: String): List<String>

  // ── Stats ───────────────────────────────────────────────────────────────
  suspend fun getThreadStats(threadId: String): SessionStats

  // ── Messages ────────────────────────────────────────────────────────────
  suspend fun insertMessage(threadId: String, message: ChatMessage)
  suspend fun updateMessage(id: String, parts: List<MessagePart>, status: MessageStatus)
  fun observeMessages(threadId: String): Flow<List<ChatMessage>>
  suspend fun getMessages(threadId: String): List<ChatMessage>
}

class RoomChatRepository(private val dao: ChatMessageDao) : ChatRepository {

  // ── Thread observation ──────────────────────────────────────────────────

  override fun observeThreads(profileId: String): Flow<List<ChatThread>> =
    dao.observeThreadsForProfile(profileId).map { threads ->
      threads.map { it.toModel() }
    }

  override fun observeActiveThreads(profileId: String): Flow<List<ChatThread>> =
    dao.observeActiveThreads(profileId).map { entities ->
      entities.map { entity ->
        val tags = dao.getTagsForThread(entity.id)
        entity.toModel(tags = tags)
      }
    }

  override fun observeArchivedThreads(profileId: String): Flow<List<ChatThread>> =
    dao.observeArchivedThreads(profileId).map { entities ->
      entities.map { entity ->
        val tags = dao.getTagsForThread(entity.id)
        entity.toModel(tags = tags)
      }
    }

  override fun searchThreads(profileId: String, query: String): Flow<List<ChatThread>> {
    // For FTS4 MATCH, wrap multi-word queries with AND logic and sanitize
    val ftsQuery = query.trim().split("\\s+".toRegex()).joinToString(" AND ") { "\"$it\"" }
    return dao.searchThreads(profileId, ftsQuery).map { entities ->
      entities.map { entity ->
        val tags = dao.getTagsForThread(entity.id)
        entity.toModel(tags = tags)
      }
    }
  }

  // ── Thread CRUD ─────────────────────────────────────────────────────────

  override suspend fun createThread(profileId: String, title: String): String {
    val id = java.util.UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    dao.insertThread(
      ChatThreadEntity(
        id = id,
        profileId = profileId,
        title = title,
        createdAtMs = now,
        updatedAtMs = now,
      )
    )
    return id
  }

  override suspend fun deleteThread(threadId: String) {
    dao.deleteThread(threadId)
  }

  override suspend fun updateThreadTitle(threadId: String, title: String) {
    dao.updateThreadTitle(threadId, title, System.currentTimeMillis())
  }

  // ── Pin / Archive ───────────────────────────────────────────────────────

  override suspend fun pinThread(threadId: String) {
    dao.setThreadPinned(threadId, true)
  }

  override suspend fun unpinThread(threadId: String) {
    dao.setThreadPinned(threadId, false)
  }

  override suspend fun archiveThread(threadId: String) {
    dao.setThreadArchived(threadId, true)
  }

  override suspend fun unarchiveThread(threadId: String) {
    dao.setThreadArchived(threadId, false)
  }

  // ── Tags ────────────────────────────────────────────────────────────────

  override suspend fun addTag(threadId: String, tag: String) {
    dao.insertTag(ThreadTagEntity(threadId = threadId, name = tag.trim()))
  }

  override suspend fun removeTag(threadId: String, tag: String) {
    dao.deleteTag(threadId, tag)
  }

  override suspend fun getTags(threadId: String): List<String> =
    dao.getTagsForThread(threadId)

  // ── Stats ───────────────────────────────────────────────────────────────

  override suspend fun getThreadStats(threadId: String): SessionStats {
    val messages = dao.getMessages(threadId)
    return messages.computeStats()
  }

  // ── Messages ────────────────────────────────────────────────────────────

  override suspend fun insertMessage(threadId: String, message: ChatMessage) {
    val entity = message.toEntity(threadId)
    val inserted = dao.insertMessage(entity)
    if (inserted == -1L) {
      // Row already exists (IGNORE conflict) — update instead
      dao.updateMessageParts(entity.id, entity.content, entity.partsJson, entity.status)
    }
    // Only update thread timestamp, do NOT overwrite the title
    dao.updateThreadTimestamp(threadId, message.timestampMs)
  }

  override suspend fun updateMessage(id: String, parts: List<MessagePart>, status: MessageStatus) {
    val textContent = parts.filterIsInstance<MessagePart.Text>()
      .joinToString(separator = "\n") { it.text }
    val partsJson = ChatMessage.json.encodeToString(parts)
    dao.updateMessageParts(id, textContent, partsJson, status.name)
  }

  override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
    dao.observeMessages(threadId).map { list ->
      list.map { it.toModel() }
    }

  override suspend fun getMessages(threadId: String): List<ChatMessage> =
    dao.getMessages(threadId).map { it.toModel() }
}
