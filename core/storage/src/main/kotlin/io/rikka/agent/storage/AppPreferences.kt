package io.rikka.agent.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val PRESET_THEME = stringPreferencesKey("preset_theme")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val DEFAULT_SHELL = stringPreferencesKey("default_shell")
    val LAST_PROFILE_ID = stringPreferencesKey("last_profile_id")
    val ENABLE_MERMAID = booleanPreferencesKey("enable_mermaid")
    val SHOW_USER_AVATAR = booleanPreferencesKey("show_user_avatar")
    val SHOW_MODEL_ICON = booleanPreferencesKey("show_model_icon")
    val CHAT_FONT = stringPreferencesKey("chat_font")
    val FONT_SIZE_RATIO = floatPreferencesKey("font_size_ratio")
    val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
    val SHOW_ASSISTANT_BUBBLE = booleanPreferencesKey("show_assistant_bubble")
    val SHOW_TIMESTAMP = booleanPreferencesKey("show_timestamp")
    val QUICK_MESSAGES = stringPreferencesKey("quick_messages")
  }

  val theme: Flow<String> = dataStore.data.map { prefs ->
    prefs[THEME] ?: "system"
  }

  val presetTheme: Flow<String> = dataStore.data.map { prefs ->
    prefs[PRESET_THEME] ?: "sakura"
  }

  val dynamicColor: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[DYNAMIC_COLOR] ?: false
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

  val showUserAvatar: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[SHOW_USER_AVATAR] ?: true
  }

  val showModelIcon: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[SHOW_MODEL_ICON] ?: true
  }

  val chatFont: Flow<String> = dataStore.data.map { prefs ->
    prefs[CHAT_FONT] ?: "default"
  }

  val fontSizeRatio: Flow<Float> = dataStore.data.map { prefs ->
    prefs[FONT_SIZE_RATIO] ?: 1.0f
  }

  val bubbleOpacity: Flow<Float> = dataStore.data.map { prefs ->
    prefs[BUBBLE_OPACITY] ?: 0.85f
  }

  val showAssistantBubble: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[SHOW_ASSISTANT_BUBBLE] ?: true
  }

  val showTimestamp: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[SHOW_TIMESTAMP] ?: true
  }

  /**
   * Observable list of user-configurable quick messages.
   * Stored as a JSON-encoded string in DataStore.
   */
  val quickMessages: Flow<List<QuickMessage>> = dataStore.data.map { prefs ->
    val json = prefs[QUICK_MESSAGES]
    if (json.isNullOrBlank()) {
      DEFAULT_QUICK_MESSAGES
    } else {
      try {
        Json.decodeFromString<List<QuickMessage>>(json)
      } catch (_: Exception) {
        DEFAULT_QUICK_MESSAGES
      }
    }
  }

  suspend fun setTheme(value: String) {
    dataStore.edit { prefs -> prefs[THEME] = value }
  }

  suspend fun setPresetTheme(value: String) {
    dataStore.edit { prefs -> prefs[PRESET_THEME] = value }
  }

  suspend fun setDynamicColor(value: Boolean) {
    dataStore.edit { prefs -> prefs[DYNAMIC_COLOR] = value }
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

  suspend fun setShowUserAvatar(value: Boolean) {
    dataStore.edit { prefs -> prefs[SHOW_USER_AVATAR] = value }
  }

  suspend fun setShowModelIcon(value: Boolean) {
    dataStore.edit { prefs -> prefs[SHOW_MODEL_ICON] = value }
  }

  suspend fun setChatFont(value: String) {
    dataStore.edit { prefs -> prefs[CHAT_FONT] = value }
  }

  suspend fun setFontSizeRatio(value: Float) {
    dataStore.edit { prefs -> prefs[FONT_SIZE_RATIO] = value.coerceIn(0.8f, 1.5f) }
  }

  suspend fun setBubbleOpacity(value: Float) {
    dataStore.edit { prefs -> prefs[BUBBLE_OPACITY] = value.coerceIn(0.5f, 1.0f) }
  }

  suspend fun setShowAssistantBubble(value: Boolean) {
    dataStore.edit { prefs -> prefs[SHOW_ASSISTANT_BUBBLE] = value }
  }

  suspend fun setShowTimestamp(value: Boolean) {
    dataStore.edit { prefs -> prefs[SHOW_TIMESTAMP] = value }
  }

  /**
   * Persist the full list of quick messages.
   *
   * @param messages The complete list of quick messages to store.
   */
  suspend fun setQuickMessages(messages: List<QuickMessage>) {
    dataStore.edit { prefs ->
      prefs[QUICK_MESSAGES] = Json.encodeToString(messages)
    }
  }

  companion object {
    /** Default quick messages shown when the user has not configured any. */
    val DEFAULT_QUICK_MESSAGES = listOf(
      QuickMessage(label = "uname -a", command = "uname -a"),
      QuickMessage(label = "df -h", command = "df -h"),
      QuickMessage(label = "uptime", command = "uptime"),
      QuickMessage(label = "free -m", command = "free -m"),
      QuickMessage(label = "top -bn1", command = "top -bn1"),
    )
  }
}
