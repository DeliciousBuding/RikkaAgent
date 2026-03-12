package io.rikka.agent.ssh

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.common.IOUtils
import java.io.InputStream
import java.io.StringReader
import java.security.PublicKey
import java.util.concurrent.TimeUnit

/**
 * Real SSH exec runner backed by sshj.
 *
 * Key design:
 * - When [reuseConnections] is true, caches the SSH client across commands.
 * - stdout/stderr read concurrently on IO dispatcher.
 * - Host key verification delegates to [HostKeyCallback] + [KnownHostsStore].
 * - Call [close] to release pooled connections when done.
 */
class SshjExecRunner(
  private val knownHostsStore: KnownHostsStore,
  private val hostKeyCallback: HostKeyCallback,
  private val passwordProvider: PasswordProvider? = null,
  private val keyContentProvider: KeyContentProvider? = null,
  private val passphraseProvider: PassphraseProvider? = null,
  private val reuseConnections: Boolean = false,
) : SshExecRunner {

  @Volatile private var cachedClient: SSHClient? = null
  @Volatile private var cachedProfileKey: String? = null

  override fun run(profile: SshProfile, command: String): Flow<ExecEvent> = callbackFlow {
    var ownedClient: SSHClient? = null
    var retried = false
    try {
      // Retry loop: if a cached connection is stale, evict and reconnect once
      while (true) {
        try {
          val client = acquireClient(profile).also {
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
    }

    awaitClose { /* cleanup already done above */ }
  }

  private fun buildVerifier(profile: SshProfile): HostKeyVerifier {
    return object : HostKeyVerifier {
      override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
        if (key == null) return false
        val h = hostname ?: profile.host
        val p = port.takeIf { it > 0 } ?: profile.port

        return when (profile.hostKeyPolicy) {
          HostKeyPolicy.AcceptAll -> true
          HostKeyPolicy.RejectUnknown -> {
            // Strictly check against stored keys
            val stored = kotlinx.coroutines.runBlocking {
              knownHostsStore.getFingerprint(h, p)
            }
            stored != null && stored.fingerprint == fingerprint(key)
          }
          HostKeyPolicy.TrustFirstUse -> {
            val fp = fingerprint(key)
            val keyType = key.algorithm
            val stored = kotlinx.coroutines.runBlocking {
              knownHostsStore.getFingerprint(h, p)
            }

            when {
              stored == null -> {
                // First connect: ask user
                val accepted = kotlinx.coroutines.runBlocking {
                  hostKeyCallback.onUnknownHost(h, p, fp, keyType)
                }
                if (accepted) {
                  kotlinx.coroutines.runBlocking {
                    knownHostsStore.store(h, p, StoredHostKey(fp, keyType))
                  }
                  true
                } else {
                  false
                }
              }
              stored.fingerprint == fp -> true
              else -> {
                // Mismatch!
                val replace = kotlinx.coroutines.runBlocking {
                  hostKeyCallback.onHostKeyMismatch(h, p, stored.fingerprint, fp, keyType)
                }
                if (replace) {
                  kotlinx.coroutines.runBlocking {
                    knownHostsStore.store(h, p, StoredHostKey(fp, keyType))
                  }
                  true
                } else {
                  false
                }
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

  private suspend fun acquireClient(profile: SshProfile): SSHClient {
    if (reuseConnections) {
      val profileKey = "[${profile.host}]:${profile.port}:${profile.username}"
      val existing = cachedClient
      if (existing != null && existing.isConnected && cachedProfileKey == profileKey) {
        return existing
      }
      // Evict stale cache
      evictCachedClient()
      val client = createClient(profile)
      cachedClient = client
      cachedProfileKey = profileKey
      return client
    }
    return createClient(profile)
  }

  private fun evictCachedClient() {
    cachedClient?.let { c ->
      try { c.disconnect() } catch (_: Exception) {}
    }
    cachedClient = null
    cachedProfileKey = null
  }

  /** Release any cached connection. Call when this runner is no longer needed. */
  fun close() {
    evictCachedClient()
  }

  private suspend fun createClient(profile: SshProfile): SSHClient {
    val client = SSHClient()
    client.loadKnownHosts()
    client.addHostKeyVerifier(buildVerifier(profile))
    withContext(Dispatchers.IO) { client.connect(profile.host, profile.port) }
    client.connection.keepAlive.keepAliveInterval = profile.keepaliveIntervalSec
    withContext(Dispatchers.IO) { authenticate(client, profile) }
    return client
  }

  private fun fingerprint(key: PublicKey): String {
    return SecurityUtils.getFingerprint(key)
  }

  private suspend fun authenticate(client: SSHClient, profile: SshProfile) {
    when (profile.authType) {
      AuthType.PublicKey -> {
        val keyContent = keyContentProvider?.getKeyContent(profile)
        if (keyContent != null) {
          val pwFinder = buildPassphraseFinder(profile)
          val keyFile = OpenSSHKeyFile()
          keyFile.init(StringReader(keyContent), null, pwFinder)
          client.authPublickey(profile.username, listOf(keyFile))
        } else {
          // No key file selected — Android has no ~/.ssh/, so fall back to password
          val password = passwordProvider?.getPassword(profile)
            ?: throw IllegalStateException(
              "No private key selected. Please select a key file in profile settings " +
                "or switch to password authentication."
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

  private suspend fun buildPassphraseFinder(profile: SshProfile): PasswordFinder? {
    val provider = passphraseProvider ?: return null
    return object : PasswordFinder {
      private var passphrase: CharArray? = null

      override fun reqPassword(resource: net.schmizz.sshj.userauth.password.Resource<*>?): CharArray {
        if (passphrase == null) {
          passphrase = kotlinx.coroutines.runBlocking {
            provider.getPassphrase(profile)
          }?.toCharArray() ?: charArrayOf()
        }
        return passphrase ?: charArrayOf()
      }

      override fun shouldRetry(resource: net.schmizz.sshj.userauth.password.Resource<*>?): Boolean {
        return false
      }
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
