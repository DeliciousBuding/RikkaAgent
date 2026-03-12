package io.rikka.agent.ssh

/**
 * Callback for host key verification decisions.
 * UI layer implements this to show confirmation dialogs.
 */
interface HostKeyCallback {
  /** Called on first connect to an unknown host. Return true to trust. */
  suspend fun onUnknownHost(host: String, port: Int, fingerprint: String, keyType: String): Boolean

  /** Called when host key doesn't match stored key. Return true to replace. */
  suspend fun onHostKeyMismatch(
    host: String, port: Int,
    expectedFingerprint: String, actualFingerprint: String,
    keyType: String,
  ): Boolean
}

/**
 * Persistent store for known host keys.
 * Keyed by "host:port" to avoid mismatch bugs (per spec 32-ssh §2).
 */
interface KnownHostsStore {
  suspend fun getFingerprint(host: String, port: Int): StoredHostKey?
  suspend fun store(host: String, port: Int, key: StoredHostKey)
  suspend fun remove(host: String, port: Int)
  suspend fun getAll(): List<Pair<String, StoredHostKey>>
}

data class StoredHostKey(
  val fingerprint: String,
  val keyType: String,
  val addedAtMs: Long = System.currentTimeMillis(),
)
