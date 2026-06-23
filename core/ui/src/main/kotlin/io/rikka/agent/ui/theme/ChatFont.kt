package io.rikka.agent.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import io.rikka.agent.core.ui.R

/**
 * User-selectable font family for chat message content.
 *
 * Each variant maps to a [FontFamily] used in the chat bubble and markdown renderer.
 */
enum class ChatFont(
  val id: String,
  val displayName: String,
  val fontFamily: FontFamily,
) {
  /** Platform default sans-serif. */
  Default("default", "Default", FontFamily.Default),

  /** Serif typeface for long-form reading. */
  Serif("serif", "Serif", FontFamily.Serif),

  /** Monospace for code-heavy workflows. */
  Monospace("monospace", "Monospace", FontFamily.Monospace),
  ;

  companion object {
    fun findById(id: String): ChatFont = entries.find { it.id == id } ?: Default
  }
}
