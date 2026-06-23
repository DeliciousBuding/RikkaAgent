package io.rikka.agent.vm

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SessionStats

/**
 * Export format for chat sessions.
 */
enum class ExportFormat {
  /** Plain text with `$ command` / output style. */
  PLAIN,

  /** Structured Markdown with headers and fenced code blocks. */
  MARKDOWN,

  /** Machine-readable JSON with full message data. */
  JSON,

  /** Standalone HTML page with embedded styling. */
  HTML,
}

/**
 * Exports chat sessions in multiple formats.
 *
 * Each format preserves the full message history and profile metadata.
 * The [export] method is the single entry point; it dispatches to
 * format-specific private methods.
 */
object SessionExporter {

  /**
   * Export a session in the given [format].
   *
   * @param profileLabel Human-readable profile label (e.g. "root@host").
   * @param messages     Ordered list of messages in the session.
   * @param stats        Optional session statistics to include.
   * @param format       Target export format.
   * @return The exported content as a string.
   */
  fun export(
    profileLabel: String,
    messages: List<ChatMessage>,
    stats: SessionStats? = null,
    format: ExportFormat = ExportFormat.PLAIN,
  ): String = when (format) {
    ExportFormat.PLAIN -> exportPlain(profileLabel, messages)
    ExportFormat.MARKDOWN -> exportMarkdown(profileLabel, messages, stats)
    ExportFormat.JSON -> exportJson(profileLabel, messages, stats)
    ExportFormat.HTML -> exportHtml(profileLabel, messages, stats)
  }

  // ── Plain text ──────────────────────────────────────────────────────────

  private fun exportPlain(profileLabel: String, messages: List<ChatMessage>): String =
    buildString {
      appendLine("# Session: $profileLabel")
      appendLine()
      for (msg in messages) {
        when (msg.role) {
          ChatRole.User -> appendLine("$ ${msg.content}")
          ChatRole.Assistant -> appendLine(msg.content)
        }
        appendLine()
      }
    }

  // ── Markdown ────────────────────────────────────────────────────────────

  private fun exportMarkdown(
    profileLabel: String,
    messages: List<ChatMessage>,
    stats: SessionStats?,
  ): String = buildString {
    appendLine("# Session: $profileLabel")
    appendLine()

    if (stats != null && stats.commandCount > 0) {
      appendLine("## Statistics")
      appendLine()
      appendLine("| Metric | Value |")
      appendLine("|--------|-------|")
      appendLine("| Commands | ${stats.commandCount} |")
      appendLine("| Execution time | ${stats.formattedDuration} |")
      appendLine("| Output lines | ${stats.outputLineCount} |")
      appendLine()
      appendLine("---")
      appendLine()
    }

    appendLine("## Transcript")
    appendLine()

    for (msg in messages) {
      when (msg.role) {
        ChatRole.User -> {
          appendLine("### Command")
          appendLine()
          appendLine("```bash")
          appendLine(msg.content)
          appendLine("```")
        }
        ChatRole.Assistant -> {
          if (msg.status == MessageStatus.Canceled) {
            appendLine("*Canceled*")
          }
          if (msg.content.isNotEmpty()) {
            appendLine()
            appendLine("```")
            appendLine(msg.content)
            appendLine("```")
          }
        }
      }
      appendLine()
    }

    appendLine("---")
    appendLine()
    appendLine("*Exported by RikkaAgent*")
  }

  // ── JSON ────────────────────────────────────────────────────────────────

  private fun exportJson(
    profileLabel: String,
    messages: List<ChatMessage>,
    stats: SessionStats?,
  ): String = buildString {
    appendLine("{")
    appendLine("  \"profile\": ${jsonEscape(profileLabel)},")
    appendLine("  \"exportedAt\": ${jsonEscape(java.time.Instant.now().toString())},")

    if (stats != null) {
      appendLine("  \"stats\": {")
      appendLine("    \"commandCount\": ${stats.commandCount},")
      appendLine("    \"totalExecutionTimeMs\": ${stats.totalExecutionTimeMs},")
      appendLine("    \"outputLineCount\": ${stats.outputLineCount}")
      appendLine("  },")
    }

    appendLine("  \"messages\": [")
    for ((i, msg) in messages.withIndex()) {
      appendLine("    {")
      appendLine("      \"role\": ${jsonEscape(msg.role.name.lowercase())},")
      appendLine("      \"content\": ${jsonEscape(msg.content)},")
      appendLine("      \"timestamp\": ${jsonEscape(java.time.Instant.ofEpochMilli(msg.timestampMs).toString())},")
      appendLine("      \"status\": ${jsonEscape(msg.status.name.lowercase())}")
      append("    }")
      if (i < messages.size - 1) append(",")
      appendLine()
    }
    appendLine("  ]")
    appendLine("}")
  }

