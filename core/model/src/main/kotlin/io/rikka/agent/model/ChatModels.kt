package io.rikka.agent.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
  val id: String,
  val role: ChatRole,
  val content: String,
  val timestampMs: Long,
  val status: MessageStatus = MessageStatus.Final
)

@Serializable
enum class ChatRole {
  User,
  Assistant
}

@Serializable
enum class MessageStatus {
  Streaming,
  Final,
  Error
}

@Serializable
data class ChatThread(
  val id: String,
  val title: String,
  val messages: List<ChatMessage>
)

