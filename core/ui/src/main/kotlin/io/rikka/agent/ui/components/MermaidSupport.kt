package io.rikka.agent.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable

enum class MermaidSegmentKind {
  Markdown,
  Mermaid,
}

@Immutable
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

    // Unclosed fence: treat remaining mermaid content as markdown
    if (inMermaidFence) {
      mdBuffer.append("```mermaid\n")
      mdBuffer.append(mermaidBuffer)
    }

    flushMarkdown()

    return segments
  }
}

object MermaidRenderSupport {
  /** Minimum WebView height in dp; actual height adapts to diagram content. */
  const val MIN_WEBVIEW_HEIGHT_DP = 120
  /** Maximum WebView height in dp to prevent runaway diagrams. */
  const val MAX_WEBVIEW_HEIGHT_DP = 600

  /**
   * Build the full HTML page for rendering a Mermaid diagram via CDN.
   *
   * The template:
   * - Loads mermaid@11 from jsDelivr CDN
   * - Maps Material3 [colorScheme] into Mermaid `themeVariables` for
   *   flowcharts, sequence diagrams, gantt charts, and more
   * - Reports render errors back to Android via `AndroidInterface.onRenderError()`
   * - Reports rendered height via `AndroidInterface.onHeightReady(dp)` for auto-sizing
   */
  fun buildHtml(source: String, colorScheme: ColorScheme): String {
    val escaped = source
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("'", "\\'")
      .replace("\n", "\\n")

    val isDark = colorScheme.surface.luminance() < 0.5f

    // Material3 -> Mermaid theme variable mapping
    val primary = colorScheme.primary.toCssHex()
    val onPrimary = colorScheme.onPrimary.toCssHex()
    val primaryContainer = colorScheme.primaryContainer.toCssHex()
    val onPrimaryContainer = colorScheme.onPrimaryContainer.toCssHex()
    val secondary = colorScheme.secondary.toCssHex()
    val secondaryContainer = colorScheme.secondaryContainer.toCssHex()
    val onSecondaryContainer = colorScheme.onSecondaryContainer.toCssHex()
    val tertiaryContainer = colorScheme.tertiaryContainer.toCssHex()
    val onTertiaryContainer = colorScheme.onTertiaryContainer.toCssHex()
    val surface = colorScheme.surface.toCssHex()
    val onSurface = colorScheme.onSurface.toCssHex()
    val surfaceVariant = colorScheme.surfaceVariant.toCssHex()
    val onSurfaceVariant = colorScheme.onSurfaceVariant.toCssHex()
    val outline = colorScheme.outline.toCssHex()
    val error = colorScheme.error.toCssHex()
    val onError = colorScheme.onError.toCssHex()
    val background = colorScheme.background.toCssHex()

    return """
      <!doctype html>
      <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
          <style>
            * { box-sizing: border-box; }
            html, body {
              margin: 0;
              padding: 0;
              background: $background;
              overflow: hidden;
            }
            .mermaid {
              padding: 4px;
              width: 100%;
              display: flex;
              justify-content: center;
            }
            .mermaid svg {
              max-width: 100%;
              height: auto;
              background: transparent !important;
            }
            .mermaid-fallback {
              white-space: pre-wrap;
              font-family: 'JetBrains Mono', 'Fira Code', monospace;
              font-size: 12px;
              line-height: 1.5;
              padding: 12px;
              border-radius: 8px;
              background: ${if (isDark) "#1E293B" else "#F1F5F9"};
              color: $onSurface;
              overflow-x: auto;
            }
            .error-banner {
              font-family: sans-serif;
              font-size: 13px;
              padding: 8px 12px;
              margin: 4px;
              border-radius: 6px;
              background: ${error}20;
              color: $error;
              border-left: 3px solid $error;
            }
          </style>
        </head>
        <body>
          <div class="mermaid" id="diagram"></div>
          <script>
            var source = '$escaped';

            mermaid.initialize({
              startOnLoad: false,
              theme: 'base',
              securityLevel: 'strict',
              themeVariables: {
                // -- Primary palette --
                primaryColor: '$primaryContainer',
                primaryTextColor: '$onPrimaryContainer',
                primaryBorderColor: '$primary',

                // -- Secondary palette --
                secondaryColor: '$secondaryContainer',
                secondaryTextColor: '$onSecondaryContainer',
                secondaryBorderColor: '$secondary',

                // -- Tertiary palette --
                tertiaryColor: '$tertiaryContainer',
                tertiaryTextColor: '$onTertiaryContainer',
                tertiaryBorderColor: '$tertiaryContainer',

                // -- Background & surface --
                background: '$background',
                mainBkg: '$primaryContainer',
                secondBkg: '$secondaryContainer',
                nodeBkg: '$surface',
                nodeBorder: '$primary',
                clusterBkg: '$surfaceVariant',
                clusterBorder: '$outline',

                // -- Text & lines --
                lineColor: '$outline',
                textColor: '$onSurface',
                labelColor: '$onSurfaceVariant',

                // -- Sequence diagram --
                actorBkg: '$primaryContainer',
                actorBorder: '$primary',
                actorTextColor: '$onPrimaryContainer',
                actorLineColor: '$outline',
                signalColor: '$onSurface',
                signalTextColor: '$onSurface',
                labelBoxBkgColor: '$surfaceVariant',
                labelBoxBorderColor: '$outline',
                loopTextColor: '$onSurface',
                noteBkgColor: '$tertiaryContainer',
                noteTextColor: '$onTertiaryContainer',
                noteBorderColor: '$outline',
                activationBkgColor: '$primaryContainer',
                activationBorderColor: '$primary',

                // -- Gantt chart --
                taskBkgColor: '$primaryContainer',
                taskTextColor: '$onPrimaryContainer',
                taskTextLightColor: '$onPrimaryContainer',
                taskTextDarkColor: '$onSurface',
                taskBorderColor: '$primary',
                activeTaskBkgColor: '$primary',
                activeTaskBorderColor: '$primary',
                todayLineColor: '$error',
                sectionBkgColor: '$surfaceVariant',
                sectionBkgColor2: '$surface',
                altSectionBkgColor: '$background',
                gridColor: '${outline}40',
                doneTaskBkgColor: '$outline',
                doneTaskBorderColor: '$outline',
                critBkgColor: '$error',
                critBorderColor: '$error',
                milestoneBkgColor: '$primary',
                milestoneBorderColor: '$primary',

                // -- Pie chart --
                pie1: '$primary',
                pie2: '$secondary',
                pie3: '$tertiaryContainer',
                pie4: '$primaryContainer',
                pie5: '$secondaryContainer',
                pie6: '$outline',
                pie7: '$surfaceVariant',
                pie8: '$onSurfaceVariant',
                pie9: '$error',
                pie10: '$onPrimary',
                pieTitleTextSize: '14px',
                pieTitleTextColor: '$onSurface',
                pieSectionTextSize: '12px',
                pieSectionTextColor: '$onSurface',
                pieLegendTextSize: '12px',
                pieLegendTextColor: '$onSurfaceVariant',
                pieStrokeColor: '$background',
                pieStrokeWidth: '2px',
                pieOpacity: '1',

                // -- Error styling --
                errorBkgColor: '$error',
                errorTextColor: '$onError'
              }
            });

            function reportHeight() {
              var h = document.documentElement.scrollHeight;
              if (h > 0 && window.AndroidInterface) {
                window.AndroidInterface.onHeightReady(h);
              }
            }

            mermaid.render('diagram', source).then(function(result) {
              var container = document.getElementById('diagram');
              container.innerHTML = result.svg;
              reportHeight();
            }).catch(function(err) {
              var container = document.getElementById('diagram');
              var safe = source
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
              container.innerHTML =
                '<div class="error-banner">Render error: ' + (err.message || 'Unknown error') + '</div>' +
                '<div class="mermaid-fallback">' + safe + '</div>';
              reportHeight();
              if (window.AndroidInterface) {
                window.AndroidInterface.onRenderError();
              }
            });
          </script>
        </body>
      </html>
    """.trimIndent()
  }
}

/**
 * Convert a Compose [Color] to a CSS hex string (e.g. "#1E293B").
 * Uses the ARGB int representation.
 */
private fun androidx.compose.ui.graphics.Color.toCssHex(): String {
  val argb = this.toArgb()
  val r = (argb shr 16) and 0xFF
  val g = (argb shr 8) and 0xFF
  val b = argb and 0xFF
  return "#%02X%02X%02X".format(r, g, b)
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
  val argb = this.toArgb()
  val r = ((argb shr 16) and 0xFF) / 255f
  val g = ((argb shr 8) and 0xFF) / 255f
  val b = (argb and 0xFF) / 255f
  return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
