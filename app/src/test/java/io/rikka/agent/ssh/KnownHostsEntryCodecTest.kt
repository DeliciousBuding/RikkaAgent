package io.rikka.agent.ssh

import io.rikka.agent.ssh.StoredHostKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownHostsEntryCodecTest {

  @Test
  fun `encode then decode roundtrip`() {
    val key = StoredHostKey(
      fingerprint = "SHA256:abc",
      keyType = "ssh-ed25519",
      addedAtMs = 1700000000000L,
    )

    val decoded = KnownHostsEntryCodec.decode(KnownHostsEntryCodec.encode(key))

    assertEquals(key, decoded)
  }

  @Test
  fun `decode returns null for invalid json`() {
    assertNull(KnownHostsEntryCodec.decode("not-json"))
  }

  @Test
  fun `decode returns null for missing fields`() {
    assertNull(KnownHostsEntryCodec.decode("{\"fingerprint\":\"SHA256:abc\"}"))
  }
}
