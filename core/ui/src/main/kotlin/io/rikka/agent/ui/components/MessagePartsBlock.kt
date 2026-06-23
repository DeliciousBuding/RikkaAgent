package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.ui.R
import lucide.icons.Lucide

/**
 * Renders the structured [MessagePart] list of a [ChatMessage].
 *
 * Each part type is dispatched to a dedicated composable:
 * - [MessagePart.Text]      -> MarkdownText (if it looks like markdown) or plain Text
 * - [MessagePart.Code]      -> CodeCard (collapsible, syntax highlight, copy)
 * - [MessagePart.Command]   -> CommandCard (monospace, gray bg, exit code)
 * - [MessagePart.Stdout]    -> StreamOutputCard (stdout style)
 * - [MessagePart.Stderr]    -> StreamOutputCard (stderr style, red/orange)
 * - [MessagePart.Error]     -> ErrorCard (red border, error icon)
 * - [MessagePart.Reasoning] -> ReasoningCard (collapsible chain-of-thought)
 * - [MessagePart.Mermaid]   -> MermaidDiagramCard
 *
 * When [onDelete] is provided, a "more actions" button is appended at the end
 * of the parts list. Tapping it opens an [ActionsSheet] with copy, re-run,
 * share, full output, and delete actions.
 *
 * @param message The [ChatMessage] whose [MessagePart] list will be rendered.
 * @param modifier Modifier applied to the outer [Column].
 * @param enableMermaid Whether to render [MessagePart.Mermaid] definitions as diagrams.
 *   Defaults to `false`; requires a Mermaid rendering dependency when enabled.
 * @param isStreaming Whether the message is currently being streamed.
 *   When `true`, text parts skip [SelectionContainer] to avoid
 *   [ConcurrentModificationException] during rapid recomposition.
 * @param onDelete Called when the user taps "Delete" in the [ActionsSheet].
 *   When `null`, the "more actions" button is hidden.
 * @param onRerun Called when the user taps "Re-run" in the [ActionsSheet].
 * @param onCopy Called when the user taps "Copy" in the [ActionsSheet].
 *   When `null`, defaults to copying [ChatMessage.textContent].
 * @param onShare Called when the user taps "Share" in the [ActionsSheet].
 * @param onFullOutput Called when the user taps "Complete output" in the [ActionsSheet].
 *
 * ```
 * // Basic usage in a chat list
 * LazyColumn {
 *     items(messages) { message ->
 *         MessagePartsBlock(message = message)
 *     }
 * }
 *
 * // With actions sheet
 * MessagePartsBlock(
 *     message = currentMessage,
 *     onDelete = { viewModel.deleteMessage(currentMessage.id) },
 *     onRerun = { viewModel.rerun(currentMessage) },
 *     onShare = { shareMessage(currentMessage) },
 * )
 * ```
 */
@Composable
fun MessagePartsBlock(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    enableMermaid: Boolean = false,
    bubbleOpacity: Float = 1.0f,
    isStreaming: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRerun: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onFullOutput: (() -> Unit)? = null,
) {
    val isStreamingNow = isStreaming || message.status == MessageStatus.Streaming
    var showActionsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(bubbleOpacity)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.parts.forEach { part ->
            key(part) {
                when (part) {
                    is MessagePart.Text -> {
                        TextPartRenderer(
                            text = part.text,
                            isStreaming = isStreamingNow,
                        )
                    }

                    is MessagePart.Code -> {
                        CodeCard(
                            code = part.code,
                            language = part.language,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is MessagePart.Command -> {
                        CommandCard(
                            command = part.command,
                            exitCode = part.exitCode,
                            isRunning = !part.isFinished,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is MessagePart.Stdout -> {
                        if (part.text.isNotEmpty()) {
                            StreamOutputCard(
                                text = part.text,
                                isStderr = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is MessagePart.Stderr -> {
                        if (part.text.isNotEmpty()) {
                            StreamOutputCard(
                                text = part.text,
                                isStderr = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is MessagePart.Error -> {
                        MessagePartErrorCard(
                            message = part.message,
                            cause = part.cause,
                            code = part.code,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is MessagePart.Reasoning -> {
                        ReasoningCard(
                            text = part.text,
                            stepId = part.stepId,
                            isStreaming = isStreamingNow,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is MessagePart.Mermaid -> {
                        if (enableMermaid) {
                            MermaidDiagramCard(
                                source = part.definition,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // More actions button (only when onDelete is provided and not streaming)
        if (onDelete != null && !isStreamingNow) {
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Lucide.MoreHorizontal,
                    contentDescription = stringResource(R.string.more_actions),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showActionsSheet = true }
                        .padding(2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }

    // Actions sheet
    if (showActionsSheet) {
        ActionsSheet(
            onDelete = onDelete ?: {},
            onEdit = onEdit,
            onRerun = onRerun,
            onCopy = onCopy ?: {},
            onShare = onShare ?: {},
            onFullOutput = onFullOutput,
            onDismissRequest = { showActionsSheet = false },
        )
    }
}

/**
 * Renders a [MessagePart.Text].
 *
 * If the text looks like markdown, renders via [MarkdownText] (which supports
 * headings, bold/italic, code blocks, tables, etc.).
 * Otherwise renders as a simple selectable Text.
 *
 * During streaming, [SelectionContainer] is skipped to avoid
 * ConcurrentModificationException from rapid recomposition (same pattern as RikkaHub).
 */
@Composable
private fun TextPartRenderer(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return

    if (looksLikeMarkdown(text)) {
        val content = @Composable {
            MarkdownText(
                markdown = text,
                modifier = modifier.fillMaxWidth(),
            )
        }
        if (isStreaming) {
            content()
        } else {
            SelectionContainer {
                content()
            }
        }
    } else {
        if (isStreaming) {
            androidx.compose.material3.Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = modifier,
            )
        } else {
            SelectionContainer {
                androidx.compose.material3.Text(
                    text = text,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * Heuristic: does the text contain markdown formatting worth rendering?
 * Copied from original ChatBubble for backward compatibility.
 */
private fun looksLikeMarkdown(text: String): Boolean {
    if (text.length < 4) return false
    val sample = if (text.length > 2000) text.substring(0, 2000) else text
    return sample.contains("```") ||
        sample.contains("## ") ||
        sample.contains("# ") ||
        sample.contains("**") ||
        sample.contains("- ") ||
        sample.contains("1. ") ||
        sample.contains("> ") ||
        (sample.contains("[") && sample.contains("]("))
}
