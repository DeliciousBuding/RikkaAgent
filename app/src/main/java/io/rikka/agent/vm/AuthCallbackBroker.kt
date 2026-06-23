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
 * ## Responsibilities
 * - Provides [HostKeyCallback] implementation that emits [HostKeyEvent] to the UI
 *   and suspends until the user responds via [respondToHostKey].
 * - Provides [PasswordProvider] and [PassphraseProvider] implementations that emit
 *   request strings to the UI and suspend until the user responds via [respondToPassword]
 *   or [respondToPassphrase].
 * - Decouples the SSH library's synchronous callback model from the asynchronous
 *   Compose UI event model using [SharedFlow] request/response pairs.
 *
 * ## Thread Safety
 * - All flows use `extraBufferCapacity = 1` to ensure emit never suspends for the sender,
 *   preventing deadlocks between the SSH thread and the UI thread.
 * - [respondToHostKey], [respondToPassword], and [respondToPassphrase] use [tryEmit]
 *   which is safe for concurrent callers.
 * - The request flows (`hostKeyEvent`, `passwordRequest`, `passphraseRequest`) are collected
 *   by the UI on the Main dispatcher; the response flows are collected by the SSH thread
 *   (which suspends via `first()` until a response arrives).
 * - The request/response pattern is inherently sequential: the SSH thread emits a request,
 *   suspends on the response flow, and resumes only when the UI emits a response.
 *   This prevents race conditions between concurrent auth callbacks.
 *
 * ## Exposed Events (SharedFlows)
 * | SharedFlow           | Type                      | Description                                      |
 * |----------------------|---------------------------|--------------------------------------------------|
 * | [hostKeyEvent]       | `SharedFlow<HostKeyEvent>`| Emitted when host key verification is needed.    |
 * | [passwordRequest]    | `SharedFlow<String>`      | Emitted with a description when password is needed. |
 * | [passphraseRequest]  | `SharedFlow<String>`      | Emitted with a description when passphrase is needed. |
 */
class AuthCallbackBroker {

  /** Backing flow for host-key verification events. */
  private val _hostKeyEvent = MutableSharedFlow<HostKeyEvent>(extraBufferCapacity = 1)
  /** Emitted when the SSH host is unknown or its key has changed. Collected by the UI. */
  val hostKeyEvent: SharedFlow<HostKeyEvent> = _hostKeyEvent.asSharedFlow()

  /** Backing flow for the user's host-key decision (true = accept, false = reject). */
  private val _hostKeyDecision = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

  /** Backing flow for password auth requests. */
  private val _passwordRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  /** Emitted with a connection description when password authentication is requested. */
  val passwordRequest: SharedFlow<String> = _passwordRequest.asSharedFlow()
  /** Backing flow for the user's password response (null = cancel). */
  private val _passwordResponse = MutableSharedFlow<String?>(extraBufferCapacity = 1)

  /** Backing flow for key passphrase requests. */
  private val _passphraseRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
  /** Emitted with a connection description when a private-key passphrase is requested. */
  val passphraseRequest: SharedFlow<String> = _passphraseRequest.asSharedFlow()
  /** Backing flow for the user's passphrase response (null = cancel). */
  private val _passphraseResponse = MutableSharedFlow<String?>(extraBufferCapacity = 1)

  // ── Response methods (called from UI) ──────────────────────────────────

  /**
   * Respond to a host-key verification prompt.
   *
   * @param accepted `true` to trust the host key, `false` to reject.
   */
  fun respondToHostKey(accepted: Boolean) {
    _hostKeyDecision.tryEmit(accepted)
  }

  /**
   * Respond to a password authentication prompt.
   *
   * @param password The password entered by the user, or `null` to cancel.
   */
  fun respondToPassword(password: String?) {
    _passwordResponse.tryEmit(password)
  }

  /**
   * Respond to a key passphrase prompt.
   *
   * @param passphrase The passphrase entered by the user, or `null` to cancel.
   */
  fun respondToPassphrase(passphrase: String?) {
    _passphraseResponse.tryEmit(passphrase)
  }

  // ── Callback factories (consumed by SSH runner) ────────────────────────

  /**
   * [HostKeyCallback] implementation that emits [HostKeyEvent] to the UI
   * and suspends until the user responds via [respondToHostKey].
   */
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

  /**
   * Create a [PasswordProvider] that emits a connection description to [_passwordRequest]
   * and suspends until the user responds via [_passwordResponse].
   *
   * @throws IllegalStateException if the user cancels (response is `null`).
   */
  fun createPasswordProvider(): PasswordProvider = PasswordProvider { profile ->
    val desc = "${profile.username}@${profile.host}:${profile.port}"
    _passwordRequest.emit(desc)
    _passwordResponse.first() ?: throw IllegalStateException("Authentication cancelled")
  }

  /**
   * Create a [PassphraseProvider] that emits a connection description to [_passphraseRequest]
   * and suspends until the user responds via [_passphraseResponse].
   *
   * Returns `null` if the user cancels (unlike [createPasswordProvider] which throws).
   */
  fun createPassphraseProvider(): PassphraseProvider = PassphraseProvider { profile ->
    val desc = "${profile.username}@${profile.host}:${profile.port}"
    _passphraseRequest.emit(desc)
    _passphraseResponse.first()
  }
}
