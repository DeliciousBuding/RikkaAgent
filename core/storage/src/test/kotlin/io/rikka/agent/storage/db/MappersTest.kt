package io.rikka.agent.storage.db

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.ProfileGroup
import io.rikka.agent.model.SshProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
      group = "Production",
      tags = "web,nginx,prod",
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
    assertEquals(ProfileGroup.Production, model.group)
    assertEquals(listOf("web", "nginx", "prod"), model.tags)
  }

  @Test
  fun `entity to model defaults group to None for unknown values`() {
    val entity = SshProfileEntity(
      id = "p1",
      name = "Test",
      host = "localhost",
      port = 22,
      username = "root",
      authType = "PublicKey",
      keyRef = null,
      hostKeyPolicy = "TrustFirstUse",
      keepaliveIntervalSec = 60,
      group = "InvalidGroup",
      tags = "",
    )

    val model = entity.toModel()
    assertEquals(ProfileGroup.None, model.group)
  }

  @Test
  fun `entity to model handles empty tags`() {
    val entity = SshProfileEntity(
      id = "p1",
      name = "Test",
      host = "localhost",
      port = 22,
      username = "root",
      authType = "PublicKey",
      keyRef = null,
      hostKeyPolicy = "TrustFirstUse",
      keepaliveIntervalSec = 60,
      tags = "",
    )

    val model = entity.toModel()
    assertTrue(model.tags.isEmpty())
  }

  @Test
  fun `entity to model handles blank tags with spaces`() {
    val entity = SshProfileEntity(
      id = "p1",
      name = "Test",
      host = "localhost",
      port = 22,
      username = "root",
      authType = "PublicKey",
      keyRef = null,
      hostKeyPolicy = "TrustFirstUse",
      keepaliveIntervalSec = 60,
      tags = " , , ",
    )

    val model = entity.toModel()
    assertTrue(model.tags.isEmpty())
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
      group = ProfileGroup.Development,
      tags = listOf("local", "dev"),
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
    assertEquals("Development", entity.group)
    assertEquals("local,dev", entity.tags)
  }

  @Test
  fun `model to entity maps None group and empty tags`() {
    val model = SshProfile(
      id = "p3",
      name = "Test",
      host = "localhost",
      port = 22,
      username = "root",
      authType = AuthType.PublicKey,
      keyRef = null,
      hostKeyPolicy = HostKeyPolicy.TrustFirstUse,
      keepaliveIntervalSec = 60,
    )

    val entity = model.toEntity()

    assertEquals("None", entity.group)
    assertEquals("", entity.tags)
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

  @Test
  fun `round trip preserves all fields`() {
    val original = SshProfile(
      id = "p4",
      name = "Round Trip",
      host = "10.0.0.1",
      port = 2222,
      username = "admin",
      authType = AuthType.Password,
      keyRef = null,
      hostKeyPolicy = HostKeyPolicy.AcceptAll,
      keepaliveIntervalSec = 45,
      codexMode = true,
      codexWorkDir = "/opt/work",
      codexApiKey = "key-123",
      group = ProfileGroup.Testing,
      tags = listOf("staging", "qa"),
    )

    val entity = original.toEntity()
    val restored = entity.toModel()

    assertEquals(original, restored)
  }
}
