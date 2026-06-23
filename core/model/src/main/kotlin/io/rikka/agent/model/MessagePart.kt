package io.rikka.agent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Polymorphic message part for RikkaAgent.
 *
 * A [MessagePart] represents a single atomic unit within a [ChatMessage].
 * Messages carry an ordered list of parts instead of a monolithic `content` string,
 * enabling structured SSH I/O, AI reasoning traces, and rich rendering in a
 * uniform model.
 *
 * ## Design principles
 * - **SSH-native**: [Command], [Stdout], [Stderr] are first-class citizens.
 * - **AI-friendly**: [Reasoning], [Code] support Codex-style JSONL workflows.
 * - **Extensible**: new subtypes can be added without breaking existing
 *   serialization, because the JSON class discriminator is `"type"`.
 * - **No metadata bag**: keep it simple; add later if needed via non-breaking
 *   field additions to existing subtypes.
 *
 * ## Serialization
 *
 * Uses kotlinx.serialization polymorphism with `@SerialName` discriminators.
 * The JSON class discriminator key is `"type"` (the kotlinx.serialization
 * default for sealed classes).
 *
 * ## Thread safety
 *
 * All concrete subtypes are Kotlin `data class` instances and therefore
 * immutable. They are safe to share across threads without synchronization.
 *
 * ## Example
 *
 * ```kotlin
 * val message = ChatMessage(
 *     id = "msg-1",
 *     role = ChatRole.Assistant,
 *     parts = listOf(
 *         MessagePart.Reasoning("Let me check the server status..."),
 *         MessagePart.Command("systemctl status nginx"),
 *         MessagePart.Stdout("nginx is running\n"),
 *     ),
 *     timestampMs = System.currentTimeMillis(),
 * )
 * ```
 */
@Serializable
sealed class MessagePart {

    // ── SSH-specific ──────────────────────────────────────────────────────────

    /**
     * A command that was (or will be) executed over SSH.
     *
     * Represents a single shell command invocation. While the command is still
     * running, [exitCode] is `null`; once the command finishes, [exitCode] is
     * set to the process exit code (0..255).
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property command The exact command string sent to the remote shell.
     * @property exitCode `null` while the command is still running; 0..255
     *   after completion. A value of `0` typically indicates success.
     * @property startedAtEpochMs Timestamp when execution started, in
     *   milliseconds since the Unix epoch. `null` if the start time is unknown.
     */
    @Serializable
    @SerialName("command")
    data class Command(
        val command: String,
        val exitCode: Int? = null,
        val startedAtEpochMs: Long? = null,
    ) : MessagePart() {

        /**
         * Whether the command has finished execution.
         *
         * `true` when [exitCode] is non-null (i.e., the process has exited),
         * `false` when the command is still running.
         */
        val isFinished: Boolean get() = exitCode != null

        /**
         * Whether the command completed successfully.
         *
         * `true` when [exitCode] is exactly `0`. Returns `false` for non-zero
         * exit codes or when the command is still running ([exitCode] is `null`).
         */
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * A chunk of stdout produced by a running or finished command.
     *
     * Multiple [Stdout] parts may appear in sequence to represent streaming
     * output from a long-running process. Renderers should concatenate adjacent
     * [Stdout] parts for display.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property text The stdout text chunk. May contain newlines and any
     *   characters emitted by the remote process to its standard output stream.
     */
    @Serializable
    @SerialName("stdout")
    data class Stdout(
        val text: String,
    ) : MessagePart()

    /**
     * A chunk of stderr produced by a running or finished command.
     *
     * Similar to [Stdout] but captures the standard error stream. Multiple
     * [Stderr] parts may appear in sequence for streaming error output.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property text The stderr text chunk. May contain newlines and any
     *   characters emitted by the remote process to its standard error stream.
     */
    @Serializable
    @SerialName("stderr")
    data class Stderr(
        val text: String,
    ) : MessagePart()

    // ── AI / Codex tool integration ───────────────────────────────────────────

    /**
     * A reasoning step from an AI model (e.g. a Codex JSONL "reasoning" event).
     *
     * Captures the model's internal chain-of-thought before it produces a
     * final answer or tool call. UI renderers may display this in a collapsed
     * or dimmed section.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property text The reasoning text produced by the model.
     * @property stepId Optional step identifier for correlating with Codex
     *   events. `null` when the reasoning step is not associated with a
     *   specific Codex event.
     */
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val text: String,
        val stepId: String? = null,
    ) : MessagePart()

    /**
     * A fenced code block, typically generated by an AI or extracted from output.
     *
     * Represents a discrete block of source code. The [code] property contains
     * the raw code content without any markdown fence delimiters.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property code The raw code content, without any markdown fence
     *   delimiters (i.e. no leading/trailing ` ``` ` markers).
     * @property language Language identifier for syntax highlighting
     *   (e.g. `"python"`, `"bash"`, `"kotlin"`). `null` means the language
     *   is unknown and should be auto-detected by the renderer.
     */
    @Serializable
    @SerialName("code")
    data class Code(
        val code: String,
        val language: String? = null,
    ) : MessagePart()

    // ── General-purpose ───────────────────────────────────────────────────────

    /**
     * Plain text content. The default type for human-readable messages.
     *
     * This is the backward-compatible target: old `ChatMessage(content: String)`
     * migrates to `parts = listOf(Text(content))`.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property text The text content. May contain newlines and any Unicode
     *   characters.
     */
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
    ) : MessagePart()

    /**
     * An error that occurred during command execution or AI processing.
     *
     * Distinct from [Stderr]: [Stderr] is raw stream output from a process;
     * [Error] is a structured error with a human-readable message and optional
     * cause chain. Use [Error] when you need to surface a failure reason at
     * the message-part level rather than as raw process output.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property message Human-readable error description suitable for display
     *   to the user.
     * @property cause Optional underlying cause (e.g. an exception message or
     *   root-cause description). `null` when no deeper cause is available.
     * @property code Optional numeric error code (e.g. an SSH exit code, HTTP
     *   status code, or application-specific code). `null` when no code applies.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
        val cause: String? = null,
        val code: Int? = null,
    ) : MessagePart()

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * A Mermaid diagram definition for rendering flowcharts, sequence diagrams,
     * and other diagram types.
     *
     * The [definition] contains the raw Mermaid DSL. Renderers should pass
     * this to a Mermaid-compatible rendering engine (e.g. the `mermaid.js`
     * library) and display the result inline.
     *
     * ## Thread safety
     *
     * Immutable data class -- safe to share across threads.
     *
     * @property definition The raw Mermaid DSL string
     *   (e.g. `"graph TD; A-->B;"` or `"sequenceDiagram\nA->>B: Hello"`).
     * @property caption Optional caption shown below the rendered diagram.
     *   `null` when no caption is desired.
     */
    @Serializable
    @SerialName("mermaid")
    data class Mermaid(
        val definition: String,
        val caption: String? = null,
    ) : MessagePart()
}
