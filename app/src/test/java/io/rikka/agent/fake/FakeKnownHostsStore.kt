package io.rikka.agent.fake

import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.StoredHostKey

/**
 * In-memory [KnownHostsStore] with test hooks.
 *
 * Unlike the production [io.rikka.agent.ssh.InMemoryKnownHostsStore], this
 * variant exposes mutation hooks and a verification callback for assertions.
 */
class FakeKnownHostsStore : KnownHostsStore {

  private val store = mutableMapOf<String, StoredHostKey>()

  // ── Configurable hooks ─────────────────────────────────────────────────────

  /** Called on every [store] invocation. Useful for asserting host-key acceptance. */
  var onStore: ((host: String, port: Int, key: StoredHostKey) -> Unit)? = null

  /** When non-null, [store] throws this exception. */
  var throwOnStore: Throwable? = null

  /** When non-null, [getFingerprint] throws this exception. */
  var throwOnGet: Throwable? = null

  // ── KnownHostsStore ────────────────────────────────────────────────────────

  private fun key(host: String, port: Int) = "[$host]:$port"

  override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? {
    throwOnGet?.let { throw it }
    return store[key(host, port)]
  }

  override suspend fun store(host: String, port: Int, key: StoredHostKey) {
    throwOnStore?.let { throw it }
    store[key(host, port)] = key
    onStore?.invoke(host, port, key)
  }

  override suspend fun remove(host: String, port: Int) {
    store.remove(key(host, port))
  }

  override suspend fun getAll(): List<Pair<String, StoredHostKey>> =
    store.entries.map { it.key to it.value }

  // ── Test helpers ───────────────────────────────────────────────────────────

  /** Seed entries directly (bypasses hooks). */
  fun seed(host: String, port: Int, key: StoredHostKey) {
    store[key(host, port)] = key
  }

  /** Return the raw backing map snapshot. */
  fun snapshot(): Map<String, StoredHostKey> = store.toMap()

  /** Check whether a host:port entry exists. */
  fun contains(host: String, port: Int): Boolean = store.containsKey(key(host, port))

  /** Clear all data. */
  fun reset() {
    store.clear()
  }

  companion object {
    /** Create a store pre-populated with the given entries. */
    fun of(vararg entries: Triple<String, Int, StoredHostKey>): FakeKnownHostsStore =
      FakeKnownHostsStore().also { s ->
        entries.forEach { (host, port, key) -> s.seed(host, port, key) }
      }
  }
}
