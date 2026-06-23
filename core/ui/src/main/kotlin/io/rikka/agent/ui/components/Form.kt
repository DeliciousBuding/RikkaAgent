package io.rikka.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A horizontal form item layout with label, description, content, and optional tail widget.
 *
 * Arranges a left-aligned label/description column alongside a right-aligned tail
 * (e.g. a [Switch]). The content slot sits below the label and description.
 *
 * @param modifier Modifier applied to the outer [Row].
 * @param label Primary label composable, rendered with [MaterialTheme.typography.titleMedium].
 * @param description Optional description text below the label, rendered with
 *   [MaterialTheme.typography.labelSmall] at 60% opacity.
 * @param tail Optional trailing widget displayed to the right of the label column,
 *   commonly used for [Switch] or [Checkbox].
 * @param content Optional content slot below the label/description, used for
 *   text fields, sliders, or other input controls.
 *
 * ```
 * FormItem(
 *     label = { Text("Enable notifications") },
 *     description = { Text("Receive push alerts for new messages") },
 *     tail = {
 *         Switch(
 *             checked = isEnabled,
 *             onCheckedChange = { isEnabled = it },
 *         )
 *     },
 * )
 *
 * FormItem(
 *     label = { Text("API Key") },
 *     description = { Text("Your OpenAI API key") },
 *     content = {
 *         OutlinedTextField(
 *             value = apiKey,
 *             onValueChange = { apiKey = it },
 *         )
 *     },
 * )
 * ```
 */
@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.weight(1f)
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.titleMedium
            ) {
                label()
            }
            ProvideTextStyle(
                value = MaterialTheme.typography.labelSmall.copy(
                    color = LocalContentColor.current.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    description?.invoke()
                }
            }
            content()
        }
        tail()
    }
}

@Preview(showBackground = true)
@Composable
private fun FormItemPreview() {
    FormItem(
        label = { Text("Label") },
        content = {
            OutlinedTextField(
                value = "",
                onValueChange = {}
            )
        },
        description = {
            Text("Description")
        },
        tail = {
            Switch(
                checked = true,
                onCheckedChange = {}
            )
        },
        modifier = Modifier.padding(4.dp),
    )
}
