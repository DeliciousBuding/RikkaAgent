package io.rikka.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single pulsing dot indicator for loading states.
 *
 * Renders a circle that fades between 30% and 100% alpha in an infinite
 * reverse-repeat animation. Useful as a lightweight inline loading indicator
 * or composed into a row of dots for richer feedback.
 *
 * @param modifier Modifier applied to the [Box] container.
 * @param color Dot fill color. Defaults to [MaterialTheme.colorScheme.primary].
 * @param animationDuration Duration of a single fade cycle in milliseconds. Defaults to `600`.
 * @param size Diameter of the dot. Defaults to `16.dp`.
 *
 * ```
 * // Standalone usage
 * DotLoading()
 *
 * // Custom size and color
 * DotLoading(
 *     color = MaterialTheme.colorScheme.tertiary,
 *     size = 8.dp,
 *     animationDuration = 400,
 * )
 *
 * // Three-dot row
 * Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
 *     repeat(3) { DotLoading(size = 8.dp) }
 * }
 * ```
 */
@Preview
@Composable
fun DotLoading(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 600,
    size: Dp = 16.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationDuration),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .then(modifier)
            .alpha(alpha)
            .background(color = color, shape = CircleShape)
    )
}
