package io.rikka.agent.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.R
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.SshProfile
import io.rikka.agent.ssh.ExecEvent
import io.rikka.agent.ssh.HostKeyCallback
import io.rikka.agent.ssh.JsonlLineBuffer
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.PassphraseProvider
import io.rikka.agent.ssh.PasswordProvider
import io.rikka.agent.ssh.SshjExecRunner
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Chat ViewModel that drives real SSH command execution.
 *
 * Architecture:
 * - Receives a profileId to look up connection details
 * - Creates an SshjExecRunner with host key callback that emits UI events
 * - Streams stdout/stderr into chat messages in real time
 */
class ChatViewModel(
  private val profileId: String,
  private val profileStore: ProfileStore,
  private val knownHostsStore: KnownHostsStore,
  private val chatRepository: ChatRepository,
  private val appPreferences: AppPreferences,
  private val keyContentProvider: KeyContentProvider? = null,
  private val app: Application,
) : ViewModel() {

  /** Max chars for in-memory stdout/stderr per run (spec: MUST cap). */
  private val maxOutputChars = 256_000

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages

  /** Observable list of past threads for this profile. */
  val threads: StateFlow<List<ChatThread>> = chatRepository.observeThreads(profileId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
  val connectionState: StateFlow<ConnectionState> = _connectionState

  /** Profile display info for the top bar. */
  private val _profileLabel = MutableStateFlow("")
  val profileLabel: StateFlow<String> = _profileLabel

  private val _hostKeyEvent = MutableSharedFlow<HostKeyEvent>(extraBufferCapacity = 1)
  val hostKeyEvent: SharedFlow<HostKeyEvent> = _hostKeyEvent.asSharedFlow()

  private val _hostKeyDecision = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

  /** Password auth: emits profile description, waits for password or null (cancel) */
  private val _passwordRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val passwordRequest: SharedFlow<String> = _passwordRequest.asSharedFlow()
  private val _passwordResponse = MutableSharedFlow<String?>(extraBufferCapacity = 1)

  /** Key passphrase: emits profile description, waits for passphrase or null (cancel) */
  private val _passphraseRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val passphraseRequest: SharedFlow<String> = _passphraseRequest.asSharedFlow()
  private val _passphraseResponse = MutableSharedFlow<String?>(extraBufferCapacity = 1)

  private var currentProfile: SshProfile? = null
  private var runningJob: Job? = null
  private var threadId: String? = null
  private var runner: SshjExecRunner? = null

  private val hostKeyCallback = object : HostKeyCallback {
    override suspend fun onUnknownHost(
      host: String, port: Int, fingerprint: String, keyType: String,
    ): Boolean {
      _hostKeyEvent.emit(HostKeyEvent.UnknownHost(host, port, fingerprint, keyType))
      return _hostKeyDecision.first()
    }

    override suspend fun onHostKeyMismatch(
      host: String, port: Int,
      expectedFingerprint: String, actualFingerprint: String,
      keyType: String,
    ): Boolean {
      _hostKeyEvent.emit(
        HostKeyEvent.Mismatch(host, port, expectedFingerprint, actualFingerprint, keyType)
      )
      return _hostKeyDecision.first()
    }
  }

  init {
    loadProfile()
  }

  private fun loadProfile() {
    viewModelScope.launch {
      val profile = profileStore.getById(profileId)
      if (profile != null) {
        currentProfile = profile
        _profileLabel.value = profile.name.ifBlank { "${profile.username}@${profile.host}" }
        _connectionState.value = ConnectionState.READY
        appendSystemMessage(app.getString(R.string.msg_profile_ready, profile.name, profile.username, profile.host))
      } else {
        _connectionState.value = ConnectionState.ERROR
        appendSystemMessage(app.getString(R.string.msg_profile_not_found))
      }
    }
  }

  fun respondToHostKey(accepted: Boolean) {
    _hostKeyDecision.tryEmit(accepted)
  }

  fun respondToPassword(password: String?) {
    _passwordResponse.tryEmit(password)
  }

  fun respondToPassphrase(passphrase: String?) {
    _passphraseResponse.tryEmit(passphrase)
  }

  /** Start a fresh session (new thread). */
  fun newSession() {
    runningJob?.cancel()
    threadId = null
    _messages.value = emptyList()
    _connectionState.value = if (currentProfile != null) ConnectionState.READY else ConnectionState.ERROR
    val profile = currentProfile ?: return
    appendSystemMessage(app.getString(R.string.msg_new_session, profile.name, profile.username, profile.host))
  }

  /** Switch to an existing thread and load its messages. */
  fun switchThread(targetThreadId: String) {
    runningJob?.cancel()
    threadId = targetThreadId
    _connectionState.value = if (currentProfile != null) ConnectionState.READY else ConnectionState.ERROR
    viewModelScope.launch {
      val msgs = chatRepository.getMessages(targetThreadId)
      _messages.value = msgs
    }
  }

  /** Delete a thread and clear UI if it was the current one. */
  fun deleteThread(targetThreadId: String) {
    viewModelScope.launch {
      chatRepository.deleteThread(targetThreadId)
      if (threadId == targetThreadId) {
        newSession()
      }
    }
  }

  fun send(text: String) {
    val command = text.trim()
    if (command.isEmpty()) return

    val profile = currentProfile ?: run {
      appendSystemMessage(app.getString(R.string.msg_not_connected))
      return
    }

    val userMsg = ChatMessage(
      id = "u-${UUID.randomUUID()}",
      role = ChatRole.User,
      content = command,
      timestampMs = System.currentTimeMillis(),
      status = MessageStatus.Final,
    )
    _messages.update { it + userMsg }
    persistMessage(userMsg)

    val assistantId = "a-${UUID.randomUUID()}"
    val assistantSeed = ChatMessage(
      id = assistantId,
      role = ChatRole.Assistant,
      content = "",
      timestampMs = System.currentTimeMillis(),
      status = MessageStatus.Streaming,
    )
    _messages.update { it + assistantSeed }
    persistMessage(assistantSeed)

    runningJob?.cancel()
    _connectionState.value = ConnectionState.EXECUTING

    val execRunner = getOrCreateRunner()
    val isCodex = profile.codexMode
    val shellCommand = if (isCodex) {
      wrapForCodex(command, profile.codexWorkDir, profile.codexApiKey)
    } else {
      wrapWithShell(command)
    }

    runningJob = viewModelScope.launch {
      val stdout = StringBuilder()
      val stderr = StringBuilder()
      // For Codex mode: parse JSONL, accumulate markdown deltas
      val jsonlBuffer = if (isCodex) JsonlLineBuffer() else null
      val markdownAccum = if (isCodex) StringBuilder() else null
      var codexStatus: String? = null

      fun renderCodexStreamingContent(): String {
        val statusPrefix = codexStatus?.takeIf { it.isNotBlank() }?.let {
          "_${app.getString(R.string.msg_codex_status_prefix, it)}_\n\n"
        }.orEmpty()
        return if (markdownAccum != null && markdownAccum.isNotEmpty()) {
          statusPrefix + markdownAccum.toString()
        } else {
          statusPrefix + formatOutput(stdout, stderr, exitCode = null)
        }
      }

      execRunner.run(profile, shellCommand).collect { event ->
        when (event) {
          is ExecEvent.StdoutChunk -> {
            if (isCodex && jsonlBuffer != null && markdownAccum != null) {
              val parsed = jsonlBuffer.feed(event.bytes)
              for (e in parsed) {
                when (e) {
                  is ExecEvent.StructuredEvent -> {
                    if (e.kind == "markdown_delta") {
                      markdownAccum.append(e.rawJson)
                      updateAssistantContent(assistantId, renderCodexStreamingContent())
                    }
                    if (e.kind == "status") {
                      codexStatus = e.rawJson
                      updateAssistantContent(assistantId, renderCodexStreamingContent())
                    }
                  }
                  is ExecEvent.StdoutChunk -> {
                    // Non-JSON line from Codex — append to raw stdout
                    stdout.append(String(e.bytes, Charsets.UTF_8))
                  }
                  else -> { /* ignore */ }
                }
              }
              // If no markdown deltas yet, show raw output as fallback
              if (markdownAccum.isEmpty()) {
                updateAssistantContent(assistantId, renderCodexStreamingContent())
              }
            } else {
              stdout.append(String(event.bytes, Charsets.UTF_8))
              updateAssistantContent(assistantId, formatOutput(stdout, stderr, exitCode = null))
            }
          }
          is ExecEvent.StderrChunk -> {
            stderr.append(String(event.bytes, Charsets.UTF_8))
            updateAssistantContent(assistantId, formatOutput(stdout, stderr, exitCode = null))
          }
          is ExecEvent.StructuredEvent -> { /* handled via jsonlBuffer above */ }
          is ExecEvent.Exit -> {
            // Flush remaining JSONL buffer
            if (isCodex && jsonlBuffer != null && markdownAccum != null) {
              for (e in jsonlBuffer.flush()) {
                if (e is ExecEvent.StructuredEvent && e.kind == "markdown_delta") {
                  markdownAccum.append(e.rawJson)
                } else if (e is ExecEvent.StdoutChunk) {
                  stdout.append(String(e.bytes, Charsets.UTF_8))
                }
              }
            }

            val finalContent = if (isCodex && markdownAccum != null && markdownAccum.isNotEmpty()) {
              val extra = buildString {
                if (stderr.isNotEmpty()) {
                  append("\n\n")
                  append(app.getString(R.string.label_stderr))
                  append("\n")
                  append(stderr)
                }
                if (event.code != null && event.code != 0) {
                  append("\n")
                  append(app.getString(R.string.msg_exit_code, event.code))
                }
              }
              markdownAccum.toString() + extra
            } else {
              formatOutput(stdout, stderr, event.code)
            }
            updateAssistantMessage(assistantId, finalContent, MessageStatus.Final)
            persistUpdate(assistantId, finalContent, MessageStatus.Final)
            _connectionState.value = ConnectionState.READY
          }
          is ExecEvent.Error -> {
            val friendlyMsg = friendlyErrorMessage(event.category, event.message)
            val errorContent = if (stdout.isNotEmpty() || stderr.isNotEmpty()) {
              formatOutput(stdout, stderr, exitCode = null) + "\n$friendlyMsg"
            } else {
              friendlyMsg
            }
            updateAssistantMessage(assistantId, errorContent, MessageStatus.Error)
            persistUpdate(assistantId, errorContent, MessageStatus.Error)
            _connectionState.value = ConnectionState.READY
          }
        }
      }
    }
  }

  fun cancelRunning() {
    runningJob?.cancel()
    runningJob = null
    _connectionState.value = ConnectionState.READY
  }

  /** Export current session as plain text. */
  fun exportSession(): String = buildString {
    val profile = _profileLabel.value
    appendLine("# Session: $profile")
    appendLine()
    for (msg in _messages.value) {
      if (msg.role == ChatRole.User) {
        appendLine("$ ${msg.content}")
      } else {
        appendLine(msg.content)
      }
      appendLine()
    }
  }

  private fun getOrCreateRunner(): SshjExecRunner {
    runner?.let { return it }

    val passwordProvider = PasswordProvider { profile ->
      val desc = "${profile.username}@${profile.host}:${profile.port}"
      _passwordRequest.emit(desc)
      _passwordResponse.first() ?: throw IllegalStateException("Authentication cancelled")
    }

    val passphraseProvider = PassphraseProvider { profile ->
      val desc = "${profile.username}@${profile.host}:${profile.port}"
      _passphraseRequest.emit(desc)
      _passphraseResponse.first()
    }

    return SshjExecRunner(
      knownHostsStore = knownHostsStore,
      hostKeyCallback = hostKeyCallback,
      passwordProvider = passwordProvider,
      keyContentProvider = keyContentProvider,
      passphraseProvider = passphraseProvider,
      reuseConnections = true,
    ).also { runner = it }
  }

  override fun onCleared() {
    super.onCleared()
    runningJob?.cancel()
    runner?.close()
  }

  private fun formatOutput(
    stdout: StringBuilder,
    stderr: StringBuilder,
    exitCode: Int?,
  ): String = buildString {
    val stdoutTruncated = stdout.length > maxOutputChars
    val stderrTruncated = stderr.length > maxOutputChars
    if (stdout.isNotEmpty()) {
      if (stdoutTruncated) {
        append(stdout, stdout.length - maxOutputChars, stdout.length)
        append("\n")
        append(app.getString(R.string.msg_output_truncated))
        append("\n")
      } else {
        append(stdout)
      }
      if (!endsWith("\n")) append("\n")
    }
    if (stderr.isNotEmpty()) {
      if (isNotEmpty()) append("\n")
      append(app.getString(R.string.label_stderr))
      append("\n")
      if (stderrTruncated) {
        append(stderr, stderr.length - maxOutputChars, stderr.length)
        append("\n")
        append(app.getString(R.string.msg_output_truncated))
        append("\n")
      } else {
        append(stderr)
      }
      if (!endsWith("\n")) append("\n")
    }
    if (exitCode != null) {
      if (stdout.isEmpty() && stderr.isEmpty()) {
        append(if (exitCode == 0) app.getString(R.string.msg_no_output) else app.getString(R.string.msg_no_output_failed))
        append("\n")
      }
      if (isNotEmpty()) append("\n")
      append(app.getString(R.string.msg_exit_code, exitCode))
    }
  }

  private fun friendlyErrorMessage(category: String, raw: String): String = when (category) {
    "connection_refused" -> app.getString(R.string.err_connection_refused)
    "timeout" -> app.getString(R.string.err_timeout)
    "unknown_host" -> app.getString(R.string.err_unknown_host)
    "auth_failed" -> app.getString(R.string.err_auth_failed)
    else -> app.getString(R.string.err_ssh_generic, raw)
  }

  private fun updateAssistantContent(id: String, content: String) {
    _messages.update { list ->
      list.map { if (it.id == id) it.copy(content = content) else it }
    }
  }

  private fun updateAssistantMessage(id: String, content: String, status: MessageStatus) {
    _messages.update { list ->
      list.map { if (it.id == id) it.copy(content = content, status = status) else it }
    }
  }

  private fun persistMessage(msg: ChatMessage) {
    viewModelScope.launch {
      val isNewThread = threadId == null
      val tid = threadId ?: chatRepository.createThread(profileId, "").also { threadId = it }
      chatRepository.insertMessage(tid, msg)
      // Auto-title the thread from the first user command
      if (isNewThread && msg.role == ChatRole.User) {
        val title = msg.content.take(50).let { if (msg.content.length > 50) "$it…" else it }
        chatRepository.updateThreadTitle(tid, title)
      }
    }
  }

  private fun persistUpdate(id: String, content: String, status: MessageStatus) {
    viewModelScope.launch {
      chatRepository.updateMessage(id, content, status)
    }
  }

  private fun appendSystemMessage(text: String) {
    val msg = ChatMessage(
      id = "sys-${UUID.randomUUID()}",
      role = ChatRole.Assistant,
      content = text,
      timestampMs = System.currentTimeMillis(),
      status = MessageStatus.Final,
    )
    _messages.update { it + msg }
  }

  /**
   * Wrap a user command with the configured default shell.
   * SSH exec typically runs commands through the server's login shell,
   * but this allows explicit shell selection (e.g., bash -c '...').
   */
  /** Wrap a natural-language task as a `codex exec --json` command. */
  private fun wrapForCodex(task: String, workDir: String?, apiKey: String? = null): String {
    val escaped = task.replace("\"", "\\\"")
    val cdPart = if (!workDir.isNullOrBlank()) "cd ${shellQuote(workDir)} && " else ""
    val envPart = if (!apiKey.isNullOrBlank()) "OPENAI_API_KEY=${shellQuote(apiKey)} " else ""
    return "${cdPart}${envPart}codex exec --json --full-auto \"$escaped\""
  }

  private fun shellQuote(s: String): String {
    val escaped = s.replace("'", "'\\''")
    return "'$escaped'"
  }

  private fun wrapWithShell(command: String): String {
    val shell = kotlinx.coroutines.runBlocking {
      appPreferences.defaultShell.first()
    }
    // If shell is the standard /bin/sh, let SSH exec handle it natively
    if (shell == "/bin/sh" || shell.isBlank()) return command
    // Escape single quotes in the command for safe shell wrapping
    val escaped = command.replace("'", "'\\''")
    return "$shell -c '$escaped'"
  }
}

enum class ConnectionState {
  IDLE,
  READY,
  EXECUTING,
  ERROR,
}

sealed class HostKeyEvent {
  data class UnknownHost(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val keyType: String,
  ) : HostKeyEvent()

  data class Mismatch(
    val host: String,
    val port: Int,
    val expectedFingerprint: String,
    val actualFingerprint: String,
    val keyType: String,
  ) : HostKeyEvent()
}