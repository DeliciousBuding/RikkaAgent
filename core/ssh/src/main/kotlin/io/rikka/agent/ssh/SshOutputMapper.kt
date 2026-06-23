package io.rikka.agent.ssh

import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.toTextPart

/**
 * Maps SSH exec output streams to [MessagePart] lists.
 *
 * Converts raw stdout/stderr bytes into structured parts:
 * - Stdout → [MessagePart.Stdout] (stripped of ANSI codes)
 * - Stderr → [MessagePart.Stderr]
 * - Exit code → [MessagePart.Command] update
 * - Errors → [MessagePart.Error]
 *
 * During streaming, accumulates output and flushes at paragraph boundaries.
 */
class SshOutputMapper {

    private val stdoutBuffer = StringBuilder()
    private val stderrBuffer = StringBuilder()
    private var currentCommand: String? = null
    private var startedAt: Long = 0L

    /** Start tracking a new command execution. */
    fun beginCommand(command: String) {
        currentCommand = command
        startedAt = System.currentTimeMillis()
        stdoutBuffer.clear()
        stderrBuffer.clear()
    }

    /**
     * Process a stdout chunk and return any immediately flushable parts.
     * Accumulates partial lines until a newline boundary.
     */
    fun onStdout(bytes: ByteArray): List<MessagePart> {
        val text = stripAnsi(String(bytes, Charsets.UTF_8))
        if (text.isEmpty()) return emptyList()

        stdoutBuffer.append(text)
        return flushStdoutAtBoundary()
    }

    /**
     * Process a stderr chunk and return immediately flushable parts.
     * Stderr is always flushed immediately (errors are urgent).
     */
    fun onStderr(bytes: ByteArray): List<MessagePart> {
        val text = stripAnsi(String(bytes, Charsets.UTF_8))
        if (text.isEmpty()) return emptyList()

        stderrBuffer.append(text)
        val part = MessagePart.Stderr(text = text)
        return listOf(part)
    }

    /**
     * Process command exit and return final parts.
     * Flushes any remaining stdout buffer.
     */
    fun onExit(exitCode: Int): List<MessagePart> {
        val parts = mutableListOf<MessagePart>()

        // Flush remaining stdout
        if (stdoutBuffer.isNotEmpty()) {
            parts.add(MessagePart.Stdout(text = stdoutBuffer.toString()))
            stdoutBuffer.clear()
        }

        // Final command part with exit code
        val cmd = currentCommand
        if (cmd != null) {
            parts.add(
                MessagePart.Command(
                    command = cmd,
                    exitCode = exitCode,
                    startedAtEpochMs = startedAt,
                )
            )
        }

        return parts
    }

    /**
     * Process an error event and return error parts.
     */
    fun onError(message: String, category: String?): List<MessagePart> {
        val parts = mutableListOf<MessagePart>()

        // Flush remaining output
        if (stdoutBuffer.isNotEmpty()) {
            parts.add(MessagePart.Stdout(text = stdoutBuffer.toString()))
            stdoutBuffer.clear()
        }
        if (stderrBuffer.isNotEmpty()) {
            parts.add(MessagePart.Stderr(text = stderrBuffer.toString()))
            stderrBuffer.clear()
        }

        parts.add(
            MessagePart.Error(
                message = message,
                cause = category,
            )
        )

        return parts
    }

    /**
     * Flush stdout at paragraph boundaries (double newline or >4KB).
     * Returns empty list if no complete paragraph available.
     */
    private fun flushStdoutAtBoundary(): List<MessagePart> {
        val text = stdoutBuffer.toString()

        // Flush at double newline (paragraph boundary)
        val doubleNewline = text.lastIndexOf("\n\n")
        if (doubleNewline >= 0) {
            val flush = text.substring(0, doubleNewline + 2)
            stdoutBuffer.clear()
            stdoutBuffer.append(text.substring(doubleNewline + 2))
            return listOf(MessagePart.Stdout(text = flush))
        }

        // Flush if buffer exceeds 4KB
        if (text.length > 4096) {
            stdoutBuffer.clear()
            return listOf(MessagePart.Stdout(text = text))
        }

        return emptyList()
    }

    /** Strip ANSI escape codes from text. */
    private fun stripAnsi(text: String): String {
        return ANSI_PATTERN.replace(text, "")
    }

    companion object {
        private val ANSI_PATTERN = Regex("""\x1B\[[0-9;]*[a-zA-Z]""")
    }
}

/**
 * Top-level mapper for Codex JSONL events → MessagePart.
 *
 * Maps Codex structured events to appropriate MessagePart subtypes:
 * - "json" kind → [MessagePart.Reasoning] (progress/thinking)
 * - "markdown_delta" kind → [MessagePart.Text] (accumulated)
 * - "status" kind → [MessagePart.Text] (status update)
 * - "code" kind → [MessagePart.Code]
 */
object CodexEventMapper {

    fun mapEvent(kind: String, rawJson: String): MessagePart? {
        return when (kind) {
            "json" -> {
                // Codex progress JSON — render as reasoning
                MessagePart.Reasoning(
                    text = rawJson.take(500),  // Truncate long JSON
                    stepId = null,
                )
            }
            "markdown_delta" -> {
                MessagePart.Text(text = rawJson)
            }
            "status" -> {
                MessagePart.Text(text = rawJson)
            }
            "code" -> {
                MessagePart.Code(code = rawJson)
            }
            else -> null
        }
    }
}
