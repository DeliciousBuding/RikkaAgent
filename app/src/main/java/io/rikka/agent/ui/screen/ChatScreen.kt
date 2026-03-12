package io.rikka.agent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.ui.components.ChatBubble
import io.rikka.agent.ui.components.ChatInput

@Composable
fun ChatScreen() {
  val vm: ChatViewModel = viewModel()
  val messages by vm.messages.collectAsState()

  Box(modifier = Modifier.fillMaxSize()) {
    // A soft, warm background direction (will be refined to match reference app).
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.linearGradient(
            colors = listOf(
              MaterialTheme.colorScheme.background,
              Color(0xFFF1EEE8),
              MaterialTheme.colorScheme.background,
            )
          )
        )
    )

    Column(modifier = Modifier.fillMaxSize()) {
      HeaderBar()

      LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        items(messages, key = { it.id }) { msg ->
          ChatBubble(message = msg)
        }
        item {
          Spacer(modifier = Modifier.height(12.dp))
        }
      }

      ChatInput(
        onSend = { text ->
          vm.send(text)
        }
      )
    }
  }
}

@Composable
private fun HeaderBar() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 18.dp),
  ) {
    Text(
      text = "Rikka Agent",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground
    )
    Text(
      text = "Mode A · SSH exec",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = Modifier.alpha(0.6f)
    )
  }
}
