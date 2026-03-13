package io.rikka.agent.storage.db

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.SshProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

  @Test
  fun `entity to model maps all fields`() {
    val entity = SshProfileEntity(
      id = "p1",
      name = "Prod",
      host = "example.com",
      port = 2222,
      username = "root",
      authType = "Password",
      keyRef = null,
      hostKeyPolicy = "RejectUnknown",
      keepaliveIntervalSec = 30,
      codexMode = true,
      codexWorkDir = "/srv/app",
      codexApiKey = "sk-test",
    )

    val model = entity.toModel()

    assertEquals("p1", model.id)
    assertEquals("Prod", model.name)
    assertEquals("example.com", model.host)
    assertEquals(2222, model.port)
    assertEquals("root", model.username)
    assertEquals(AuthType.Password, model.authType)
    assertNull(model.keyRef)
    assertEquals(HostKeyPolicy.RejectUnknown, model.hostKeyPolicy)
    assertEquals(30, model.keepaliveIntervalSec)
    assertEquals(true, model.codexMode)
    assertEquals("/srv/app", model.codexWorkDir)
    assertEquals("sk-test", model.codexApiKey)
  }

  @Test
  fun `model to entity maps enum names and codex fields`() {
    val model = SshProfile(
      id = "p2",
      name = "Dev",
      host = "127.0.0.1",
      port = 22,
      username = "ding",
      authType = AuthType.PublicKey,
      keyRef = "internal-key://abc",
      hostKeyPolicy = HostKeyPolicy.TrustFirstUse,
      keepaliveIntervalSec = 60,
      codexMode = false,
      codexWorkDir = null,
      codexApiKey = null,
    )

    val entity = model.toEntity()

    assertEquals("p2", entity.id)
    assertEquals("Dev", entity.name)
    assertEquals("127.0.0.1", entity.host)
    assertEquals(22, entity.port)
    assertEquals("ding", entity.username)
    assertEquals("PublicKey", entity.authType)
    assertEquals("internal-key://abc", entity.keyRef)
    assertEquals("TrustFirstUse", entity.hostKeyPolicy)
    assertEquals(60, entity.keepaliveIntervalSec)
    assertEquals(false, entity.codexMode)
    assertNull(entity.codexWorkDir)
    assertNull(entity.codexApiKey)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `entity to model throws on invalid enum values`() {
    val entity = SshProfileEntity(
      id = "p3",
      name = "Broken",
      host = "bad",
      port = 22,
      username = "u",
      authType = "UnknownAuth",
      keyRef = null,
      hostKeyPolicy = "TrustFirstUse",
      keepaliveIntervalSec = 60,
      codexMode = false,
      codexWorkDir = null,
      codexApiKey = null,
    )

    entity.toModel()
  }
}
