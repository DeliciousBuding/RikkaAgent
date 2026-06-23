package io.rikka.agent.ui.screen

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.R
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.ui.components.ChatBubble
import io.rikka.agent.ui.components.ChatInput
import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.vm.ConnectionError
import io.rikka.agent.vm.ConnectionState
import io.rikka.agent.vm.HostKeyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lucide.icons.Lucide
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private val SessionItemShape = RoundedCornerShape(12.dp)
private val ErrorBannerShape = RoundedCornerShape(12.dp)
private val SuggestionCommands = listOf("uname -a", "df -h", "uptime")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  profileId: String = "",
  onBack: () -> Unit = {},
  vmOverride: ChatViewModel? = null,
  prefsOverride: AppPreferences? = null,
  startActivityOverride: ((Intent) -> Unit)? = null,
) {
  val vm: ChatViewModel = vmOverride ?: koinViewModel { parametersOf(profileId) }
  val prefs: AppPreferences = prefsOverride ?: koinInject()
  val messages by vm.messages.collectAsStateWithLifecycle()
  val enableMermaid by prefs.enableMermaid.collectAsStateWithLifecycle(initialValue = false)
  val connectionState by vm.connectionState.collectAsStateWithLifecycle()
  val connectionError by vm.lastConnectionError.collectAsStateWithLifecycle()
  val threads by vm.threads.collectAsStateWithLifecycle()
  val profileLabel by vm.profileLabel.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val context = LocalContext.current
  val startActivity = startActivityOverride ?: { intent -> context.startActivity(intent) }

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
  var hostKeyDialogState by remember { mutableStateOf(HostKeyDialogState()) }

  // Password dialog state
  var passwordTarget by remember { mutableStateOf<String?>(null) }

  // Passphrase dialog state
  var passphraseTarget by remember { mutableStateOf<String?>(null) }
  var fullOutputDialog by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    vm.hostKeyEvent.collect { event ->
      hostKeyDialogState = HostKeyDialogStateMachine.receive(event)
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
  hostKeyDialogState.event?.let { event ->
    HostKeyDialog(
      event = event,
      onAccept = {
        val result = HostKeyDialogStateMachine.acceptPrimary(hostKeyDialogState)
        hostKeyDialogState = result.nextState
        result.decision?.let(vm::respondToHostKey)
      },
      onReject = {
        val result = HostKeyDialogStateMachine.rejectPrimary()
        hostKeyDialogState = result.nextState
        result.decision?.let(vm::respondToHostKey)
      },
    )
  }

  hostKeyDialogState.confirmReplacement?.let { event ->
    HostKeyReplacementConfirmDialog(
      event = event,
      onConfirm = {
        val result = HostKeyDialogStateMachine.confirmReplacement(accepted = true)
        hostKeyDialogState = result.nextState
        result.decision?.let(vm::respondToHostKey)
      },
      onReject = {
        val result = HostKeyDialogStateMachine.confirmReplacement(accepted = false)
        hostKeyDialogState = result.nextState
        result.decision?.let(vm::respondToHostKey)
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

  // Show complete output dialog for truncated content
  fullOutputDialog?.let { fullText ->
    FullOutputDialog(
      fullText = fullText,
      onShare = {
        startActivity(
          ShareIntents.plainText(
            text = fullText,
            chooserTitle = context.getString(R.string.share_full_output),
          )
        )
      },
      onDismiss = { fullOutputDialog = null },
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
        currentThreadId = threads.firstOrNull()?.id,
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
        ChatTopAppBar(
          profileLabel = profileLabel,
          connectionState = connectionState,
          elapsedSeconds = elapsedSeconds,
          hasMessages = messages.isNotEmpty(),
          isStreaming = isStreaming,
          onMenuClick = { scope.launch { drawerState.open() } },
          onExportClick = {
            val text = vm.exportSession()
            startActivity(
              ShareIntents.sessionExport(
                text = text,
                subject = context.getString(R.string.ssh_session_subject, profileLabel),
                chooserTitle = context.getString(R.string.export_session),
              )
            )
          },
          onCancelClick = { vm.cancelRunning() },
          onBackClick = onBack,
        )
      },
      containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Progress bar when executing
          if (connectionState == ConnectionState.EXECUTING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }

          // Connection error banner (RikkaHub ErrorCard style)
          ConnectionErrorBanner(
            error = connectionError,
            onDismiss = { vm.dismissConnectionError() },
          )

          if (messages.isEmpty()) {
            EmptySessionState(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              onSuggestionClick = { cmd ->
                vm.send(cmd)
              },
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
                  enableMermaid = enableMermaid,
                  showExpand = vm.hasFullOutput(msg.id),
                  onExpand = {
                    fullOutputDialog = vm.getFullOutput(msg.id)
                  },
                  onRerun = { cmd ->
                    if (connectionState != ConnectionState.EXECUTING) vm.send(cmd)
                  },
                  onShare = { content ->
                    startActivity(
                      ShareIntents.plainText(
                        text = content,
                        chooserTitle = context.getString(R.string.share_output),
                      )
                    )
                  },
                  onShareFull = {
                    vm.getFullOutput(msg.id)?.let { fullText ->
                      startActivity(
                        ShareIntents.plainText(
                          text = fullText,
                          chooserTitle = context.getString(R.string.share_full_output),
                        )
                      )
                    }
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
              Lucide.ChevronDown,
              contentDescription = stringResource(R.string.scroll_to_bottom),
              modifier = Modifier.size(20.dp),
            )
          }
        }

        // ChatInput at bottom
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
          ChatInput(
            enabled = !isStreaming,
            onSend = { text -> vm.send(text) },
          )
        }
      }
    }
  }
}

// ── Top App Bar ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopAppBar(
  profileLabel: String,
  connectionState: ConnectionState,
  elapsedSeconds: Int,
  hasMessages: Boolean,
  isStreaming: Boolean,
  onMenuClick: () -> Unit,
  onExportClick: () -> Unit,
  onCancelClick: () -> Unit,
  onBackClick: () -> Unit,
) {
  TopAppBar(
    title = {
      Column {
        Text(
          text = profileLabel.ifBlank { stringResource(R.string.session_fallback_name) },
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        // Connection status indicator (RikkaHub-aligned)
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
          ConnectionState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        // Pulsing animation for active states (IDLE = connecting, EXECUTING = running)
        val shouldPulse = connectionState == ConnectionState.IDLE ||
          connectionState == ConnectionState.EXECUTING
        val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
          initialValue = 1f,
          targetValue = 0.3f,
          animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
          ),
          label = "statusPulseAlpha",
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Animated dot indicator
          Surface(
            modifier = Modifier
              .size(8.dp)
              .graphicsLayer { alpha = if (shouldPulse) pulseAlpha else 1f },
            shape = CircleShape,
            color = statusColor,
          ) {}
          Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
          )
        }
      }
    },
    navigationIcon = {
      IconButton(onClick = onMenuClick) {
        Icon(Lucide.Menu, contentDescription = stringResource(R.string.sessions))
      }
    },
    actions = {
      // Export button -- visible when messages exist and not executing
      if (hasMessages && !isStreaming) {
        IconButton(onClick = onExportClick) {
          Icon(
            Lucide.Share,
            contentDescription = stringResource(R.string.export_session),
            modifier = Modifier.size(20.dp),
          )
        }
      }
      // Cancel button when executing
      if (isStreaming) {
        IconButton(onClick = onCancelClick) {
          Icon(
            Lucide.X,
            contentDescription = stringResource(R.string.cancel),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      } else {
        IconButton(onClick = onBackClick) {
          Icon(
            Lucide.ArrowLeft,
            contentDescription = stringResource(R.string.back),
          )
        }
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.background,
    ),
  )
}

// ── Connection Error Banner (RikkaHub ErrorCard style) ───────────────────────

/**
 * Maps a connection error category to a user-friendly title and icon.
 * Styled after RikkaHub's ErrorCard pattern: Surface with errorContainer color,
 * RoundedCornerShape(12.dp), shadowElevation = 4.dp, copy + dismiss actions.
 */
@Composable
private fun ConnectionErrorBanner(
  error: ConnectionError?,
  onDismiss: () -> Unit,
) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  AnimatedVisibility(
    visible = error != null,
    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
  ) {
    error ?: return@AnimatedVisibility

    val bannerColor = when (error.category) {
      "key_mismatch" -> MaterialTheme.colorScheme.errorContainer
      else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (error.category) {
      "key_mismatch" -> MaterialTheme.colorScheme.onErrorContainer
      else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when (error.category) {
      "connection_refused" -> Lucide.WifiOff
      "timeout" -> Lucide.Clock
      "unknown_host" -> Lucide.CircleHelp
      "auth_failed" -> Lucide.KeyRound
      "key_mismatch" -> Lucide.ShieldAlert
      else -> Lucide.AlertTriangle
    }
    val title = when (error.category) {
      "connection_refused" -> stringResource(R.string.err_banner_connection_refused)
      "timeout" -> stringResource(R.string.err_banner_timeout)
      "unknown_host" -> stringResource(R.string.err_banner_unknown_host)
      "auth_failed" -> stringResource(R.string.err_banner_auth_failed)
      "key_mismatch" -> stringResource(R.string.err_banner_key_mismatch)
      else -> stringResource(R.string.err_banner_generic)
    }

    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      shape = ErrorBannerShape,
      color = bannerColor,
      shadowElevation = 4.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = contentColor,
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = error.message,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        // Copy button
        IconButton(
          onClick = {
            scope.launch {
              clipboard.setClipEntry(
                ClipEntry(ClipData.newPlainText("ConnectionError", "$title: ${error.message}"))
              )
            }
          },
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = Lucide.Copy,
            contentDescription = stringResource(R.string.copy),
            tint = contentColor,
            modifier = Modifier.size(16.dp),
          )
        }
        // Dismiss button
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = Lucide.X,
            contentDescription = stringResource(R.string.cancel),
            tint = contentColor,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

// ── Session Drawer ───────────────────────────────────────────────────────────

@Composable
private fun SessionDrawerContent(
  threads: List<io.rikka.agent.model.ChatThread>,
  currentThreadId: String?,
  onNewSession: () -> Unit,
  onSelectThread: (String) -> Unit,
  onDeleteThread: (String) -> Unit,
) {
  var confirmDeleteId by remember { mutableStateOf<String?>(null) }

  // Delete confirmation dialog
  confirmDeleteId?.let { id ->
    val thread = threads.find { it.id == id }
    AlertDialog(
      onDismissRequest = { confirmDeleteId = null },
      title = { Text(stringResource(R.string.delete_session_title)) },
      text = {
        Text(
          stringResource(
            R.string.delete_session_msg,
            thread?.title?.ifBlank { stringResource(R.string.session_fallback_name) }
              ?: stringResource(R.string.session_fallback_name),
          )
        )
      },
      confirmButton = {
        TextButton(onClick = { onDeleteThread(id); confirmDeleteId = null }) {
          Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteId = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
    // Header: title + new session button
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.sessions),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = onNewSession,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          Lucide.Plus,
          contentDescription = stringResource(R.string.new_session),
          modifier = Modifier.size(20.dp),
        )
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
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
      ) {
        items(threads, key = { it.id }) { thread ->
          val isActive = thread.id == currentThreadId
          SessionItem(
            title = thread.title.ifBlank { stringResource(R.string.session_fallback_name) },
            isActive = isActive,
            onClick = { onSelectThread(thread.id) },
            onDelete = { confirmDeleteId = thread.id },
          )
        }
      }
    }
  }
}

@Composable
private fun SessionItem(
  title: String,
  isActive: Boolean,
  onClick: () -> Unit,
  onDelete: () -> Unit,
) {
  val containerColor = if (isActive) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    MaterialTheme.colorScheme.surface
  }
  val contentColor = if (isActive) {
    MaterialTheme.colorScheme.onSecondaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 2.dp)
      .clip(SessionItemShape)
      .clickable { onClick() },
    shape = SessionItemShape,
    color = containerColor,
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Lucide.MessageSquare,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = contentColor.copy(alpha = 0.6f),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          Lucide.Trash2,
          contentDescription = stringResource(R.string.delete),
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
      }
    }
  }
}

