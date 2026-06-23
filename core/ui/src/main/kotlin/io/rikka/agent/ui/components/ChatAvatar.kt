package io.rikka.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.ui.R
import lucide.icons.Lucide
import org.koin.compose.koinInject
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Avatar size in dp, consistent with RikkaHub's 28dp avatar.
 */
private val AVATAR_SIZE = 28.dp

/**
 * Displays a user avatar row: nickname label + circular avatar with initial.
 *
 * Only shown when:
 * - The message role is [ChatRole.User]
 * - The message has non-empty content
 * - The `showUserAvatar` preference is enabled
 *
 * Mirrors RikkaHub's [ChatMessageUserAvatar] layout: label on the left,
 * avatar on the right, right-aligned in the chat bubble.
 *
 * @param message The chat message to display the avatar for.
 * @param nickname The display name for the user (first letter is used for the avatar).
 * @param modifier Modifier applied to the outer [Row].
 */
@Composable
fun ChatUserAvatar(
    message: ChatMessage,
    nickname: String = "You",
    modifier: Modifier = Modifier,
) {
    val prefs: AppPreferences = koinInject()
    val showAvatar by prefs.showUserAvatar.collectAsStateWithLifecycle(initialValue = true)

    if (message.role == ChatRole.User && message.textContent.isNotBlank() && showAvatar) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nickname.ifEmpty { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ProceduralAvatar(
                name = nickname,
                modifier = Modifier.size(AVATAR_SIZE),
            )
        }
    }
}

/**
 * Displays an assistant avatar row: circular bot icon + optional model/assistant name.
 *
 * Only shown when:
 * - The message role is [ChatRole.Assistant]
 * - The message has non-empty parts
 * - The `showModelIcon` preference is enabled
 *
 * Mirrors RikkaHub's [ChatMessageAssistantAvatar] layout: avatar on the left,
 * name on the right, left-aligned in the chat bubble.
 *
 * @param message The chat message to display the avatar for.
 * @param modelName Optional model name to display next to the icon.
 *   When non-empty, shown as a label next to the avatar.
 * @param isStreaming Whether the message is currently being streamed (affects loading animation).
 * @param modifier Modifier applied to the outer [Row].
 */
@Composable
fun ChatAssistantAvatar(
    message: ChatMessage,
    modelName: String = "",
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val prefs: AppPreferences = koinInject()
    val showIcon by prefs.showModelIcon.collectAsStateWithLifecycle(initialValue = true)

    if (message.role == ChatRole.Assistant && message.parts.isNotEmpty() && showIcon) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantIcon(
                modifier = Modifier.size(AVATAR_SIZE),
            )
            if (modelName.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Circular avatar with a procedural gradient background derived from [name].
 *
 * Uses SHA-1 hashing (same algorithm as RikkaHub's `ProceduralAvatar`) to generate
 * deterministic, visually distinct gradients from any input string.
 *
 * @param name The name to derive the gradient from. First character is displayed as text.
 * @param modifier Modifier applied to the outer [Box].
 */
@Composable
private fun ProceduralAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val (fromColor, toColor) = remember(name) {
        avatarGradientColors(name.ifBlank { "?" })
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(fromColor, toColor),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            lineHeight = 0.8.em,
        )
    }
}

/**
 * Circular icon for assistant messages.
 *
 * Shows a bot icon from Lucide inside a secondaryContainer-colored circle.
 *
 * @param modifier Modifier applied to the outer [Box].
 */
@Composable
private fun AssistantIcon(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Lucide.Bot,
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Generates a deterministic pair of gradient colors from a name string.
 *
 * Uses SHA-1 hashing to produce a hue value, then creates two HSL-based colors
 * 120 degrees apart for a visually appealing gradient. This is the same algorithm
 * used by RikkaHub's `vercelAvatarColors`.
 *
 * @param name The input string to hash.
 * @return A pair of [Color] values for the gradient start and end.
 */
private fun avatarGradientColors(name: String): Pair<Color, Color> {
    val bytes = MessageDigest.getInstance("SHA-1").digest(name.toByteArray(Charsets.UTF_8))
    val sum = bytes.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
    val hue = (sum % 360).toFloat()
    return Pair(
        hslToColor(hue, 0.65f, 0.55f),
        hslToColor((hue + 120f) % 360f, 0.65f, 0.55f),
    )
}

/**
 * Converts HSL color values to a Compose [Color].
 *
 * @param h Hue in degrees (0..360).
 * @param s Saturation (0..1).
 * @param l Lightness (0..1).
 * @return The corresponding [Color].
 */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hPrime = h / 60f
    val x = c * (1f - abs(hPrime % 2f - 1f))
    val (r1, g1, b1) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}
