@file:Suppress("unused")

package io.rikka.agent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * DataTable: SubcomposeLayout-based table with horizontal scrolling and column-width auto-fit.
 *
 * Two-phase measurement:
 * 1. Natural-size pass to determine column widths and row heights.
 * 2. Fixed-width re-measure with uniform row heights per row.
 *
 * When [stretchToFillWidth] is true and content is narrower than the viewport,
 * extra space is distributed proportionally across columns.
 *
 * Ported from RikkaHub's DataTable, adapted for RikkaAgent's SSH output rendering.
 */
@Composable
fun DataTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    cellPadding: Dp = 8.dp,
    cellBorder: BorderStroke? = BorderStroke(
        0.5.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    ),
    headerBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    zebraStriping: Boolean = true,
    columnMinWidths: List<Dp> = emptyList(),
    columnMaxWidths: List<Dp> = emptyList(),
    cellAlignment: Alignment = Alignment.CenterStart,
    outerBorder: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
    ),
    shape: Shape = MaterialTheme.shapes.small,
    stretchToFillWidth: Boolean = true,
) {
    val headerComposables = remember(headers) {
        headers.map { header ->
            @Composable {
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    val bodyComposables = remember(rows) {
        rows.map { row ->
            row.map { cell ->
                @Composable {
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    DataTableLayout(
        headers = headerComposables,
        rows = bodyComposables,
        modifier = modifier,
        cellPadding = cellPadding,
        cellBorder = cellBorder,
        headerBackground = headerBackground,
        zebraStriping = zebraStriping,
        columnMinWidths = columnMinWidths,
        columnMaxWidths = columnMaxWidths,
        cellAlignment = cellAlignment,
        outerBorder = outerBorder,
        shape = shape,
        stretchToFillWidth = stretchToFillWidth,
    )
}

/**
 * Generic DataTable that accepts composable cell content.
 *
 * Use this overload when you need custom cell rendering (e.g. styled text, icons).
 * For simple string data from SSH/CSV output, prefer the [DataTable] overload.
 */
@Composable
fun DataTableLayout(
    headers: List<@Composable () -> Unit>,
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    cellPadding: Dp = 8.dp,
    cellBorder: BorderStroke? = BorderStroke(
        0.5.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    ),
    headerBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    zebraStriping: Boolean = true,
    columnMinWidths: List<Dp> = emptyList(),
    columnMaxWidths: List<Dp> = emptyList(),
    cellAlignment: Alignment = Alignment.CenterStart,
    outerBorder: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant,
    ),
    shape: Shape = MaterialTheme.shapes.small,
    stretchToFillWidth: Boolean = true,
) {
    val hScroll = rememberScrollState()
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .then(
                if (outerBorder != null) Modifier.border(outerBorder, shape) else Modifier
            ),
    ) {
        val viewportMaxWidth = constraints.maxWidth

        Box(modifier = Modifier.horizontalScroll(hScroll)) {
            SubcomposeLayout { subcomposeConstraints ->
                val columnCount = max(headers.size, rows.maxOfOrNull { it.size } ?: 0)
                val rowCount = rows.size
                if (columnCount == 0) return@SubcomposeLayout layout(0, 0) {}

                val infinity = Constraints.Infinity
                val unbounded = Constraints(0, infinity, 0, infinity)
                val minWidthsPx = IntArray(columnCount) { i ->
                    columnMinWidths.getOrNull(i)?.roundToPx() ?: 0
                }
                val maxWidthsPx = IntArray(columnCount) { i ->
                    columnMaxWidths.getOrNull(i)?.roundToPx() ?: Int.MAX_VALUE
                }
                val colWidths = IntArray(columnCount) { 0 }
                val headerP1 = arrayOfNulls<Placeable>(columnCount)
                val bodyP1 = arrayOfNulls<Placeable>(rowCount * columnCount)

                // Phase 1: natural-size measurement
                fun subcomposeHeaderOnce(c: Int): Placeable {
                    val measurables = subcompose("h1_$c") {
                        CellBox(
                            padding = cellPadding,
                            border = cellBorder,
                            background = headerBackground,
                            alignment = cellAlignment,
                        ) {
                            headers.getOrNull(c)?.invoke()
                        }
                    }
                    val cellConstraints = if (maxWidthsPx[c] != Int.MAX_VALUE) {
                        Constraints(0, maxWidthsPx[c], 0, infinity)
                    } else {
                        unbounded
                    }
                    val p = measurables.first().measure(cellConstraints)
                    colWidths[c] = max(
                        colWidths[c],
                        max(p.width, minWidthsPx[c]),
                    ).coerceAtMost(maxWidthsPx[c])
                    return p
                }

                fun subcomposeBodyOnce(r: Int, c: Int): Placeable {
                    val bg = if (zebraStriping && r % 2 == 1) {
                        surfaceContainer
                    } else {
                        Color.Transparent
                    }
                    val measurables = subcompose("b1_${r}_$c") {
                        CellBox(
                            padding = cellPadding,
                            border = cellBorder,
                            background = bg,
                            alignment = cellAlignment,
                        ) {
                            rows[r].getOrNull(c)?.invoke()
                        }
                    }
                    val cellConstraints = if (maxWidthsPx[c] != Int.MAX_VALUE) {
                        Constraints(0, maxWidthsPx[c], 0, infinity)
                    } else {
                        unbounded
                    }
                    val p = measurables.first().measure(cellConstraints)
                    colWidths[c] = max(
                        colWidths[c],
                        max(p.width, minWidthsPx[c]),
                    ).coerceAtMost(maxWidthsPx[c])
                    return p
                }

                for (c in 0 until columnCount) headerP1[c] = subcomposeHeaderOnce(c)
                for (r in 0 until rowCount) {
                    for (c in 0 until columnCount) {
                        bodyP1[r * columnCount + c] = subcomposeBodyOnce(r, c)
                    }
                }

                val rowHeights = IntArray(rowCount) { r ->
                    var h = 0
                    for (c in 0 until columnCount) {
                        h = max(h, bodyP1[r * columnCount + c]!!.height)
                    }
                    h
                }
                val headerHeight = headerP1.maxOf { it?.height ?: 0 }

                // Stretch columns to fill viewport width when content is narrow
                val naturalWidth = colWidths.sum()
                if (stretchToFillWidth &&
                    viewportMaxWidth != Constraints.Infinity &&
                    viewportMaxWidth > naturalWidth &&
                    naturalWidth > 0
                ) {
                    val extra = viewportMaxWidth - naturalWidth
                    var distributed = 0
                    for (c in 0 until columnCount) {
                        val add = if (c == columnCount - 1) {
                            extra - distributed
                        } else {
                            (extra.toLong() * colWidths[c] / naturalWidth).toInt()
                        }
                        colWidths[c] += add
                        distributed += add
                    }
                }

                // Phase 2: fixed-width + uniform-height re-measure
                fun constraintsFor(colWidth: Int, minHeight: Int): Constraints {
                    val safeColWidth = colWidth.coerceAtLeast(0)
                    val safeMinHeight = minHeight.coerceAtLeast(0)
                    return Constraints(
                        minWidth = safeColWidth,
                        maxWidth = safeColWidth,
                        minHeight = safeMinHeight,
                        maxHeight = infinity,
                    )
                }

                val headerPlaceables = Array(columnCount) { c ->
                    val measurables = subcompose("h2_$c") {
                        CellBox(
                            padding = cellPadding,
                            border = cellBorder,
                            background = headerBackground,
                            alignment = cellAlignment,
                        ) {
                            headers.getOrNull(c)?.invoke()
                        }
                    }
                    measurables.first().measure(constraintsFor(colWidths[c], headerHeight))
                }

                val bodyPlaceables = Array(rowCount * columnCount) { i ->
                    val r = i / columnCount
                    val c = i % columnCount
                    val bg = if (zebraStriping && r % 2 == 1) {
                        surfaceContainer
                    } else {
                        Color.Transparent
                    }
                    val measurables = subcompose("b2_${r}_$c") {
                        CellBox(
                            padding = cellPadding,
                            border = cellBorder,
                            background = bg,
                            alignment = cellAlignment,
                        ) {
                            rows[r].getOrNull(c)?.invoke()
                        }
                    }
                    measurables.first().measure(constraintsFor(colWidths[c], rowHeights[r]))
                }

                val tableWidth = colWidths.sum()
                val tableHeight = headerHeight + rowHeights.sum()
                val finalWidth = tableWidth.coerceIn(
                    subcomposeConstraints.minWidth,
                    subcomposeConstraints.maxWidth,
                )
                val finalHeight = tableHeight.coerceIn(
                    subcomposeConstraints.minHeight,
                    subcomposeConstraints.maxHeight,
                )

                layout(finalWidth, finalHeight) {
                    var x = 0
                    for (c in 0 until columnCount) {
                        headerPlaceables[c].placeRelative(x, 0)
                        x += colWidths[c]
                    }
                    var y = headerHeight
                    for (r in 0 until rowCount) {
                        x = 0
                        for (c in 0 until columnCount) {
                            bodyPlaceables[r * columnCount + c].placeRelative(x, y)
                            x += colWidths[c]
                        }
                        y += rowHeights[r]
                    }
                }
            }
        }
    }
}

@Composable
private fun CellBox(
    padding: Dp,
    border: BorderStroke?,
    background: Color,
    alignment: Alignment,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .then(
                if (background != Color.Transparent) Modifier.background(background) else Modifier
            )
            .then(if (border != null) Modifier.border(border) else Modifier)
            .padding(padding),
        contentAlignment = alignment,
    ) {
        content()
    }
}

// ── SSH Output Parsers ──────────────────────────────────────────────────────────

/**
 * Parsed table data extracted from SSH command output.
 */
data class ParsedTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

/**
 * Parses a GFM Markdown table into [ParsedTable].
 *
 * Handles standard pipe-delimited tables:
 * ```
 * | Header 1 | Header 2 |
 * |----------|----------|
 * | Cell 1   | Cell 2   |
 * ```
 *
 * Also handles tables without leading/trailing pipes:
 * ```
 * Header 1 | Header 2
 * ---------|---------
 * Cell 1   | Cell 2
 * ```
 */
fun parseMarkdownTable(text: String): ParsedTable? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size < 2) return null

    fun splitCells(line: String): List<String> {
        val trimmed = line.trim().trimStart('|').trimEnd('|')
        return trimmed.split('|').map { it.trim() }
    }

    fun isSeparator(line: String): Boolean {
        val stripped = line.trim().trimStart('|').trimEnd('|')
        return stripped.split('|').all { cell ->
            cell.trim().let { it.isNotEmpty() && it.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }
        }
    }

    val headerCells = splitCells(lines[0])
    if (headerCells.isEmpty() || headerCells.all { it.isBlank() }) return null

    // Find the separator row
    val separatorIdx = lines.indexOfFirst { isSeparator(it) }
    if (separatorIdx < 0) return null

    val dataRows = mutableListOf<List<String>>()
    for (i in (separatorIdx + 1) until lines.size) {
        if (isSeparator(lines[i])) continue
        val cells = splitCells(lines[i])
        if (cells.all { it.isBlank() }) continue
        // Pad or trim to match header column count
        val padded = if (cells.size < headerCells.size) {
            cells + List(headerCells.size - cells.size) { "" }
        } else {
            cells.take(headerCells.size)
        }
        dataRows.add(padded)
    }

    return ParsedTable(headers = headerCells, rows = dataRows)
}

/**
 * Parses CSV or whitespace-delimited tabular output common in SSH command results.
 *
 * Supports two modes:
 * - **CSV**: Comma-separated values (optionally quoted).
 * - **Whitespace-delimited**: Two or more spaces act as column separators,
 *   which matches the output of commands like `ps aux`, `df -h`, `docker ps`.
 */
fun parseCsvOrWhitespaceTable(text: String): ParsedTable? {
    val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.size < 2) return null

    val isCsv = lines[0].contains(',') && lines[0].count { it == ',' } >= 1

    if (isCsv) {
        return parseCsvLines(lines)
    }

    // Try whitespace-delimited (2+ spaces as separator)
    val whitespacePattern = Regex("""\s{2,}""")
    val parsedLines = lines.map { line ->
        whitespacePattern.split(line.trim()).filter { it.isNotEmpty() }
    }

    val maxCols = parsedLines.maxOf { it.size }
    if (maxCols < 2) return null

    // Check consistency: at least 60% of rows should have similar column count
    val avgCols = parsedLines.map { it.size }.average()
    val consistentRows = parsedLines.count { kotlin.math.abs(it.size - avgCols) <= 1.0 }
    if (consistentRows.toDouble() / parsedLines.size < 0.6) return null

    val header = parsedLines.first().let { row ->
        if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row.take(maxCols)
    }
    val rows = parsedLines.drop(1).map { row ->
        if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row.take(maxCols)
    }

    return ParsedTable(headers = header, rows = rows)
}

private fun parseCsvLines(lines: List<String>): ParsedTable? {
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    val parsed = lines.map { parseCsvLine(it) }
    val maxCols = parsed.maxOf { it.size }
    if (maxCols < 2) return null

    val header = parsed.first().let { row ->
        if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row.take(maxCols)
    }
    val rows = parsed.drop(1).map { row ->
        if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row.take(maxCols)
    }

    return ParsedTable(headers = header, rows = rows)
}

/**
 * Attempts to parse a block of text as a table (Markdown or CSV/whitespace).
 * Returns null if the text does not look like tabular data.
 */
fun parseTabularOutput(text: String): ParsedTable? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    return parseMarkdownTable(trimmed)
        ?: parseCsvOrWhitespaceTable(trimmed)
}
