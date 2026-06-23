package io.rikka.agent.vm

import android.app.Application
import io.rikka.agent.R
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SshProfile
import io.rikka.agent.ssh.ClosableSshExecRunner
import io.rikka.agent.ssh.ExecEvent
import io.rikka.agent.ssh.JsonlLineBuffer
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.SshExecRunnerFactory
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Lightweight holder for the most recent connection-level error. */
data class ConnectionError(
  val category: String,
  val message: String,
)

/**
 * Manages SSH command execution lifecycle: connection, streaming output, cancellation.
 *
 * Handles both plain shell commands and Codex JSONL mode with structured progress rendering
 * mapped to [MessagePart] subtypes for ChainOfThought display.
 */
class CommandExecutor(
  private val profileStore: ProfileStore,
  private val knownHostsStore: KnownHostsStore,
  private val appPreferences: AppPreferences,
  private val keyContentProvider: KeyContentProvider?,
  private val runnerFactory: SshExecRunnerFactory,
  private val authBroker: AuthCallbackBroker,
  private val app: Application,
  private val scope: CoroutineScope,
) {

  /** Max chars for in-memory stdout/stderr per run. */
  private val maxOutputChars = 256_000
  private val outputTexts by lazy {
    OutputTexts(
      stderrLabel = app.getString(R.string.label_stderr),
      truncatedHint = app.getString(R.string.msg_output_truncated),
      noOutputOk = app.getString(R.string.msg_no_output),
      noOutputFailed = app.getString(R.string.msg_no_output_failed),
      exitCodeLabel = { c -> app.getString(R.string.msg_exit_code, c) },
    )
  }

  private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
  val connectionState: StateFlow<ConnectionState> = _connectionState

  private val _lastConnectionError = MutableStateFlow<ConnectionError?>(null)
  val lastConnectionError: StateFlow<ConnectionError?> = _lastConnectionError

  fun dismissConnectionError() {
    _lastConnectionError.value = null
  }

  private val fullOutputByMessageId = mutableMapOf<String, String>()

  var currentProfile: SshProfile? = null
    private set
  private var runningJob: Job? = null
  private var runningAssistantId: String? = null
  private var runner: ClosableSshExecRunner? = null

  // ── Profile ────────────────────────────────────────────────────────────

  /** Load profile and set connection state. Returns (profile, label) or null. */
  suspend fun loadProfile(profileId: String): Pair<SshProfile, String>? {
    val profile = profileStore.getById(profileId)
    if (profile != null) {
      currentProfile = profile
      _connectionState.value = ConnectionState.READY
      return profile to profile.name.ifBlank { "${profile.username}@${profile.host}" }
    } else {
      _connectionState.value = ConnectionState.ERROR
      return null
    }
  }

  /** Reset connection state to READY (if profile loaded) or ERROR. */
  fun resetConnectionState() {
    _connectionState.value = if (currentProfile != null) ConnectionState.READY else ConnectionState.ERROR
  }

  // ── Full output access ─────────────────────────────────────────────────

  fun hasFullOutput(messageId: String): Boolean =
    fullOutputByMessageId.containsKey(messageId)

  fun getFullOutput(messageId: String): String? =
    fullOutputByMessageId[messageId]

  fun clearFullOutput() {
    fullOutputByMessageId.clear()
  }

  // ── Execution ──────────────────────────────────────────────────────────

  /**
   * Execute a command over SSH. Events update the assistant message via callbacks.
   *
   * For Codex JSONL mode, structured [MessagePart] instances are accumulated and
   * emitted via [updateParts] for ChainOfThought rendering. A flat-string rendering
   * is also provided via [updateContent] for backward compatibility.
   *
   * @param command The user command to execute.
   * @param assistantId The message ID of the assistant seed message.
   * @param isCodex Whether to wrap the command for Codex JSONL mode.
   * @param updateContent Called on each content update during streaming (flat string).
   * @param updateParts Called on each content update with structured [MessagePart] list (Codex mode).
   * @param updateMessage Called when the message reaches a final state (Final/Canceled/Error).
   * @param getAssistantContent Returns current assistant message content by ID.
   */
  fun execute(
    command: String,
    assistantId: String,
    isCodex: Boolean,
    updateContent: (id: String, content: String) -> Unit,
    updateParts: ((id: String, parts: List<MessagePart>) -> Unit)? = null,
    updateMessage: (id: String, content: String, status: MessageStatus) -> Unit,
    getAssistantContent: (id: String) -> String?,
  ) {
    val profile = currentProfile ?: return

    runningJob?.cancel()
    _connectionState.value = ConnectionState.EXECUTING

    val execRunner = getOrCreateRunner()
    val shellCommand = if (isCodex) {
      CommandComposer.wrapForCodex(command, profile.codexWorkDir, profile.codexApiKey)
    } else {
      wrapWithShell(command)
    }

    runningAssistantId = assistantId

    runningJob = scope.launch {
      val stdout = StringBuilder()
      val stderr = StringBuilder()
      val jsonlBuffer = if (isCodex) JsonlLineBuffer() else null
      val codexParts = if (isCodex) mutableListOf<MessagePart>() else null
      var codexStatus: String? = null
      var codexProgress = CodexProgressState()

      fun emitCodexUpdate() {
        updateContent(assistantId, renderCodexContent(codexParts, codexProgress, codexStatus, stderr, exitCode = null))
        if (codexParts != null) {
          updateParts?.invoke(assistantId, codexParts.toList())
        }
      }

      fun processCodexEvent(kind: String, rawJson: String) {
        when (kind) {
          "json" -> {
            codexProgress = CodexProgressFormatter.update(codexProgress, rawJson)
            CodexEventMapper.mapToPart(rawJson)?.let { part ->
              codexParts?.add(part)
            }
            emitCodexUpdate()
          }
          "markdown_delta" -> {
            // Legacy path: raw markdown delta from older JsonlParser extraction.
            // Prefer the structured "json" path above; this handles pre-mapped deltas.
            codexParts?.let { parts ->
              val last = parts.lastOrNull()
              if (last is MessagePart.Text) {
                parts[parts.lastIndex] = last.copy(text = last.text + rawJson)
              } else {
                parts.add(MessagePart.Text(rawJson))
              }
            }
            emitCodexUpdate()
          }
          "status" -> {
            codexStatus = rawJson
            emitCodexUpdate()
          }
        }
      }

      execRunner.run(profile, shellCommand).collect { event ->
        when (event) {
          is ExecEvent.StdoutChunk -> {
            if (isCodex && jsonlBuffer != null && codexParts != null) {
              val parsed = jsonlBuffer.feed(event.bytes)
              for (e in parsed) {
                when (e) {
                  is ExecEvent.StructuredEvent -> processCodexEvent(e.kind, e.rawJson)
                  is ExecEvent.StdoutChunk -> stdout.append(String(e.bytes, Charsets.UTF_8))
                  else -> { /* ignore */ }
                }
              }
              if (codexParts.isEmpty()) {
                updateContent(assistantId, renderCodexContent(codexParts, codexProgress, codexStatus, stderr, exitCode = null))
              }
            } else {
              stdout.append(String(event.bytes, Charsets.UTF_8))
              val formatted = formatOutputPair(stdout, stderr, exitCode = null)
              if (formatted.truncated) fullOutputByMessageId[assistantId] = formatted.full
              updateContent(assistantId, formatted.display)
            }
          }
          is ExecEvent.StderrChunk -> {
            stderr.append(String(event.bytes, Charsets.UTF_8))
            val formatted = formatOutputPair(stdout, stderr, exitCode = null)
            if (formatted.truncated) fullOutputByMessageId[assistantId] = formatted.full
            updateContent(assistantId, formatted.display)
          }
          is ExecEvent.StructuredEvent -> { /* handled via jsonlBuffer above */ }
          is ExecEvent.Exit -> {
            if (isCodex && jsonlBuffer != null && codexParts != null) {
              for (e in jsonlBuffer.flush()) {
                when (e) {
                  is ExecEvent.StructuredEvent -> processCodexEvent(e.kind, e.rawJson)
                  is ExecEvent.StdoutChunk -> stdout.append(String(e.bytes, Charsets.UTF_8))
                  else -> Unit
                }
              }
            }

            val finalContent = if (isCodex) {
              renderCodexContent(codexParts, codexProgress, codexStatus, stderr, event.code)
            } else {
              formatOutputPair(stdout, stderr, event.code).display
            }
            val finalFull = if (isCodex) {
              finalContent
            } else {
              formatOutputPair(stdout, stderr, event.code).full
            }
            if (finalFull != finalContent) {
              fullOutputByMessageId[assistantId] = finalFull
            }
            updateMessage(assistantId, finalContent, MessageStatus.Final)
            runningAssistantId = null
            _connectionState.value = ConnectionState.READY
          }
          is ExecEvent.Canceled -> {
            val canceledText = app.getString(R.string.msg_command_canceled)
            val current = getAssistantContent(assistantId)
            val canceledContent = CancelMessageHelper.mergeCanceledContent(current, canceledText)
            updateMessage(assistantId, canceledContent, MessageStatus.Canceled)
            runningAssistantId = null
            _connectionState.value = ConnectionState.READY
          }
          is ExecEvent.Error -> {
            val friendlyMsg = ErrorMessageMapper.friendlyErrorMessage(
              category = event.category,
              raw = event.message,
              connectionRefused = app.getString(R.string.err_connection_refused),
              timeout = app.getString(R.string.err_timeout),
              unknownHost = app.getString(R.string.err_unknown_host),
              authFailed = app.getString(R.string.err_auth_failed),
              generic = { raw -> app.getString(R.string.err_ssh_generic, raw) },
            )
            _lastConnectionError.value = ConnectionError(event.category, friendlyMsg)
            val errorContent = if (stdout.isNotEmpty() || stderr.isNotEmpty()) {
              formatOutput(stdout, stderr, exitCode = null) + "\n$friendlyMsg"
            } else {
              friendlyMsg
            }
            updateMessage(assistantId, errorContent, MessageStatus.Error)
            runningAssistantId = null
            _connectionState.value = ConnectionState.READY
          }
        }
      }
    }
  }

  /**
   * Cancel the currently running command.
   * Returns the assistant message ID that was canceled, or null if nothing was running.
   */
  fun cancel(): String? {
    val canceledId = runningAssistantId
    runningJob?.cancel()
    runningJob = null
    runningAssistantId = null
    return canceledId
  }

  /** Release SSH runner resources. */
  fun cleanUp() {
    runningJob?.cancel()
    runner?.close()
  }

  // ── Codex rendering ────────────────────────────────────────────────────

  /**
   * Render accumulated [MessagePart] list plus progress state into a flat display string.
   *
   * Sections are ordered: progress header, then reasoning (collapsible), then text/code content.
   * This preserves the existing CodexProgressFormatter summary while adding ChainOfThought
   * reasoning display.
   */
  private fun renderCodexContent(
    parts: MutableList<MessagePart>?,
    progress: CodexProgressState,
    status: String?,
    stderr: StringBuilder,
    exitCode: Int?,
  ): String {
    val progressLines = buildList {
      status?.takeIf { it.isNotBlank() }?.let {
        add("_${app.getString(R.string.msg_codex_status_prefix, it)}_")
      }
      progress.thread?.let {
        add("- ${app.getString(R.string.msg_codex_progress_thread)}: $it")
      }
      progress.turn?.let {
        add("- ${app.getString(R.string.msg_codex_progress_turn)}: $it")
      }
      progress.item?.let {
        add("- ${app.getString(R.string.msg_codex_progress_item)}: $it")
      }
    }.joinToString("\n")

    val body = if (parts != null && parts.isNotEmpty()) {
      renderParts(parts, stderr, exitCode)
    } else {
      formatOutput(StringBuilder(), stderr, exitCode = exitCode)
    }

    return if (progressLines.isBlank()) body else "$progressLines\n\n$body"
  }

  /**
   * Render a list of [MessagePart] into a flat markdown string.
   *
   * Reasoning parts are grouped under a collapsible "Thinking" header.
   * Code parts get fenced code blocks with language tags.
   * Text parts are emitted as-is.
   */
  private fun renderParts(
    parts: List<MessagePart>,
    stderr: StringBuilder,
    exitCode: Int?,
  ): String {
    val sb = StringBuilder()
    var inReasoningBlock = false

    for (part in parts) {
      when (part) {
        is MessagePart.Reasoning -> {
          if (!inReasoningBlock) {
            sb.appendLine("<details><summary>Thinking</summary>\n")
            inReasoningBlock = true
          }
          sb.appendLine(part.text.trim())
          sb.appendLine()
        }
        is MessagePart.Text -> {
          if (inReasoningBlock) {
            sb.appendLine("</details>\n")
            inReasoningBlock = false
          }
          sb.append(part.text)
        }
        is MessagePart.Code -> {
          if (inReasoningBlock) {
            sb.appendLine("</details>\n")
            inReasoningBlock = false
          }
          val lang = part.language ?: ""
          sb.appendLine("```$lang")
          sb.appendLine(part.code.trimEnd())
          sb.appendLine("```")
        }
        else -> {
          // Command, Stdout, Stderr, Error, Mermaid -- not expected in Codex stream
        }
      }
    }

    if (inReasoningBlock) {
      sb.appendLine("</details>\n")
    }

    if (stderr.isNotEmpty()) {
      sb.append("\n\n")
      sb.append(app.getString(R.string.label_stderr))
      sb.append("\n")
      sb.append(stderr)
    }
    if (exitCode != null && exitCode != 0) {
      sb.append("\n")
      sb.append(app.getString(R.string.msg_exit_code, exitCode))
    }

    return sb.toString()
  }

  // ── Private ────────────────────────────────────────────────────────────

  private fun getOrCreateRunner(): ClosableSshExecRunner {
    runner?.let { return it }
    return runnerFactory.create(
      knownHostsStore = knownHostsStore,
      hostKeyCallback = authBroker.hostKeyCallback,
      passwordProvider = authBroker.createPasswordProvider(),
      keyContentProvider = keyContentProvider,
      passphraseProvider = authBroker.createPassphraseProvider(),
    ).also { runner = it }
  }

  private fun formatOutput(
    stdout: StringBuilder,
    stderr: StringBuilder,
    exitCode: Int?,
  ): String = formatOutputPair(stdout, stderr, exitCode).display

  private fun formatOutputPair(
    stdout: StringBuilder,
    stderr: StringBuilder,
    exitCode: Int?,
  ): FormattedOutput = OutputFormatter.format(
    stdout = stdout.toString(),
    stderr = stderr.toString(),
    exitCode = exitCode,
    capChars = maxOutputChars,
    texts = outputTexts,
  )

  private fun wrapWithShell(command: String): String {
    val shell = kotlinx.coroutines.runBlocking {
      appPreferences.defaultShell.first()
    }
    return CommandComposer.wrapWithShell(command, shell)
  }
}

