package io.rikka.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rikka.agent.ui.theme.extendColors

/**
 * Semantic type of a [Tag], controlling its background and text colors.
 *
 * - [DEFAULT] -- tertiary container colors (neutral)
 * - [SUCCESS] -- green tones
 * - [ERROR]   -- red tones
 * - [WARNING] -- orange tones
 * - [INFO]    -- blue tones
 */
enum class TagType {
    DEFAULT,
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * A pill-shaped inline label for status or category indication.
 *
 * Uses [MaterialTheme.extendColors] for semantic color mapping based on [type].
 * The tag is non-clickable by default; pass an [onClick] handler to make it interactive.
 *
 * @param modifier Modifier applied to the outer [Row].
 * @param type Semantic [TagType] that controls background and text colors. Defaults to [TagType.DEFAULT].
 * @param onClick Optional click handler. When non-null, the tag becomes clickable and a ripple effect is shown.
 * @param children Composable content rendered inside the tag row, typically a [Text].
 *
 * ```
 * Tag(type = TagType.SUCCESS) {
 *     Text("Deployed")
 * }
 *
 * Tag(type = TagType.ERROR, onClick = { showErrorDetails() }) {
 *     Text("Build failed")
 * }
 * ```
 */
@Composable
fun Tag(
    modifier: Modifier = Modifier,
    type: TagType = TagType.DEFAULT,
    onClick: (() -> Unit)? = null,
    children: @Composable RowScope.() -> Unit
) {
    val background = when (type) {
        TagType.SUCCESS -> MaterialTheme.extendColors.green2
        TagType.ERROR -> MaterialTheme.extendColors.red2
        TagType.WARNING -> MaterialTheme.extendColors.orange2
        TagType.INFO -> MaterialTheme.extendColors.blue2
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val textColor = when (type) {
        TagType.SUCCESS -> MaterialTheme.extendColors.gray8
        TagType.ERROR -> MaterialTheme.extendColors.red8
        TagType.WARNING -> MaterialTheme.extendColors.orange8
        TagType.INFO -> MaterialTheme.extendColors.blue8
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = textColor)) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(background)
                .let {
                    if (onClick != null) {
                        it.clickable { onClick() }
                    } else {
                        it
                    }
                }
                .padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            children()
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun TagPreview() {
    Column(
        modifier = Modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Tag(type = TagType.SUCCESS) {
            Text("Success")
        }
        Tag(type = TagType.ERROR) {
            Text("Error")
        }
        Tag(type = TagType.WARNING) {
            Text("Warning")
        }
        Tag(type = TagType.INFO) {
            Text("Info")
        }
        Tag(type = TagType.DEFAULT) {
            Text("Default")
        }
    }
}
