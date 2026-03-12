package io.rikka.agent.ui.screen

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.rikka.agent.R
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
  val context = LocalContext.current

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
      title = stringResource(R.string.key_passphrase),
      label = stringResource(R.string.label_passphrase),
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
              text = profileLabel.ifBlank { stringResource(R.string.session_fallback_name) },
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val statusText = when (connectionState) {
              ConnectionState.IDLE -> stringResource(R.string.status_connecting)
              ConnectionState.READY -> stringResource(R.string.status_ready)
              ConnectionState.EXECUTING -> stringResource(R.string.status_running, elapsedSeconds)
              ConnectionState.ERROR -> stringResource(R.string.status_error)
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
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.sessions))
          }
        },
        actions = {
          if (messages.isNotEmpty() && connectionState != ConnectionState.EXECUTING) {
            IconButton(onClick = {
              val text = vm.exportSession()
              val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.ssh_session_subject, profileLabel))
              }
              context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_session)))
            }) {
              Icon(
                painter = androidx.compose.ui.res.painterResource(
                  id = io.rikka.agent.ui.R.drawable.ic_share,
                ),
                contentDescription = stringResource(R.string.export_session),
                modifier = Modifier.size(20.dp),
              )
            }
          }
          when (connectionState) {
            ConnectionState.EXECUTING -> {
              IconButton(onClick = { vm.cancelRunning() }) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = stringResource(R.string.cancel),
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
            else -> {
              IconButton(onClick = onBack) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(R.string.back),
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
              ChatBubble(
                message = msg,
                onRerun = { cmd ->
                  if (connectionState != ConnectionState.EXECUTING) vm.send(cmd)
                },
                onShare = { content ->
                  val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                  }
                  context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.share_output))
                  )
                },
              )
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
            contentDescription = stringResource(R.string.scroll_to_bottom),
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
      title = { Text(stringResource(R.string.delete_session_title)) },
      text = { Text(stringResource(R.string.delete_session_msg, thread?.title?.ifBlank { stringResource(R.string.session_fallback_name) } ?: stringResource(R.string.session_fallback_name))) },
      confirmButton = {
        TextButton(onClick = { onDeleteThread(id); confirmDeleteId = null }) {
          Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteId = null }) { Text(stringResource(R.string.cancel)) }
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
        text = stringResource(R.string.sessions),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onNewSession) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_session))
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
          text = stringResource(R.string.no_past_sessions),
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
                text = thread.title.ifBlank { stringResource(R.string.session_fallback_name) },
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
                  contentDescription = stringResource(R.string.delete),
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
      stringResource(R.string.trust_host_title) to
        stringResource(R.string.trust_host_msg, event.host, event.port, event.keyType, event.fingerprint)
    }
    is HostKeyEvent.Mismatch -> {
      stringResource(R.string.host_key_changed_title) to
        stringResource(R.string.host_key_changed_msg, event.host, event.port, event.expectedFingerprint, event.actualFingerprint)
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
        Text(if (event is HostKeyEvent.Mismatch) stringResource(R.string.btn_replace_trust) else stringResource(R.string.btn_trust))
      }
    },
    dismissButton = {
      TextButton(onClick = onReject) {
        Text(stringResource(R.string.btn_reject))
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
      text = stringResource(R.string.empty_chat_hint),
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
  title: String = stringResource(R.string.ssh_password),
  label: String = stringResource(R.string.label_password),
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
          text = stringResource(R.string.password_prompt, label, target),
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
        Text(stringResource(R.string.btn_connect))
      }
    },
    dismissButton = {
      TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    },
  )
}
