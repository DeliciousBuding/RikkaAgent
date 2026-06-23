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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Syntax-highlighted code block component.
 *
 * Provides lightweight keyword-based highlighting for common languages.
 * Falls back to monospace plain text for unknown languages.
 *
 * @param code The source code to display.
 * @param language Optional language identifier for highlighting.
 * @param modifier Modifier for the outer container.
 */
@Composable
fun HighlightCodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
) {
    val highlighted = remember(code, language) {
        highlightCode(code, language)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
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
                    text = highlighted,
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

/**
 * Lightweight syntax highlighting using regex-based keyword matching.
 * Supports: Kotlin, Java, Python, JavaScript/TypeScript, Rust, Go, Shell, JSON, XML/HTML, SQL, C/C++.
 */
private fun highlightCode(code: String, language: String?): AnnotatedString {
    val lang = language?.lowercase()?.trim()

    val keywords = when (lang) {
        "kotlin", "kt" -> setOf(
            "fun", "val", "var", "class", "interface", "object", "data", "sealed", "enum",
            "when", "if", "else", "for", "while", "do", "return", "break", "continue",
            "is", "in", "as", "try", "catch", "finally", "throw", "import", "package",
            "private", "public", "internal", "protected", "override", "abstract", "open",
            "companion", "suspend", "inline", "reified", "out", "in", "where", "by",
            "init", "constructor", "get", "set", "lateinit", "lazy",
        )
        "java" -> setOf(
            "public", "private", "protected", "static", "final", "abstract", "class",
            "interface", "extends", "implements", "new", "return", "if", "else", "for",
            "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally",
            "throw", "throws", "import", "package", "void", "int", "long", "double", "float",
            "boolean", "char", "String", "byte", "short", "null", "true", "false", "this", "super",
        )
        "python", "py" -> setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "break", "continue",
            "import", "from", "as", "try", "except", "finally", "raise", "with", "yield",
            "lambda", "pass", "del", "global", "nonlocal", "assert", "and", "or", "not",
            "is", "in", "True", "False", "None", "self", "async", "await",
        )
        "javascript", "js", "typescript", "ts" -> setOf(
            "function", "const", "let", "var", "class", "extends", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch",
            "finally", "throw", "new", "delete", "typeof", "instanceof", "void", "this",
            "super", "import", "export", "default", "from", "as", "async", "await", "yield",
            "of", "in", "true", "false", "null", "undefined", "static", "get", "set",
        )
        "rust", "rs" -> setOf(
            "fn", "let", "mut", "const", "struct", "enum", "impl", "trait", "type", "mod",
            "use", "pub", "crate", "self", "super", "if", "else", "match", "for", "while",
            "loop", "break", "continue", "return", "move", "ref", "unsafe", "async", "await",
            "dyn", "where", "as", "in", "true", "false", "Some", "None", "Ok", "Err",
        )
        "go" -> setOf(
            "func", "var", "const", "type", "struct", "interface", "map", "chan", "package",
            "import", "return", "if", "else", "for", "range", "switch", "case", "default",
            "break", "continue", "go", "defer", "select", "fallthrough", "goto", "nil",
            "true", "false", "make", "new", "append", "len", "cap", "copy", "delete",
        )
        "shell", "bash", "sh", "zsh" -> setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "return", "exit", "local", "export", "source", "alias", "unalias",
            "cd", "ls", "grep", "sed", "awk", "find", "cat", "echo", "printf", "read",
            "test", "true", "false", "in", "select", "until", "time",
        )
        "sql" -> setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
            "TABLE", "INDEX", "VIEW", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AS",
            "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS", "NULL", "ORDER", "BY", "GROUP",
            "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "SET", "VALUES", "INTO",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CASCADE", "CONSTRAINT", "DEFAULT",
            "select", "from", "where", "insert", "update", "delete", "create", "drop", "alter",
            "table", "index", "view", "join", "left", "right", "inner", "outer", "on", "as",
            "and", "or", "not", "in", "between", "like", "is", "null", "order", "by", "group",
            "having", "limit", "offset", "union", "all", "distinct", "set", "values", "into",
        )
        "json" -> setOf()  // JSON has no keywords; highlight strings/numbers via patterns
        "xml", "html" -> setOf()  // XML/HTML: highlight tags via patterns
        else -> setOf()
    }

    val stringPatterns = when (lang) {
        "json" -> listOf(
            Regex("\"\"\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"\"\""),
        )
        else -> listOf(
            Regex("\"\"\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"\"\""),
            Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""),
            Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"),
            Regex("`[^`\\\\]*(?:\\\\.[^`\\\\]*)*`"),
        )
    }

    val commentPattern = when (lang) {
        "python", "py" -> Regex("#[^\n]*")
        "shell", "bash", "sh", "zsh" -> Regex("#[^\n]*")
        "sql" -> Regex("--[^\n]*")
        "json" -> null  // JSON has no comments
        else -> Regex("//[^\n]*|/\\*[\\s\\S]*?\\*/")
    }

    val numberPattern = Regex("\\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|0[oO][0-7]+|\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)[fFlLuU]*\\b")

    return buildAnnotatedString {
        var pos = 0
        val len = code.length

        while (pos < len) {
            // Check for comments
            if (commentPattern != null) {
                val commentMatch = commentPattern.find(code, pos)
                if (commentMatch != null && commentMatch.range.first == pos) {
                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF6A9955))) {
                        append(commentMatch.value)
                    }
                    pos = commentMatch.range.last + 1
                    continue
                }
            }

            // Check for strings
            var matched = false
            for (pattern in stringPatterns) {
                val strMatch = pattern.find(code, pos)
                if (strMatch != null && strMatch.range.first == pos) {
                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFCE9178))) {
                        append(strMatch.value)
                    }
                    pos = strMatch.range.last + 1
                    matched = true
                    break
                }
            }
            if (matched) continue

            // Check for numbers
            val numMatch = numberPattern.find(code, pos)
            if (numMatch != null && numMatch.range.first == pos) {
                withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFB5CEA8))) {
                    append(numMatch.value)
                }
                pos = numMatch.range.last + 1
                continue
            }

            // Check for keywords
            if (keywords.isNotEmpty()) {
                val wordMatch = Regex("\\b\\w+\\b").find(code, pos)
                if (wordMatch != null && wordMatch.range.first == pos) {
                    val word = wordMatch.value
                    if (word in keywords) {
                        withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF569CD6), fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                    } else {
                        append(word)
                    }
                    pos = wordMatch.range.last + 1
                    continue
                }
            }

            // Plain character
            append(code[pos])
            pos++
        }
    }
}
