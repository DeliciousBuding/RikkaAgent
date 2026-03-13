package io.rikka.agent.ssh

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.model.SshProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContentUriKeyContentProviderTest {

  private lateinit var app: Application
  private lateinit var keyDir: File
  private lateinit var provider: ContentUriKeyContentProvider

  @Before
  fun setUp() {
    stopKoin()
    app = ApplicationProvider.getApplicationContext()
    keyDir = File(app.cacheDir, "test-keys").also {
      it.deleteRecursively()
      it.mkdirs()
    }
    provider = ContentUriKeyContentProvider(
      context = app,
      internalKeyStore = PlaintextInternalKeyStore(keyDir),
    )
  }

  @After
  fun tearDown() {
    stopKoin()
    keyDir.deleteRecursively()
  }

  @Test
  fun `savePastedKey persists encrypted content for internal key refs`() = runBlocking {
    val privateKey = """
      -----BEGIN OPENSSH PRIVATE KEY-----
      test-private-key
      -----END OPENSSH PRIVATE KEY-----
    """.trimIndent()

    val keyRef = provider.savePastedKey(privateKey)
    val profile = SshProfile(
      id = "profile-1",
      name = "Test",
      host = "example.test",
      username = "root",
      keyRef = keyRef,
    )

    val loaded = provider.getKeyContent(profile)

    assertTrue(keyRef.startsWith(ContentUriKeyContentProvider.INTERNAL_KEY_SCHEME))
    assertEquals(privateKey, loaded)
  }

  @Test
  fun `deleteKey removes internal key file and future reads return null`() = runBlocking {
    val keyRef = provider.savePastedKey("secret")
    val profile = SshProfile(
      id = "profile-2",
      name = "Test",
      host = "example.test",
      username = "root",
      keyRef = keyRef,
    )

    provider.deleteKey(keyRef)

    assertNull(provider.getKeyContent(profile))
  }

  private class PlaintextInternalKeyStore(
    private val dir: File,
  ) : ContentUriKeyContentProvider.InternalKeyStore {

    override fun read(keyId: String): String? {
      val file = File(dir, keyId)
      return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    override fun write(keyId: String, content: String) {
      File(dir, keyId).writeText(content, Charsets.UTF_8)
    }

    override fun delete(keyId: String) {
      File(dir, keyId).delete()
    }
  }
}
