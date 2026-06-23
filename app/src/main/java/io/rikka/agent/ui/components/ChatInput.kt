package io.rikka.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.rikka.agent.R
import io.rikka.agent.ui.R as UiR

/**
 * State holder for [ChatInput].
 *
 * @property text Current input text.
 * @property isExpanded Whether the full-screen editor is open.
 * @property isExecuting Whether a command is currently running (disables input, shows cancel).
 * @property suggestions Shell command suggestion chips shown when input is empty.
 */
data class ChatInputState(
  val text: String = "",
  val isExpanded: Boolean = false,
  val isExecuting: Boolean = false,
  val suggestions: List<String> = DEFAULT_SUGGESTIONS,
) {
  companion object {
    val DEFAULT_SUGGESTIONS = listOf("uname -a", "df -h", "uptime", "free -m", "top -bn1")
  }
}

/**
 * Chat input component for RikkaAgent.
 *
 * Features:
 * - Multi-line input; Enter sends, Shift+Enter inserts newline.
 * - Monospace font throughout (command-input scenario).
 * - Shell command suggestion chips when the input is empty.
 * - Disabled + cancel button while a command is executing.
 * - Full-screen edit mode via an expand button.
 * - Auto-height text field (up to 5 visible lines, then scrolls).
 *
 * Visual style follows RikkaHub: rounded Surface with outline border,
 * trailing send/cancel circle, bottom safe-area padding.
 *
 * @param state Current [ChatInputState].
 * @param onTextChange Called when the input text changes.
 * @param onSend Called with trimmed text when the user taps send or presses Enter.
 * @param onCancel Called when the user cancels a running command.
 * @param onSuggestionClick Called when a suggestion chip is tapped.
 * @param onExpandedChange Called to toggle the full-screen editor.
 * @param modifier Modifier applied to the root Column.
 */
@Composable
fun ChatInput(
  state: ChatInputState,
  onTextChange: (String) -> Unit,
  onSend: (String) -> Unit,
  onCancel: () -> Unit,
  onSuggestionClick: (String) -> Unit,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current

  fun doSend() {
    if (state.text.isNotBlank() && !state.isExecuting) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onSend(state.text.trim())
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .imePadding()
      .navigationBarsPadding()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Suggestion chips -- visible when input is empty and nothing is running
    if (state.text.isEmpty() && !state.isExecuting) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        state.suggestions.forEach { cmd ->
          SuggestionChip(
            onClick = { onSuggestionClick(cmd) },
            label = {
              Text(
                text = cmd,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontFamily = FontFamily.Monospace,
                ),
              )
            },
          )
        }
      }
    }

    // Input surface -- rounded box with subtle border (RikkaHub-aligned)
    Surface(
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 0.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
      ) {
        // Text field -- monospace, multi-line, auto-height (max 5 visible lines)
        TextField(
          value = state.text,
          onValueChange = onTextChange,
          modifier = Modifier.fillMaxWidth(),
          enabled = !state.isExecuting,
          placeholder = {
            Text(
              text = stringResource(UiR.string.input_placeholder),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
          },
          textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
          ),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
          ),
          maxLines = 5,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = { doSend() },
          ),
        )

        // Action row -- expand (left) + send/cancel (right)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Expand button -- toggles full-screen editor
          IconButton(
            onClick = { onExpandedChange(!state.isExpanded) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Default.KeyboardArrowUp,
              contentDescription = stringResource(R.string.cd_expand),
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.weight(1f))

          // Send / Cancel circle button
          val containerColor = when {
            state.isExecuting -> MaterialTheme.colorScheme.errorContainer
            state.text.isBlank() -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.primary
          }
          val contentColor = when {
            state.isExecuting -> MaterialTheme.colorScheme.onErrorContainer
            state.text.isBlank() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onPrimary
          }

          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(containerColor)
              .clickable(
                enabled = state.isExecuting || state.text.isNotBlank(),
                onClick = {
                  if (state.isExecuting) onCancel() else doSend()
                },
              ),
            contentAlignment = Alignment.Center,
          ) {
            if (state.isExecuting) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel),
                tint = contentColor,
                modifier = Modifier.size(18.dp),
              )
            } else {
              Icon(
                painter = painterResource(id = UiR.drawable.ic_send),
                contentDescription = stringResource(UiR.string.cd_send),
                tint = contentColor,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }

  // Full-screen editor dialog
  if (state.isExpanded) {
    FullScreenEditor(
      text = state.text,
      onTextChange = onTextChange,
      onDismiss = { onExpandedChange(false) },
    )
  }
}

/**
 * Full-screen editor dialog for long command editing.
 *
 * Anchored to the bottom of the screen, occupying 90% of height.
 * Shares the same [TextFieldState][state] as the inline input so
 * edits are reflected immediately when dismissed.
 */
@Composable
private fun FullScreenEditor(
  text: String,
  onTextChange: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .imePadding(),
      verticalArrangement = Arrangement.Bottom,
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.9f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
      ) {
        Column(
          modifier = Modifier
            .padding(8.dp)
            .fillMaxSize(),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.done))
          }

          TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
              .padding(bottom = 2.dp)
              .fillMaxSize(),
            shape = RoundedCornerShape(32.dp),
            placeholder = {
              Text(stringResource(UiR.string.input_placeholder))
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              fontFamily = FontFamily.Monospace,
            ),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = Color.Transparent,
              unfocusedContainerColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
            ),
          )
        }
      }
    }
  }
}