  // ── HTML ────────────────────────────────────────────────────────────────

  private fun exportHtml(
    profileLabel: String,
    messages: List<ChatMessage>,
    stats: SessionStats?,
  ): String = buildString {
    appendLine("<!DOCTYPE html>")
    appendLine("<html lang=\"en\">")
    appendLine("<head>")
    appendLine("  <meta charset=\"UTF-8\">")
    appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
    appendLine("  <title>Session: ${htmlEscape(profileLabel)}</title>")
    appendLine("  <style>")
    appendLine("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 800px; margin: 0 auto; padding: 24px; background: #fafafa; color: #1a1a1a; }")
    appendLine("    h1 { font-size: 1.5rem; border-bottom: 2px solid #e0e0e0; padding-bottom: 8px; }")
    appendLine("    .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0; }")
    appendLine("    .stat { background: #fff; border: 1px solid #e0e0e0; border-radius: 8px; padding: 12px; text-align: center; }")
    appendLine("    .stat-value { font-size: 1.5rem; font-weight: 600; color: #1976d2; }")
    appendLine("    .stat-label { font-size: 0.75rem; color: #666; margin-top: 4px; }")
    appendLine("    .message { margin: 12px 0; border-radius: 8px; padding: 12px; }")
    appendLine("    .message-user { background: #e3f2fd; border-left: 3px solid #1976d2; }")
    appendLine("    .message-assistant { background: #fff; border-left: 3px solid #4caf50; border: 1px solid #e0e0e0; }")
    appendLine("    .message-canceled { opacity: 0.6; border-left-color: #ff9800; }")
    appendLine("    .role { font-size: 0.7rem; font-weight: 600; text-transform: uppercase; color: #666; margin-bottom: 4px; }")
    appendLine("    pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: 'SF Mono', 'Fira Code', monospace; font-size: 0.85rem; line-height: 1.5; }")
    appendLine("    .footer { margin-top: 24px; padding-top: 12px; border-top: 1px solid #e0e0e0; font-size: 0.75rem; color: #999; text-align: center; }")
    appendLine("  </style>")
    appendLine("</head>")
    appendLine("<body>")
    appendLine("  <h1>Session: ${htmlEscape(profileLabel)}</h1>")

    if (stats != null && stats.commandCount > 0) {
      appendLine("  <div class=\"stats\">")
      appendLine("    <div class=\"stat\"><div class=\"stat-value\">${stats.commandCount}</div><div class=\"stat-label\">Commands</div></div>")
      appendLine("    <div class=\"stat\"><div class=\"stat-value\">${stats.formattedDuration}</div><div class=\"stat-label\">Execution Time</div></div>")
      appendLine("    <div class=\"stat\"><div class=\"stat-value\">${stats.outputLineCount}</div><div class=\"stat-label\">Output Lines</div></div>")
      appendLine("  </div>")
    }

    for (msg in messages) {
      val roleClass = when {
        msg.role == ChatRole.Assistant && msg.status == MessageStatus.Canceled ->
          "message message-assistant message-canceled"
        msg.role == ChatRole.Assistant -> "message message-assistant"
        else -> "message message-user"
      }
      val roleLabel = if (msg.role == ChatRole.User) "Command" else "Output"

      appendLine("  <div class=\"$roleClass\">")
      appendLine("    <div class=\"role\">$roleLabel</div>")
      if (msg.content.isNotEmpty()) {
        appendLine("    <pre>${htmlEscape(msg.content)}</pre>")
      }
      if (msg.role == ChatRole.Assistant && msg.status == MessageStatus.Canceled) {
        appendLine("    <div class=\"role\" style=\"color: #ff9800; margin-top: 4px;\">[canceled]</div>")
      }
      appendLine("  </div>")
    }

    appendLine("  <div class=\"footer\">Exported by RikkaAgent</div>")
    appendLine("</body>")
    appendLine("</html>")
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private fun jsonEscape(s: String): String {
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    return "\"$escaped\""
  }

  private fun htmlEscape(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
}
