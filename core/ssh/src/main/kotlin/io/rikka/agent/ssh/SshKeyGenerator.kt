package io.rikka.agent.ssh

import java.security.KeyPairGenerator
import java.security.Security
import java.util.Base64

/**
 * Generates Ed25519 SSH key pairs.
 * Private key is in PKCS8 PEM format (compatible with sshj's OpenSSHKeyFile).
 * Public key is in standard OpenSSH authorized_keys format.
 */
object SshKeyGenerator {

  data class GeneratedKeyPair(
    val privateKeyPem: String,
    val publicKeyLine: String,
  )

  fun generateEd25519(comment: String = "rikka-agent"): GeneratedKeyPair {
    // Ensure BouncyCastle is available (sshj depends on it)
    if (Security.getProvider("BC") == null) {
      Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
    val keyPair = kpg.generateKeyPair()

    // Private key in PKCS8 PEM format
    val pkcs8Der = keyPair.private.encoded
    val pkcs8Base64 = Base64.getEncoder().encodeToString(pkcs8Der)
    val privateKeyPem = buildString {
      append("-----BEGIN PRIVATE KEY-----\n")
      pkcs8Base64.chunked(64).forEach { line -> append(line); append('\n') }
      append("-----END PRIVATE KEY-----\n")
    }

    // Public key in OpenSSH authorized_keys format
    // Manually build SSH wire format: type-string + key-data
    val pubKeyEncoded = keyPair.public.encoded
    // Extract raw 32-byte Ed25519 key from X.509/SubjectPublicKeyInfo DER
    // X.509 DER for Ed25519: 30 2a 30 05 06 03 2b 65 70 03 21 00 <32 bytes>
    val raw32 = pubKeyEncoded.takeLast(32).toByteArray()
    val typeBytes = "ssh-ed25519".toByteArray(Charsets.UTF_8)
    val wireFormat = java.io.ByteArrayOutputStream()
    wireFormat.write(intToBytes(typeBytes.size))
    wireFormat.write(typeBytes)
    wireFormat.write(intToBytes(raw32.size))
    wireFormat.write(raw32)
    val sshPubBase64 = Base64.getEncoder().encodeToString(wireFormat.toByteArray())
    val publicKeyLine = "ssh-ed25519 $sshPubBase64 $comment"

    return GeneratedKeyPair(privateKeyPem, publicKeyLine)
  }

  private fun intToBytes(value: Int): ByteArray = byteArrayOf(
    (value shr 24 and 0xFF).toByte(),
    (value shr 16 and 0xFF).toByte(),
    (value shr 8 and 0xFF).toByte(),
    (value and 0xFF).toByte(),
  )
}
