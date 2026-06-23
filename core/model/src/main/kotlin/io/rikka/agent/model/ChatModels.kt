package io.rikka.agent.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
  val id: String,
  val role: ChatRole,
  val content: String = "",
  val parts: List<MessagePart> = emptyList(),
  val timestampMs: Long,
  val status: MessageStatus = MessageStatus.Final
) {
  /** Best-effort text extracted from parts. Falls back to legacy [content]. */
  val textContent: String
    get() {
      if (parts.isNotEmpty()) {
        return parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
            .ifEmpty { content }
      }
      return content
    }
}

@Serializable
enum class ChatRole {
  User,
  Assistant
}

@Serializable
enum class MessageStatus {
  Streaming,
  Final,
  Error,
  Canceled,
}

@Serializable
data class ChatThread(
  val id: String,
  val title: String,
  val messages: List<ChatMessage>
)

