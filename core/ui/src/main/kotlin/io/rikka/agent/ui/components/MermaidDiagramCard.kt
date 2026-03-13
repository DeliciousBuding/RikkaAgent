package io.rikka.agent.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.rikka.agent.ui.R
import androidx.compose.ui.res.stringResource

@Composable
fun MermaidDiagramCard(
  source: String,
  modifier: Modifier = Modifier,
) {
  val renderFailedState = remember(source) { mutableStateOf(false) }
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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

      if (renderFailedState.value) {
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
              renderFailedState.value = false
            },
        )
      } else {
        MermaidWebView(
          source = source,
          mermaidTheme = MermaidRenderSupport.mermaidTheme(isDark),
          onRenderError = { renderFailedState.value = true },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(
  source: String,
  mermaidTheme: String,
  onRenderError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          (MermaidRenderSupport.WEBVIEW_HEIGHT_DP * context.resources.displayMetrics.density).toInt(),
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
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.loadsImagesAutomatically = false
        settings.blockNetworkLoads = true
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
      val html = MermaidRenderSupport.buildHtml(source, mermaidTheme)
      webView.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
      if (mermaidTheme.isBlank() || !html.contains("window.mermaid")) {
        onRenderError()
      }
    },
  )
}
