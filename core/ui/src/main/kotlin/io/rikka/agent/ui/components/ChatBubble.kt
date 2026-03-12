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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ui.R
import androidx.compose.ui.res.stringResource
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
  onRerun: ((String) -> Unit)? = null,
  onShare: ((String) -> Unit)? = null,
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
      // Assistant messages: markdown for rich content, code card for plain output/errors
      // During streaming, skip markdown parsing (expensive) — render as CodeCard until finalized
      Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      ) {
        if (isError || isStreaming || !looksLikeMarkdown(message.content)) {
          CodeCard(
            code = message.content,
            language = if (isError) "error" else null,
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          MarkdownText(
            markdown = message.content,
            modifier = Modifier.fillMaxWidth(),
          )
        }
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
      if (isUser && onRerun != null && message.content.isNotBlank()) {
        RerunButton(command = message.content, onRerun = onRerun)
      }
      if (message.status == MessageStatus.Final && message.content.isNotBlank()) {
        CopyButton(content = message.content)
        if (!isUser && onShare != null) {
          ShareButton(content = message.content, onShare = onShare)
        }
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
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  var copied by remember { mutableStateOf(false) }

  IconButton(
    onClick = {
      clipboardManager.setText(AnnotatedString(content))
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        contentDescription = stringResource(R.string.cd_copy),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

@Composable
private fun RerunButton(command: String, onRerun: (String) -> Unit) {
  val haptic = LocalHapticFeedback.current
  IconButton(
    onClick = {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      onRerun(command)
    },
    modifier = Modifier.size(24.dp),
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_replay),
      contentDescription = stringResource(R.string.cd_rerun),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      modifier = Modifier.size(14.dp),
    )
  }
}

@Composable
private fun ShareButton(content: String, onShare: (String) -> Unit) {
  IconButton(
    onClick = { onShare(content) },
    modifier = Modifier.size(24.dp),
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_share),
      contentDescription = stringResource(R.string.cd_share),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      modifier = Modifier.size(14.dp),
    )
  }
}

@Composable
private fun formatTimestamp(timestampMs: Long): String {
  val now = System.currentTimeMillis()
  val diff = now - timestampMs
  return when {
    diff < TimeUnit.MINUTES.toMillis(1) -> stringResource(R.string.time_just_now)
    diff < TimeUnit.HOURS.toMillis(1) -> stringResource(R.string.time_minutes_ago, TimeUnit.MILLISECONDS.toMinutes(diff).toInt())
    diff < TimeUnit.DAYS.toMillis(1) -> stringResource(R.string.time_hours_ago, TimeUnit.MILLISECONDS.toHours(diff).toInt())
    else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))
  }
}

/** Heuristic: does the text contain markdown formatting worth rendering? */
private fun looksLikeMarkdown(text: String): Boolean {
  if (text.length < 4) return false
  val sample = if (text.length > 2000) text.substring(0, 2000) else text
  return sample.contains("```") ||
    sample.contains("## ") ||
    sample.contains("# ") ||
    sample.contains("**") ||
    sample.contains("- ") ||
    sample.contains("1. ") ||
    sample.contains("> ") ||
    sample.contains("[") && sample.contains("](")
}
