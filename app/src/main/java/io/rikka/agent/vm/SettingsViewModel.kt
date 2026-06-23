package io.rikka.agent.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.QuickMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 *
 * ## Responsibilities
 * - Exposes user preferences as observable [StateFlow] instances for Compose collection.
 * - Provides setter methods that persist changes via [AppPreferences] on the IO dispatcher.
 *
 * ## Thread Safety
 * - All preference flows are created via [StateFlow.stateIn] with [SharingStarted.WhileSubscribed],
 *   which is safe for multi-collector scenarios.
 * - Setter methods launch coroutines in [viewModelScope]; the underlying [AppPreferences]
 *   DataStore operations are inherently thread-safe.
 *
 * ## Exposed State
 * | StateFlow       | Type                | Default      | Description                          |
 * |-----------------|---------------------|--------------|--------------------------------------|
 * | [theme]         | `StateFlow<String>` | `"system"`   | UI theme: "system", "light", "dark". |
 * | [dynamicColor]  | `StateFlow<Boolean>`| `false`      | Whether to use Material You dynamic colors. |
 * | [defaultShell]  | `StateFlow<String>` | `"/bin/bash"`| Default shell for SSH command execution. |
 * | [enableMermaid] | `StateFlow<Boolean>`| `false`      | Whether to render Mermaid diagrams in output. |
 *
 * @param prefs The [AppPreferences] DataStore-backed preferences source.
 */
