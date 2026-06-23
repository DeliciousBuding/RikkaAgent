package io.rikka.agent.ssh

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.InputStream
import java.io.StringReader
import java.security.PublicKey
import java.util.concurrent.TimeUnit

internal enum class PrivateKeyFormat {
  PuTTY,
  OpenSSH,
}

internal fun detectPrivateKeyFormat(keyContent: String): PrivateKeyFormat {
  val trimmed = keyContent.trimStart()
  return if (trimmed.startsWith("PuTTY-User-Key-File-")) {
    PrivateKeyFormat.PuTTY
  } else {
    PrivateKeyFormat.OpenSSH
  }
}

/**
 * Bridges the synchronous sshj [HostKeyVerifier.verify] callback to the asynchronous
 * host-key decision flow.  Created inside [SshjExecRunner.run] and consumed by the
 * same callbackFlow coroutine that processes host-key UI events.
 */
private class HostKeyDecisionRequest(
  val host: String,
  val port: Int,
  val fingerprint: String,
  val keyType: String,
  val isMismatch: Boolean,
  val expectedFingerprint: String?,
  val needsStore: Boolean,
  val decision: CompletableDeferred<Boolean>,
)

/**
 * Real SSH exec runner backed by sshj.
 *
 * Key design:
 * - When [reuseConnections] is true, caches the SSH client across commands.
 * - stdout/stderr read concurrently on IO dispatcher.
 * - Host key verification uses [CompletableDeferred] + [Channel] to bridge
 *   sshj's synchronous [HostKeyVerifier.verify] to the async [HostKeyCallback],
 *   eliminating all [runBlocking] calls except the unavoidable deferred-await
 *   inside the synchronous callback.
 * - Known-host fingerprint and passphrase are pre-loaded before blocking sshj
 *   calls so that no suspend functions are invoked via [runBlocking].
 * - Call [close] to release pooled connections when done.
 */
