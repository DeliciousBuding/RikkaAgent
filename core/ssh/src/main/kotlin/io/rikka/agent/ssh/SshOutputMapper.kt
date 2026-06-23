package io.rikka.agent.ssh

import io.rikka.agent.model.MessagePart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Bridges SSH exec output ([ExecEvent] flow) to structured [MessagePart] sequence.
 *
 * Design goals:
 * - Real-time streaming: accumulate byte chunks, flush at natural content boundaries
 * - Content-aware: detect code blocks, Markdown, and plain text in stdout
 * - ANSI-safe: strip terminal escape codes before content analysis
 * - Thread-safe: all mutable state accessed only within the flow collector coroutine
 *
 * Usage:
 * ```
 * val mapper = SshOutputMapper()
 * mapper.map(execFlow, command).collect { part -> /* render MessagePart */ }
 * ```
 *
 * The mapper emits parts in this order:
 * 1. [MessagePart.Command] — emitted immediately with command + timestamp
 * 2. [MessagePart.Text] / [MessagePart.Code] — stdout content, streamed incrementally
 * 3. [MessagePart.Stderr] — stderr chunks, emitted as-is
 * 4. [MessagePart.Command] (updated) — final command with exit code, emitted on [ExecEvent.Exit]
 * 5. [MessagePart.Error] — on [ExecEvent.Error]
 */
class SshOutputMapper {

    companion object {
        /**
         * Regex matching ANSI escape sequences:
         * - CSI: `ESC [ ... letter`  (e.g. `ESC[31m`, `ESC[2K`)
         * - OSC: `ESC ] ... BEL` or `ESC ] ... ESC \`  (e.g. `ESC]0;title BEL`)
         */
        private val ANSI_REGEX =
            Regex("""\[[0-9;]*[A-Za-z]|\][^]*(|\\)""")

        /** Matches a Markdown fenced code block opening line: ``` or ```lang */
        private val CODE_FENCE_OPEN = Regex("""^`{3}(\w*)\s*$""")

        /** Matches a Markdown fenced code block closing line: ``` */
        private val CODE_FENCE_CLOSE = Regex("""^`{3}\s*$""")

        /**
         * When to force-flush a buffer that has no natural boundary.
         * Prevents unbounded memory growth for continuous streaming output
         * (e.g. a long-running process printing lines without blank-line gaps).
         */
        private const val MAX_BUFFER_CHARS = 16_000
    }

    // ── Mutable state (accessed only within the flow collector coroutine) ─────

    /** Accumulated stdout text awaiting boundary-based flush. */
    private val buffer = StringBuilder()

    /** True when buffer content is inside an opened but not yet closed code fence. */
    private var isInsideCodeBlock = false

    /** Language tag from the opening code fence (e.g. "python", "bash"). */
    private var codeBlockLanguage: String? = null

    /** Command string, captured for the final Command part. */
    private var commandStr = ""