class SettingsViewModel(
  private val prefs: AppPreferences,
) : ViewModel() {

  /**
   * Observable UI theme preference.
   * Values: `"system"` (follow system), `"light"`, `"dark"`.
   */
  val theme: StateFlow<String> = prefs.theme
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

  /**
   * Observable preset theme preference.
   * Values: `"sakura"`, `"ocean"`, `"spring"`, `"autumn"`, `"black"`.
   */
  val presetTheme: StateFlow<String> = prefs.presetTheme
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "sakura")

  /**
   * Observable dynamic color preference.
   * When `true`, the app uses Material You dynamic colors (Android 12+).
   */
  val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /**
   * Observable default shell path for SSH command execution.
   * Used by [CommandExecutor] to wrap user commands.
   */
  val defaultShell: StateFlow<String> = prefs.defaultShell
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "/bin/bash")

  /**
   * Observable Mermaid diagram rendering preference.
   * When `true`, Mermaid code blocks in command output are rendered as diagrams.
   */
  val enableMermaid: StateFlow<Boolean> = prefs.enableMermaid
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /**
   * Observable user avatar visibility preference.
   * When `true`, user messages show a circular avatar with the user's initial.
   */
  val showUserAvatar: StateFlow<Boolean> = prefs.showUserAvatar
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

  /**
   * Observable model icon visibility preference.
   * When `true`, assistant messages show a circular icon (bot or model icon).
   */
  val showModelIcon: StateFlow<Boolean> = prefs.showModelIcon
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

  /**
   * Observable chat font family preference.
   * Values: `"default"`, `"serif"`, `"monospace"`.
   */
  val chatFont: StateFlow<String> = prefs.chatFont
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "default")

  /**
   * Observable font size ratio for chat content.
   * Range: 0.8 (small) to 1.5 (large), default 1.0.
   */
  val fontSizeRatio: StateFlow<Float> = prefs.fontSizeRatio
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

  val bubbleOpacity: StateFlow<Float> = prefs.bubbleOpacity
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.85f)

  val showAssistantBubble: StateFlow<Boolean> = prefs.showAssistantBubble
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

  val showTimestamp: StateFlow<Boolean> = prefs.showTimestamp
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

  /**
   * Observable list of user-configurable quick messages.
   * Used by the chat input's long-press quick message picker.
   */
  val quickMessages: StateFlow<List<QuickMessage>> = prefs.quickMessages
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.DEFAULT_QUICK_MESSAGES)

  /**
   * Persist a new theme value.
   *
   * @param value The theme string: `"system"`, `"light"`, or `"dark"`.
   */
  fun setTheme(value: String) {
    viewModelScope.launch { prefs.setTheme(value) }
  }

  /**
   * Persist a new preset theme value.
   *
   * @param value The preset theme id: `"sakura"`, `"ocean"`, `"spring"`, `"autumn"`, or `"black"`.
   */
  fun setPresetTheme(value: String) {
    viewModelScope.launch { prefs.setPresetTheme(value) }
  }

  /**
   * Persist a new dynamic color preference.
   *
   * @param value `true` to enable Material You dynamic colors, `false` to disable.
   */
  fun setDynamicColor(value: Boolean) {
    viewModelScope.launch { prefs.setDynamicColor(value) }
  }

  /**
   * Persist a new default shell path.
   *
   * @param value The shell path (e.g. `/bin/bash`, `/bin/sh`, `/bin/zsh`).
   */
  fun setDefaultShell(value: String) {
    viewModelScope.launch { prefs.setDefaultShell(value) }
  }

  /**
   * Persist a new Mermaid rendering preference.
   *
   * @param value `true` to enable Mermaid diagram rendering, `false` to disable.
   */
  fun setEnableMermaid(value: Boolean) {
    viewModelScope.launch { prefs.setEnableMermaid(value) }
  }

  /**
   * Persist a new user avatar visibility preference.
   *
   * @param value `true` to show user avatars, `false` to hide.
   */
  fun setShowUserAvatar(value: Boolean) {
    viewModelScope.launch { prefs.setShowUserAvatar(value) }
  }

  /**
   * Persist a new model icon visibility preference.
   *
   * @param value `true` to show model/assistant icons, `false` to hide.
   */
  fun setShowModelIcon(value: Boolean) {
    viewModelScope.launch { prefs.setShowModelIcon(value) }
  }

  /**
   * Persist a new chat font family preference.
   *
   * @param value The font id: `"default"`, `"serif"`, or `"monospace"`.
   */
  fun setChatFont(value: String) {
    viewModelScope.launch { prefs.setChatFont(value) }
  }

  /**
   * Persist a new font size ratio.
   *
   * @param value The ratio (0.8 .. 1.5).
   */
  fun setFontSizeRatio(value: Float) {
    viewModelScope.launch { prefs.setFontSizeRatio(value) }
  }

  fun setBubbleOpacity(value: Float) {
    viewModelScope.launch { prefs.setBubbleOpacity(value) }
  }

  fun setShowAssistantBubble(value: Boolean) {
    viewModelScope.launch { prefs.setShowAssistantBubble(value) }
  }

  fun setShowTimestamp(value: Boolean) {
    viewModelScope.launch { prefs.setShowTimestamp(value) }
  }

  /**
   * Persist the full list of quick messages.
   *
   * @param messages The complete list of quick messages to store.
   */
  fun setQuickMessages(messages: List<QuickMessage>) {
    viewModelScope.launch { prefs.setQuickMessages(messages) }
  }

  /**
   * Add a new quick message.
   *
   * @param label Short display label.
   * @param command The command text to insert/send.
   */
  fun addQuickMessage(label: String, command: String) {
    viewModelScope.launch {
      val current = quickMessages.value
      prefs.setQuickMessages(current + QuickMessage(label = label, command = command))
    }
  }

  /**
   * Remove a quick message by its id.
   *
   * @param id The unique identifier of the quick message to remove.
   */
  fun removeQuickMessage(id: String) {
    viewModelScope.launch {
      val current = quickMessages.value
      prefs.setQuickMessages(current.filter { it.id != id })
    }
  }

  /**
   * Update an existing quick message.
   *
   * @param id The unique identifier of the quick message to update.
   * @param label New display label.
   * @param command New command text.
   */
  fun updateQuickMessage(id: String, label: String, command: String) {
    viewModelScope.launch {
      val current = quickMessages.value
      prefs.setQuickMessages(current.map {
        if (it.id == id) it.copy(label = label, command = command) else it
      })
    }
  }
}
