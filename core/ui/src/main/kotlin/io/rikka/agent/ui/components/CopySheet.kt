package io.rikka.agent.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.MessagePart
import io.rikka.agent.ui.R
import lucide.icons.Lucide

/**
 * Copy mode for message content.
 */
enum class CopyMode {
    /** Copy as Markdown, preserving formatting (code fences, headings, etc.). */
    Markdown,

    /** Copy as plain text, stripping all formatting. */
    PlainText,
}

/**
 * A modal bottom sheet for selective message copying.
 *
 * Shows all text-bearing [MessagePart]s of a [ChatMessage] in a scrollable,
 * selectable list. The user can:
 * - Copy the entire message (as Markdown or plain text) via header buttons.
 * - Long-press / tap a part's copy button to copy an individual part.
 *
 * Inspired by RikkaHub's `ChatMessageCopySheet`, enhanced with per-part
 * selective copy and dual copy modes (Markdown / plain text).
 *
 * @param message The [ChatMessage] to display parts from.
 * @param onDismissRequest Called when the sheet should be dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopySheet(
    message: ChatMessage,
    onDismissRequest: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Collect all text-bearing parts with their display labels
    val copyableParts = remember(message) { buildCopyableParts(message) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Expanded,
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
        ),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // ── Header ─────────────────────────────────────────────────────
            CopySheetHeader(
                onDismiss = onDismissRequest,
                onCopyMarkdown = {
                    val md = message.toMarkdown()
                    clipboardManager.setText(AnnotatedString(md))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismissRequest()
                },
                onCopyPlainText = {
                    val plain = message.toPlainText()
                    clipboardManager.setText(AnnotatedString(plain))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismissRequest()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Body ───────────────────────────────────────────────────────
            if (copyableParts.isEmpty()) {
                // No copyable content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.copy_sheet_no_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        copyableParts.forEachIndexed { index, (label, part) ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                            CopyablePartRow(
                                label = label,
                                part = part,
                                onCopyPart = { mode ->
                                    val text = when (mode) {
                                        CopyMode.Markdown -> part.toMarkdown()
                                        CopyMode.PlainText -> part.toPlainText()
                                    }
                                    clipboardManager.setText(AnnotatedString(text))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            )
                        }
                    }
                }
            }

            // Bottom padding for navigation bar
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun CopySheetHeader(
    onDismiss: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onCopyPlainText: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = stringResource(R.string.copy_sheet_close),
            )
        }

        Text(
            text = stringResource(R.string.copy_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        // Copy-all button with dropdown-style two options
        Row {
            TextButton(onClick = onCopyMarkdown) {
                Icon(
                    imageVector = Lucide.FileText,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.copy_sheet_copy_markdown),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onCopyPlainText) {
                Icon(
                    imageVector = Lucide.Type,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.copy_sheet_copy_plain),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Per-part row ────────────────────────────────────────────────────────────

@Composable
private fun CopyablePartRow(
    label: String,
    part: MessagePart,
    onCopyPart: (CopyMode) -> Unit,
) {
    var showPartActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPartActions = !showPartActions },
    ) {
        // Part label + inline copy buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onCopyPart(CopyMode.Markdown) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Lucide.FileText,
                    contentDescription = stringResource(R.string.copy_sheet_copy_part_markdown),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onCopyPart(CopyMode.PlainText) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Lucide.Type,
                    contentDescription = stringResource(R.string.copy_sheet_copy_part_plain),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Part content preview
        val preview = remember(part) { part.toPlainText() }
        Text(
            text = preview,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = if (showPartActions) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Data helpers ────────────────────────────────────────────────────────────

/**
 * A labeled [MessagePart] for display in the copy sheet.
 */
private data class CopyablePart(val label: String, val part: MessagePart)

/**
 * Extracts all text-bearing parts from a [ChatMessage] with human-readable labels.
 */
private fun buildCopyableParts(message: ChatMessage): List<CopyablePart> {
    val result = mutableListOf<CopyablePart>()
    var textIndex = 0
    var codeIndex = 0
    var cmdIndex = 0
    var reasoningIndex = 0

    for (part in message.parts) {
        when (part) {
            is MessagePart.Text -> {
                if (part.text.isNotBlank()) {
                    textIndex++
                    result += CopyablePart("Text #$textIndex", part)
                }
            }
            is MessagePart.Code -> {
                if (part.code.isNotBlank()) {
                    codeIndex++
                    val lang = part.language?.let { " ($it)" } ?: ""
                    result += CopyablePart("Code #$codeIndex$lang", part)
                }
            }
            is MessagePart.Command -> {
                cmdIndex++
                result += CopyablePart("Command #$cmdIndex", part)
            }
            is MessagePart.Stdout -> {
                if (part.text.isNotBlank()) {
                    result += CopyablePart("Stdout", part)
                }
            }
            is MessagePart.Stderr -> {
                if (part.text.isNotBlank()) {
                    result += CopyablePart("Stderr", part)
                }
            }
            is MessagePart.Reasoning -> {
                if (part.text.isNotBlank()) {
                    reasoningIndex++
                    result += CopyablePart("Reasoning #$reasoningIndex", part)
                }
            }
            is MessagePart.Error -> {
                result += CopyablePart("Error", part)
            }
            is MessagePart.Mermaid -> {
                if (part.definition.isNotBlank()) {
                    result += CopyablePart("Mermaid", part)
                }
            }
        }
    }
    return result
}

