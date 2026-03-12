package io.rikka.agent.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.ssh.StoredHostKey
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(
  onBack: () -> Unit,
) {
  val store: KnownHostsStore = koinInject()
  val scope = rememberCoroutineScope()
  var entries by remember { mutableStateOf<List<Pair<String, StoredHostKey>>>(emptyList()) }
  var confirmDelete by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    entries = store.getAll()
  }

  // Delete confirmation dialog
  confirmDelete?.let { hostKey ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      title = { Text("Remove host key?") },
      text = { Text("Remove the trusted key for $hostKey? You'll be prompted again on next connection.") },
      confirmButton = {
        TextButton(onClick = {
          scope.launch {
            val parts = hostKey.split(":")
            if (parts.size == 2) {
              store.remove(parts[0], parts[1].toIntOrNull() ?: 22)
            }
            entries = store.getAll()
            confirmDelete = null
          }
        }) {
          Text("Remove", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Known Hosts") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    if (entries.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "No known hosts",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Host keys will appear here after connecting",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    } else {
      LazyColumn(
        contentPadding = innerPadding,
      ) {
        items(entries, key = { it.first }) { (hostKey, stored) ->
          ListItem(
            headlineContent = {
              Text(hostKey, style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
              Column {
                Text(
                  text = stored.fingerprint,
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                  ),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "${stored.keyType} · Added ${formatDate(stored.addedAtMs)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
              }
            },
            trailingContent = {
              IconButton(onClick = { confirmDelete = hostKey }) {
                Icon(
                  Icons.Default.Delete,
                  contentDescription = "Remove",
                  tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
              }
            },
          )
        }
      }
    }
  }
}

private fun formatDate(ms: Long): String {
  return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
}
