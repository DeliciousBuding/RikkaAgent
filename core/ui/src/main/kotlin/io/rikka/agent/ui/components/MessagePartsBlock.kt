package io.rikka.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.X
import com.composables.icons.lucide.TriangleAlert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.MessagePart

/**
 * Renders a list of [MessagePart] items with type-specific composables.
 *
 * Each part type gets its own visual treatment:
 * - [MessagePart.Text]: Markdown or plain text
 * - [MessagePart.Command]: Monospace command line with exit code badge
 * - [MessagePart.Stdout]: Monospace output block
 * - [MessagePart.Stderr]: Red-tinted error output
 * - [MessagePart.Code]: Code block with language tag
 * - [MessagePart.Reasoning]: Collapsible reasoning step
 * - [MessagePart.Error]: Error card with icon
 * - [MessagePart.Mermaid]: Mermaid diagram (falls back to source)
 */
@Composable
fun MessagePartsBlock(
    parts: List<MessagePart>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> TextPartView(part)
                is MessagePart.Command -> CommandPartView(part)
                is MessagePart.Stdout -> StdoutPartView(part)
                is MessagePart.Stderr -> StderrPartView(part)
                is MessagePart.Code -> CodePartView(part)
                is MessagePart.Reasoning -> ReasoningPartView(part)
                is MessagePart.Error -> ErrorPartView(part)
                is MessagePart.Mermaid -> MermaidPartView(part)
            }
        }
    }
}

@Composable
private fun TextPartView(part: MessagePart.Text) {
    Text(
        text = part.text,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CommandPartView(part: MessagePart.Command) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$ ${part.command}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
        if (part.isFinished) {
            Spacer(modifier = Modifier.width(8.dp))
            val (icon, color) = if (part.isSuccess) {
                Lucide.CircleCheck to Color(0xFF00B42A)
            } else {
                Lucide.X to MaterialTheme.colorScheme.error
            }
            Icon(
                imageVector = icon,
                contentDescription = "Exit code: ${part.exitCode}",
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StdoutPartView(part: MessagePart.Stdout) {
    Text(
        text = part.text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
    )
}

@Composable
private fun StderrPartView(part: MessagePart.Stderr) {
    Text(
        text = part.text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
    )
}

@Composable
private fun CodePartView(part: MessagePart.Code) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        val lang = part.language
        if (lang != null) {
            Text(
                text = lang,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = part.code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun ReasoningPartView(part: MessagePart.Reasoning) {
    // Simplified version — ChainOfThought integration deferred
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(
            text = "Reasoning",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = part.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorPartView(part: MessagePart.Error) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Lucide.TriangleAlert,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = part.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val cause = part.cause
            if (cause != null) {
                Text(
                    text = cause,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun MermaidPartView(part: MessagePart.Mermaid) {
    // Fallback: show source code
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = "Mermaid",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = part.definition,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
