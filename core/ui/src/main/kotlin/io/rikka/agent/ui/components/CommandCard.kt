package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rikka.agent.ui.util.AnsiStripper

private val CommandCardShape = RoundedCornerShape(12.dp)

/**
 * Renders a [MessagePart.Command] as a terminal-style card.
 *
 * Visual design:
 * - Gray/dark header with `$` prompt and command text in monospace
 * - Exit code badge at the right (green for 0, red for non-null non-zero, spinner for running)
 * - Rounded corners, consistent with CodeCard
 *
 * ANSI escape sequences are stripped from the command text via [AnsiStripper].
 * Long command text is horizontally scrollable.
 *
 * @param command The shell command string to display. ANSI codes are stripped automatically.
 * @param exitCode The exit code of the command. `null` when the command has not finished.
 *   `0` renders a green badge; non-zero renders a red badge.
 * @param isRunning Whether the command is currently executing.
 *   When `true`, a pulsing "..." badge is shown instead of an exit code.
 * @param modifier Modifier applied to the outer [Surface].
 *
 * ```
 * // Completed command
 * CommandCard(
 *     command = "git log --oneline -5",
 *     exitCode = 0,
 *     isRunning = false,
 * )
 *
 * // Running command
 * CommandCard(
 *     command = "npm run build",
 *     exitCode = null,
 *     isRunning = true,
 * )
 * ```
 */
@Composable
fun CommandCard(
    command: String,
    exitCode: Int?,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val headerBg = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CommandCardShape)
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
        shape = CommandCardShape,
    ) {
        Column {
            // Header: prompt + command
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Prompt symbol
                Text(
                    text = "$",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Command text (scrollable for long commands)
                Text(
                    text = AnsiStripper.strip(command),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Exit code badge
                ExitCodeBadge(exitCode = exitCode, isRunning = isRunning)
            }
        }
    }
}

/**
 * Small colored badge showing the exit code of a command.
 *
 * - Running: pulsing gray dot
 * - Success (0): green "exit 0"
 * - Failure (non-zero): red "exit N"
 */
@Composable
private fun ExitCodeBadge(
    exitCode: Int?,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val (text, bgColor, fgColor) = when {
        isRunning -> Triple(
            "...",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        exitCode == 0 -> Triple(
            "exit 0",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
        )
        else -> Triple(
            "exit ${exitCode ?: "?"}",
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.error,
        )
    }

    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = fgColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
