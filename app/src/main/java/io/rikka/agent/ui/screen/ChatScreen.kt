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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import io.rikka.agent.ssh.ConnectionState
import io.rikka.agent.vm.ExportFormat
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
  val bubbleOpacity by prefs.bubbleOpacity.collectAsStateWithLifecycle(initialValue = 1.0f)
  val connectionState by vm.connectionState.collectAsStateWithLifecycle()
  val connectionError by vm.lastConnectionError.collectAsStateWithLifecycle()
  val activeThreads by vm.activeThreads.collectAsStateWithLifecycle()
  val archivedThreads by vm.archivedThreads.collectAsStateWithLifecycle()
  val searchResults by vm.searchResults.collectAsStateWithLifecycle()
  val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
  val currentThreadId by vm.currentThreadId.collectAsStateWithLifecycle()
  val profileLabel by vm.profileLabel.collectAsStateWithLifecycle()
  val quickMessages by prefs.quickMessages.collectAsStateWithLifecycle(initialValue = emptyList())
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val startActivity = startActivityOverride ?: { intent -> context.startActivity(intent) }

  // Elapsed timer for running commands
  var elapsedSeconds by remember { mutableStateOf(0) }
  LaunchedEffect(connectionState) {
    if (connectionState == ConnectionState.Executing) {
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

  // Export format dialog state
  var showExportFormatDialog by remember { mutableStateOf(false) }

  // Tag input dialog state
  var tagInputThreadId by remember { mutableStateOf<String?>(null) }

  // Archive confirmation dialog state
  var archiveConfirmThreadId by remember { mutableStateOf<String?>(null) }
  var unarchiveConfirmThreadId by remember { mutableStateOf<String?>(null) }

  // Show export format dialog
  if (showExportFormatDialog) {
    ExportFormatDialog(
      onSelect = { format ->
        showExportFormatDialog = false
        val text = vm.exportSession(format)
        val mimeType = when (format) {
          ExportFormat.HTML -> "text/html"
          ExportFormat.JSON -> "application/json"
          else -> "text/plain"
        }
        startActivity(
          ShareIntents.sessionExport(
            text = text,
            subject = context.getString(R.string.ssh_session_subject, profileLabel),
            chooserTitle = context.getString(R.string.export_session),
          )
        )
      },
      onDismiss = { showExportFormatDialog = false },
    )
  }

  // Show tag input dialog
  tagInputThreadId?.let { threadId ->
    TagInputDialog(
      onAdd = { tag ->
        vm.addTag(threadId, tag)
        tagInputThreadId = null
      },
      onDismiss = { tagInputThreadId = null },
    )
  }

  // Show archive confirmation
  archiveConfirmThreadId?.let { id ->
    val thread = activeThreads.find { it.id == id }
    AlertDialog(
      onDismissRequest = { archiveConfirmThreadId = null },
      title = { Text(stringResource(R.string.archive_confirm_title)) },
      text = {
        Text(stringResource(R.string.archive_confirm_msg,
          thread?.title?.ifBlank { stringResource(R.string.session_fallback_name) }
            ?: stringResource(R.string.session_fallback_name)))
      },
      confirmButton = {
        TextButton(onClick = { vm.archiveThread(id); archiveConfirmThreadId = null }) {
          Text(stringResource(R.string.archive_session))
        }
      },
      dismissButton = {
        TextButton(onClick = { archiveConfirmThreadId = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Show unarchive confirmation
  unarchiveConfirmThreadId?.let { id ->
    val thread = archivedThreads.find { it.id == id }
    AlertDialog(
      onDismissRequest = { unarchiveConfirmThreadId = null },
      title = { Text(stringResource(R.string.unarchive_confirm_title)) },
      text = {
        Text(stringResource(R.string.unarchive_confirm_msg,
          thread?.title?.ifBlank { stringResource(R.string.session_fallback_name) }
            ?: stringResource(R.string.session_fallback_name)))
      },
      confirmButton = {
        TextButton(onClick = { vm.unarchiveThread(id); unarchiveConfirmThreadId = null }) {
          Text(stringResource(R.string.unarchive_session))
        }
      },
      dismissButton = {
        TextButton(onClick = { unarchiveConfirmThreadId = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

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

  val isStreaming = connectionState == ConnectionState.Executing

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
        activeThreads = activeThreads,
        archivedThreads = archivedThreads,
        searchResults = searchResults,
        searchQuery = searchQuery,
        currentThreadId = currentThreadId,
        onSearchQueryChange = { vm.setSearchQuery(it) },
        onNewSession = {
          vm.newSession()
          scope.launch { drawerState.close() }
        },
        onSelectThread = { threadId ->
          vm.switchThread(threadId)
          scope.launch { drawerState.close() }
        },
        onDeleteThread = { threadId -> vm.deleteThread(threadId) },
        onTogglePin = { threadId -> vm.togglePin(threadId) },
        onArchiveThread = { threadId -> archiveConfirmThreadId = threadId },
        onUnarchiveThread = { threadId -> unarchiveConfirmThreadId = threadId },
        onAddTag = { threadId -> tagInputThreadId = threadId },
        onRemoveTag = { threadId, tag -> vm.removeTag(threadId, tag) },
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
          onExportClick = { showExportFormatDialog = true },
          onCancelClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            vm.cancelRunning()
          },
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
          if (connectionState == ConnectionState.Executing) {
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
                  bubbleOpacity = bubbleOpacity,
                  showExpand = vm.hasFullOutput(msg.id),
                  onExpand = {
                    fullOutputDialog = vm.getFullOutput(msg.id)
                  },
                  onRerun = { cmd ->
                    if (connectionState != ConnectionState.Executing) vm.send(cmd)
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
            quickMessages = quickMessages,
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
          ConnectionState.Idle -> stringResource(R.string.status_connecting)
          ConnectionState.Ready -> stringResource(R.string.status_ready)
          ConnectionState.Executing -> stringResource(R.string.status_running, elapsedSeconds)
          is ConnectionState.Reconnecting -> stringResource(R.string.status_connecting)
          is ConnectionState.Disconnected -> stringResource(R.string.status_error)
          is ConnectionState.Failed -> stringResource(R.string.status_error)
        }
        val statusColor = when (connectionState) {
          ConnectionState.Ready -> MaterialTheme.colorScheme.primary
          ConnectionState.Executing -> MaterialTheme.colorScheme.tertiary
          is ConnectionState.Failed, is ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
          ConnectionState.Idle, is ConnectionState.Reconnecting -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        // Pulsing animation for active states (Idle = connecting, Executing = running, Reconnecting)
        val shouldPulse = connectionState == ConnectionState.Idle ||
          connectionState == ConnectionState.Executing ||
          connectionState is ConnectionState.Reconnecting
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDrawerContent(
  activeThreads: List<io.rikka.agent.model.ChatThread>,
  archivedThreads: List<io.rikka.agent.model.ChatThread>,
  searchResults: List<io.rikka.agent.model.ChatThread>,
  searchQuery: String,
  currentThreadId: String?,
  onSearchQueryChange: (String) -> Unit,
  onNewSession: () -> Unit,
  onSelectThread: (String) -> Unit,
  onDeleteThread: (String) -> Unit,
  onTogglePin: (String) -> Unit,
  onArchiveThread: (String) -> Unit,
  onUnarchiveThread: (String) -> Unit,
  onAddTag: (String) -> Unit,
  onRemoveTag: (String, String) -> Unit,
) {
  var confirmDeleteId by remember { mutableStateOf<String?>(null) }
  var showArchived by remember { mutableStateOf(false) }

  // Determine which list to display
  val displayThreads = when {
    searchQuery.isNotBlank() -> searchResults
    showArchived -> archivedThreads
    else -> activeThreads
  }

  // Delete confirmation dialog
  confirmDeleteId?.let { id ->
    val allThreads = activeThreads + archivedThreads
    val thread = allThreads.find { it.id == id }
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

    // Search bar
    TextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp),
      placeholder = {
        Text(
          text = stringResource(R.string.search_sessions),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      leadingIcon = {
        Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(18.dp))
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(32.dp)) {
            Icon(Lucide.X, contentDescription = stringResource(R.string.clear_search), modifier = Modifier.size(16.dp))
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
      ),
    )

    // Active / Archived filter chips (hidden during search)
    if (searchQuery.isBlank()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !showArchived,
          onClick = { showArchived = false },
          label = { Text(stringResource(R.string.filter_active), style = MaterialTheme.typography.labelSmall) },
        )
        FilterChip(
          selected = showArchived,
          onClick = { showArchived = true },
          label = { Text(stringResource(R.string.filter_archived), style = MaterialTheme.typography.labelSmall) },
        )
      }
    }

    if (displayThreads.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (searchQuery.isNotBlank()) stringResource(R.string.no_search_results)
                 else stringResource(R.string.no_past_sessions),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
      ) {
        items(displayThreads, key = { it.id }) { thread ->
          val isActive = thread.id == currentThreadId
          SessionItem(
            thread = thread,
            isActive = isActive,
            onClick = { onSelectThread(thread.id) },
            onDelete = { confirmDeleteId = thread.id },
            onTogglePin = { onTogglePin(thread.id) },
            onArchive = { onArchiveThread(thread.id) },
            onUnarchive = { onUnarchiveThread(thread.id) },
            onAddTag = { onAddTag(thread.id) },
            onRemoveTag = { tag -> onRemoveTag(thread.id, tag) },
            isArchivedView = showArchived,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionItem(
  thread: io.rikka.agent.model.ChatThread,
  isActive: Boolean,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  onTogglePin: () -> Unit,
  onArchive: () -> Unit,
  onUnarchive: () -> Unit,
  onAddTag: () -> Unit,
  onRemoveTag: (String) -> Unit,
  isArchivedView: Boolean,
) {
  var showContextMenu by remember { mutableStateOf(false) }

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

  Box {
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
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Pin icon or message icon
          if (thread.isPinned) {
            Icon(
              Lucide.Pin,
              contentDescription = stringResource(R.string.pin_session),
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
          } else {
            Icon(
              Lucide.MessageSquare,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = contentColor.copy(alpha = 0.6f),
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = thread.title.ifBlank { stringResource(R.string.session_fallback_name) },
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          // More actions button
          IconButton(
            onClick = { showContextMenu = true },
            modifier = Modifier.size(36.dp),
          ) {
            Icon(
              Lucide.MoreVertical,
              contentDescription = stringResource(R.string.more_actions),
              modifier = Modifier.size(16.dp),
              tint = contentColor.copy(alpha = 0.5f),
            )
          }
        }

        // Stats subtitle
        if (thread.stats.commandCount > 0) {
          Text(
            text = stringResource(
              R.string.session_stats_subtitle,
              thread.stats.commandCount,
              thread.stats.outputLineCount,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 28.dp),
          )
        }

        // Tags
        if (thread.tags.isNotEmpty()) {
          FlowRow(
            modifier = Modifier.padding(start = 28.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            thread.tags.forEach { tag ->
              AssistChip(
                onClick = { onRemoveTag(tag) },
                label = {
                  Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                  )
                },
                modifier = Modifier.height(24.dp),
                leadingIcon = {
                  Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.remove_tag),
                    modifier = Modifier.size(12.dp),
                  )
                },
                colors = AssistChipDefaults.assistChipColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
                border = null,
              )
            }
          }
        }
      }
    }

    // Context menu
    DropdownMenu(
      expanded = showContextMenu,
      onDismissRequest = { showContextMenu = false },
    ) {
      // Pin / Unpin
      DropdownMenuItem(
        text = { Text(stringResource(if (thread.isPinned) R.string.unpin_session else R.string.pin_session)) },
        onClick = { onTogglePin(); showContextMenu = false },
        leadingIcon = {
          Icon(
            if (thread.isPinned) Lucide.PinOff else Lucide.Pin,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        },
      )
      // Archive / Unarchive
      if (isArchivedView) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.unarchive_session)) },
          onClick = { onUnarchive(); showContextMenu = false },
          leadingIcon = { Icon(Lucide.ArchiveRestore, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
      } else {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.archive_session)) },
          onClick = { onArchive(); showContextMenu = false },
          leadingIcon = { Icon(Lucide.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
      }
      // Add tag
      DropdownMenuItem(
        text = { Text(stringResource(R.string.add_tag)) },
        onClick = { onAddTag(); showContextMenu = false },
        leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
      // Delete
      DropdownMenuItem(
        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
        onClick = { onDelete(); showContextMenu = false },
        leadingIcon = {
          Icon(
            Lucide.Trash2,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error,
          )
        },
      )
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
