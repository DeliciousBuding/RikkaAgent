package io.rikka.agent.storage

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.storage.db.ChatMessageDao
import io.rikka.agent.storage.db.ChatMessageEntity
import io.rikka.agent.storage.db.ChatThreadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChatRepository {
  fun observeThreads(profileId: String): Flow<List<ChatThread>>
  suspend fun createThread(profileId: String, title: String): String
  suspend fun deleteThread(threadId: String)
  suspend fun insertMessage(threadId: String, message: ChatMessage)
  suspend fun updateMessage(id: String, content: String, status: MessageStatus)
  fun observeMessages(threadId: String): Flow<List<ChatMessage>>
  suspend fun getMessages(threadId: String): List<ChatMessage>
}

class RoomChatRepository(private val dao: ChatMessageDao) : ChatRepository {

  override fun observeThreads(profileId: String): Flow<List<ChatThread>> =
    dao.observeThreadsForProfile(profileId).map { threads ->
      threads.map { ChatThread(id = it.id, title = it.title, messages = emptyList()) }
    }

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

  override suspend fun insertMessage(threadId: String, message: ChatMessage) {
    dao.insertMessage(
      ChatMessageEntity(
        id = message.id,
        threadId = threadId,
        role = message.role.name,
        content = message.content,
        timestampMs = message.timestampMs,
        status = message.status.name,
      )
    )
    dao.updateThread(threadId, title = "", updatedAtMs = message.timestampMs)
  }

  override suspend fun updateMessage(id: String, content: String, status: MessageStatus) {
    dao.updateMessageContent(id, content, status.name)
  }

  override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
    dao.observeMessages(threadId).map { list ->
      list.map { it.toModel() }
    }

  override suspend fun getMessages(threadId: String): List<ChatMessage> =
    dao.getMessages(threadId).map { it.toModel() }

  private fun ChatMessageEntity.toModel() = ChatMessage(
    id = id,
    role = ChatRole.valueOf(role),
    content = content,
    timestampMs = timestampMs,
    status = MessageStatus.valueOf(status),
  )
}
