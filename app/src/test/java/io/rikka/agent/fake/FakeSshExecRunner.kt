package io.rikka.agent.fake

import io.rikka.agent.model.SshProfile
import io.rikka.agent.ssh.ClosableSshExecRunner
import io.rikka.agent.ssh.ExecEvent
import io.rikka.agent.ssh.SshExecRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake [SshExecRunner] that emits a configurable sequence of [ExecEvent]s.
 *
 * ## Usage
 *
 * ```kotlin
 * val runner = FakeSshExecRunner.withEvents(
 *     ExecEvent.StdoutChunk("hello\n".toByteArray()),
 *     ExecEvent.Exit(0),
 * )
 * val events = runner.run(profile, "echo hello").toList()
 * ```
 *
 * ## Advanced: custom Flow
 *
 * ```kotlin
 * val runner = FakeSshExecRunner { profile, command ->
 *     flow {
 *         emit(ExecEvent.StdoutChunk("output".toByteArray()))
 *         emit(ExecEvent.Exit(0))
 *     }
 * }
 * ```
 *
 * ## Invocation tracking
 *
 * [lastProfile] and [lastCommand] capture the most recent invocation for
 * assertion purposes. [invocationCount] tracks how many times [run] was called.
 */
class FakeSshExecRunner(
  private val eventProducer: (SshProfile, String) -> Flow<ExecEvent>,
) : SshExecRunner {

  // ── Invocation tracking ────────────────────────────────────────────────────

  /** The profile passed to the most recent [run] call. */
  var lastProfile: SshProfile? = null
    private set

  /** The command passed to the most recent [run] call. */
  var lastCommand: String? = null
    private set

  /** Total number of [run] invocations. */
  var invocationCount: Int = 0
    private set

  // ── SshExecRunner ──────────────────────────────────────────────────────────

  override fun run(profile: SshProfile, command: String): Flow<ExecEvent> {
    lastProfile = profile
    lastCommand = command
    invocationCount++
    return eventProducer(profile, command)
  }

  // ── Test helpers ───────────────────────────────────────────────────────────

  /** Reset invocation tracking state. */
  fun resetTracking() {
    lastProfile = null
    lastCommand = null
    invocationCount = 0
  }

  companion object {
    /**
     * Create a runner that emits the given [events] in order, then completes.
     *
     * Each call to [run] replays the same sequence.
     */
    fun withEvents(vararg events: ExecEvent): FakeSshExecRunner =
      FakeSshExecRunner { _, _ ->
        flow {
          events.forEach { emit(it) }
        }
      }

    /**
     * Create a runner that always emits an [ExecEvent.Error].
     */
    fun withError(category: String, message: String): FakeSshExecRunner =
      withEvents(ExecEvent.Error(category, message))

    /**
     * Create a runner that emits stdout + successful exit.
     */
    fun withOutput(stdout: String, stderr: String = ""): FakeSshExecRunner {
      val events = mutableListOf<ExecEvent>()
      if (stdout.isNotEmpty()) events += ExecEvent.StdoutChunk(stdout.toByteArray())
      if (stderr.isNotEmpty()) events += ExecEvent.StderrChunk(stderr.toByteArray())
      events += ExecEvent.Exit(0)
      return withEvents(*events.toTypedArray())
    }

    /**
     * Create a runner that emits events based on the command string.
     *
     * The [commandRouter] maps a command to a list of events. Unknown
     * commands produce an [ExecEvent.Exit] with code 0.
     */
    fun withRouting(
      commandRouter: (String) -> List<ExecEvent>,
    ): FakeSshExecRunner = FakeSshExecRunner { _, command ->
      flow {
        val events = commandRouter(command)
        if (events.isEmpty()) {
          emit(ExecEvent.Exit(0))
        } else {
          events.forEach { emit(it) }
        }
      }
    }
  }
}

/**
 * [ClosableSshExecRunner] wrapper around [FakeSshExecRunner].
 *
 * Tracks whether [close] was called.
 */
class FakeClosableSshExecRunner(
  private val delegate: FakeSshExecRunner,
) : ClosableSshExecRunner, SshExecRunner by delegate {

  /** Whether [close] has been called. */
  var isClosed: Boolean = false
    private set

  override fun close() {
    isClosed = true
  }

  /** Access the underlying [FakeSshExecRunner] for assertion helpers. */
  val fake: FakeSshExecRunner get() = delegate

  companion object {
    /** Wrap a [FakeSshExecRunner] as a [FakeClosableSshExecRunner]. */
    fun wrap(runner: FakeSshExecRunner): FakeClosableSshExecRunner =
      FakeClosableSshExecRunner(runner)
  }
}
