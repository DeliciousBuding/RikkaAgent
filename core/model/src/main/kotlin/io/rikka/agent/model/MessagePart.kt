package io.rikka.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Polymorphic message part for RikkaAgent.
 *
 * SSH-native: Command, Stdout, Stderr are first-class citizens.
 * AI-friendly: Reasoning, Code support Codex-style JSONL workflows.
 * Extensible: new subtypes can be added without breaking existing serialization.
 */
@Serializable
sealed class MessagePart {

    // ── SSH-specific ──

    /** A command executed over SSH. [exitCode] is null while running. */
    @Serializable
    @SerialName("command")
    data class Command(
        val command: String,
        val exitCode: Int? = null,
        val startedAtEpochMs: Long? = null,
    ) : MessagePart() {
        val isFinished get() = exitCode != null
        val isSuccess get() = exitCode == 0
    }

    /** Standard output chunk (streaming-friendly). */
    @Serializable
    @SerialName("stdout")
    data class Stdout(val text: String) : MessagePart()

    /** Standard error chunk. */
    @Serializable
    @SerialName("stderr")
    data class Stderr(val text: String) : MessagePart()

    // ── AI / Codex ──

    /** AI reasoning step, e.g. Codex `reasoning` JSONL field. */
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val text: String,
        val stepId: String? = null,
    ) : MessagePart()

    /** Fenced code block with optional language identifier. */
    @Serializable
    @SerialName("code")
    data class Code(
        val code: String,
        val language: String? = null,
    ) : MessagePart()

    // ── General ──

    /** Plain text or Markdown content. */
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : MessagePart()

    /** Structured error with optional cause and code. */
    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        val cause: String? = null,
        val code: Int? = null,
    ) : MessagePart()

    // ── Rendering ──

    /** Mermaid diagram definition for optional rendering. */
    @Serializable
    @SerialName("mermaid")
    data class Mermaid(
        val definition: String,
        val caption: String? = null,
    ) : MessagePart()
}

/** Extension to wrap a plain string as a [MessagePart.Text]. */
fun String.toTextPart(): MessagePart.Text = MessagePart.Text(this)
