package io.rikka.agent.vm

import android.app.Application
import io.rikka.agent.R
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

/**
 * Manages SSH command execution lifecycle: connection, streaming output, cancellation.
 *
 * Handles both plain shell commands and Codex JSONL mode with structured progress rendering.
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
   * @param command The user command to execute.
   * @param assistantId The message ID of the assistant seed message.
   * @param isCodex Whether to wrap the command for Codex JSONL mode.
   * @param updateContent Called on each content update during streaming.
   * @param updateMessage Called when the message reaches a final state (Final/Canceled/Error).
   * @param getAssistantContent Returns current assistant message content by ID.
   */
  fun execute(
    command: String,
    assistantId: String,
    isCodex: Boolean,
    updateContent: (id: String, content: String) -> Unit,
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
      val markdownAccum = if (isCodex) StringBuilder() else null
      var codexStatus: String? = null
      var codexProgress = CodexProgressState()

      fun renderCodexContent(exitCode: Int?): String {
        val progressLines = buildList {
          codexStatus?.takeIf { it.isNotBlank() }?.let {
            add("_${app.getString(R.string.msg_codex_status_prefix, it)}_")
          }
          codexProgress.thread?.let {
            add("- ${app.getString(R.string.msg_codex_progress_thread)}: $it")
          }
          codexProgress.turn?.let {
            add("- ${app.getString(R.string.msg_codex_progress_turn)}: $it")
          }
          codexProgress.item?.let {
            add("- ${app.getString(R.string.msg_codex_progress_item)}: $it")
          }
        }.joinToString("\n")

        val body = if (markdownAccum != null && markdownAccum.isNotEmpty()) {
          buildString {
            append(markdownAccum)
            if (stderr.isNotEmpty()) {
              append("\n\n")
              append(app.getString(R.string.label_stderr))
              append("\n")
              append(stderr)
            }
            if (exitCode != null && exitCode != 0) {
              append("\n")
              append(app.getString(R.string.msg_exit_code, exitCode))
            }
          }
        } else {
          formatOutput(stdout, stderr, exitCode = exitCode)
        }

        return if (progressLines.isBlank()) body else "$progressLines\n\n$body"
      }

      execRunner.run(profile, shellCommand).collect { event ->
        when (event) {
          is ExecEvent.StdoutChunk -> {
            if (isCodex && jsonlBuffer != null && markdownAccum != null) {
              val parsed = jsonlBuffer.feed(event.bytes)
              for (e in parsed) {
                when (e) {
                  is ExecEvent.StructuredEvent -> {
                    if (e.kind == "json") {
                      codexProgress = CodexProgressFormatter.update(codexProgress, e.rawJson)
                      updateContent(assistantId, renderCodexContent(exitCode = null))
                    }
                    if (e.kind == "markdown_delta") {
                      markdownAccum.append(e.rawJson)
                      updateContent(assistantId, renderCodexContent(exitCode = null))
                    }
                    if (e.kind == "status") {
                      codexStatus = e.rawJson
                      updateContent(assistantId, renderCodexContent(exitCode = null))
                    }
                  }
                  is ExecEvent.StdoutChunk -> {
                    stdout.append(String(e.bytes, Charsets.UTF_8))
                  }
                  else -> { /* ignore */ }
                }
              }
              if (markdownAccum.isEmpty()) {
                updateContent(assistantId, renderCodexContent(exitCode = null))
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
            if (isCodex && jsonlBuffer != null && markdownAccum != null) {
              for (e in jsonlBuffer.flush()) {
                when (e) {
                  is ExecEvent.StructuredEvent -> {
                    if (e.kind == "json") {
                      codexProgress = CodexProgressFormatter.update(codexProgress, e.rawJson)
                    } else if (e.kind == "markdown_delta") {
                      markdownAccum.append(e.rawJson)
                    } else if (e.kind == "status") {
                      codexStatus = e.rawJson
                    }
                  }
                  is ExecEvent.StdoutChunk -> {
                    stdout.append(String(e.bytes, Charsets.UTF_8))
                  }
                  else -> Unit
                }
              }
            }

            val finalContent = if (isCodex && markdownAccum != null && markdownAccum.isNotEmpty()) {
              renderCodexContent(event.code)
            } else {
              if (isCodex) renderCodexContent(event.code) else formatOutputPair(stdout, stderr, event.code).display
            }
            val finalFull = if (isCodex && markdownAccum != null && markdownAccum.isNotEmpty()) {
              finalContent
            } else {
              if (isCodex) finalContent else formatOutputPair(stdout, stderr, event.code).full
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