    /** Epoch-ms when execution started. */
    private var startedAt = 0L

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Transform an [ExecEvent] flow into a [MessagePart] flow.
     *
     * Stdout bytes are stripped of ANSI escapes, accumulated in an internal buffer,
     * and flushed at content boundaries:
     * - Fenced code blocks (triple backtick) become [MessagePart.Code]
     * - Everything else becomes [MessagePart.Text]
     *
     * Stderr is emitted as [MessagePart.Stderr] after flushing any pending stdout.
     *
     * @param execFlow The raw SSH exec event flow (from [SshjExecRunner.run]).
     * @param command The shell command being executed.
     * @return A cold flow of [MessagePart] instances.
     */
    fun map(execFlow: Flow<ExecEvent>, command: String): Flow<MessagePart> = flow {
        // Reset state for this execution
        buffer.clear()
        isInsideCodeBlock = false
        codeBlockLanguage = null
        commandStr = command
        startedAt = System.currentTimeMillis()

        // Emit initial Command part (exitCode=null while running)
        emit(
            MessagePart.Command(
                command = command,
                exitCode = null,
                startedAtEpochMs = startedAt,
            )
        )

        execFlow.collect { event ->
            when (event) {
                is ExecEvent.StdoutChunk -> {
                    val text = stripAnsi(String(event.bytes, Charsets.UTF_8))
                    if (text.isNotEmpty()) {
                        accumulateAndFlush(text, this)
                    }
                }

                is ExecEvent.StderrChunk -> {
                    flushBuffer(this)
                    val text = stripAnsi(String(event.bytes, Charsets.UTF_8))
                    if (text.isNotEmpty()) {
                        emit(MessagePart.Stderr(text = text))
                    }
                }

                is ExecEvent.Exit -> {
                    flushBuffer(this)
                    emit(
                        MessagePart.Command(
                            command = commandStr,
                            exitCode = event.code,
                            startedAtEpochMs = startedAt,
                        )
                    )
                }

                is ExecEvent.Error -> {
                    flushBuffer(this)
                    emit(
                        MessagePart.Error(
                            message = event.message,
                            code = null,
                            cause = event.category,
                        )
                    )
                }

                is ExecEvent.Canceled -> {
                    flushBuffer(this)
                    emit(
                        MessagePart.Command(
                            command = commandStr,
                            exitCode = null,
                            startedAtEpochMs = startedAt,
                        )
                    )
                }

                is ExecEvent.StructuredEvent -> {
                    // Structured JSONL events are not mapped here;
                    // they are handled by JsonlLineBuffer in CommandExecutor.
                }
            }
        }
    }

    // ── Streaming accumulation and boundary detection ───────────────────────

    /**
     * Append [text] to the internal buffer, then flush complete segments.
     *
     * Flush strategy (in priority order):
     * 1. Code block close: when a ``` fence line is found while inside a block
     * 2. Code block open: when a ``` fence line is found while outside a block
     * 3. Paragraph boundary: `\n\n` (double newline)
     * 4. Line boundary: `\n` (single newline) — fallback for dense output
     * 5. Size threshold: [MAX_BUFFER_CHARS] — prevents unbounded growth
     */
    private suspend fun accumulateAndFlush(
        text: String,
        emitFn: suspend (MessagePart) -> Unit,
    ) {
        buffer.append(text)

        while (buffer.isNotEmpty()) {
            val bufStr = buffer.toString()

            if (isInsideCodeBlock) {
                // Look for closing fence
                val closeIdx = findCodeFenceClose(bufStr)
                if (closeIdx != null) {
                    val codeContent = bufStr.substring(0, closeIdx.first).trimEnd('\n', '\r')
                    if (codeContent.isNotEmpty()) {
                        emitFn(
                            MessagePart.Code(
                                code = codeContent,
                                language = codeBlockLanguage,
                            )
                        )
                    }
                    buffer.delete(0, closeIdx.second)
                    isInsideCodeBlock = false
                    codeBlockLanguage = null
                    continue
                }
                // No closing fence yet — keep accumulating
                break
            }

            // Not inside a code block: look for an opening fence
            val openIdx = findCodeFenceOpen(bufStr)
            if (openIdx != null) {
                // Flush text before the fence
                val beforeFence = bufStr.substring(0, openIdx.first).trimEnd('\n', '\r')
                if (beforeFence.isNotEmpty()) {
                    emitFn(MessagePart.Text(text = beforeFence))
                }
                isInsideCodeBlock = true
                codeBlockLanguage = openIdx.third
                buffer.delete(0, openIdx.second)
                continue
            }

            // Paragraph boundary (double newline)
            val paraIdx = bufStr.indexOf("\n\n")
            if (paraIdx >= 0) {
                val segment = bufStr.substring(0, paraIdx).trimEnd('\n', '\r')
                if (segment.isNotEmpty()) {
                    emitFn(MessagePart.Text(text = segment))
                }
                buffer.delete(0, paraIdx + 2)
                continue
            }

            // Line boundary (single newline)
            val lineIdx = bufStr.indexOf('\n')
            if (lineIdx >= 0) {
                val segment = bufStr.substring(0, lineIdx).trimEnd('\r')
                if (segment.isNotEmpty()) {
                    emitFn(MessagePart.Text(text = segment))
                }
                buffer.delete(0, lineIdx + 1)
                continue
            }

            // Size threshold — prevent unbounded growth
            if (buffer.length > MAX_BUFFER_CHARS) {
                val content = buffer.toString().trimEnd()
                if (content.isNotEmpty()) {
                    emitFn(MessagePart.Text(text = content))
                }
                buffer.clear()
                continue
            }

            // Not enough content yet — wait for more data
            break
        }
    }

