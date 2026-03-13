package io.rikka.agent.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonlParserTest {

  @Test
  fun `parse json line extracts markdown delta and status`() {
    val line = """{"type":"turn.started","delta":"hello","status":"running"}"""

    val events = JsonlParser.parseLine(line)

    assertTrue(events.any { it is ExecEvent.StructuredEvent && it.kind == "json" })
    assertTrue(events.any { it is ExecEvent.StructuredEvent && it.kind == "markdown_delta" && it.rawJson == "hello" })
    assertTrue(events.any { it is ExecEvent.StructuredEvent && it.kind == "status" && it.rawJson == "running" })
  }

  @Test
  fun `parse non-json line falls back to stdout chunk`() {
    val events = JsonlParser.parseLine("plain output")

    assertEquals(1, events.size)
    val chunk = events.first() as ExecEvent.StdoutChunk
    assertEquals("plain output\n", String(chunk.bytes, Charsets.UTF_8))
  }

  @Test
  fun `line buffer handles split chunks`() {
    val buffer = JsonlLineBuffer()
    val first = "{".toByteArray()
    val second = "\"delta\":\"abc\"}\n".toByteArray()

    val e1 = buffer.feed(first)
    assertTrue(e1.isEmpty())

    val e2 = buffer.feed(second)
    assertTrue(e2.any { it is ExecEvent.StructuredEvent && it.kind == "markdown_delta" && it.rawJson == "abc" })
  }

  @Test
  fun `flush emits trailing buffered line`() {
    val buffer = JsonlLineBuffer()
    buffer.feed("not-json-without-newline".toByteArray())

    val events = buffer.flush()
    assertEquals(1, events.size)
    val chunk = events.first() as ExecEvent.StdoutChunk
    assertEquals("not-json-without-newline\n", String(chunk.bytes, Charsets.UTF_8))
  }

  @Test
  fun `malformed json falls back to stdout`() {
    val events = JsonlParser.parseLine("{not-valid-json")

    assertEquals(1, events.size)
    val chunk = events.first() as ExecEvent.StdoutChunk
    assertEquals("{not-valid-json\n", String(chunk.bytes, Charsets.UTF_8))
  }

  @Test
  fun `extract nested content field`() {
    val line = """{"content":{"text":"nested"},"type":"item.updated"}"""
    val events = JsonlParser.parseLine(line)

    assertTrue(events.any { it is ExecEvent.StructuredEvent && it.kind == "markdown_delta" && it.rawJson == "nested" })
  }
}
