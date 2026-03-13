package io.rikka.agent.ssh

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreKnownHostsStoreTest {

  private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
  private lateinit var store: DataStoreKnownHostsStore

  @Before
  fun setUp() = runBlocking {
    stopKoin()
    store = DataStoreKnownHostsStore(context)
    clearStore()
  }

  @After
  fun tearDown() = runBlocking {
    clearStore()
    stopKoin()
  }

  @Test
  fun `store and read host key roundtrip`() = runBlocking {
    val key = StoredHostKey(
      fingerprint = "SHA256:abc123",
      keyType = "ssh-ed25519",
      addedAtMs = 1700000000000L,
    )

    store.store("example.test", 22, key)

    assertEquals(key, store.getFingerprint("example.test", 22))
    assertNull(store.getFingerprint("example.test", 2222))
  }

  @Test
  fun `remove only deletes matching host and port`() = runBlocking {
    store.store("example.test", 22, StoredHostKey("SHA256:22", "ssh-ed25519", 1L))
    store.store("example.test", 2222, StoredHostKey("SHA256:2222", "ssh-rsa", 2L))

    store.remove("example.test", 22)

    assertNull(store.getFingerprint("example.test", 22))
    assertEquals(
      "SHA256:2222",
      store.getFingerprint("example.test", 2222)?.fingerprint,
    )
  }

  @Test
  fun `getAll returns bracketed endpoint keys`() = runBlocking {
    store.store("host-a", 22, StoredHostKey("SHA256:a", "ssh-ed25519", 1L))
    store.store("host-b", 2200, StoredHostKey("SHA256:b", "ssh-rsa", 2L))

    val all = store.getAll().toMap()

    assertEquals("SHA256:a", all["[host-a]:22"]?.fingerprint)
    assertEquals("SHA256:b", all["[host-b]:2200"]?.fingerprint)
  }

  private suspend fun clearStore() {
    store.getAll().forEach { (hostKey, _) ->
      val host = hostKey.substringAfter("[").substringBefore("]:")
      val port = hostKey.substringAfter("]:").toInt()
      store.remove(host, port)
    }
  }
}
