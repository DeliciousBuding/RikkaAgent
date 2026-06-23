package io.rikka.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Serializable role enum for chat messages.
 */
@Serializable
enum class ChatRole {
    User,
    Assistant,
}

/**
 * Status of a chat message in its lifecycle.
 */
@Serializable
enum class MessageStatus {
    /** Message is still being streamed / assembled. */
    Streaming,

    /** Message is complete and final. */
    Final,

    /** Message encountered an error during processing. */
    Error,

    /** Message generation was canceled by the user. */
    Canceled,
}

/**
 * A single message in a chat thread.
 *
 * ## Backward compatibility
 *
 * Old messages stored with only `content: String` (no `parts`) will be
 * automatically migrated on deserialization: the [parts] list is synthesized
 * as `listOf(Text(content))` when the serialized JSON contains no `parts` field.
 *
 * ## Migration path
 *
 * 1. This class keeps [content] as a read-only derived property so that
 *    existing code doing `message.content` continues to compile.
 * 2. New code should prefer [parts] and use the helper extensions.
 * 3. When persisting to Room, store `parts` as a JSON column (see TypeConverter).
 *    The `content` column can be kept for search / FTS, derived from [textContent].
 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    /**
     * The structured parts of this message.
     *
     * Defaults to a single [MessagePart.Text] wrapping [content] for backward
     * compatibility with old serialized data that lacks the `parts` field.
     */
    val parts: List<MessagePart> = emptyList(),
    val timestampMs: Long,
    val status: MessageStatus = MessageStatus.Final,
    /**
     * Legacy plain-text content field.
     *
     * - On deserialization: if `parts` is empty but `content` is present,
     *   the [ChatMessage] factory auto-migrates to `parts = listOf(Text(content))`.
     * - On access: [content] returns the concatenated text of all [MessagePart.Text]
     *   parts, or empty string if none. This preserves source compatibility for
     *   callers that did `message.content`.
     *
     * **Do not set this directly in new code.** Use [parts] instead.
     */
    @Deprecated("Use parts. Kept for backward-compatible deserialization only.")
    @SerialName("content")
    private val _content: String = "",
) {
    /**
     * Backward-compatible accessor: returns plain-text representation.
     *
     * For new code, prefer [textContent] or iterate [parts] directly.
     */
    @Suppress("DEPRECATION")
    val content: String
        get() = _content.ifEmpty { textContent }

    /**
     * Extracts concatenated text from all [MessagePart.Text] parts.
     * Returns empty string if there are no text parts.
     */
    val textContent: String
        get() = parts.filterIsInstance<MessagePart.Text>()
            .joinToString(separator = "\n") { it.text }

    /**
     * Returns all [MessagePart.Command] parts in this message.
     */
    val commands: List<MessagePart.Command>
        get() = parts.filterIsInstance<MessagePart.Command>()

    /**
     * Returns the last [MessagePart.Command] in this message, or null.
     */
    val lastCommand: MessagePart.Command?
        get() = commands.lastOrNull()

    /**
     * Returns all stdout parts concatenated.
     */
    val stdoutText: String
        get() = parts.filterIsInstance<MessagePart.Stdout>()
            .joinToString(separator = "") { it.text }

    /**
     * Returns all stderr parts concatenated.
     */
    val stderrText: String
        get() = parts.filterIsInstance<MessagePart.Stderr>()
            .joinToString(separator = "") { it.text }

    companion object {
        /**
         * Custom JSON instance configured for [MessagePart] polymorphism.
         *
         * Use this for serialization/deserialization of [ChatMessage] and [MessagePart].
         */
        val json: Json = Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /**
         * Creates a simple text message (convenience factory).
         */
        fun text(
            id: String,
            role: ChatRole,
            text: String,
            timestampMs: Long = System.currentTimeMillis(),
            status: MessageStatus = MessageStatus.Final,
        ): ChatMessage = ChatMessage(
            id = id,
            role = role,
            parts = listOf(MessagePart.Text(text)),
            timestampMs = timestampMs,
            status = status,
        )

        /**
         * Creates a command message with its output parts.
         */
        fun command(
            id: String,
            command: String,
            stdout: String = "",
            stderr: String = "",
            exitCode: Int? = null,
            timestampMs: Long = System.currentTimeMillis(),
        ): ChatMessage {
            val parts = mutableListOf<MessagePart>()
            parts += MessagePart.Command(command, exitCode)
            if (stdout.isNotEmpty()) parts += MessagePart.Stdout(stdout)
            if (stderr.isNotEmpty()) parts += MessagePart.Stderr(stderr)
            return ChatMessage(
                id = id,
                role = ChatRole.Assistant,
                parts = parts,
                timestampMs = timestampMs,
                status = if (exitCode != null) MessageStatus.Final else MessageStatus.Streaming,
            )
        }
    }
}

/**
 * A chat thread containing an ordered list of messages.
 */
@Serializable
data class ChatThread(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
)

// ── Backward-compatible deserialization ───────────────────────────────────────

/**
 * Deserializes a [ChatMessage] from JSON, handling the legacy format where
 * `content` was a plain string and `parts` did not exist.
 *
 * Usage:
 * ```kotlin
 * val message = ChatMessage.json.decodeFromString<ChatMessage>(legacyJson)
 * ```
 *
 * The [ChatMessage] data class default `parts = emptyList()` combined with
 * the `@Deprecated _content` field handles this automatically:
 * - Old JSON: `{"content":"hello","role":"User",...}` -> `parts` defaults to
 *   empty, `content` accessor returns `_content` ("hello").
 * - New JSON: `{"parts":[{"type":"text","text":"hello"}],...}` -> `parts`
 *   populated, `content` accessor returns `textContent`.
 *
 * For explicit migration (e.g. Room migration), use [migrateToParts].
 */
@Suppress("DEPRECATION")
fun ChatMessage.migrateToParts(): ChatMessage {
    if (parts.isNotEmpty()) return this
    if (_content.isEmpty()) return this
    return copy(
        parts = listOf(MessagePart.Text(_content)),
        _content = "",
    )
}

/**
 * Extension to create a [MessagePart.Text] from a plain string.
 */
fun String.toTextPart(): MessagePart.Text = MessagePart.Text(this)

/**
 * Extension to create a list containing a single [MessagePart.Text].
 */
fun String.toTextParts(): List<MessagePart.Text> = listOf(MessagePart.Text(this))
