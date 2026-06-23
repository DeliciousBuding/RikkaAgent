package io.rikka.agent.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

// ---------------------------------------------------------------------------
// Parser (lazy singleton, GFM flavour)
// ---------------------------------------------------------------------------

private val flavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

private val mdParser by lazy {
    MarkdownParser(flavour)
}

private val BREAK_LINE_REGEX = Regex("(?i)<br\\s*/?>")

// ---------------------------------------------------------------------------
// Parse result
// ---------------------------------------------------------------------------

private data class MdParseResult(
    val preprocessed: String,
    val ast: ASTNode,
)

private fun parseMarkdown(content: String): MdParseResult {
    val preprocessed = BREAK_LINE_REGEX.replace(content, "\n")
    val ast = mdParser.buildMarkdownTreeFromString(preprocessed)
    return MdParseResult(preprocessed, ast)
}

// ---------------------------------------------------------------------------
// Header style helper
// ---------------------------------------------------------------------------

private object HeaderStyle {
    private const val LINE_HEIGHT_RATIO = 1.25f

    fun styleFor(type: IElementType): TextStyle {
        val fontSize = when (type) {
            MarkdownElementTypes.ATX_1 -> 24.sp
            MarkdownElementTypes.ATX_2 -> 22.sp
            MarkdownElementTypes.ATX_3 -> 20.sp
            MarkdownElementTypes.ATX_4 -> 18.sp
            MarkdownElementTypes.ATX_5 -> 16.sp
            else -> 14.sp
        }
        return TextStyle(
            fontStyle = FontStyle.Normal,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = fontSize * LINE_HEIGHT_RATIO,
        )
    }

