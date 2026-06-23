package io.rikka.agent.fake

import io.rikka.agent.ssh.ClosableSshExecRunner
import io.rikka.agent.ssh.HostKeyCallback
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.SshExecRunnerFactory

/**
 * [SshExecRunnerFactory] that returns a pre-built [FakeClosableSshExecRunner].
 *
 * Captures the arguments passed to [create] for assertion.
 */
class FakeSshExecRunnerFactory(
  private val runner: FakeClosableSshExecRunner = FakeClosableSshExecRunner.wrap(FakeSshExecRunner.withEvents()),
) : SshExecRunnerFactory {

  /** Arguments from the most recent [create] call. */
  data class CreateArgs(
    val knownHostsStore: KnownHostsStore,
    val hostKeyCallback: HostKeyCallback,
  )

  var lastCreateArgs: CreateArgs? = null
    private set

  var createCount: Int = 0
    private set

  override fun create(
    knownHostsStore: KnownHostsStore,
    hostKeyCallback: HostKeyCallback,
    passwordProvider: io.rikka.agent.ssh.PasswordProvider?,
    keyContentProvider: io.rikka.agent.ssh.KeyContentProvider?,
    passphraseProvider: io.rikka.agent.ssh.PassphraseProvider?,
  ): ClosableSshExecRunner {
    lastCreateArgs = CreateArgs(knownHostsStore, hostKeyCallback)
    createCount++
    return runner
  }

  /** Access the underlying [FakeSshExecRunner] for assertions. */
  val fake: FakeSshExecRunner get() = runner.fake

  /** Reset invocation tracking. */
  fun resetTracking() {
    lastCreateArgs = null
    createCount = 0
  }

  companion object {
    /**
     * Create a factory whose runner emits the given events.
     *
     * Convenience for the common case of wiring up a full fake stack.
     */
    fun withEvents(vararg events: io.rikka.agent.ssh.ExecEvent): FakeSshExecRunnerFactory =
      FakeSshExecRunnerFactory(
        FakeClosableSshExecRunner.wrap(FakeSshExecRunner.withEvents(*events))
      )
  }
}