class SshjExecRunner(
  private val knownHostsStore: KnownHostsStore,
  private val hostKeyCallback: HostKeyCallback,
  private val passwordProvider: PasswordProvider? = null,
  private val keyContentProvider: KeyContentProvider? = null,
  private val passphraseProvider: PassphraseProvider? = null,
  private val reuseConnections: Boolean = false,
) : ClosableSshExecRunner {

  @Volatile private var cachedClient: SSHClient? = null
  @Volatile private var cachedProfileKey: String? = null

  override fun run(profile: SshProfile, command: String): Flow<ExecEvent> = callbackFlow {
    // Channel that bridges synchronous verify() calls to this coroutine.
    // Capacity 8 is generous — sshj rarely verifies more than one key per connect.
    val hostKeyChannel = Channel<HostKeyDecisionRequest>(capacity = 8)

    // Consume host key decision requests asynchronously (suspend, no runBlocking).
    val hostKeyJob = launch {
      try {
        for (request in hostKeyChannel) {
          try {
            val accepted = if (request.isMismatch) {
              hostKeyCallback.onHostKeyMismatch(
                request.host, request.port,
                request.expectedFingerprint!!, request.fingerprint, request.keyType,
              )
            } else {
              hostKeyCallback.onUnknownHost(
                request.host, request.port, request.fingerprint, request.keyType,
              )
            }
            request.decision.complete(accepted)

            // Persist the host key if the user accepted and it needs storing.
            // Done here (suspend context) instead of inside verify() to avoid runBlocking.
            if (accepted && request.needsStore) {
              knownHostsStore.store(
                request.host, request.port,
                StoredHostKey(request.fingerprint, request.keyType),
              )
            }
          } catch (e: Exception) {
            // Always complete the deferred so verify() unblocks.
            request.decision.complete(false)
            if (e !is CancellationException) throw e
          }
        }
      } catch (_: ClosedReceiveChannelException) {
        // Channel closed during normal cleanup — expected.
      }
    }

    var ownedClient: SSHClient? = null
    var retried = false
    try {
      // Retry loop: if a cached connection is stale, evict and reconnect once
      while (true) {
        try {
          val client = acquireClient(profile, hostKeyChannel).also {
            if (!reuseConnections) ownedClient = it
          }

          // Open exec channel
          val session: Session = withContext(Dispatchers.IO) { client.startSession() }
          val cmd = withContext(Dispatchers.IO) { session.exec(command) }

          // Read stdout and stderr concurrently
          val stdoutJob = launch(Dispatchers.IO) {
            readStream(cmd.inputStream) { bytes ->
              trySend(ExecEvent.StdoutChunk(bytes))
            }
          }

          val stderrJob = launch(Dispatchers.IO) {
            readStream(cmd.errorStream) { bytes ->
              trySend(ExecEvent.StderrChunk(bytes))
            }
          }

          // Wait for both streams to finish
          stdoutJob.join()
          stderrJob.join()

          // Wait for exit status with a timeout
          withContext(Dispatchers.IO) {
            cmd.join(30, TimeUnit.SECONDS)
          }

          val exitCode = cmd.exitStatus
          trySend(ExecEvent.Exit(exitCode))

          // Cleanup — only disconnect if we own the client (not pooled)
          withContext(Dispatchers.IO) {
            session.close()
            ownedClient?.disconnect()
          }

          break // success, exit retry loop
        } catch (e: Exception) {
          // Only retry once for connection-level errors on cached connections
          val isRetryable = reuseConnections && !retried &&
            e !is SshHostKeyRejectedException &&
            e.message?.contains("Auth") != true
          if (isRetryable) {
            retried = true
            evictCachedClient()
            ownedClient = null
            continue // retry with fresh connection
          }
          throw e // re-throw for outer handler
        }
      }

      channel.close()
    } catch (e: CancellationException) {
      trySend(ExecEvent.Canceled)
      channel.close()
    } catch (e: SshHostKeyRejectedException) {
      trySend(ExecEvent.Error("host_key_rejected", e.message ?: "Host key rejected"))
      channel.close()
    } catch (e: Exception) {
      if (reuseConnections) {
        evictCachedClient()
      }
      ownedClient?.let {
        try { it.disconnect() } catch (_: Exception) {}
      }
      val category = when {
        e is java.net.ConnectException -> "connection_refused"
        e is java.net.SocketTimeoutException -> "timeout"
        e is java.net.UnknownHostException -> "unknown_host"
        e.message?.contains("Auth") == true -> "auth_failed"
        else -> "ssh_error"
      }
      trySend(ExecEvent.Error(category, e.message ?: e.javaClass.simpleName))
      channel.close()
    } finally {
      // Shut down the host-key channel and wait for the consumer to drain.
      hostKeyChannel.close()
      hostKeyJob.cancel()
      hostKeyJob.join()
    }

    awaitClose { /* cleanup already done above */ }
  }

  /**
   * Build a [HostKeyVerifier] that uses [CompletableDeferred] to bridge sshj's
   * synchronous [HostKeyVerifier.verify] callback to the async host-key decision
   * flow running in [callbackFlow].
   *
   * Known-host data is pre-loaded (see [createClient]) so that no suspend store
   * reads happen inside the synchronous callback.  Store writes are deferred to
   * the callbackFlow consumer via [HostKeyDecisionRequest.needsStore].
   *
   * The only remaining blocking call is [runBlocking] on
   * [CompletableDeferred.await] — this is unavoidable because sshj's verify()
   * is synchronous, but it is a pure signal-wait with no coroutine dispatching,
   * so it cannot deadlock.
   */
  private fun buildVerifier(
    profile: SshProfile,
    knownHostFingerprint: StoredHostKey?,
    hostKeyChannel: Channel<HostKeyDecisionRequest>,
  ): HostKeyVerifier {
    return object : HostKeyVerifier {
      override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
        if (key == null) return false
        val h = hostname ?: profile.host
        val p = port.takeIf { it > 0 } ?: profile.port
        val fp = fingerprint(key)

        return when (profile.hostKeyPolicy) {
          HostKeyPolicy.AcceptAll -> true

          HostKeyPolicy.RejectUnknown -> {
            // Strictly check against pre-loaded stored key
            knownHostFingerprint != null && knownHostFingerprint.fingerprint == fp
          }

          HostKeyPolicy.TrustFirstUse -> {
            when {
              knownHostFingerprint == null -> {
                // First connect: ask user via CompletableDeferred
                val deferred = CompletableDeferred<Boolean>()
                val request = HostKeyDecisionRequest(
                  host = h, port = p,
                  fingerprint = fp, keyType = key.algorithm,
                  isMismatch = false, expectedFingerprint = null,
                  needsStore = true,
                  decision = deferred,
                )
                if (!hostKeyChannel.trySend(request).isSuccess) return false
                runBlocking { deferred.await() }
              }
              knownHostFingerprint.fingerprint == fp -> true
              else -> {
                // Key mismatch: ask user whether to replace
                val deferred = CompletableDeferred<Boolean>()
                val request = HostKeyDecisionRequest(
                  host = h, port = p,
                  fingerprint = fp, keyType = key.algorithm,
                  isMismatch = true,
                  expectedFingerprint = knownHostFingerprint.fingerprint,
                  needsStore = true,
                  decision = deferred,
                )
                if (!hostKeyChannel.trySend(request).isSuccess) return false
                runBlocking { deferred.await() }
              }
            }
          }
        }
      }

      override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> {
        return mutableListOf()
      }
    }
  }

  private suspend fun acquireClient(
    profile: SshProfile,
    hostKeyChannel: Channel<HostKeyDecisionRequest>,
  ): SSHClient {
    if (reuseConnections) {
      val profileKey = "[${profile.host}]:${profile.port}:${profile.username}"
      val existing = cachedClient
      if (existing != null && existing.isConnected && cachedProfileKey == profileKey) {
        return existing
      }
      // Evict stale cache
      evictCachedClient()
      val client = createClient(profile, hostKeyChannel)
      cachedClient = client
      cachedProfileKey = profileKey
      return client
    }
    return createClient(profile, hostKeyChannel)
  }

  private fun evictCachedClient() {
    cachedClient?.let { c ->
      try { c.disconnect() } catch (_: Exception) {}
    }
    cachedClient = null
    cachedProfileKey = null
  }

  /** Release any cached connection. Call when this runner is no longer needed. */
  override fun close() {
    evictCachedClient()
  }

  /**
   * Create and connect an [SSHClient].
   *
   * Known-host fingerprint is pre-loaded here (suspend context) so that the
   * synchronous [HostKeyVerifier.verify] callback never needs to call
   * [runBlocking] for store reads.
   */
  private suspend fun createClient(
    profile: SshProfile,
    hostKeyChannel: Channel<HostKeyDecisionRequest>,
  ): SSHClient {
    val client = SSHClient()
    client.loadKnownHosts()

    // Pre-load known host fingerprint on IO dispatcher.
    // This was previously done via runBlocking inside verify().
    val knownHostFingerprint = withContext(Dispatchers.IO) {
      knownHostsStore.getFingerprint(profile.host, profile.port)
    }

    client.addHostKeyVerifier(buildVerifier(profile, knownHostFingerprint, hostKeyChannel))
    withContext(Dispatchers.IO) { client.connect(profile.host, profile.port) }
    client.connection.keepAlive.keepAliveInterval = profile.keepaliveIntervalSec
    withContext(Dispatchers.IO) { authenticate(client, profile) }
    return client
  }

  private fun fingerprint(key: PublicKey): String {
    return SecurityUtils.getFingerprint(key)
  }

  /**
   * Authenticate the SSH session.
   *
   * Passphrase is pre-fetched here (suspend context) and injected into a
   * simple [PasswordFinder], replacing the old approach of calling
   * [runBlocking] inside the synchronous [PasswordFinder.reqPassword] callback.
   */
  private suspend fun authenticate(client: SSHClient, profile: SshProfile) {
    when (profile.authType) {
      AuthType.PublicKey -> {
        val keyContent = keyContentProvider?.getKeyContent(profile)
        if (keyContent != null) {
          // Pre-fetch passphrase — previously done via runBlocking inside reqPassword()
          val passphrase = passphraseProvider?.getPassphrase(profile)
          val pwFinder = passphrase?.takeIf { it.isNotEmpty() }?.let { pw ->
            object : PasswordFinder {
              override fun reqPassword(
                resource: net.schmizz.sshj.userauth.password.Resource<*>?,
              ): CharArray = pw.toCharArray()

              override fun shouldRetry(
                resource: net.schmizz.sshj.userauth.password.Resource<*>?,
              ): Boolean = false
            }
          }
          val keyFile = loadKeyProvider(keyContent, pwFinder)
          client.authPublickey(profile.username, listOf(keyFile))
        } else {
          // No key file selected — Android has no ~/.ssh/, so fall back to password
          val password = passwordProvider?.getPassword(profile)
            ?: throw IllegalStateException(
              "No private key selected. Please select a key file in profile settings " +
                "or switch to password authentication.",
            )
          client.authPassword(profile.username, password)
        }
      }
      AuthType.Password -> {
        val password = passwordProvider?.getPassword(profile)
          ?: throw IllegalStateException("Password required but no provider configured")
        client.authPassword(profile.username, password)
      }
    }
  }

  private fun loadKeyProvider(keyContent: String, pwFinder: PasswordFinder?): KeyProvider {
    return if (detectPrivateKeyFormat(keyContent) == PrivateKeyFormat.PuTTY) {
      val keyFile = PuTTYKeyFile()
      keyFile.init(StringReader(keyContent), null, pwFinder)
      keyFile
    } else {
      val keyFile = OpenSSHKeyFile()
      keyFile.init(StringReader(keyContent), null, pwFinder)
      keyFile
    }
  }

  private suspend fun readStream(
    input: InputStream,
    onChunk: (ByteArray) -> Unit,
  ) {
    val buf = ByteArray(4096)
    try {
      while (true) {
        val n = input.read(buf)
        if (n == -1) break
        onChunk(buf.copyOf(n))
      }
    } catch (_: Exception) {
      // Stream closed, normal during disconnect
    }
  }
}

/** Exception thrown when host key verification fails and user rejects. */
class SshHostKeyRejectedException(message: String) : Exception(message)

/**
 * Provides passwords for SSH authentication.
 * UI layer implements this (typically prompting the user).
 */
fun interface PasswordProvider {
  suspend fun getPassword(profile: SshProfile): String
}

/**
 * Provides private key content for SSH authentication.
 * Returns the PEM-encoded key content as a String, or null if unavailable.
 */
fun interface KeyContentProvider {
  suspend fun getKeyContent(profile: SshProfile): String?
}

/**
 * Provides passphrase for encrypted private keys.
 */
fun interface PassphraseProvider {
  suspend fun getPassphrase(profile: SshProfile): String?
}
