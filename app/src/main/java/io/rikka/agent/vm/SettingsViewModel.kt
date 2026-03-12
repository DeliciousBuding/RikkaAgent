package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.storage.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val prefs: AppPreferences,
) : ViewModel() {

  val theme: StateFlow<String> = prefs.theme
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

  val defaultShell: StateFlow<String> = prefs.defaultShell
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "/bin/bash")

  fun setTheme(value: String) {
    viewModelScope.launch { prefs.setTheme(value) }
  }

  fun setDefaultShell(value: String) {
    viewModelScope.launch { prefs.setDefaultShell(value) }
  }
}
