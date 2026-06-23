package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rikka.agent.ui.R
import io.rikka.agent.ui.theme.LocalDarkMode
import io.rikka.agent.ui.util.AnsiStripper
import androidx.compose.ui.res.stringResource

private const val COLLAPSED_MAX_LINES = 15
private val SCROLLABLE_MAX_HEIGHT = 400.dp

/**
 * Renders stdout or stderr output in a terminal-style card.
 *
 * Visual design:
 * - Stdout: monospace text on neutral background
 * - Stderr: monospace text with reddish/orange tint on the left border
 * - Auto-collapse for long output (over [COLLAPSED_MAX_LINES] lines)
 * - Horizontal scroll for wide lines
 * - Vertical scroll with max height cap for very long output
 * - ANSI escape codes are stripped for display
 */
@Composable
fun StreamOutputCard(
    text: String,
    isStderr: Boolean,
    modifier: Modifier = Modifier,
) {
    val cleanText = remember(text) { AnsiStripper.strip(text) }
    val lines = remember(cleanText) { cleanText.lines() }
    val isLong = lines.size > COLLAPSED_MAX_LINES
    var expanded by remember { mutableStateOf(!isLong) }

    val displayText = if (expanded || !isLong) cleanText
    else lines.take(COLLAPSED_MAX_LINES).joinToString("\n")

    val shape = RoundedCornerShape(12.dp)
    val isDark = LocalDarkMode.current

    // Stderr gets a warmer tint; stdout is neutral
    val bgColor = if (isStderr) {
        if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = if (isStderr) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .animateContentSize(),
        color = bgColor,
        tonalElevation = 0.dp,
        shape = shape,
    ) {
        Column {
            // Left accent border + content
            Row(modifier = Modifier.fillMaxWidth()) {
                // Accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .heightIn(min = if (isLong && !expanded) SCROLLABLE_MAX_HEIGHT else 0.dp)
                        .background(borderColor)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!expanded && isLong) Modifier.heightIn(max = SCROLLABLE_MAX_HEIGHT)
                            else Modifier
                        ),
                ) {
                    // Label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isStderr) stringResource(R.string.label_stderr)
                            else stringResource(R.string.label_stdout),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isStderr) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }

                    // Output text
                    SelectionContainer {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = if (isStderr) {
                                if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Expand/collapse toggle
            if (isLong) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (expanded) stringResource(R.string.collapse_lines, lines.size)
                        else stringResource(R.string.show_all_lines, lines.size),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
