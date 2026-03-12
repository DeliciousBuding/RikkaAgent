package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ChatBubble(
  message: ChatMessage,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
) {
  val isUser = message.role == ChatRole.User
  val isError = message.status == MessageStatus.Error
  val isStreaming = message.status == MessageStatus.Streaming
  val bubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomEnd = if (isUser) 6.dp else 18.dp,
    bottomStart = if (isUser) 18.dp else 6.dp,
  )

  val bubbleColor = when {
    isUser -> MaterialTheme.colorScheme.primary
    isError -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surface
  }

  val contentColor = when {
    isUser -> MaterialTheme.colorScheme.onPrimary
    isError -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurface
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    if (isUser) {
      // User messages: chat bubble style
      Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = bubbleColor,
        shape = bubbleShape,
        modifier = Modifier
          .clip(bubbleShape)
          .animateContentSize()
          .padding(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Box(
          modifier = Modifier
            .background(color = bubbleColor)
            .padding(contentPadding),
        ) {
          Text(
            text = message.content,
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Clip,
          )
        }
      }
    } else if (isStreaming && message.content.isEmpty()) {
      // Empty streaming: dots in a minimal bubble
      Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = bubbleColor,
        shape = bubbleShape,
        modifier = Modifier
          .clip(bubbleShape)
          .padding(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Box(modifier = Modifier.padding(contentPadding)) {
          StreamingDots()
        }
      }
    } else {
      // Assistant messages: code card for output
      Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      ) {
        CodeCard(
          code = message.content,
          language = if (isError) "error" else null,
          modifier = Modifier.fillMaxWidth(),
        )
        if (isStreaming) {
          Spacer(modifier = Modifier.height(4.dp))
          StreamingDots()
        }
      }
    }

    // Timestamp + action row
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = formatTimestamp(message.timestampMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      )
      if (message.status == MessageStatus.Final && message.content.isNotBlank()) {
        CopyButton(content = message.content)
      }
    }
  }
}

@Composable
private fun StreamingDots() {
  val transition = rememberInfiniteTransition(label = "streaming")
  val dotCount by transition.animateFloat(
    initialValue = 1f,
    targetValue = 4f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "dots",
  )
  Text(
    text = ".".repeat(dotCount.toInt().coerceIn(1, 3)),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
  )
}

@Composable
private fun CopyButton(content: String) {
  val clipboardManager = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var copied by remember { mutableStateOf(false) }

  IconButton(
    onClick = {
      clipboardManager.setText(AnnotatedString(content))
      copied = true
      scope.launch {
        delay(1500)
        copied = false
      }
    },
    modifier = Modifier.size(24.dp),
  ) {
    if (copied) {
      Text(
        text = "✓",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
    } else {
      Icon(
        painter = painterResource(id = R.drawable.ic_copy),
        contentDescription = "Copy",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

private fun formatTimestamp(timestampMs: Long): String {
  val now = System.currentTimeMillis()
  val diff = now - timestampMs
  return when {
    diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
    diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
    diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
    else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))
  }
}
