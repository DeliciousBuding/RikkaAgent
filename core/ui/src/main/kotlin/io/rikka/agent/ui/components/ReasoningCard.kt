package io.rikka.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.rikka.agent.ui.R
import io.rikka.agent.ui.theme.LocalDarkMode

/**
 * Collapse/expand states for the reasoning card.
 */
private enum class ReasoningState {
    Collapsed,
    Expanded,
}

private val ReasoningShape = RoundedCornerShape(12.dp)

/**
 * Renders a [MessagePart.Reasoning] as a collapsible chain-of-thought card.
 *
 * Inspired by RikkaHub's ChainOfThought component:
 * - Collapsed by default with a "Thinking" label and expand chevron
 * - When streaming, auto-expands and shows a pulsing animation
 * - When streaming ends, auto-collapses
 * - Content is rendered as MarkdownText inside a scrollable container
 * - Uses a card with slightly elevated surface to distinguish from regular content
 */
@Composable
fun ReasoningCard(
    text: String,
    stepId: String?,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    var state by remember {
        mutableStateOf(
            if (isStreaming) ReasoningState.Expanded
            else ReasoningState.Collapsed
        )
    }

    // Auto-expand when streaming starts, auto-collapse when streaming ends
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            state = ReasoningState.Expanded
        }
        // Don't auto-collapse -- let user keep it open if they want
    }

    val isDark = LocalDarkMode.current
    val cardColors = CardDefaults.cardColors(
        containerColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
        shape = ReasoningShape,
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            // Header: always visible, clickable to toggle
            ReasoningHeader(
                isExpanded = state == ReasoningState.Expanded,
                isStreaming = isStreaming,
                stepId = stepId,
                onClick = {
                    state = when (state) {
                        ReasoningState.Collapsed -> ReasoningState.Expanded
                        ReasoningState.Expanded -> ReasoningState.Collapsed
                    }
                },
            )

            // Content: only visible when expanded
            AnimatedVisibility(
                visible = state == ReasoningState.Expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (isStreaming) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningHeader(
    isExpanded: Boolean,
    isStreaming: Boolean,
    stepId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReasoningShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Brain/thinking icon (using a lightbulb-like icon from drawable)
        Icon(
            painter = painterResource(id = R.drawable.ic_send), // fallback; ideally a brain icon
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
        )

        // Label
        val label = if (stepId != null) {
            stringResource(R.string.label_reasoning_step, stepId)
        } else {
            stringResource(R.string.label_reasoning)
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )

        if (isStreaming) {
            StreamingPulse()
        }

        Spacer(modifier = Modifier.weight(1f))

        // Expand/collapse indicator
        Text(
            text = if (isExpanded) "▲" else "▼",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

/**
 * Small pulsing dot to indicate streaming is active.
 */
@Composable
private fun StreamingPulse() {
    val transition = rememberInfiniteTransition(label = "reasoning_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    Surface(
        modifier = Modifier.size(6.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
    ) {}
}
