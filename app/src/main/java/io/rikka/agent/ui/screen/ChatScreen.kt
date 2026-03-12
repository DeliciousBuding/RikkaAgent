package io.rikka.agent.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.vm.ConnectionState
import io.rikka.agent.vm.HostKeyEvent
import io.rikka.agent.ui.components.ChatBubble
import io.rikka.agent.ui.components.ChatInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  profileId: String = "",
  onBack: () -> Unit = {},
) {
  val vm: ChatViewModel = koinViewModel { parametersOf(profileId) }
  val messages by vm.messages.collectAsState()
  val connectionState by vm.connectionState.collectAsState()
  val threads by vm.threads.collectAsState()
  val profileLabel by vm.profileLabel.collectAsState()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)

  // Elapsed timer for running commands
  var elapsedSeconds by remember { mutableStateOf(0) }
  LaunchedEffect(connectionState) {
    if (connectionState == ConnectionState.EXECUTING) {
      elapsedSeconds = 0
      while (true) {
        delay(1000)
        elapsedSeconds++
      }
    }
  }

  // Host key verification dialog state
  var hostKeyEvent by remember { mutableStateOf<HostKeyEvent?>(null) }

  // Password dialog state
  var passwordTarget by remember { mutableStateOf<String?>(null) }

  // Passphrase dialog state
  var passphraseTarget by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    vm.hostKeyEvent.collect { event ->
      hostKeyEvent = event
    }
  }

  LaunchedEffect(Unit) {
    vm.passwordRequest.collect { target ->
      passwordTarget = target
    }
  }

  LaunchedEffect(Unit) {
    vm.passphraseRequest.collect { target ->
      passphraseTarget = target
    }
  }

  // Show host key dialog
  hostKeyEvent?.let { event ->
    HostKeyDialog(
      event = event,
      onAccept = {
        vm.respondToHostKey(true)
        hostKeyEvent = null
      },
      onReject = {
        vm.respondToHostKey(false)
        hostKeyEvent = null
      },
    )
  }

  // Show password dialog
  passwordTarget?.let { target ->
    PasswordDialog(
      target = target,
      onSubmit = { password ->
        vm.respondToPassword(password)
        passwordTarget = null
      },
      onCancel = {
        vm.respondToPassword(null)
        passwordTarget = null
      },
    )
  }

  // Show passphrase dialog
  passphraseTarget?.let { target ->
    PasswordDialog(
      target = target,
      title = "Key Passphrase",
      label = "Passphrase",
      onSubmit = { passphrase ->
        vm.respondToPassphrase(passphrase)
        passphraseTarget = null
      },
      onCancel = {
        vm.respondToPassphrase(null)
        passphraseTarget = null
      },
    )
  }

  val isStreaming = connectionState == ConnectionState.EXECUTING

  val showScrollToBottom by remember {
    derivedStateOf {
      val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      lastVisible < messages.size - 1
    }
  }

  // Auto-scroll to bottom when new messages arrive or streaming
  LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      SessionDrawerContent(
        threads = threads,
        onNewSession = {
          vm.newSession()
          scope.launch { drawerState.close() }
        },
        onSelectThread = { threadId ->
          vm.switchThread(threadId)
          scope.launch { drawerState.close() }
        },
        onDeleteThread = { threadId -> vm.deleteThread(threadId) },
      )
    },
  ) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = profileLabel.ifBlank { "Session" },
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val statusText = when (connectionState) {
              ConnectionState.IDLE -> "Connecting…"
              ConnectionState.READY -> "Ready"
              ConnectionState.EXECUTING -> "Running… ${elapsedSeconds}s"
              ConnectionState.ERROR -> "Error"
            }
            val statusColor = when (connectionState) {
              ConnectionState.READY -> MaterialTheme.colorScheme.primary
              ConnectionState.EXECUTING -> MaterialTheme.colorScheme.tertiary
              ConnectionState.ERROR -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
              text = statusText,
              style = MaterialTheme.typography.labelSmall,
              color = statusColor,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(Icons.Default.Menu, contentDescription = "Sessions")
          }
        },
        actions = {
          when (connectionState) {
            ConnectionState.EXECUTING -> {
              IconButton(onClick = { vm.cancelRunning() }) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = "Cancel",
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
            else -> {
              IconButton(onClick = onBack) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back",
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .imePadding(),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Progress bar when executing
        if (connectionState == ConnectionState.EXECUTING) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (messages.isEmpty()) {
          EmptySessionState(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onSuggestionClick = { vm.send(it) },
          )
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            items(messages, key = { it.id }) { msg ->
              ChatBubble(message = msg)
            }
            item {
              Spacer(modifier = Modifier.height(12.dp))
            }
          }
        }
      }

      // Scroll-to-bottom FAB
      AnimatedVisibility(
        visible = showScrollToBottom && !isStreaming,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 80.dp),
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut(),
      ) {
        SmallFloatingActionButton(
          onClick = {
            scope.launch {
              if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
              }
            }
          },
          shape = CircleShape,
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        ) {
          Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Scroll to bottom",
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // Input bar at bottom
      Box(modifier = Modifier.align(Alignment.BottomCenter)) {
        ChatInput(
          enabled = !isStreaming,
          onSend = { text -> vm.send(text) },
        )
      }
    }
  }
  } // ModalNavigationDrawer
}

