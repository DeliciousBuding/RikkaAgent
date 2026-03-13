package io.rikka.agent.ssh

interface ClosableSshExecRunner : SshExecRunner {
  fun close()
}

fun interface SshExecRunnerFactory {
  fun create(
    knownHostsStore: KnownHostsStore,
    hostKeyCallback: HostKeyCallback,
    passwordProvider: PasswordProvider?,
    keyContentProvider: KeyContentProvider?,
    passphraseProvider: PassphraseProvider?,
  ): ClosableSshExecRunner
}

object DefaultSshExecRunnerFactory : SshExecRunnerFactory {
  override fun create(
    knownHostsStore: KnownHostsStore,
    hostKeyCallback: HostKeyCallback,
    passwordProvider: PasswordProvider?,
    keyContentProvider: KeyContentProvider?,
    passphraseProvider: PassphraseProvider?,
  ): ClosableSshExecRunner = SshjExecRunner(
    knownHostsStore = knownHostsStore,
    hostKeyCallback = hostKeyCallback,
    passwordProvider = passwordProvider,
    keyContentProvider = keyContentProvider,
    passphraseProvider = passphraseProvider,
    reuseConnections = true,
  )
}
