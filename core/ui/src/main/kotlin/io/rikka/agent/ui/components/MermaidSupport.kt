package io.rikka.agent.ui.components

enum class MermaidSegmentKind {
  Markdown,
  Mermaid,
}

data class MermaidSegment(
  val kind: MermaidSegmentKind,
  val content: String,
)

object MermaidFenceParser {
  fun hasMermaidFence(markdown: String): Boolean {
    return split(markdown).any { it.kind == MermaidSegmentKind.Mermaid }
  }

  fun split(markdown: String): List<MermaidSegment> {
    if (markdown.isBlank()) return emptyList()

    val lines = markdown.split("\n")
    val segments = mutableListOf<MermaidSegment>()
    val mdBuffer = StringBuilder()
    val mermaidBuffer = StringBuilder()
    var inMermaidFence = false

    fun flushMarkdown() {
      if (mdBuffer.isNotEmpty()) {
        segments.add(MermaidSegment(MermaidSegmentKind.Markdown, mdBuffer.toString()))
        mdBuffer.clear()
      }
    }

    fun flushMermaid() {
      if (mermaidBuffer.isNotEmpty()) {
        segments.add(MermaidSegment(MermaidSegmentKind.Mermaid, mermaidBuffer.toString().trimEnd('\n')))
        mermaidBuffer.clear()
      }
    }

    for (line in lines) {
      val trimmed = line.trim()
      if (!inMermaidFence && trimmed.startsWith("```") && trimmed.removePrefix("```").trim().equals("mermaid", ignoreCase = true)) {
        flushMarkdown()
        inMermaidFence = true
        continue
      }

      if (inMermaidFence && trimmed == "```") {
        flushMermaid()
        inMermaidFence = false
        continue
      }

      if (inMermaidFence) {
        mermaidBuffer.append(line).append('\n')
      } else {
        mdBuffer.append(line).append('\n')
      }
    }

    if (inMermaidFence) {
      mdBuffer.append("```mermaid\n")
      mdBuffer.append(mermaidBuffer)
    }

    flushMarkdown()

    return segments
  }
}

object MermaidRenderSupport {
  const val WEBVIEW_HEIGHT_DP = 220

  fun mermaidTheme(isDark: Boolean): String = if (isDark) "dark" else "default"

  fun buildHtml(source: String, theme: String): String {
    val escaped = source
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

    val safeTheme = when (theme) {
      "dark" -> "dark"
      else -> "default"
    }

    return """
      <!doctype html>
      <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <style>
            body {
              margin: 0;
              padding: 0;
              background: transparent;
              color: ${'$'}{if (safeTheme == "dark") "#E2E8F0" else "#1E293B"};
              font-family: sans-serif;
            }
            #mermaid {
              width: 100%;
              min-height: 120px;
            }
            #src {
              display: none;
            }
            .mermaid-fallback {
              white-space: pre-wrap;
              font-family: monospace;
              font-size: 12px;
              line-height: 1.4;
              border-radius: 8px;
              padding: 8px;
              background: ${'$'}{if (safeTheme == "dark") "#1F2937" else "#F1F5F9"};
            }
          </style>
        </head>
        <body>
          <pre id="src" style="white-space: pre-wrap; font-family: monospace;">$escaped</pre>
          <div id="mermaid"></div>
          <script>
            (function() {
              const source = document.getElementById('src').textContent || '';
              const container = document.getElementById('mermaid');
              const safe = source
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');

              // Local lightweight runtime shim: keeps rendering offline and deterministic.
              window.mermaid = {
                initialize: function() {},
                render: function(id, text, cb) {
                  const fg = '${'$'}{if (safeTheme == "dark") "#E2E8F0" else "#1E293B"}';
                  const bg = '${'$'}{if (safeTheme == "dark") "#0F172A" else "#FFFFFF"}';
                  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="220" viewBox="0 0 960 220">'
                    + '<rect x="0" y="0" width="960" height="220" fill="' + bg + '"/>'
                    + '<text x="16" y="32" fill="' + fg + '" font-family="monospace" font-size="16">Mermaid Diagram (' + '${safeTheme}' + ')</text>'
                    + '<foreignObject x="16" y="46" width="928" height="160">'
                    + '<div xmlns="http://www.w3.org/1999/xhtml" style="font-family: monospace; font-size: 12px; color:' + fg + '; white-space: pre-wrap;">'
                    + safe +
                    + '</div></foreignObject></svg>';
                  cb(svg);
                }
              };

              try {
                window.mermaid.initialize({ startOnLoad: false, theme: '${safeTheme}' });
                window.mermaid.render('m1', source, function(svg) {
                  container.innerHTML = svg;
                });
              } catch (e) {
                container.innerHTML = '<div class="mermaid-fallback">' + safe + '</div>';
              }
            })();
          </script>
        </body>
      </html>
    """.trimIndent()
  }
}
