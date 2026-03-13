package io.rikka.agent.vm

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.rikka.agent.model.AuthType
import io.rikka.agent.model.SshProfile
import io.rikka.agent.storage.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileEditorViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private lateinit var app: Application
  private lateinit var store: FakeProfileStore

  @Before
  fun setUp() {
    stopKoin()
    Dispatchers.setMain(dispatcher)
    app = ApplicationProvider.getApplicationContext()
    store = FakeProfileStore()
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
  }

  @Test
  fun `save creates trimmed profile with generated name and codex fields`() = runBlocking {
    val viewModel = ProfileEditorViewModel(
      profileId = null,
      store = store,
      app = app,
    )

    viewModel.updateForm(
      ProfileForm(
        name = "",
        host = "  ops.example.com  ",
        port = "2222",
        username = "  deploy  ",
        authType = AuthType.Password,
        keyRef = "ignored-for-password",
        codexMode = true,
        codexWorkDir = "  /srv/app  ",
        codexApiKey = "  sk-test  ",
      )
    )

    viewModel.save()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.saved.value)

    val stored = store.listProfiles().single()
    assertNotNull(stored.id)
    assertEquals("deploy@ops.example.com:2222", stored.name)
    assertEquals("ops.example.com", stored.host)
    assertEquals(2222, stored.port)
    assertEquals("deploy", stored.username)
    assertEquals(AuthType.Password, stored.authType)
    assertEquals("ignored-for-password", stored.keyRef)
    assertTrue(stored.codexMode)
    assertEquals("/srv/app", stored.codexWorkDir)
    assertEquals("sk-test", stored.codexApiKey)
  }

  @Test
  fun `save ignores blank host or username`() = runBlocking {
    val viewModel = ProfileEditorViewModel(
      profileId = null,
      store = store,
      app = app,
    )

    viewModel.updateForm(
      ProfileForm(
        host = "   ",
        username = "",
      )
    )

    viewModel.save()
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.saved.value)
    assertTrue(store.listProfiles().isEmpty())
  }

  @Test
  fun `init loads existing profile into form`() = runBlocking {
    val existing = SshProfile(
      id = "profile-1",
      name = "Prod",
      host = "prod.example.com",
      port = 2200,
      username = "root",
      authType = AuthType.Password,
      keyRef = "content://keys/prod",
      codexMode = true,
      codexWorkDir = "/workspace/app",
      codexApiKey = "sk-live",
    )
    store.upsert(existing)

    val viewModel = ProfileEditorViewModel(
      profileId = existing.id,
      store = store,
      app = app,
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(
      ProfileForm(
        name = "Prod",
        host = "prod.example.com",
        port = "2200",
        username = "root",
        authType = AuthType.Password,
        keyRef = "content://keys/prod",
        codexMode = true,
        codexWorkDir = "/workspace/app",
        codexApiKey = "sk-live",
      ),
      viewModel.form.value,
    )
  }

  @Test
  fun `save clears blank optional codex fields when editing existing profile`() = runBlocking {
    val existing = SshProfile(
      id = "profile-2",
      name = "Stage",
      host = "stage.example.com",
      port = 22,
      username = "ubuntu",
      authType = AuthType.PublicKey,
      keyRef = "content://keys/stage",
      codexMode = true,
      codexWorkDir = "/srv/stage",
      codexApiKey = "sk-old",
    )
    store.upsert(existing)

    val viewModel = ProfileEditorViewModel(
      profileId = existing.id,
      store = store,
      app = app,
    )
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.updateForm(
      viewModel.form.value.copy(
        codexMode = false,
        codexWorkDir = "   ",
        codexApiKey = " ",
      )
    )

    viewModel.save()
    dispatcher.scheduler.advanceUntilIdle()

    val stored = store.getById(existing.id)
    assertNotNull(stored)
    assertFalse(stored!!.codexMode)
    assertNull(stored.codexWorkDir)
    assertNull(stored.codexApiKey)
  }

  private class FakeProfileStore : ProfileStore {
    private val profiles = linkedMapOf<String, SshProfile>()

    override suspend fun listProfiles(): List<SshProfile> = profiles.values.toList()

    override suspend fun getById(id: String): SshProfile? = profiles[id]

    override suspend fun upsert(profile: SshProfile) {
      profiles[profile.id] = profile
    }

    override suspend fun delete(profileId: String) {
      profiles.remove(profileId)
    }
  }
}
