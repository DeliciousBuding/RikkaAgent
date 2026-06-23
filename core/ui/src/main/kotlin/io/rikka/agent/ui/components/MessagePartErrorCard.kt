package io.rikka.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rikka.agent.ui.R
import io.rikka.agent.ui.theme.LocalDarkMode

/**
 * Renders a [MessagePart.Error] as a visually distinct error card.
 *
 * Visual design:
 * - Tinted error-container background
 * - Error icon + "Error" label in the header
 * - Message body in regular text
 * - Optional cause chain shown below in monospace
 * - Optional error code badge
 * - Rounded corners consistent with CodeCard / CommandCard
 */
@Composable
fun MessagePartErrorCard(
    message: String,
    cause: String?,
    code: Int?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val isDark = LocalDarkMode.current

    val bgColor = if (isDark) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        color = bgColor,
        tonalElevation = 0.dp,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Header row: icon + label + optional code badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.label_error),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                if (code != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "code: $code",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Error message
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            // Cause chain
            if (!cause.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.label_cause),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                    )
                    Text(
                        text = cause,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
