package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach

private val LocalCardColor = staticCompositionLocalOf { Color.White }

/**
 * Displays a list of reasoning / tool-call steps as a timeline card.
 *
 * Supports auto-collapsing when the step count exceeds [collapsedVisibleCount],
 * with an expand/collapse control bar at the top.
 *
 * @param modifier Modifier applied to the outer card.
 * @param cardColors Card color configuration.
 * @param steps The list of step data to render.
 * @param collapsedVisibleCount Number of trailing steps visible when collapsed.
 * @param collapsedAdaptiveWidth Whether the collapsed state uses content-adaptive width.
 * @param content Composable lambda that renders each step via [ChainOfThoughtScope].
 */
@Composable
fun <T> ChainOfThought(
    modifier: Modifier = Modifier,
    cardColors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    steps: List<T>,
    collapsedVisibleCount: Int = 2,
    collapsedAdaptiveWidth: Boolean = false,
    content: @Composable ChainOfThoughtScope.(T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val canCollapse = steps.size > collapsedVisibleCount
    val shouldFillCollapseControlWidth = expanded || !collapsedAdaptiveWidth

    CompositionLocalProvider(
        LocalCardColor provides cardColors.containerColor
    ) {
        Card(
            modifier = modifier,
            colors = cardColors,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
            ) {
                val visibleSteps = if (expanded || !canCollapse) {
                    steps
                } else {
                    steps.takeLast(collapsedVisibleCount)
                }

                // Expand / collapse toggle bar (always at the top)
                if (canCollapse) {
                    Row(
                        modifier = Modifier
                            .then(
                                if (shouldFillCollapseControlWidth) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier
                                }
                            )
                            .clip(MaterialTheme.shapes.small)
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left icon area (24.dp, aligned with step icons)
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Right text area
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = if (expanded) {
                                "Collapse"
                            } else {
                                "Show ${steps.size - collapsedVisibleCount} more steps"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                val lineColor = MaterialTheme.colorScheme.outlineVariant
                val scope = remember { ChainOfThoughtScopeImpl() }
                Box(
                    modifier = Modifier.drawBehind {
                        val x = 12.dp.toPx()
                        val offsetPx = 18.dp.toPx()
                        drawLine(
                            color = lineColor,
                            start = Offset(x, offsetPx),
                            end = Offset(x, size.height - offsetPx),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                ) {
                    Column {
                        visibleSteps.fastForEach { step ->
                            scope.content(step)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Scope for building individual [ChainOfThought] steps.
 *
 * Provides both uncontrolled and controlled step declarations with
 * unified timeline layout and interaction behavior.
 */
interface ChainOfThoughtScope {
    /**
     * Declares an uncontrolled step (expand/collapse managed internally).
     *
     * @param icon Step icon composable.
     * @param label Step title area.
     * @param extra Additional info displayed to the right of the title.
     * @param onClick Custom click handler; takes priority over expand/collapse when set.
     * @param collapsedAdaptiveWidth Whether to use content-adaptive width when collapsed and content is hidden.
     * @param content Expandable content; `null` makes the step non-expandable.
     */
    @Composable
    fun ChainOfThoughtStep(
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        collapsedAdaptiveWidth: Boolean = false,
        content: (@Composable () -> Unit)? = null,
    )

    /**
     * Declares a controlled step with externally managed expand state.
     *
     * Suitable for scenarios that need to sync with external state,
     * e.g. "show preview while reasoning / collapse when done".
     *
     * @param expanded Current expand state.
     * @param onExpandedChange Expand state change callback.
     * @param icon Step icon composable.
     * @param label Step title area.
     * @param extra Additional info displayed to the right of the title.
     * @param onClick Custom click handler; takes priority over expand/collapse when set.
     * @param collapsedAdaptiveWidth Whether to use content-adaptive width when collapsed and content is hidden.
     * @param contentVisible Whether content area is visible; can be decoupled from [expanded].
     * @param content Step content; `null` makes the step non-expandable.
     */
    @Composable
    fun ControlledChainOfThoughtStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: (@Composable () -> Unit)? = null,
        label: (@Composable () -> Unit),
        extra: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        collapsedAdaptiveWidth: Boolean = false,
        contentVisible: Boolean = expanded,
        content: (@Composable () -> Unit)? = null,
    )
}

private class ChainOfThoughtScopeImpl : ChainOfThoughtScope {
    @Composable
    override fun ChainOfThoughtStep(
        icon: @Composable (() -> Unit)?,
        label: @Composable (() -> Unit),
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        collapsedAdaptiveWidth: Boolean,
        content: @Composable (() -> Unit)?
    ) {
        var expanded by remember { mutableStateOf(false) }
        ChainOfThoughtStepContent(
            icon = icon,
            label = label,
            extra = extra,
            onClick = onClick,
            collapsedAdaptiveWidth = collapsedAdaptiveWidth,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            contentVisible = expanded,
            content = content,
        )
    }

    @Composable
    override fun ControlledChainOfThoughtStep(
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        icon: @Composable (() -> Unit)?,
        label: @Composable (() -> Unit),
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        collapsedAdaptiveWidth: Boolean,
        contentVisible: Boolean,
        content: @Composable (() -> Unit)?
    ) {
        ChainOfThoughtStepContent(
            icon = icon,
            label = label,
            extra = extra,
            onClick = onClick,
            collapsedAdaptiveWidth = collapsedAdaptiveWidth,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            contentVisible = contentVisible,
            content = content,
        )
    }

    @Composable
    private fun ChainOfThoughtStepContent(
        icon: @Composable (() -> Unit)?,
        label: @Composable (() -> Unit),
        extra: @Composable (() -> Unit)?,
        onClick: (() -> Unit)?,
        collapsedAdaptiveWidth: Boolean,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        contentVisible: Boolean,
        content: @Composable (() -> Unit)?
    ) {
        val hasContent = content != null
        val shouldFillMaxWidth = !collapsedAdaptiveWidth || contentVisible

        Column(
            modifier = Modifier.then(
                if (shouldFillMaxWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                }
            ),
        ) {
            // Label row: Icon + Label + Extra + indicator
            Row(
                modifier = Modifier
                    .then(
                        if (shouldFillMaxWidth) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (onClick != null) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onClick() }
                        } else if (hasContent) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onExpandedChange(!expanded) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon (opaque background hides the timeline line behind it)
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(LocalCardColor.current),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier.size(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                icon()
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }

                // Label
                Box(
                    modifier = Modifier.then(
                        if (shouldFillMaxWidth) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        }
                    )
                ) {
                    label()
                }

                // Extra
                if (extra != null) {
                    extra()
                }

                // Indicator: onClick shows right arrow, content shows expand/collapse arrow
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expandable content (indented to align with label)
            if (contentVisible && hasContent) {
                Box(
                    modifier = Modifier
                        .then(
                            if (shouldFillMaxWidth) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                            }
                        )
                        .padding(start = 32.dp, top = 4.dp, bottom = 8.dp)
                ) {
                    content()
                }
            }
        }
    }
}
