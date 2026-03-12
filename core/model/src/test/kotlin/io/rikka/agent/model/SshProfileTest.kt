package io.rikka.agent.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SshProfileTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `SshProfile default port is 22`() {
    val profile = SshProfile(
      id = "p1",
      name = "Dev Server",
      host = "example.com",
      username = "admin"
    )
    assertEquals(22, profile.port)
  }

  @Test
  fun `SshProfile default authType is PublicKey`() {
    val profile = SshProfile(id = "p1", name = "t", host = "h", username = "u")
    assertEquals(AuthType.PublicKey, profile.authType)
  }

  @Test
  fun `SshProfile default hostKeyPolicy is TrustFirstUse`() {
    val profile = SshProfile(id = "p1", name = "t", host = "h", username = "u")
    assertEquals(HostKeyPolicy.TrustFirstUse, profile.hostKeyPolicy)
  }

  @Test
  fun `SshProfile default keepalive is 60`() {
    val profile = SshProfile(id = "p1", name = "t", host = "h", username = "u")
    assertEquals(60, profile.keepaliveIntervalSec)
  }

  @Test
  fun `SshProfile custom port`() {
    val profile = SshProfile(
      id = "p2",
      name = "Custom",
      host = "example.com",
      port = 2222,
      username = "root"
    )
    assertEquals(2222, profile.port)
  }

  @Test
  fun `SshProfile serialization round-trip with all fields`() {
    val profile = SshProfile(
      id = "p3",
      name = "Production",
      host = "prod.example.com",
      port = 443,
      username = "deploy",
      authType = AuthType.Password,
      keyRef = "key-ref-123",
      hostKeyPolicy = HostKeyPolicy.RejectUnknown,
      keepaliveIntervalSec = 30
    )
    val encoded = json.encodeToString(SshProfile.serializer(), profile)
    val decoded = json.decodeFromString(SshProfile.serializer(), encoded)
    assertEquals(profile, decoded)
  }

  @Test
  fun `SshProfile JSON missing optional fields uses defaults`() {
    val jsonStr = """{"id":"p4","name":"Minimal","host":"h","username":"u"}"""
    val decoded = json.decodeFromString(SshProfile.serializer(), jsonStr)
    assertEquals(22, decoded.port)
    assertEquals(AuthType.PublicKey, decoded.authType)
    assertEquals(null, decoded.keyRef)
    assertEquals(HostKeyPolicy.TrustFirstUse, decoded.hostKeyPolicy)
    assertEquals(60, decoded.keepaliveIntervalSec)
  }

  @Test
  fun `AuthType enum values`() {
    assertEquals(2, AuthType.entries.size)
    assertEquals(AuthType.PublicKey, AuthType.valueOf("PublicKey"))
    assertEquals(AuthType.Password, AuthType.valueOf("Password"))
  }

  @Test
  fun `HostKeyPolicy enum values`() {
    assertEquals(3, HostKeyPolicy.entries.size)
    assertEquals(HostKeyPolicy.TrustFirstUse, HostKeyPolicy.valueOf("TrustFirstUse"))
    assertEquals(HostKeyPolicy.RejectUnknown, HostKeyPolicy.valueOf("RejectUnknown"))
    assertEquals(HostKeyPolicy.AcceptAll, HostKeyPolicy.valueOf("AcceptAll"))
  }
}
