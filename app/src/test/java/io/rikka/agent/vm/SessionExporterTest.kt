package io.rikka.agent.vm

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExporterTest {

  @Test
  fun `exports user and assistant messages in plain text format`() {
    val messages = listOf(
      ChatMessage(
        id = "u-1",
        role = ChatRole.User,
        content = "ls -la",
        timestampMs = 1L,
        status = MessageStatus.Final,
      ),
      ChatMessage(
        id = "a-1",
        role = ChatRole.Assistant,
        content = "total 8",
        timestampMs = 2L,
        status = MessageStatus.Final,
      ),
    )

    val exported = SessionExporter.export("user@host", messages)

    val expected = """
      # Session: user@host

      $ ls -la

      total 8

    """.trimIndent() + "\n"
    assertEquals(expected, exported)
  }

  @Test
  fun `exports empty session with header`() {
    val exported = SessionExporter.export("demo", emptyList())
    val expected = "# Session: demo\n\n"
    assertEquals(expected, exported)
  }
}
