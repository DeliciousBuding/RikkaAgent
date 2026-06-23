package io.rikka.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ui.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main chat bubble component for RikkaAgent.
 *
 * Renders a [ChatMessage] whose [ChatMessage.parts] are dispatched to
 * type-specific renderers: [MessagePart.Text] -> MarkdownText or plain Text,
 * [MessagePart.Code] -> CodeCard, [MessagePart.Command] -> CommandCard,
 * [MessagePart.Stdout]/[Stderr] -> StreamOutputCard, [MessagePart.Error] ->
 * ErrorCard, [MessagePart.Reasoning] -> ReasoningCard, [MessagePart.Mermaid] ->
 * MermaidDiagramCard.
 *
 * Preserves the RikkaHub visual style: asymmetric rounded corners, user/assistant
 * color distinction, and a bottom action bar with copy, rerun, and share.
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    enableMermaid: Boolean = false,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    onRerun: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
    showExpand: Boolean = false,
    onExpand: (() -> Unit)? = null,
    onShareFull: (() -> Unit)? = null,
) {
    val isUser = message.role == ChatRole.User
    val isError = message.status == MessageStatus.Error
    val isCanceled = message.status == MessageStatus.Canceled
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
        isCanceled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isCanceled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (isUser) {
            // User messages: chat bubble style with plain text
            UserBubble(
                message = message,
                bubbleShape = bubbleShape,
                bubbleColor = bubbleColor,
                contentColor = contentColor,
                contentPadding = contentPadding,
            )
        } else if (isStreaming && message.parts.isEmpty()) {
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
            // Assistant messages: dispatch each part to its renderer
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MessagePartsBlock(
                    message = message,
                    enableMermaid = enableMermaid,
                    isStreaming = isStreaming,
                )
                if (isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    StreamingDots()
                }
            }
        }

        // Timestamp + action row
        val showActions = !isStreaming && message.parts.isNotEmpty()
        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            ActionBar(
                message = message,
                isUser = isUser,
                showExpand = showExpand,
                onRerun = onRerun,
                onShare = onShare,
                onExpand = onExpand,
                onShareFull = onShareFull,
            )
        }
    }
}

// ── User bubble ──────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(
    message: ChatMessage,
    bubbleShape: RoundedCornerShape,
    bubbleColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
) {
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
                text = message.textContent,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

// ── Action bar ───────────────────────────────────────────────────────────────

@Composable
private fun ActionBar(
    message: ChatMessage,
    isUser: Boolean,
    showExpand: Boolean,
    onRerun: ((String) -> Unit)?,
    onShare: ((String) -> Unit)?,
    onExpand: (() -> Unit)?,
    onShareFull: (() -> Unit)?,
) {
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
        if (isUser && onRerun != null && message.textContent.isNotBlank()) {
            RerunButton(command = message.textContent, onRerun = onRerun)
        }
        if (message.parts.isNotEmpty()) {
            CopyButton(content = message.partsToCopyableText())
            if (!isUser && showExpand && onExpand != null) {
                ExpandButton(onExpand = onExpand)
            }
            if (!isUser && onShare != null) {
                ShareButton(content = message.partsToCopyableText(), onShare = onShare)
            }
            if (!isUser && showExpand && onShareFull != null) {
                ShareFullButton(onShareFull = onShareFull)
            }
        }
    }
}

// ── Copyable text extraction ─────────────────────────────────────────────────

/**
 * Extracts a human-readable copyable representation from all parts.
 * Structured parts (Command, Stdout, Stderr, Error) are formatted with markers.
 */
private fun ChatMessage.partsToCopyableText(): String = buildString {
    parts.forEachIndexed { index, part ->
        if (index > 0) append("\n")
        when (part) {
            is MessagePart.Text -> append(part.text)
            is MessagePart.Code -> {
                val lang = part.language ?: ""
                append("```$lang\n${part.code}\n```")
            }
            is MessagePart.Command -> {
                append("$ ${part.command}")
                part.exitCode?.let { code ->
                    append("\n[exit $code]")
                }
            }
            is MessagePart.Stdout -> append(part.text)
            is MessagePart.Stderr -> append("[stderr] ${part.text}")
            is MessagePart.Error -> {
                append("[Error] ${part.message}")
                part.cause?.let { append("\n  Cause: $it") }
            }
            is MessagePart.Reasoning -> append("[Reasoning] ${part.text}")
            is MessagePart.Mermaid -> append("[Mermaid]\n${part.definition}")
        }
    }
}

// ── Streaming dots ───────────────────────────────────────────────────────────

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

// ── Action icon buttons ──────────────────────────────────────────────────────

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
private fun ExpandButton(onExpand: () -> Unit) {
    Text(
        text = stringResource(R.string.expand_output),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable { onExpand() },
    )
}

@Composable
private fun ShareFullButton(onShareFull: () -> Unit) {
    IconButton(
        onClick = { onShareFull() },
        modifier = Modifier.size(24.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_share),
            contentDescription = stringResource(R.string.cd_share_full),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ── Timestamp formatting ─────────────────────────────────────────────────────

@Composable
private fun formatTimestamp(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> stringResource(R.string.time_just_now)
        diff < TimeUnit.HOURS.toMillis(1) -> stringResource(
            R.string.time_minutes_ago,
            TimeUnit.MILLISECONDS.toMinutes(diff).toInt()
        )
        diff < TimeUnit.DAYS.toMillis(1) -> stringResource(
            R.string.time_hours_ago,
            TimeUnit.MILLISECONDS.toHours(diff).toInt()
        )
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}
