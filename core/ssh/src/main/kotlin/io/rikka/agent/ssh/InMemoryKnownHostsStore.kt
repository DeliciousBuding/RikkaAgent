package io.rikka.agent.ssh

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Simple in-memory KnownHostsStore.
 * Suitable for v1; a DataStore-backed version will replace this for persistence.
 */
class InMemoryKnownHostsStore : KnownHostsStore {
  private val mutex = Mutex()
  private val store = mutableMapOf<String, StoredHostKey>()

  private fun key(host: String, port: Int) = "[$host]:$port"

  override suspend fun getFingerprint(host: String, port: Int): StoredHostKey? {
    return mutex.withLock { store[key(host, port)] }
  }

  override suspend fun store(host: String, port: Int, key: StoredHostKey) {
    mutex.withLock { store[key(host, port)] = key }
  }

  override suspend fun remove(host: String, port: Int) {
    mutex.withLock { store.remove(key(host, port)) }
  }

  override suspend fun getAll(): List<Pair<String, StoredHostKey>> {
    return mutex.withLock { store.entries.map { it.key to it.value } }
  }
}