// ── Empty State ──────────────────────────────────────────────────────────────

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
      SuggestionCommands.forEach { cmd ->
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

// ── Dialogs ──────────────────────────────────────────────────────────────────

/**
 * Password / passphrase dialog, styled with Material3 Surface cards.
 * Includes a visibility toggle for the input field.
 */
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
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Lucide.KeyRound,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Info card
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Text(
            text = stringResource(R.string.password_prompt, label, target),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
        // Input field with visibility toggle
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
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
              Icon(
                imageVector = if (passwordVisible) Lucide.EyeOff else Lucide.Eye,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
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

/**
 * Host key verification dialog, redesigned with ErrorCard-style warning surface.
 * Uses a colored warning card with icon instead of plain AlertDialog text.
 */
@Composable
internal fun HostKeyDialog(
  event: HostKeyEvent,
  onAccept: () -> Unit,
  onReject: () -> Unit,
) {
  val isMismatch = event is HostKeyEvent.Mismatch
  val warningColor = if (isMismatch) {
    MaterialTheme.colorScheme.errorContainer
  } else {
    MaterialTheme.colorScheme.secondaryContainer
  }
  val warningContentColor = if (isMismatch) {
    MaterialTheme.colorScheme.onErrorContainer
  } else {
    MaterialTheme.colorScheme.onSecondaryContainer
  }
  val title = if (isMismatch) {
    stringResource(R.string.host_key_changed_title)
  } else {
    stringResource(R.string.trust_host_title)
  }
  val icon = if (isMismatch) Lucide.ShieldAlert else Lucide.ShieldQuestion

  AlertDialog(
    onDismissRequest = onReject,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
          tint = if (isMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Warning card (ErrorCard style)
        Surface(
          shape = ErrorBannerShape,
          color = warningColor,
          shadowElevation = 2.dp,
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val detailText = when (event) {
              is HostKeyEvent.UnknownHost -> stringResource(
                R.string.trust_host_msg,
                event.host,
                event.port,
                event.keyType,
                event.fingerprint,
              )
              is HostKeyEvent.Mismatch -> stringResource(
                R.string.host_key_changed_msg,
                event.host,
                event.port,
                event.expectedFingerprint,
                event.actualFingerprint,
              )
            }
            Text(
              text = detailText,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = warningContentColor,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onAccept) {
        Text(
          text = if (isMismatch) stringResource(R.string.btn_replace_trust) else stringResource(R.string.btn_trust),
          color = if (isMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onReject) {
        Text(stringResource(R.string.btn_reject))
      }
    },
  )
}

/**
 * Host key replacement confirmation dialog.
 * Uses a prominent warning card for the final confirmation step.
 */
@Composable
internal fun HostKeyReplacementConfirmDialog(
  event: HostKeyEvent.Mismatch,
  onConfirm: () -> Unit,
  onReject: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onReject,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Lucide.AlertTriangle,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
          tint = MaterialTheme.colorScheme.error,
        )
        Text(
          text = stringResource(R.string.host_key_replace_confirm_title),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    text = {
      Surface(
        shape = ErrorBannerShape,
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 2.dp,
      ) {
        Text(
          text = stringResource(
            R.string.host_key_replace_confirm_msg,
            event.host,
            event.port,
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(12.dp),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          text = stringResource(R.string.host_key_replace_confirm_action),
          color = MaterialTheme.colorScheme.error,
        )
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
internal fun FullOutputDialog(
  fullText: String,
  onShare: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.full_output_title)) },
    text = {
      Column(modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
        Text(text = fullText, fontFamily = FontFamily.Monospace)
      }
    },
    confirmButton = {
      TextButton(onClick = onShare) {
        Text(stringResource(R.string.share_full_output))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.close))
      }
    },
  )
}
