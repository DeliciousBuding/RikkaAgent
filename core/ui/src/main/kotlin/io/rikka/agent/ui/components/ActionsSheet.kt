package io.rikka.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rikka.agent.ui.R
import lucide.icons.Lucide

/**
 * A modal bottom sheet providing message-level actions for [io.rikka.agent.model.ChatMessage].
 *
 * Modeled after RikkaHub's `ChatMessageActionsSheet`: each action is rendered
 * as a [Card] containing an icon and a label. The delete action uses
 * [MaterialTheme.colorScheme.errorContainer] to signal danger.
 *
 * @param onDelete Called when the user taps "Delete".
 * @param onEdit Called when the user taps "Edit". May be `null` to hide the item.
 * @param onRerun Called when the user taps "Re-run". May be `null` to hide the item.
 * @param onCopy Called when the user taps "Copy".
 * @param onShare Called when the user taps "Share".
 * @param onFullOutput Called when the user taps "Complete output". May be `null` to hide.
 * @param onDismissRequest Called when the sheet should be dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsSheet(
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onRerun: (() -> Unit)? = null,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onFullOutput: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Copy
            ActionItem(
                icon = Lucide.Copy,
                label = stringResource(R.string.action_copy),
                onClick = {
                    onDismissRequest()
                    onCopy()
                },
            )

            // Edit
            if (onEdit != null) {
                ActionItem(
                    icon = Lucide.Pencil,
                    label = stringResource(R.string.action_edit),
                    onClick = {
                        onDismissRequest()
                        onEdit()
                    },
                )
            }

            // Re-run
            if (onRerun != null) {
                ActionItem(
                    icon = Lucide.RefreshCw,
                    label = stringResource(R.string.action_rerun),
                    onClick = {
                        onDismissRequest()
                        onRerun()
                    },
                )
            }

            // Share
            ActionItem(
                icon = Lucide.Share2,
                label = stringResource(R.string.action_share),
                onClick = {
                    onDismissRequest()
                    onShare()
                },
            )

            // Complete output
            if (onFullOutput != null) {
                ActionItem(
                    icon = Lucide.Maximize2,
                    label = stringResource(R.string.action_full_output),
                    onClick = {
                        onDismissRequest()
                        onFullOutput()
                    },
                )
            }

            // Delete (danger style)
            ActionItem(
                icon = Lucide.Trash2,
                label = stringResource(R.string.action_delete),
                onClick = {
                    onDismissRequest()
                    onDelete()
                },
                isDestructive = true,
            )
        }
    }
}

/**
 * A single action row inside [ActionsSheet].
 *
 * @param icon Lucide icon to display.
 * @param label Human-readable action label.
 * @param onClick Invoked when the card is tapped.
 * @param isDestructive When `true`, the card uses [MaterialTheme.colorScheme.errorContainer].
 */
@Composable
private fun ActionItem(
    icon: lucide.icons.Lucide.Icon,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = if (isDestructive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