@Composable
private fun SessionDrawerContent(
  threads: List<io.rikka.agent.model.ChatThread>,
  onNewSession: () -> Unit,
  onSelectThread: (String) -> Unit,
  onDeleteThread: (String) -> Unit,
) {
  var confirmDeleteId by remember { mutableStateOf<String?>(null) }

  confirmDeleteId?.let { id ->
    val thread = threads.find { it.id == id }
    AlertDialog(
      onDismissRequest = { confirmDeleteId = null },
      title = { Text("Delete session?") },
      text = { Text("Delete \"${thread?.title?.ifBlank { "Session" } ?: "Session"}\"? This cannot be undone.") },
      confirmButton = {
        TextButton(onClick = { onDeleteThread(id); confirmDeleteId = null }) {
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
      },
    )
  }

  ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Sessions",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onNewSession) {
        Icon(Icons.Default.Add, contentDescription = "New session")
      }
    }
    if (threads.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No past sessions",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
      }
    } else {
      LazyColumn {
        items(threads, key = { it.id }) { thread ->
          ListItem(
            headlineContent = {
              Text(
                text = thread.title.ifBlank { "Session" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            trailingContent = {
              IconButton(
                onClick = { confirmDeleteId = thread.id },
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  Icons.Default.Delete,
                  contentDescription = "Delete",
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
              }
            },
            modifier = Modifier.clickable { onSelectThread(thread.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun HostKeyDialog(
  event: HostKeyEvent,
  onAccept: () -> Unit,
  onReject: () -> Unit,
) {
  val (title, text) = when (event) {
    is HostKeyEvent.UnknownHost -> {
      "Trust this host?" to
        "First connection to ${event.host}:${event.port}\n\n" +
        "Key type: ${event.keyType}\n" +
        "Fingerprint:\n${event.fingerprint}\n\n" +
        "Do you want to trust this host?"
    }
    is HostKeyEvent.Mismatch -> {
      "⚠️ Host key changed!" to
        "WARNING: The host key for ${event.host}:${event.port} has changed!\n\n" +
        "Expected: ${event.expectedFingerprint}\n" +
        "Got: ${event.actualFingerprint}\n\n" +
        "This could indicate a man-in-the-middle attack. " +
        "Only accept if you know why the key changed."
    }
  }

  AlertDialog(
    onDismissRequest = onReject,
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
      )
    },
    confirmButton = {
      TextButton(onClick = onAccept) {
        Text(if (event is HostKeyEvent.Mismatch) "Replace & Trust" else "Trust")
      }
    },
    dismissButton = {
      TextButton(onClick = onReject) {
        Text("Reject")
      }
    },
  )
}

@Composable
private fun EmptySessionState(
  modifier: Modifier = Modifier,
  onSuggestionClick: (String) -> Unit = {},
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "Type a command to run on the server",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.alpha(0.6f),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf("uname -a", "df -h", "uptime").forEach { cmd ->
        androidx.compose.material3.SuggestionChip(
          onClick = { onSuggestionClick(cmd) },
          label = {
            Text(
              text = cmd,
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
              ),
            )
          },
        )
      }
    }
  }
}

@Composable
private fun PasswordDialog(
  target: String,
  title: String = "SSH Password",
  label: String = "Password",
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Enter $label for $target",
          style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.material3.OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(label) },
          singleLine = true,
          visualTransformation = if (passwordVisible) {
            androidx.compose.ui.text.input.VisualTransformation.None
          } else {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSubmit(password) },
        enabled = password.isNotEmpty(),
      ) {
        Text("Connect")
      }
    },
    dismissButton = {
      TextButton(onClick = onCancel) { Text("Cancel") }
    },
  )
}
