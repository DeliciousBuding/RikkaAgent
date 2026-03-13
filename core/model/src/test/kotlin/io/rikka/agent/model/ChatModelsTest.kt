package io.rikka.agent.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatModelsTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `ChatMessage serialization round-trip`() {
    val msg = ChatMessage(
      id = "msg-1",
      role = ChatRole.User,
      content = "ls -la",
      timestampMs = 1710000000000L,
      status = MessageStatus.Final
    )
    val encoded = json.encodeToString(ChatMessage.serializer(), msg)
    val decoded = json.decodeFromString(ChatMessage.serializer(), encoded)
    assertEquals(msg, decoded)
  }

  @Test
  fun `ChatMessage default status is Final`() {
    val msg = ChatMessage(
      id = "msg-2",
      role = ChatRole.Assistant,
      content = "output",
      timestampMs = 0L
    )
    assertEquals(MessageStatus.Final, msg.status)
  }

  @Test
  fun `ChatRole enum values`() {
    assertEquals(2, ChatRole.entries.size)
    assertEquals(ChatRole.User, ChatRole.valueOf("User"))
    assertEquals(ChatRole.Assistant, ChatRole.valueOf("Assistant"))
  }

  @Test
  fun `MessageStatus enum values`() {
    assertEquals(4, MessageStatus.entries.size)
    assertEquals(MessageStatus.Streaming, MessageStatus.valueOf("Streaming"))
    assertEquals(MessageStatus.Final, MessageStatus.valueOf("Final"))
    assertEquals(MessageStatus.Error, MessageStatus.valueOf("Error"))
    assertEquals(MessageStatus.Canceled, MessageStatus.valueOf("Canceled"))
  }

  @Test
  fun `ChatThread serialization round-trip`() {
    val thread = ChatThread(
      id = "thread-1",
      title = "Test Session",
      messages = listOf(
        ChatMessage("m1", ChatRole.User, "uptime", 1L),
        ChatMessage("m2", ChatRole.Assistant, "12:00 up 5 days", 2L)
      )
    )
    val encoded = json.encodeToString(ChatThread.serializer(), thread)
    val decoded = json.decodeFromString(ChatThread.serializer(), encoded)
    assertEquals(thread, decoded)
    assertEquals(2, decoded.messages.size)
  }
}