/**
 * Maps Codex JSONL event objects to [MessagePart] subtypes.
 *
 * Recognized event types:
 * - `type` containing "reasoning" → [MessagePart.Reasoning] (with optional stepId)
 * - `type` containing "code" → [MessagePart.Code] (with optional language)
 * - Text-bearing fields (text, content, delta, message) → [MessagePart.Text]
 * - `type` containing "thread." / "turn." / "item." → null (handled by [CodexProgressFormatter])
 *
 * Unknown event types that carry text content still produce [MessagePart.Text]
 * so that no visible output is silently dropped.
 */
internal object CodexEventMapper {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /**
   * Parse a raw JSON string and map it to a [MessagePart], or null if the event
   * is purely structural (progress/thread/turn/item) or unrecognizable.
   */
  fun mapToPart(rawJson: String): MessagePart? {
    val obj = try {
      json.parseToJsonElement(rawJson).jsonObject
    } catch (_: Exception) {
      return null
    }

    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: ""

    // Reasoning step
    if (type.contains("reasoning", ignoreCase = true)) {
      val text = extractText(obj) ?: return null
      val stepId = extractString(obj, "step_id", "stepId", "id")
      return MessagePart.Reasoning(text = text, stepId = stepId)
    }

    // Code block
    if (type.contains("code", ignoreCase = true)) {
      val code = extractText(obj)
        ?: extractString(obj, "code")
        ?: return null
      val language = extractString(obj, "language", "lang")
      return MessagePart.Code(code = code, language = language)
    }

    // Progress events -- handled by CodexProgressFormatter, no MessagePart needed
    if (type.startsWith("thread.") || type.startsWith("turn.") || type.startsWith("item.")) {
      return null
    }

    // Text content (generic message or text-bearing event)
    val text = extractText(obj)
    if (text != null) {
      return MessagePart.Text(text = text)
    }

    return null
  }

  /** Try multiple field names to extract text content from a JSON object. */
  private fun extractText(obj: JsonObject): String? {
    for (key in listOf("text", "content", "delta", "message")) {
      val value = obj[key] ?: continue
      // Try as primitive first
      value.jsonPrimitive.contentOrNull?.let { if (it.isNotBlank()) return it }
      // Try nested object with .text or .content
      try {
        val nested = value.jsonObject
        nested["text"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) return it }
        nested["content"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) return it }
      } catch (_: Exception) { /* not an object */ }
    }
    return null
  }

  /** Try multiple field names to extract a string value. */
  private fun extractString(obj: JsonObject, vararg keys: String): String? {
    for (key in keys) {
      obj[key]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) return it }
      // Try nested: e.g. obj["item"]["id"]
      try {
        obj[key]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) return it }
      } catch (_: Exception) { /* not an object */ }
    }
    return null
  }
}