    fun paddingFor(type: IElementType): androidx.compose.ui.unit.Dp = when (type) {
        MarkdownElementTypes.ATX_1 -> 16.dp
        MarkdownElementTypes.ATX_2 -> 14.dp
        MarkdownElementTypes.ATX_3 -> 12.dp
        MarkdownElementTypes.ATX_4 -> 10.dp
        MarkdownElementTypes.ATX_5 -> 8.dp
        else -> 6.dp
    }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Renders a Markdown string as Compose UI using IntelliJ MarkdownParser.
 *
 * Supports: headings, paragraphs, bold / italic / strikethrough / code,
 * fenced / indented code blocks, ordered / bullet lists, block quotes,
 * thematic breaks, links (clickable), and GFM tables.
 *
 * Parsing is performed on [Dispatchers.Default] so that frequent updates
 * during streaming do not block the UI thread.
 *
 * Mermaid fenced code blocks are split out and rendered via
 * [MermaidDiagramCard] when [enableMermaid] is true.
 */
@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
    enableMermaid: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    // Split out mermaid fences if requested
    val segments = remember(content, enableMermaid) {
        if (enableMermaid && MermaidFenceParser.hasMermaidFence(content)) {
            MermaidFenceParser.split(content)
        } else {
            listOf(MermaidSegment(MermaidSegmentKind.Markdown, content))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEach { seg ->
            when (seg.kind) {
                MermaidSegmentKind.Markdown -> MarkdownSegment(seg.content, style)
                MermaidSegmentKind.Mermaid -> MermaidDiagramCard(source = seg.content)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Internal: single markdown segment
// ---------------------------------------------------------------------------

@Composable
private fun MarkdownSegment(content: String, style: TextStyle) {
    if (content.isBlank()) return

    // Background-thread parse with caching
    var (data, setData) = remember { mutableStateOf(parseMarkdown(content)) }
    val updatedContent by rememberUpdatedState(content)
    LaunchedEffect(Unit) {
        snapshotFlow { updatedContent }
            .distinctUntilChanged()
            .mapLatest { parseMarkdown(it) }
            .catch { it.printStackTrace() }
            .flowOn(Dispatchers.Default)
            .collect { setData(it) }
    }

    ProvideTextStyle(style) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            data.ast.children.forEach { child ->
                MdNode(node = child, content = data.preprocessed)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Block-level node renderer
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MdNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    listLevel: Int = 0,
) {
    when (node.type) {
        // -- Root --
        MarkdownElementTypes.MARKDOWN_FILE -> node.children.forEach { c ->
            MdNode(node = c, content = content, modifier = modifier)
        }

        // -- Paragraph --
        MarkdownElementTypes.PARAGRAPH -> MdParagraph(
            node = node, content = content, modifier = modifier,
        )

        // -- Headings --
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            val headingStyle = HeaderStyle.styleFor(node.type)
            val pad = HeaderStyle.paddingFor(node.type)
            ProvideTextStyle(LocalTextStyle.current.merge(headingStyle)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.children.forEach { c ->
                        if (c.type == MarkdownTokenTypes.ATX_CONTENT) {
                            MdParagraph(
                                node = c, content = content,
                                modifier = modifier.padding(vertical = pad),
                                trim = true,
                            )
                        }
                    }
                }
            }
        }

        // -- Lists --
        MarkdownElementTypes.UNORDERED_LIST -> MdUnorderedList(
            node = node, content = content, level = listLevel,
        )

        MarkdownElementTypes.ORDERED_LIST -> MdOrderedList(
            node = node, content = content, level = listLevel,
        )

        // -- Checkbox (GFM) --
        GFMTokenTypes.CHECK_BOX -> {
            val checked = node.getTextInNode(content).trim() == "[x]"
            Text(
                text = if (checked) "☑ " else "☐ ",
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // -- Block quote --
        MarkdownElementTypes.BLOCK_QUOTE -> {
            ProvideTextStyle(LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)) {
                val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                Column(
                    modifier = Modifier
                        .drawWithContent {
                            drawContent()
                            drawRect(color = bgColor, size = size)
                            drawRect(color = borderColor, size = Size(10f, size.height))
                        }
                        .padding(8.dp)
                ) {
                    node.children.forEach { c ->
                        MdNode(node = c, content = content)
                    }
                }
            }
        }

        // -- Emphasis / Strong / Strikethrough (block-level fallback) --
        MarkdownElementTypes.EMPH -> ProvideTextStyle(TextStyle(fontStyle = FontStyle.Italic)) {
            node.children.forEach { c ->
                MdNode(node = c, content = content, modifier = modifier)
            }
        }

        MarkdownElementTypes.STRONG -> ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold)) {
            node.children.forEach { c ->
                MdNode(node = c, content = content, modifier = modifier)
            }
        }

        GFMElementTypes.STRIKETHROUGH -> Text(
            text = node.getTextInNode(content),
            textDecoration = TextDecoration.LineThrough,
            modifier = modifier,
        )

        // -- Table --
        GFMElementTypes.TABLE -> MdTable(node = node, content = content, modifier = modifier)

        // -- Horizontal rule --
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            thickness = 0.5.dp,
        )

        // -- Link (block-level) --
        MarkdownElementTypes.INLINE_LINK -> {
            val linkDest = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(content) ?: ""
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)
                ?.findChildOfTypeRecursive(GFMTokenTypes.GFM_AUTOLINK, MarkdownTokenTypes.TEXT)
                ?.getTextInNode(content) ?: linkDest
            val context = LocalContext.current
            Text(
                text = linkText,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, linkDest.toUri()))
                },
            )
        }

        // -- Code span --
        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            Text(text = code, fontFamily = FontFamily.Monospace, modifier = modifier)
        }

        // -- Indented code block --
        MarkdownElementTypes.CODE_BLOCK -> {
            val code = node.getTextInNode(content)
            CodeCard(code = code, modifier = Modifier.fillMaxWidth())
        }

        // -- Fenced code block --
        MarkdownElementTypes.CODE_FENCE -> {
            val contentStartIdx = node.children.indexOfFirst {
                it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
            }
            if (contentStartIdx != -1) {
                val eolEl = node.children.subList(0, contentStartIdx)
                    .findLast { it.type == MarkdownTokenTypes.EOL }
                val startOff = eolEl?.endOffset ?: 0
                val endOff = node.children.findLast {
                    it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
                }?.endOffset ?: startOff
                val code = content.substring(startOff, endOff).trimIndent()
                val lang = node.findChildOfTypeRecursive(MarkdownTokenTypes.FENCE_LANG)
                    ?.getTextInNode(content) ?: "plaintext"
                CodeCard(
                    code = code,
                    language = lang,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .fillMaxWidth(),
                )
            }
        }

        // -- Leaf text --
        MarkdownTokenTypes.TEXT -> Text(
            text = node.getTextInNode(content),
            modifier = modifier,
        )

        // -- Fallback: recurse children --
        else -> node.children.forEach { c ->
            MdNode(node = c, content = content, modifier = modifier, listLevel = listLevel)
        }
    }
}

