package io.rikka.agent.ssh

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryKnownHostsStoreTest {

  @Test
  fun `store and read host key by host and port`() = runBlocking {
    val store = InMemoryKnownHostsStore()
    val key = StoredHostKey(fingerprint = "SHA256:abc", keyType = "ssh-ed25519", addedAtMs = 123L)

    store.store("example.com", 22, key)

    assertEquals(key, store.getFingerprint("example.com", 22))
    assertNull(store.getFingerprint("example.com", 2222))
  }

  @Test
  fun `remove deletes only target endpoint`() = runBlocking {
    val store = InMemoryKnownHostsStore()
    store.store("example.com", 22, StoredHostKey("SHA256:22", "ssh-rsa", 1L))
    store.store("example.com", 2222, StoredHostKey("SHA256:2222", "ssh-rsa", 2L))

    store.remove("example.com", 22)

    assertNull(store.getFingerprint("example.com", 22))
    assertEquals("SHA256:2222", store.getFingerprint("example.com", 2222)?.fingerprint)
  }

  @Test
  fun `getAll returns bracketed host and port keys`() = runBlocking {
    val store = InMemoryKnownHostsStore()
    store.store("host-a", 22, StoredHostKey("SHA256:a", "ssh-ed25519", 1L))
    store.store("host-b", 2222, StoredHostKey("SHA256:b", "ssh-rsa", 2L))

    val all = store.getAll().toMap()

    assertEquals("SHA256:a", all["[host-a]:22"]?.fingerprint)
    assertEquals("SHA256:b", all["[host-b]:2222"]?.fingerprint)
  }

  @Test
  fun `concurrent writes keep all entries`() = runBlocking {
    val store = InMemoryKnownHostsStore()

    (1..20).map { i ->
      async {
        store.store("host-$i", 22, StoredHostKey("SHA256:$i", "ssh-ed25519", i.toLong()))
      }
    }.awaitAll()

    assertEquals(20, store.getAll().size)
  }
}
