package io.rikka.agent.ssh

import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.SSHClient

/**
 * Maintains a cache of connected, authenticated [SSHClient] instances keyed by profile.
 * Re-creates the connection if it becomes stale or disconnected.
 */
class SshConnectionPool(
  private val clientFactory: suspend (SshProfile) -> SSHClient,
) {

  private data class Entry(val client: SSHClient, val connectedAtMs: Long)

  private val mutex = Mutex()
  private val pool = mutableMapOf<String, Entry>()

  /**
   * Gets a connected, authenticated client for the given profile.
   * Reuses an existing connection if it's still alive, otherwise creates a new one.
   */
  suspend fun acquire(profile: SshProfile): SSHClient = mutex.withLock {
    val key = cacheKey(profile)
    val existing = pool[key]

    if (existing != null && existing.client.isConnected) {
      // Verify the connection is still alive with a quick transport check
      try {
        existing.client.connection // access connection obj — will throw if broken
        return existing.client
      } catch (_: Exception) {
        // Connection is dead, remove and recreate
        safeClose(existing.client)
        pool.remove(key)
      }
    } else if (existing != null) {
      pool.remove(key)
    }

    val client = clientFactory(profile)
    pool[key] = Entry(client, System.currentTimeMillis())
    return client
  }

  /** Close and remove the connection for a specific profile. */
  suspend fun release(profile: SshProfile) = mutex.withLock {
    val key = cacheKey(profile)
    pool.remove(key)?.let { safeClose(it.client) }
  }

  /** Close all pooled connections. */
  suspend fun closeAll() = mutex.withLock {
    pool.values.forEach { safeClose(it.client) }
    pool.clear()
  }

  private fun cacheKey(profile: SshProfile): String =
    "[${profile.host}]:${profile.port}:${profile.username}"

  private fun safeClose(client: SSHClient) {
    try {
      client.disconnect()
    } catch (_: Exception) {
      // ignore cleanup errors
    }
  }
}
