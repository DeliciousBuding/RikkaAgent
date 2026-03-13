package io.rikka.agent.ssh

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tolerant JSONL parser for Codex `--json` output.
 *
 * Per spec (33-remote-exec.md), the parser:
 * 1. Splits stdout by newline
 * 2. For each line: tries to parse as JSON → StructuredEvent, or keeps as raw StdoutChunk
 * 3. Best-effort extraction of known fields (text/content/delta → markdown_delta, status → status)
 */
object JsonlParser {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /**
   * Attempt to parse a single line as a JSONL event.
   * Returns a list of ExecEvents: either one or more StructuredEvents (if JSON),
   * or a single StdoutChunk (if not JSON).
   */
  fun parseLine(line: String): List<ExecEvent> {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return emptyList()

    val obj = tryParseJson(trimmed) ?: return listOf(
      ExecEvent.StdoutChunk((line + "\n").toByteArray(Charsets.UTF_8))
    )

    val events = mutableListOf<ExecEvent>()

    // Always emit the raw json event
    events.add(ExecEvent.StructuredEvent(kind = "json", rawJson = trimmed))

    // Best-effort extraction of text content → markdown_delta
    val textContent = extractTextField(obj)
    if (textContent != null) {
      events.add(ExecEvent.StructuredEvent(kind = "markdown_delta", rawJson = textContent))
    }

    // Best-effort extraction of status/progress → status
    val statusContent = extractStatusField(obj)
    if (statusContent != null) {
      events.add(ExecEvent.StructuredEvent(kind = "status", rawJson = statusContent))
    }

    return events
  }

  private fun tryParseJson(line: String): JsonObject? {
    if (!line.startsWith("{")) return null
    return try {
      json.parseToJsonElement(line).jsonObject
    } catch (_: Exception) {
      null
    }
  }

  /** Extract text from common content fields: text, content, delta, message. */
  private fun extractTextField(obj: JsonObject): String? {
    for (key in listOf("text", "content", "delta", "message")) {
      val value = obj[key]
      if (value != null) {
        return try {
          value.jsonPrimitive.content
        } catch (_: Exception) {
          // Might be an object, try nested .text or .content
          try {
            val nested = value.jsonObject
            nested["text"]?.jsonPrimitive?.content
              ?: nested["content"]?.jsonPrimitive?.content
          } catch (_: Exception) {
            null
          }
        }
      }
    }
    return null
  }

  /** Extract status from common progress fields: status, stage, progress, type. */
  private fun extractStatusField(obj: JsonObject): String? {
    for (key in listOf("status", "stage", "progress")) {
      val value = obj[key]
      if (value != null) {
        return try {
          value.jsonPrimitive.content
        } catch (_: Exception) {
          null
        }
      }
    }
    return null
  }
}

/**
 * Accumulates incoming byte chunks and splits them into complete lines
 * for JSONL parsing. Handles partial lines across chunk boundaries.
 */
class JsonlLineBuffer {
  private val buffer = StringBuilder()

  /**
   * Feed raw bytes from stdout and get back complete parsed events.
   * Incomplete trailing lines are buffered until the next call.
   */
  fun feed(bytes: ByteArray): List<ExecEvent> {
    buffer.append(String(bytes, Charsets.UTF_8))
    val events = mutableListOf<ExecEvent>()

    while (true) {
      val newline = buffer.indexOf('\n')
      if (newline == -1) break
      val line = buffer.substring(0, newline)
      buffer.delete(0, newline + 1)
      events.addAll(JsonlParser.parseLine(line))
    }

    return events
  }

  /** Flush any remaining buffered content as a final raw chunk. */
  fun flush(): List<ExecEvent> {
    if (buffer.isEmpty()) return emptyList()
    val remaining = buffer.toString()
    buffer.clear()
    return JsonlParser.parseLine(remaining)
  }
}
