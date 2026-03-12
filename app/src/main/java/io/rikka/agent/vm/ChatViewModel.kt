package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * v1: purely local state + fake streaming to validate rendering/animations first.
 * SSH exec + real streaming gets wired in after the UI contract is stable.
 */
class ChatViewModel : ViewModel() {
  private val _messages = MutableStateFlow(
    listOf(
      ChatMessage(
        id = "m1",
        role = ChatRole.Assistant,
        content = "Connected. What should I run?",
        timestampMs = System.currentTimeMillis(),
        status = MessageStatus.Final,
      ),
    )
  )

  val messages: StateFlow<List<ChatMessage>> = _messages

  private var streamingJob: Job? = null

  fun send(text: String) {
    val now = System.currentTimeMillis()
    val userMsg = ChatMessage(
      id = "u-${UUID.randomUUID()}",
      role = ChatRole.User,
      content = text,
      timestampMs = now,
      status = MessageStatus.Final,
    )

    val assistantId = "a-${UUID.randomUUID()}"
    val assistantSeed = ChatMessage(
      id = assistantId,
      role = ChatRole.Assistant,
      content = "",
      timestampMs = now,
      status = MessageStatus.Streaming,
    )

    _messages.update { it + userMsg + assistantSeed }

    // Fake streaming: proves our Markdown rendering and batching feel good before SSH.
    streamingJob?.cancel()
    streamingJob = viewModelScope.launch {
      val reply = buildString {
        append("Running on server (mock).\n\n")
        append("```\n")
        append("$ uptime\n")
        append(" 12 days,  3:44,  load average: 0.14 0.09 0.05\n")
        append("\n")
        append("$ df -h\n")
        append("Filesystem   Size  Used Avail Use% Mounted\n")
        append("/dev/sda1     80G   34G   42G  45% /\n")
        append("```\n\n")
        append("Next: we’ll wire this bubble to SSH exec streaming frames.\n")
      }

      // 50ms tick-ish, chunking small groups of characters to mimic real streaming.
      var i = 0
      while (i < reply.length) {
        val end = (i + 8).coerceAtMost(reply.length)
        val delta = reply.substring(i, end)
        i = end

        _messages.update { list ->
          list.map { msg ->
            if (msg.id == assistantId) msg.copy(content = msg.content + delta) else msg
          }
        }

        delay(50)
      }

      _messages.update { list ->
        list.map { msg ->
          if (msg.id == assistantId) msg.copy(status = MessageStatus.Final) else msg
        }
      }
    }
  }
}