// ---------------------------------------------------------------------------
// Lists
// ---------------------------------------------------------------------------

@Composable
private fun MdUnorderedList(
    node: ASTNode,
    content: String,
    level: Int = 0,
) {
    val bullet = when (level % 3) {
        0 -> "• "
        1 -> "◦ "
        else -> "▪ "
    }
    Column(modifier = Modifier.padding(start = (level * 8).dp)) {
        node.children.forEach { c ->
            if (c.type == MarkdownElementTypes.LIST_ITEM) {
                MdListItem(
                    node = c, content = content, bulletText = bullet, level = level,
                )
            }
        }
    }
}

@Composable
private fun MdOrderedList(
    node: ASTNode,
    content: String,
    level: Int = 0,
) {
    Column(modifier = Modifier.padding(start = (level * 8).dp)) {
        var idx = 1
        node.children.forEach { c ->
            if (c.type == MarkdownElementTypes.LIST_ITEM) {
                val numText = c.findChildOfTypeRecursive(MarkdownTokenTypes.LIST_NUMBER)
                    ?.getTextInNode(content) ?: "$idx. "
                MdListItem(
                    node = c, content = content, bulletText = numText, level = level,
                )
                idx++
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MdListItem(
    node: ASTNode,
    content: String,
    bulletText: String,
    level: Int,
) {
    val (direct, nested) = separateContentAndLists(node)
    Column {
        if (direct.isNotEmpty()) {
            Row {
                Text(
                    text = bulletText,
                    modifier = Modifier.alignBy(Alignment.FirstBaseline),
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    direct.forEach { c ->
                        MdNode(node = c, content = content, listLevel = level)
                    }
                }
            }
        }
        nested.forEach { c ->
            MdNode(node = c, content = content, listLevel = level + 1)
        }
    }
}

private fun separateContentAndLists(
    listItemNode: ASTNode,
): Pair<List<ASTNode>, List<ASTNode>> {
    val direct = mutableListOf<ASTNode>()
    val nested = mutableListOf<ASTNode>()
    listItemNode.children.forEach { c ->
        when (c.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> nested.add(c)
            else -> direct.add(c)
        }
    }
    return direct to nested
}

// ---------------------------------------------------------------------------
// Paragraph (inline content via AnnotatedString)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MdParagraph(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    trim: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val textStyle = LocalTextStyle.current
    val density = LocalDensity.current

    // If paragraph contains images or block math, fall back to FlowRow rendering
    if (node.findChildOfTypeRecursive(MarkdownElementTypes.IMAGE) != null) {
        FlowRow(modifier = modifier) {
            node.children.forEach { c -> MdNode(node = c, content = content) }
        }
        return
    }

    val inlineContents = remember { mutableStateMapOf<String, InlineTextContent>() }

    val annotated = remember(content, trim) {
        buildAnnotatedString {
            node.children.forEach { c ->
                appendMdInline(
                    node = c,
                    content = content,
                    trim = trim,
                    colorScheme = colorScheme,
                    density = density,
                    style = textStyle,
                    inlineContents = inlineContents,
                )
            }
        }
    }

    FlowRow(
        modifier = modifier.then(
            if (node.nextSibling() != null) Modifier.padding(bottom = textStyle.fontSize.toDp())
            else Modifier
        )
    ) {
        Text(
            text = annotated,
            inlineContent = inlineContents,
            softWrap = true,
            overflow = TextOverflow.Visible,
        )
    }
}

// ---------------------------------------------------------------------------
// Inline content builder
// ---------------------------------------------------------------------------

private fun AnnotatedString.Builder.appendMdInline(
    node: ASTNode,
    content: String,
    trim: Boolean,
    colorScheme: ColorScheme,
    density: Density,
    style: TextStyle,
    inlineContents: MutableMap<String, InlineTextContent>,
) {
    when {
        node.type == MarkdownTokenTypes.BLOCK_QUOTE -> { /* skip */ }

        node.type == GFMTokenTypes.GFM_AUTOLINK -> {
            val link = node.getTextInNode(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(link) }
            }
        }

        node is LeafASTNode -> {
            val text = node.getTextInNode(content).let { if (trim) it.trim() else it }
            append(text)
        }

        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.trim(MarkdownTokenTypes.EMPH, 1).forEach { c ->
                    appendMdInline(c, content, trim, colorScheme, density, style, inlineContents)
                }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.trim(MarkdownTokenTypes.EMPH, 2).forEach { c ->
                    appendMdInline(c, content, trim, colorScheme, density, style, inlineContents)
                }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                node.children.trim(GFMTokenTypes.TILDE, 2).forEach { c ->
                    appendMdInline(c, content, trim, colorScheme, density, style, inlineContents)
                }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                ?.getTextInNode(content) ?: ""
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)
                ?.getTextInNode(content)?.trim('[', ']') ?: linkDest
            withLink(LinkAnnotation.Url(linkDest)) {
                withStyle(
                    SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append(linkText)
                }
            }
        }

        node.type == MarkdownElementTypes.AUTOLINK -> {
            node.children.trim(MarkdownTokenTypes.LT, 1).trim(MarkdownTokenTypes.GT, 1)
                .forEach { link ->
                    val url = link.getTextInNode(content)
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(url) }
                    }
                }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.9.em,
                    color = colorScheme.secondary,
                    background = colorScheme.secondaryContainer,
                )
            ) {
                append(' ')
                append(code)
                append(' ')
            }
        }

        else -> {
            node.children.forEach { c ->
                appendMdInline(c, content, trim, colorScheme, density, style, inlineContents)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Table
// ---------------------------------------------------------------------------

@Composable
private fun MdTable(node: ASTNode, content: String, modifier: Modifier = Modifier) {
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
    if (columnCount == 0) return

    val headerCells = headerNode?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { it.getTextInNode(content).trim() }
        ?: emptyList()

    val rows = rowNodes.map { rn ->
        rn.children.filter { it.type == GFMTokenTypes.CELL }.map { it.getTextInNode(content).trim() }
    }

    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            headerCells.forEach { cell ->
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Data rows
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// AST utility extensions
// ---------------------------------------------------------------------------

private fun ASTNode.getTextInNode(text: String): String =
    text.substring(startOffset, endOffset)

private fun ASTNode.nextSibling(): ASTNode? {
    val siblings = this.parent?.children ?: return null
    val idx = siblings.indexOf(this)
    return if (idx + 1 < siblings.size) siblings[idx + 1] else null
}

private fun ASTNode.findChildOfTypeRecursive(vararg types: IElementType): ASTNode? {
    if (this.type in types) return this
    for (c in children) {
        val r = c.findChildOfTypeRecursive(*types)
        if (r != null) return r
    }
    return null
}

private fun List<ASTNode>.trim(type: IElementType, size: Int): List<ASTNode> {
    if (isEmpty() || size <= 0) return this
    var start = 0
    var end = size
    var trimmed = 0
    while (start < end && trimmed < size && this[start].type == type) { start++; trimmed++ }
    trimmed = 0
    while (end > start && trimmed < size && this[end - 1].type == type) { end--; trimmed++ }
    return subList(start, end)
}