// ── Markdown serialization ──────────────────────────────────────────────────

/**
 * Converts a [ChatMessage] to a Markdown string, preserving structure.
 *
 * - Text parts are included as-is (assumed to already contain markdown).
 * - Code parts are wrapped in fenced code blocks with language tags.
 * - Command parts are wrapped in ` ```bash ` blocks.
 * - Stdout/Stderr are wrapped in ` ``` ` blocks with labels.
 * - Reasoning parts are wrapped in blockquotes.
 * - Error parts are rendered as `> [!CAUTION]` callouts.
 * - Mermaid parts are wrapped in ` ```mermaid ` blocks.
 */
private fun ChatMessage.toMarkdown(): String = buildString {
    for (part in parts) {
        when (part) {
            is MessagePart.Text -> {
                if (part.text.isNotBlank()) {
                    appendLine(part.text)
                    appendLine()
                }
            }
            is MessagePart.Code -> {
                if (part.code.isNotBlank()) {
                    append("```")
                    appendLine(part.language ?: "")
                    appendLine(part.code)
                    appendLine("```")
                    appendLine()
                }
            }
            is MessagePart.Command -> {
                appendLine("```bash")
                appendLine(part.command)
                appendLine("```")
                if (part.isFinished) {
                    appendLine("_Exit code: ${part.exitCode}_")
                    appendLine()
                }
            }
            is MessagePart.Stdout -> {
                if (part.text.isNotBlank()) {
                    appendLine("```")
                    appendLine(part.text.trimEnd())
                    appendLine("```")
                    appendLine()
                }
            }
            is MessagePart.Stderr -> {
                if (part.text.isNotBlank()) {
                    appendLine("> **stderr:**")
                    appendLine(">")
                    part.text.trimEnd().lines().forEach { line ->
                        append("> ")
                        appendLine(line)
                    }
                    appendLine()
                }
            }
            is MessagePart.Reasoning -> {
                if (part.text.isNotBlank()) {
                    appendLine("> **Thinking:**")
                    appendLine(">")
                    part.text.lines().forEach { line ->
                        append("> ")
                        appendLine(line)
                    }
                    appendLine()
                }
            }
            is MessagePart.Error -> {
                appendLine("> [!CAUTION]")
                append("> **Error:** ")
                appendLine(part.message)
                if (part.cause != null) {
                    append("> **Cause:** ")
                    appendLine(part.cause)
                }
                if (part.code != null) {
                    append("> **Code:** ")
                    appendLine(part.code)
                }
                appendLine()
            }
            is MessagePart.Mermaid -> {
                if (part.definition.isNotBlank()) {
                    appendLine("```mermaid")
                    appendLine(part.definition)
                    appendLine("```")
                    if (part.caption != null) {
                        appendLine("_${part.caption}_")
                    }
                    appendLine()
                }
            }
        }
    }
}.trimEnd()

/**
 * Converts a single [MessagePart] to a Markdown string.
 */
private fun MessagePart.toMarkdown(): String = when (this) {
    is MessagePart.Text -> text
    is MessagePart.Code -> buildString {
        append("```")
        appendLine(language ?: "")
        appendLine(code)
        append("```")
    }
    is MessagePart.Command -> buildString {
        appendLine("```bash")
        appendLine(command)
        append("```")
        if (isFinished) {
            appendLine()
            append("_Exit code: ${exitCode}_")
        }
    }
    is MessagePart.Stdout -> text
    is MessagePart.Stderr -> buildString {
        appendLine("**stderr:**")
        text.trimEnd().lines().forEach { line ->
            appendLine(line)
        }
    }
    is MessagePart.Reasoning -> buildString {
        appendLine("**Thinking:**")
        appendLine(text)
    }
    is MessagePart.Error -> buildString {
        append("**Error:** ")
        append(message)
        if (cause != null) {
            appendLine()
            append("**Cause:** ")
            append(cause)
        }
        if (code != null) {
            appendLine()
            append("**Code:** ")
            append(code)
        }
    }
    is MessagePart.Mermaid -> buildString {
        appendLine("```mermaid")
        appendLine(definition)
        append("```")
        if (caption != null) {
            appendLine()
            append(caption)
        }
    }
}

// ── Plain text serialization ────────────────────────────────────────────────

/**
 * Converts a [ChatMessage] to plain text, stripping all formatting.
 */
private fun ChatMessage.toPlainText(): String = buildString {
    for (part in parts) {
        val text = part.toPlainText()
        if (text.isNotBlank()) {
            appendLine(text)
        }
    }
}.trimEnd()

/**
 * Converts a single [MessagePart] to plain text, stripping formatting.
 */
private fun MessagePart.toPlainText(): String = when (this) {
    is MessagePart.Text -> text
    is MessagePart.Code -> code
    is MessagePart.Command -> buildString {
        append("$ ")
        append(command)
        if (isFinished) {
            appendLine()
            append("[exit ${exitCode}]")
        }
    }
    is MessagePart.Stdout -> text.trimEnd()
    is MessagePart.Stderr -> text.trimEnd()
    is MessagePart.Reasoning -> text
    is MessagePart.Error -> buildString {
        append("Error: ")
        append(message)
        if (cause != null) {
            append(" — ")
            append(cause)
        }
    }
    is MessagePart.Mermaid -> buildString {
        append("[Mermaid Diagram]")
        if (caption != null) {
            append(" ")
            append(caption)
        }
    }
}
