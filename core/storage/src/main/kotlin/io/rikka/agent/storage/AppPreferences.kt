package io.rikka.agent.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed user preferences.
 * Keeps simple key-value pairs; structured data lives in Room.
 */
class AppPreferences(private val context: Context) {

  companion object Keys {
    val THEME = stringPreferencesKey("theme")
    val DEFAULT_SHELL = stringPreferencesKey("default_shell")
    val LAST_PROFILE_ID = stringPreferencesKey("last_profile_id")
  }

  val theme: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[THEME] ?: "system"
  }

  val defaultShell: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[DEFAULT_SHELL] ?: "/bin/bash"
  }

  val lastProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
    prefs[LAST_PROFILE_ID]
  }

  suspend fun setTheme(value: String) {
    context.dataStore.edit { prefs -> prefs[THEME] = value }
  }

  suspend fun setDefaultShell(value: String) {
    context.dataStore.edit { prefs -> prefs[DEFAULT_SHELL] = value }
  }

  suspend fun setLastProfileId(value: String) {
    context.dataStore.edit { prefs -> prefs[LAST_PROFILE_ID] = value }
  }
}
