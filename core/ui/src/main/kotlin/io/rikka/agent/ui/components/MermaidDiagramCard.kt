package io.rikka.agent.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.rikka.agent.ui.R

/**
 * Renders a Mermaid diagram inside a card with Material3 theming.
 *
 * Features:
 * - Real Mermaid rendering via CDN (mermaid@11)
 * - Dark/light theme auto-detection from MaterialTheme.colorScheme
 * - Auto-height WebView that adapts to diagram content
 * - Graceful fallback: on render error, shows source code + retry button
 * - Supports all Mermaid diagram types: flowchart, sequence, gantt, class, state, etc.
 */
@Composable
fun MermaidDiagramCard(
  source: String,
  modifier: Modifier = Modifier,
) {
  var renderFailed by remember(source) { mutableStateOf(false) }
  var webViewHeightPx by remember(source) { mutableFloatStateOf(0f) }
  val colorScheme = MaterialTheme.colorScheme
  val density = LocalDensity.current

  val webViewHeightDp = with(density) {
    if (webViewHeightPx > 0f) {
      webViewHeightPx.toDp().coerceIn(
        MermaidRenderSupport.MIN_WEBVIEW_HEIGHT_DP.dp,
        MermaidRenderSupport.MAX_WEBVIEW_HEIGHT_DP.dp,
      )
    } else {
      MermaidRenderSupport.MIN_WEBVIEW_HEIGHT_DP.dp
    }
  }

  Surface(
    modifier = modifier.fillMaxWidth(),
    tonalElevation = 0.dp,
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.mermaid_diagram),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )

      AnimatedVisibility(
        visible = renderFailed,
        enter = expandVertically(),
        exit = shrinkVertically(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.mermaid_render_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
          CodeCard(
            code = source,
            language = "mermaid",
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = stringResource(R.string.mermaid_retry),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .padding(top = 2.dp)
              .fillMaxWidth()
              .clickable {
                renderFailed = false
                webViewHeightPx = 0f
              },
          )
        }
      }

      AnimatedVisibility(
        visible = !renderFailed,
        enter = expandVertically(),
        exit = shrinkVertically(),
      ) {
        MermaidWebView(
          source = source,
          colorScheme = colorScheme,
          onRenderError = { renderFailed = true },
          onHeightReady = { heightPx -> webViewHeightPx = heightPx },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MermaidRenderSupport.MIN_WEBVIEW_HEIGHT_DP.dp)
            .then(
              if (webViewHeightPx > 0f) Modifier.heightIn(max = webViewHeightDp)
              else Modifier
            ),
        )
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(
  source: String,
  colorScheme: androidx.compose.material3.ColorScheme,
  onRenderError: () -> Unit,
  onHeightReady: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        webViewClient = object : WebViewClient() {
          override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String?,
            failingUrl: String?,
          ) {
            onRenderError()
          }
        }
        // JS bridge for render callbacks
        addJavascriptInterface(
          object {
            @JavascriptInterface
            fun onRenderError() {
              post { onRenderError() }
            }

            @JavascriptInterface
            fun onHeightReady(heightPx: Float) {
              post {
                // Convert from CSS pixels (what the WebView reports) to device pixels
                val devicePx = heightPx * context.resources.displayMetrics.density
                onHeightReady(devicePx)
              }
            }
          },
          "AndroidInterface",
        )
        // Security-hardened settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.loadsImagesAutomatically = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        try {
          settings.javaClass.getMethod("setAllowFileAccessFromFileURLs", Boolean::class.javaPrimitiveType)
            .invoke(settings, false)
          settings.javaClass.getMethod("setAllowUniversalAccessFromFileURLs", Boolean::class.javaPrimitiveType)
            .invoke(settings, false)
        } catch (_: Exception) {
          // Ignore on old platform APIs.
        }
      }
    },
    update = { webView ->
      val html = MermaidRenderSupport.buildHtml(source, colorScheme)
      webView.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
    },
  )
}
