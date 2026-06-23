package io.rikka.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Square
import io.rikka.agent.ui.R

/**
 * RikkaHub-style chat input with rounded container, border, and bottom action row.
 */
@Composable
fun ChatInput(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onSend: (String) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  val haptic = LocalHapticFeedback.current
  val containerShape = RoundedCornerShape(24.dp)
  val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
  val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

  fun doSend() {
    if (text.isNotBlank()) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onSend(text.trim())
      text = ""
    }
  }

  Surface(
    modifier = modifier
      .imePadding()
      .navigationBarsPadding()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    shape = containerShape,
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // Text input
      TextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = {
          Text(
            stringResource(R.string.input_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
          focusedContainerColor = Color.Transparent,
          unfocusedContainerColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
        ),
        maxLines = 5,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { doSend() }),
      )

      // Bottom action row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Left side: placeholder for future action buttons
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Reserved for future: model picker, search, etc.
        }

        // Right side: send / stop button
        IconButton(
          onClick = { doSend() },
          modifier = Modifier.size(30.dp),
        ) {
          Icon(
            imageVector = Lucide.Send,
            contentDescription = stringResource(R.string.cd_send),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
