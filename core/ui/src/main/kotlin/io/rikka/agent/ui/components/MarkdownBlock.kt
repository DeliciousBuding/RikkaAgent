package io.rikka.agent.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/**
 * Renders Markdown text using commonmark-java with GFM extensions.
 *
 * Supports: headings, paragraphs, bold, italic, strikethrough, links,
 * inline code, fenced code blocks, lists, blockquotes, tables.
 */
@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
) {
    val document = remember(content) {
        try {
            val parser = Parser.builder()
                .extensions(listOf(
                    org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                    org.commonmark.ext.gfm.tables.TablesExtension.create(),
                ))
                .build()
            parser.parse(content)
        } catch (_: Exception) {
            null
        }
    }

    if (document != null) {
        Column(modifier = modifier.fillMaxWidth()) {
            RenderCommonmarkNode(document)
        }
    } else {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

@Composable
private fun RenderCommonmarkNode(node: Node) {
    when (node) {
        is Document -> {
            var child = node.firstChild
            while (child != null) {
                RenderCommonmarkNode(child)
                child = child.next
            }
        }

        is Heading -> {
            val text = node.collectText()
            val style = when (node.level) {
                1 -> MaterialTheme.typography.headlineLarge
                2 -> MaterialTheme.typography.headlineMedium
                3 -> MaterialTheme.typography.headlineSmall
                4 -> MaterialTheme.typography.titleLarge
                5 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Text(text = text, style = style)
            Spacer(modifier = Modifier.height(8.dp))
        }

        is Paragraph -> {
            val annotated = buildCommonmarkAnnotated(node)
            Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        is FencedCodeBlock -> {
            val lang = node.info?.takeIf { it.isNotBlank() }
            CodeBlockCard(code = node.literal.trimEnd(), language = lang)
        }

        is IndentedCodeBlock -> {
            CodeBlockCard(code = node.literal.trimEnd(), language = null)
        }

        is BlockQuote -> {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)) {
                    var child = node.firstChild
                    while (child != null) {
                        RenderCommonmarkNode(child)
                        child = child.next
                    }
                }
            }
        }

        is BulletList -> {
            var child = node.firstChild
            while (child != null) {
                Row(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = "•", style = MaterialTheme.typography.bodyMedium)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        var itemChild = (child as? ListItem)?.firstChild
                        while (itemChild != null) {
                            RenderCommonmarkNode(itemChild)
                            itemChild = itemChild.next
                        }
                    }
                }
                child = child.next
            }
        }

        is OrderedList -> {
            var index = node.startNumber
            var child = node.firstChild
            while (child != null) {
                Row(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = "$index.", style = MaterialTheme.typography.bodyMedium)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        var itemChild = (child as? ListItem)?.firstChild
                        while (itemChild != null) {
                            RenderCommonmarkNode(itemChild)
                            itemChild = itemChild.next
                        }
                    }
                }
                index++
                child = child.next
            }
        }

        is TableBlock -> {
            // Simplified table rendering
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    var child = node.firstChild
                    while (child != null) {
                        when (child) {
                            is TableHead -> {
                                var rowChild = child.firstChild
                                while (rowChild != null) {
                                    RenderTableRow(rowChild, isHeader = true)
                                    rowChild = rowChild.next
                                }
                            }
                            is TableRow -> {
                                RenderTableRow(child, isHeader = false)
                            }
                        }
                        child = child.next
                    }
                }
            }
        }

        is Link -> {
            val uriHandler = LocalUriHandler.current
            val linkText = node.collectText()
            val destination = node.destination
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )) { append(linkText) }
                addStringAnnotation("URL", destination, 0, linkText.length)
            }
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                onClick = { offset ->
                    annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        uriHandler.openUri(it.item)
                    }
                },
            )
        }

        else -> {
            // Recursively render children for unknown types
            var child = node.firstChild
            while (child != null) {
                RenderCommonmarkNode(child)
                child = child.next
            }
        }
    }
}

@Composable
private fun RenderTableRow(row: Node, isHeader: Boolean) {
    Row {
        var cell = row.firstChild
        while (cell != null) {
            val text = (cell as? TableCell)?.collectText() ?: ""
            val style = if (isHeader) {
                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodySmall
            }
            Text(
                text = text,
                style = style,
                modifier = Modifier.weight(1f).padding(4.dp),
            )
            cell = cell.next
        }
    }
}

@Composable
private fun CodeBlockCard(code: String, language: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (language != null) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                )
            }
        }
    }
}

private fun Node.collectText(): String {
    val sb = StringBuilder()
    var child = firstChild
    while (child != null) {
        when (child) {
            is Text -> sb.append(child.literal)
            is Code -> sb.append(child.literal)
            is Emphasis -> sb.append(child.collectText())
            is StrongEmphasis -> sb.append(child.collectText())
            is Strikethrough -> sb.append(child.collectText())
            is SoftLineBreak -> sb.append(" ")
            is HardLineBreak -> sb.append("\n")
            is Link -> sb.append(child.collectText())
            else -> sb.append(child.collectText())
        }
        child = child.next
    }
    return sb.toString()
}

@Composable
private fun buildCommonmarkAnnotated(node: Node): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> append(child.literal)
                is Code -> {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = androidx.compose.ui.graphics.Color(0x1A000000),
                    )) { append(child.literal) }
                }
                is StrongEmphasis -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(child.collectText())
                    }
                }
                is Emphasis -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(child.collectText())
                    }
                }
                is Strikethrough -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(child.collectText())
                    }
                }
                is Link -> {
                    val dest = child.destination
                    withStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )) {
                        append(child.collectText())
                    }
                    addStringAnnotation("URL", dest, this.length - child.collectText().length, this.length)
                }
                is SoftLineBreak -> append(" ")
                is HardLineBreak -> append("\n")
                else -> {
                    val text = child.collectText()
                    if (text.isNotEmpty()) append(text)
                }
            }
            child = child.next
        }
    }
}
