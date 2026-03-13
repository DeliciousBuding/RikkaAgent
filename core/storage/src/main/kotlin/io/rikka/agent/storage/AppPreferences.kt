package io.rikka.agent.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
class AppPreferences(
  private val dataStore: DataStore<Preferences>,
) {

  constructor(context: Context) : this(context.dataStore)

  companion object Keys {
    val THEME = stringPreferencesKey("theme")
    val DEFAULT_SHELL = stringPreferencesKey("default_shell")
    val LAST_PROFILE_ID = stringPreferencesKey("last_profile_id")
    val ENABLE_MERMAID = booleanPreferencesKey("enable_mermaid")
  }

  val theme: Flow<String> = dataStore.data.map { prefs ->
    prefs[THEME] ?: "system"
  }

  val defaultShell: Flow<String> = dataStore.data.map { prefs ->
    prefs[DEFAULT_SHELL] ?: "/bin/bash"
  }

  val lastProfileId: Flow<String?> = dataStore.data.map { prefs ->
    prefs[LAST_PROFILE_ID]
  }

  val enableMermaid: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[ENABLE_MERMAID] ?: false
  }

  suspend fun setTheme(value: String) {
    dataStore.edit { prefs -> prefs[THEME] = value }
  }

  suspend fun setDefaultShell(value: String) {
    dataStore.edit { prefs -> prefs[DEFAULT_SHELL] = value }
  }

  suspend fun setLastProfileId(value: String) {
    dataStore.edit { prefs -> prefs[LAST_PROFILE_ID] = value }
  }

  suspend fun setEnableMermaid(value: Boolean) {
    dataStore.edit { prefs -> prefs[ENABLE_MERMAID] = value }
  }
}
