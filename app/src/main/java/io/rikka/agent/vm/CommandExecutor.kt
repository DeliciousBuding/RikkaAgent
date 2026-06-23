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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight holder for the most recent connection-level error.
 *
 * @property category  Error category from the SSH layer (e.g. "auth", "timeout", "unknown-host").
 * @property message   Human-readable, localized error message suitable for UI display.
 */
data class ConnectionError(
  val category: String,
  val message: String,
)

/**
 * Manages SSH command execution lifecycle: connection, streaming output, and cancellation.
 *
 * ## Responsibilities
 * - Loads an [SshProfile] from the [ProfileStore] and exposes the connection state.
 * - Executes commands over SSH via [ClosableSshExecRunner], streaming stdout/stderr chunks
 *   back to the caller through callbacks.
 * - Supports two execution modes:
 *   - **Plain shell**: Wraps the command with the user-configured default shell.
 *   - **Codex JSONL**: Wraps the command for Codex mode, parses JSONL output into structured
 *     [MessagePart] instances (Reasoning, Code, Text) for ChainOfThought rendering.
 * - Manages output truncation (max [maxOutputChars] chars) and stores full output separately
 *   for "view full output" UI affordance.
 * - Handles authentication callbacks (host key, password, passphrase) via [AuthCallbackBroker].
 *
 * ## Thread Safety
 * - All mutable UI-facing state is held in [MutableStateFlow] instances, safe for concurrent access.
 * - [runningJob], [runningAssistantId], and [runner] are mutated only from the calling coroutine
 *   and the single [scope.launch] block; no concurrent mutation occurs because a new `execute()`
 *   call cancels the previous job before mutating.
 * - The [fullOutputByMessageId] map is accessed only from coroutines launched in [scope], which
 *   run on the Main dispatcher by default.
 * - Callbacks ([updateContent], [updateMessage]) are invoked from the [scope] coroutine and
 *   must be thread-safe (they are, via `StateFlow.update` in [ChatViewModel]).
 *
 * ## Exposed State
 * | StateFlow                  | Type                           | Description                                     |
 * |----------------------------|--------------------------------|-------------------------------------------------|
 * | [connectionState]          | `StateFlow<ConnectionState>`   | IDLE / READY / EXECUTING / ERROR.               |
 * | [lastConnectionError]      | `StateFlow<ConnectionError?>`  | Most recent connection error, dismissable.       |
 *
 * @param profileStore      Source for loading [SshProfile] by ID.
 * @param knownHostsStore   Persistent store for SSH known-hosts fingerprints.
 * @param appPreferences    User preferences (default shell, etc.).
 * @param keyContentProvider Optional provider for SSH private-key content.
 * @param runnerFactory     Factory for creating [ClosableSshExecRunner] instances.
 * @param authBroker        Broker that bridges SSH auth callbacks to UI SharedFlows.
 * @param app               Android [Application] for string resource access.
 * @param scope             Coroutine scope for launching execution jobs (typically [viewModelScope]).
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

  /** Max chars for in-memory stdout/stderr per run; output beyond this is truncated. */
  private val maxOutputChars = 256_000
  /** Localized text templates for output formatting (stderr label, truncation hints, etc.). */
  private val outputTexts by lazy {
    OutputTexts(
      stderrLabel = app.getString(R.string.label_stderr),
      truncatedHint = app.getString(R.string.msg_output_truncated),
      noOutputOk = app.getString(R.string.msg_no_output),
      noOutputFailed = app.getString(R.string.msg_no_output_failed),
      exitCodeLabel = { c -> app.getString(R.string.msg_exit_code, c) },
    )
  }

  /** Backing field for connection lifecycle state. */
  private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
  /** Observable SSH connection lifecycle state. */
  val connectionState: StateFlow<ConnectionState> = _connectionState

  /** Backing field for the most recent connection error. */
  private val _lastConnectionError = MutableStateFlow<ConnectionError?>(null)
  /** Observable most recent connection-level error, or null if none / dismissed. */
  val lastConnectionError: StateFlow<ConnectionError?> = _lastConnectionError

  /** Clear the current [lastConnectionError]. */
  fun dismissConnectionError() {
    _lastConnectionError.value = null
  }

  /** In-memory cache of full (untruncated) output keyed by assistant message ID.
   *  Uses [ConcurrentHashMap] for thread-safe access from multiple coroutines. */
  private val fullOutputByMessageId = ConcurrentHashMap<String, String>()

  /** The currently loaded SSH profile, or null if not yet loaded. */
  var currentProfile: SshProfile? = null
    private set
  /** The coroutine Job for the currently running command, or null. */
  private var runningJob: Job? = null
  /** The assistant message ID of the currently running command, or null. */
  private var runningAssistantId: String? = null
  /** The reusable SSH exec runner, lazily created on first execution. */
  private var runner: ClosableSshExecRunner? = null

  // ── Profile ────────────────────────────────────────────────────────────

  /**
   * Load an SSH profile by [profileId] and set the connection state.
   *
   * @param profileId The profile ID to load from [ProfileStore].
   * @return A pair of (profile, display label) on success, or null if the profile was not found.
   *         On success, [connectionState] transitions to [ConnectionState.READY];
   *         on failure, it transitions to [ConnectionState.ERROR].
   */
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

  /**
   * Reset [connectionState] to [ConnectionState.READY] if a profile is loaded,
   * or [ConnectionState.ERROR] otherwise. Typically called after cancellation or
   * thread switching.
   */
  fun resetConnectionState() {
    _connectionState.value = if (currentProfile != null) ConnectionState.READY else ConnectionState.ERROR
  }

  // ── Full output access ─────────────────────────────────────────────────

  /**
   * Check whether full (untruncated) output is stored for the given [messageId].
   *
   * @param messageId The assistant message ID.
   * @return `true` if full output is available in [fullOutputByMessageId].
   */
  fun hasFullOutput(messageId: String): Boolean =
    fullOutputByMessageId.containsKey(messageId)

  /**
   * Retrieve the full (untruncated) output for the given [messageId].
   *
   * @param messageId The assistant message ID.
   * @return The full output string, or null if not available.
   */
  fun getFullOutput(messageId: String): String? =
    fullOutputByMessageId[messageId]

  /** Clear all cached full outputs. Called when starting a new session. */
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

    runningAssistantId = assistantId

    runningJob = scope.launch {
      val shellCommand = if (isCodex) {
        CommandComposer.wrapForCodex(command, profile.codexWorkDir, profile.codexApiKey)
      } else {
        wrapWithShell(command)
      }

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

      execRunner.run(profile, shellCommand)
        .catch { e ->
          // Defensive catch: SshjExecRunner handles errors internally via ExecEvent.Error,
          // but if the Flow itself throws (e.g. connection dropped mid-stream), surface it.
          val friendlyMsg = ErrorMessageMapper.friendlyErrorMessage(
            category = "ssh_error",
            raw = e.message ?: e.javaClass.simpleName,
            connectionRefused = app.getString(R.string.err_connection_refused),
            timeout = app.getString(R.string.err_timeout),
            unknownHost = app.getString(R.string.err_unknown_host),
            authFailed = app.getString(R.string.err_auth_failed),
            generic = { raw -> app.getString(R.string.err_ssh_generic, raw) },
          )
          _lastConnectionError.value = ConnectionError("ssh_error", friendlyMsg)
          updateMessage(assistantId, friendlyMsg, MessageStatus.Error)
          runningAssistantId = null
          _connectionState.value = ConnectionState.READY
        }
        .collect { event ->
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
   *
   * Cancels the coroutine [Job] and clears the running state.
   *
   * @return The assistant message ID that was canceled, or null if nothing was running.
   */
  fun cancel(): String? {
    val canceledId = runningAssistantId
    runningJob?.cancel()
    runningJob = null
    runningAssistantId = null
    return canceledId
  }

  /**
   * Release SSH runner resources. Cancels any running job and closes the underlying
   * [ClosableSshExecRunner]. Called from [ChatViewModel.onCleared].
   */
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

  private suspend fun wrapWithShell(command: String): String {
    val shell = withContext(Dispatchers.IO) {
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
