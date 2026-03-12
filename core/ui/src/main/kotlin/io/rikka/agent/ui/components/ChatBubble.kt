package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatBubble(
  message: ChatMessage,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
) {
  val isUser = message.role == ChatRole.User
  val bubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomEnd = if (isUser) 6.dp else 18.dp,
    bottomStart = if (isUser) 18.dp else 6.dp,
  )

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    Surface(
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
      shape = bubbleShape,
      modifier = Modifier
        .clip(bubbleShape)
        .animateContentSize()
        .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Box(
        modifier = Modifier
          .background(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
          )
          .padding(contentPadding),
      ) {
        if (isUser) {
          Text(
            text = message.content,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Clip,
          )
        } else {
          RichText {
            Markdown(message.content)
          }
        }
      }
    }

    // Action row: copy button for completed messages
    if (message.status == MessageStatus.Final && message.content.isNotBlank()) {
      CopyButton(content = message.content, isUser = isUser)
    }
  }
}

@Composable
private fun CopyButton(content: String, isUser: Boolean) {
  val clipboardManager = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var copied by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.padding(horizontal = 12.dp),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    IconButton(
      onClick = {
        clipboardManager.setText(AnnotatedString(content))
        copied = true
        scope.launch {
          delay(1500)
          copied = false
        }
      },
      modifier = Modifier.size(32.dp),
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
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
