package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole

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

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
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
          // v1: render assistant content as Markdown (code blocks, lists). Styling will be refined
          // later to match the reference app more closely.
          RichText {
            Markdown(message.content)
          }
        }
      }
    }
  }
}
