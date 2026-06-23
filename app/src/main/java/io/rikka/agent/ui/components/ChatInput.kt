package io.rikka.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.PopupProperties
import io.rikka.agent.R
import io.rikka.agent.storage.QuickMessage
import io.rikka.agent.ui.R as UiR

/**
 * Chat input component for RikkaAgent.
 *
 * Features:
 * - Multi-line input; Enter sends, Shift+Enter inserts newline.
 * - Monospace font throughout (command-input scenario).
 * - Disabled state while a command is executing.
 * - Full-screen edit mode via an expand button.
 * - Auto-height text field (up to 5 visible lines, then scrolls).
 * - Long-press send button to open quick message picker.
 *
 * Visual style follows RikkaHub: rounded Surface with outline border,
 * trailing send/cancel circle, bottom safe-area padding.
 *
 * @param enabled Whether the input is enabled (false while streaming/executing).
 * @param quickMessages List of user-configurable quick messages for the long-press picker.
 * @param onSend Called with trimmed text when the user taps send or presses Enter.
 * @param modifier Modifier applied to the root Column.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInput(
  enabled: Boolean,
  quickMessages: List<QuickMessage> = emptyList(),
  onSend: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var text by remember { mutableStateOf("") }
  var isExpanded by remember { mutableStateOf(false) }
  var showQuickMessages by remember { mutableStateOf(false) }
  val haptic = LocalHapticFeedback.current

  fun doSend() {
    if (text.isNotBlank() && enabled) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onSend(text.trim())
      text = ""
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
          value = text,
          onValueChange = { text = it },
          modifier = Modifier.fillMaxWidth(),
          enabled = enabled,
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

        // Action row -- expand (left) + send (right)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Expand button -- toggles full-screen editor
          IconButton(
            onClick = { isExpanded = !isExpanded },
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

          // Quick message dropdown anchor
          Box {
            // Send circle button with long-press for quick messages
            val containerColor = when {
              !enabled -> MaterialTheme.colorScheme.errorContainer
              text.isBlank() -> MaterialTheme.colorScheme.surfaceContainerHigh
              else -> MaterialTheme.colorScheme.primary
            }
            val contentColor = when {
              !enabled -> MaterialTheme.colorScheme.onErrorContainer
              text.isBlank() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              else -> MaterialTheme.colorScheme.onPrimary
            }

            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(containerColor)
                .combinedClickable(
                  enabled = enabled || text.isNotBlank(),
                  onClick = {
                    if (!enabled) return@combinedClickable
                    doSend()
                  },
                  onLongClick = {
                    if (quickMessages.isNotEmpty()) {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      showQuickMessages = true
                    }
                  },
                ),
              contentAlignment = Alignment.Center,
            ) {
              if (!enabled) {
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

            // Quick message dropdown menu
            DropdownMenu(
              expanded = showQuickMessages,
              onDismissRequest = { showQuickMessages = false },
              properties = PopupProperties(focusable = true),
            ) {
              quickMessages.forEach { msg ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(
                        text = msg.label,
                        style = MaterialTheme.typography.bodyMedium,
                      )
                      if (msg.label != msg.command) {
                        Text(
                          text = msg.command,
                          style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                          ),
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  },
                  onClick = {
                    text = msg.command
                    showQuickMessages = false
                  },
                )
              }
            }
          }
        }
      }
    }
  }

  // Full-screen editor dialog
  if (isExpanded) {
    FullScreenEditor(
      text = text,
      onTextChange = { text = it },
      onDismiss = { isExpanded = false },
    )
  }
}

/**
 * Full-screen editor dialog for long command editing.
 *
 * Anchored to the bottom of the screen, occupying 90% of height.
 * Shares the same text state as the inline input so edits are
 * reflected immediately when dismissed.
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
