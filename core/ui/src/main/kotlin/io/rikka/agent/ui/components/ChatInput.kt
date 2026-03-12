package io.rikka.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.rikka.agent.ui.R

@Composable
fun ChatInput(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onSend: (String) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  val shape = RoundedCornerShape(24.dp)
  val haptic = LocalHapticFeedback.current

  fun doSend() {
    if (text.isNotBlank()) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onSend(text.trim())
      text = ""
    }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
        .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
      TextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = {
          Text(
            stringResource(R.string.input_placeholder),
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
          onSend = { doSend() }
        ),
      )
    }

    IconButton(
      onClick = { doSend() },
      modifier = Modifier.padding(start = 8.dp),
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_send),
        contentDescription = stringResource(R.string.cd_send),
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
