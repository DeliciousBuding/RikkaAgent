package io.rikka.agent.ssh

import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * v1 target: SSH exec only (no PTY). This interface is intentionally small so we can swap SSH libs
 * without touching UI code.
 */
interface SshExecRunner {
  fun run(profile: SshProfile, command: String): Flow<ExecEvent>
}

@Serializable
sealed class ExecEvent {
  @Serializable
  data class StdoutChunk(val bytes: ByteArray) : ExecEvent()

  @Serializable
  data class StderrChunk(val bytes: ByteArray) : ExecEvent()

  /** A parsed JSONL structured event from Codex output. */
  @Serializable
  data class StructuredEvent(val kind: String, val rawJson: String) : ExecEvent()

  @Serializable
  data class Exit(val code: Int?) : ExecEvent()

  @Serializable
  data object Canceled : ExecEvent()

  @Serializable
  data class Error(val category: String, val message: String) : ExecEvent()
}
