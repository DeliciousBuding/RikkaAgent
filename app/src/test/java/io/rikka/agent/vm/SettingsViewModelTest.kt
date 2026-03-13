package io.rikka.agent.vm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.rikka.agent.storage.AppPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private lateinit var prefs: AppPreferences
  private lateinit var dataStoreFile: File
  private lateinit var dataStoreScope: CoroutineScope

  @Before
  fun setUp() = runTest(dispatcher) {
    Dispatchers.setMain(dispatcher)
    stopKoin()
    dataStoreFile = File.createTempFile("settings-view-model", ".preferences_pb")
    dataStoreScope = CoroutineScope(SupervisorJob() + dispatcher)
    prefs = AppPreferences(createTestDataStore(dataStoreScope, dataStoreFile))
    prefs.setTheme("system")
    prefs.setDefaultShell("/bin/bash")
    prefs.setEnableMermaid(false)
  }

  @After
  fun tearDown() {
    stopKoin()
    Dispatchers.resetMain()
    dataStoreScope.cancel()
    dataStoreFile.delete()
  }

  @Test
  fun `exposes persisted defaults`() = runTest(dispatcher) {
    val viewModel = SettingsViewModel(prefs)
    val collector = launch {
      launch { viewModel.theme.collect { } }
      launch { viewModel.defaultShell.collect { } }
      launch { viewModel.enableMermaid.collect { } }
    }
    advanceUntilIdle()

    try {
      assertEquals("system", viewModel.theme.value)
      assertEquals("/bin/bash", viewModel.defaultShell.value)
      assertFalse(viewModel.enableMermaid.value)
    } finally {
      collector.cancel()
    }
  }

  @Test
  fun `updates theme shell and mermaid preference`() = runTest(dispatcher) {
    val viewModel = SettingsViewModel(prefs)
    val collector = launch {
      launch { viewModel.theme.collect { } }
      launch { viewModel.defaultShell.collect { } }
      launch { viewModel.enableMermaid.collect { } }
    }
    advanceUntilIdle()

    try {
      viewModel.setTheme("dark")
      viewModel.setDefaultShell("/bin/zsh")
      viewModel.setEnableMermaid(true)
      advanceUntilIdle()

      assertEquals("dark", prefs.theme.first())
      assertEquals("/bin/zsh", prefs.defaultShell.first())
      assertEquals(true, prefs.enableMermaid.first())
    } finally {
      collector.cancel()
    }
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
