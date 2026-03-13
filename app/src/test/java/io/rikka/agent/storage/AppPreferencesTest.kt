package io.rikka.agent.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {

  private lateinit var prefs: AppPreferences
  private lateinit var dataStoreFile: File
  private lateinit var dataStoreScope: CoroutineScope

  @Before
  fun setUp() = runTest {
    stopKoin()
    dataStoreFile = File.createTempFile("app-preferences", ".preferences_pb")
    dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    prefs = AppPreferences(createTestDataStore(dataStoreScope, dataStoreFile))
    prefs.setTheme("system")
    prefs.setDefaultShell("/bin/bash")
    prefs.setEnableMermaid(false)
  }

  @After
  fun tearDown() {
    dataStoreScope.cancel()
    dataStoreFile.delete()
    stopKoin()
  }

  @Test
  fun `returns safe defaults for settings`() = runBlocking {
    assertEquals("system", prefs.theme.first())
    assertEquals("/bin/bash", prefs.defaultShell.first())
    assertFalse(prefs.enableMermaid.first())
  }

  @Test
  fun `persists mermaid preference updates`() = runBlocking {
    prefs.setEnableMermaid(true)

    assertEquals(true, prefs.enableMermaid.first())
  }
}

private fun createTestDataStore(
  scope: CoroutineScope,
  file: File,
): DataStore<Preferences> {
  return PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { file },
  )
}
