package io.rikka.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus

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
 */
@Composable
fun MessagePartsBlock(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    enableMermaid: Boolean = false,
    isStreaming: Boolean = false,
) {
    val isStreamingNow = isStreaming || message.status == MessageStatus.Streaming

    Column(
        modifier = modifier
            .fillMaxWidth()
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
