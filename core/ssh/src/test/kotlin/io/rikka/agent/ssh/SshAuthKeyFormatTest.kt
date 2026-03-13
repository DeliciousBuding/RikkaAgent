package io.rikka.agent.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class SshAuthKeyFormatTest {

  @Test
  fun `detect PuTTY key format`() {
    val content = """
      PuTTY-User-Key-File-3: ssh-ed25519
      Encryption: none
      Comment: imported-openssh-key
    """.trimIndent()

    assertEquals(PrivateKeyFormat.PuTTY, detectPrivateKeyFormat(content))
  }

  @Test
  fun `detect OpenSSH key format`() {
    val content = """
      -----BEGIN OPENSSH PRIVATE KEY-----
      AAAAB3NzaC1rZXktdjEAAAAA
      -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    assertEquals(PrivateKeyFormat.OpenSSH, detectPrivateKeyFormat(content))
  }
}
