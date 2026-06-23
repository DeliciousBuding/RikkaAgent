package io.rikka.agent.ui.components

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lightweight error information for [ErrorCard] display.
 *
 * @param id Unique identifier for the error (used as dismiss key).
 * @param title Optional short title / category.
 * @param message The error message body.
 * @param actionLabel Optional action link label (e.g. "Check settings").
 * @param onAction Optional callback when the action link is tapped.
 */
data class ErrorInfo(
    val id: String,
    val title: String? = null,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Animated container that displays a stack of [ErrorCard]s.
 *
 * @param errors List of active errors.
 * @param onDismissError Callback when a single error is dismissed.
 * @param onClearAllErrors Callback when the "clear all" button is tapped.
 * @param modifier Modifier applied to the outer AnimatedVisibility.
 * @param autoDismissMs Auto-dismiss delay in milliseconds; 0 disables auto-dismiss.
 */
@Composable
fun ErrorCardsDisplay(
    errors: List<ErrorInfo>,
    onDismissError: (String) -> Unit,
    onClearAllErrors: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5000L,
) {
    AnimatedVisibility(
        visible = errors.isNotEmpty(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Clear-all button (visible when multiple errors exist)
            if (errors.size > 1) {
                Surface(
                    onClick = onClearAllErrors,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "Clear all",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Error card list
            errors.forEach { error ->
                ErrorCard(
                    error = error,
                    onDismiss = { onDismissError(error.id) },
                    autoDismissMs = autoDismissMs,
                )
            }
        }
    }
}

/**
 * A single dismissible error card with optional action link and copy-to-clipboard.
 *
 * @param error The [ErrorInfo] to display.
 * @param onDismiss Callback when the card is dismissed (by user or auto-dismiss).
 * @param modifier Modifier applied to the card.
 * @param autoDismissMs Auto-dismiss delay in milliseconds; 0 disables auto-dismiss.
 */
@Composable
fun ErrorCard(
    error: ErrorInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5000L,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Auto-dismiss after delay
    if (autoDismissMs > 0) {
        LaunchedEffect(error.id) {
            delay(autoDismissMs)
            onDismiss()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (error.title != null) {
                    Text(
                        text = error.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    overflow = TextOverflow.Ellipsis,
                )
                if (error.actionLabel != null && error.onAction != null) {
                    Text(
                        text = error.actionLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { error.onAction() }
                            .padding(top = 2.dp),
                    )
                }
            }
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(
                                clipData = ClipData.newPlainText("Error", error.message)
                            )
                        )
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy error message",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
