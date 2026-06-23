package io.rikka.agent.vm

import io.rikka.agent.ssh.HostKeyCallback
import io.rikka.agent.ssh.PassphraseProvider
import io.rikka.agent.ssh.PasswordProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Bridges sshj synchronous authentication callbacks to Compose UI SharedFlows.
 *
 * Host key verification, password auth, and key passphrase requests all flow
 * through here. The UI collects the request flows and responds via the
 * `respondTo*` methods.
 */
class AuthCallbackBroker {

  private val _hostKeyEvent = MutableSharedFlow<HostKeyEvent>(extraBufferCapacity = 1)
  val hostKeyEvent: SharedFlow<HostKeyEvent> = _hostKeyEvent.asSharedFlow()

  private val _hostKeyDecision = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

  private val _passwordRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val passwordRequest: SharedFlow<String> = _passwordRequest.asSharedFlow()
  private val _passwordResponse = MutableSharedFlow<String?>(extraBufferCapacity = 1)

  private val _passphraseRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val passphraseRequest: SharedFlow<String> = _passphraseRequest.asSharedFlow()
  private val _passphraseResponse = MutableSharedFlow<String?>(extraBufferCapacity = 1)

  // ── Response methods (called from UI) ──────────────────────────────────

  fun respondToHostKey(accepted: Boolean) {
    _hostKeyDecision.tryEmit(accepted)
  }

  fun respondToPassword(password: String?) {
    _passwordResponse.tryEmit(password)
  }

  fun respondToPassphrase(passphrase: String?) {
    _passphraseResponse.tryEmit(passphrase)
  }

  // ── Callback factories (consumed by SSH runner) ────────────────────────

  val hostKeyCallback = object : HostKeyCallback {
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
        HostKeyEvent.Mismatch(host, port, expectedFingerprint, actualFingerprint, keyType),
      )
      return _hostKeyDecision.first()
    }
  }

  fun createPasswordProvider(): PasswordProvider = PasswordProvider { profile ->
    val desc = "${profile.username}@${profile.host}:${profile.port}"
    _passwordRequest.emit(desc)
    _passwordResponse.first() ?: throw IllegalStateException("Authentication cancelled")
  }

  fun createPassphraseProvider(): PassphraseProvider = PassphraseProvider { profile ->
    val desc = "${profile.username}@${profile.host}:${profile.port}"
    _passphraseRequest.emit(desc)
    _passphraseResponse.first()
  }
}
