package io.rikka.agent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
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
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private val mdParser: Parser = Parser.builder()
  .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
  .build()

/**
 * Renders a Markdown string as Compose UI.
 *
 * Supports: headings, paragraphs, bold/italic/strikethrough/code,
 * fenced/indented code blocks, ordered/bullet lists, block quotes,
 * thematic breaks, links, and GFM tables.
 */
@Composable
fun MarkdownText(
  markdown: String,
  modifier: Modifier = Modifier,
) {
  val document = remember(markdown) { mdParser.parse(markdown) }

  SelectionContainer {
    Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      RenderChildren(document)
    }
  }
}

@Composable
private fun RenderChildren(parent: Node) {
  var child = parent.firstChild
  while (child != null) {
    RenderBlock(child)
    child = child.next
  }
}

@Composable
private fun RenderBlock(node: Node) {
  when (node) {
    is Heading -> {
      val style = when (node.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
      }
      Text(
        text = buildInlineAnnotatedString(node),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
      )
    }

    is Paragraph -> {
      Text(
        text = buildInlineAnnotatedString(node),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    is FencedCodeBlock -> {
      val lang = node.info?.takeIf { it.isNotBlank() }
      val code = node.literal.trimEnd('\n')
      CodeCard(
        code = code,
        language = lang,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    is IndentedCodeBlock -> {
      val code = node.literal.trimEnd('\n')
      CodeCard(
        code = code,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    is BlockQuote -> {
      Row(modifier = Modifier.padding(vertical = 2.dp)) {
        HorizontalDivider(
          modifier = Modifier
            .width(3.dp)
            .padding(end = 0.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        // For block quotes, use a vertical colored bar + content column
        Column(
          modifier = Modifier
            .padding(start = 8.dp)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          RenderChildren(node)
        }
      }
    }

    is BulletList -> {
      Column(
        modifier = Modifier.padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        var item = node.firstChild
        while (item != null) {
          if (item is ListItem) {
            Row {
              Text(
                text = "•  ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Column(modifier = Modifier.weight(1f)) {
                RenderChildren(item)
              }
            }
          }
          item = item.next
        }
      }
    }

    is OrderedList -> {
      Column(
        modifier = Modifier.padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        @Suppress("DEPRECATION")
        var idx = node.startNumber
        var item = node.firstChild
        while (item != null) {
          if (item is ListItem) {
            Row {
              Text(
                text = "${idx}.  ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Column(modifier = Modifier.weight(1f)) {
                RenderChildren(item)
              }
              idx++
            }
          }
          item = item.next
        }
      }
    }

    is ThematicBreak -> {
      HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
      )
    }

    is TableBlock -> {
      RenderTable(node)
    }

    is ListItem -> {
      // Handled by parent list
      RenderChildren(node)
    }

    else -> {
      // Fallback: render inline text
      val text = buildInlineAnnotatedString(node)
      if (text.isNotBlank()) {
        Text(
          text = text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun RenderTable(table: Node) {
  val rows = mutableListOf<List<AnnotatedString>>()
  var isHeader = false

  var section = table.firstChild
  while (section != null) {
    val sectionIsHead = section is TableHead
    var row = section.firstChild
    while (row != null) {
      if (row is TableRow) {
        val cells = mutableListOf<AnnotatedString>()
        var cell = row.firstChild
        while (cell != null) {
          if (cell is TableCell) {
            cells.add(buildInlineAnnotatedString(cell))
          }
          cell = cell.next
        }
        rows.add(cells)
        if (sectionIsHead && rows.size == 1) isHeader = true
      }
      row = row.next
    }
    section = section.next
  }

  if (rows.isEmpty()) return

  Column(
    modifier = Modifier.padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    rows.forEachIndexed { idx, cells ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        cells.forEach { cell ->
          Text(
            text = cell,
            style = if (idx == 0 && isHeader)
              MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
        }
      }
      if (idx == 0 && isHeader) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
    }
  }
}

/**
 * Walks inline children of [parent] and builds a styled AnnotatedString.
 */
@Composable
private fun buildInlineAnnotatedString(parent: Node): AnnotatedString {
  val codeColor = MaterialTheme.colorScheme.secondary
  val codeBg = MaterialTheme.colorScheme.secondaryContainer
  val linkColor = MaterialTheme.colorScheme.primary
  return remember(parent) {
    buildAnnotatedString {
      appendInlineNodes(parent, codeColor, codeBg, linkColor)
    }
  }
}

private fun AnnotatedString.Builder.appendInlineNodes(
  parent: Node,
  codeColor: Color,
  codeBg: Color,
  linkColor: Color,
) {
  var child = parent.firstChild
  while (child != null) {
    when (child) {
      is org.commonmark.node.Text -> append(child.literal)
      is Code -> {
        withStyle(SpanStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 13.sp,
          color = codeColor,
          background = codeBg,
        )) {
          append(" ${child.literal} ")
        }
      }
      is StrongEmphasis -> {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
          appendInlineNodes(child, codeColor, codeBg, linkColor)
        }
      }
      is Emphasis -> {
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
          appendInlineNodes(child, codeColor, codeBg, linkColor)
        }
      }
      is Strikethrough -> {
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
          appendInlineNodes(child, codeColor, codeBg, linkColor)
        }
      }
      is Link -> {
        withStyle(SpanStyle(
          color = linkColor,
          textDecoration = TextDecoration.Underline,
        )) {
          appendInlineNodes(child, codeColor, codeBg, linkColor)
        }
      }
      is SoftLineBreak -> append(" ")
      is HardLineBreak -> append("\n")
      else -> {
        // Recurse for any unknown inline container
        appendInlineNodes(child, codeColor, codeBg, linkColor)
      }
    }
    child = child.next
  }
}
