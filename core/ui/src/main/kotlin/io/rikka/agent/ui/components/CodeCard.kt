package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rikka.agent.ui.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLLAPSED_MAX_LINES = 15

/**
 * Code card for rendering command output with:
 * - Optional language label
 * - Copy button in the header
 * - Auto-collapse for content exceeding [COLLAPSED_MAX_LINES] lines
 * - Horizontal scroll for wide content
 * - Selectable text
 */
@Composable
fun CodeCard(
  code: String,
  language: String? = null,
  modifier: Modifier = Modifier,
) {
  val lines = code.lines()
  val isLong = lines.size > COLLAPSED_MAX_LINES
  var expanded by remember { mutableStateOf(!isLong) }

  val displayText = if (expanded || !isLong) code
  else lines.take(COLLAPSED_MAX_LINES).joinToString("\n")

  val shape = RoundedCornerShape(12.dp)

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .animateContentSize(),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    tonalElevation = 0.dp,
    shape = shape,
  ) {
    Column {
      // Header: language + copy
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = language ?: stringResource(R.string.label_output),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.weight(1f))
        CopyCodeButton(code = code)
      }

      // Code body
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .then(
            if (!expanded && isLong) Modifier.heightIn(max = 320.dp)
            else Modifier
          ),
      ) {
        SelectionContainer {
          Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp,
              lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(12.dp),
          )
        }
      }

      // Expand/collapse toggle
      if (isLong) {
        TextButton(
          onClick = { expanded = !expanded },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = if (expanded) stringResource(R.string.collapse_lines, lines.size)
            else stringResource(R.string.show_all_lines, lines.size),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun CopyCodeButton(code: String) {
  val clipboardManager = LocalClipboardManager.current
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  var copied by remember { mutableStateOf(false) }

  IconButton(
    onClick = {
      clipboardManager.setText(AnnotatedString(code))
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      copied = true
      scope.launch { delay(1500); copied = false }
    },
    modifier = Modifier.size(28.dp),
  ) {
    if (copied) {
      Text("✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    } else {
      Icon(
        painter = painterResource(id = R.drawable.ic_copy),
        contentDescription = stringResource(R.string.copy_code),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(14.dp),
      )
    }
  }
}
