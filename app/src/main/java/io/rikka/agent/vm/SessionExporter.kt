package io.rikka.agent.vm

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole

object SessionExporter {
  fun export(profileLabel: String, messages: List<ChatMessage>): String = buildString {
    appendLine("# Session: $profileLabel")
    appendLine()
    for (msg in messages) {
      if (msg.role == ChatRole.User) {
        appendLine("$ ${msg.content}")
      } else {
        appendLine(msg.content)
      }
      appendLine()
    }
  }
}