    /**
     * Flush any remaining buffer content as a final segment.
     * Called when a stderr chunk, exit, or error arrives.
     */
    private suspend fun flushBuffer(emitFn: suspend (MessagePart) -> Unit) {
        if (buffer.isEmpty()) return

        val content = buffer.toString().trimEnd()
        buffer.clear()

        if (content.isEmpty()) return

        if (isInsideCodeBlock) {
            // Unclosed code block — emit as code anyway (partial output)
            emitFn(
                MessagePart.Code(
                    code = content,
                    language = codeBlockLanguage,
                )
            )
            isInsideCodeBlock = false
            codeBlockLanguage = null
        } else {
            val paragraphs = content.split("\n\n")
            for (para in paragraphs) {
                val trimmed = para.trim()
                if (trimmed.isNotEmpty()) {
                    emitFn(MessagePart.Text(text = trimmed))
                }
            }
        }
    }

    // ── Code fence detection ────────────────────────────────────────────────

    /**
     * Find a code fence opening line (``` or ```lang) in [text].
     *
     * @return Triple(lineStart, afterLine, language) or null.
     *         lineStart = index of the fence line start
     *         afterLine = index after the fence line (code content starts here)
     *         language  = language tag or null
     */
    private fun findCodeFenceOpen(text: String): Triple<Int, Int, String?>? {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val lineStart = if (searchFrom == 0) 0 else {
                val nl = text.indexOf('\n', searchFrom)
                if (nl == -1) return null
                nl + 1
            }
            if (lineStart >= text.length) return null

            val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
            val line = text.substring(lineStart, lineEnd).trimEnd('\r')

            val match = CODE_FENCE_OPEN.matchEntire(line)
            if (match != null) {
                val lang = match.groupValues[1].ifEmpty { null }
                return Triple(lineStart, if (lineEnd < text.length) lineEnd + 1 else lineEnd, lang)
            }

            searchFrom = lineEnd + 1
        }
        return null
    }

    /**
     * Find a code fence closing line (```) in [text].
     *
     * @return Pair(lineStart, afterLine) or null.
     */
    private fun findCodeFenceClose(text: String): Pair<Int, Int>? {
        var searchFrom = 0
        while (searchFrom < text.length) {
            val lineStart = if (searchFrom == 0) 0 else {
                val nl = text.indexOf('\n', searchFrom)
                if (nl == -1) return null
                nl + 1
            }
            if (lineStart >= text.length) return null

            val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
            val line = text.substring(lineStart, lineEnd).trimEnd('\r')

            if (CODE_FENCE_CLOSE.matches(line)) {
                return Pair(lineStart, if (lineEnd < text.length) lineEnd + 1 else lineEnd)
            }

            searchFrom = lineEnd + 1
        }
        return null
    }

    // ── ANSI stripping ──────────────────────────────────────────────────────

    /**
     * Strip ANSI escape sequences from [input].
     *
     * Inlined from [io.rikka.agent.ui.util.AnsiStripper] to avoid a
     * core/ssh → core/ui dependency. The regex is identical.
     */
    internal fun stripAnsi(input: String): String = input.replace(ANSI_REGEX, "")
}
