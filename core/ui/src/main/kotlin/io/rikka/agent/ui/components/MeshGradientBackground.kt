package io.rikka.agent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gemini-style animated mesh gradient background.
 *
 * Four radial blobs drift along independent sine/cosine trajectories,
 * layered on top of a base linear gradient. No blur dependency — works on all API levels.
 */
@Composable
fun MeshGradientBackground(
  modifier: Modifier = Modifier,
  darkMode: Boolean = isSystemInDarkTheme(),
  content: @Composable BoxScope.() -> Unit = {},
) {
  val transition = rememberInfiniteTransition(label = "aurora")

  @Composable
  fun phase(durationMillis: Int, label: String) = transition.animateFloat(
    initialValue = 0f,
    targetValue = (2 * PI).toFloat(),
    animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
    label = label,
  )

  val p1 by phase(5_500, "p1")
  val p2 by phase(7_000, "p2")
  val p3 by phase(8_500, "p3")
  val p4 by phase(6_200, "p4")

  val baseGradient = if (darkMode) {
    arrayOf(
      0.0f to Color(0xFF1B2A45),
      0.22f to Color(0xFF15223A),
      0.45f to Color(0xFF0D1626),
      0.65f to Color(0xFF0A0F18),
      1.0f to Color(0xFF080B12),
    )
  } else {
    arrayOf(
      0.0f to Color(0xFFAFD0F2),
      0.22f to Color(0xFFCBE0F6),
      0.45f to Color(0xFFF1F7FD),
      0.65f to Color(0xFFFFFFFF),
      1.0f to Color(0xFFFFFFFF),
    )
  }

  val blobBlue = if (darkMode) Color(0xFF3E6FB0) else Color(0xFF9EC5F0)
  val blobTeal = if (darkMode) Color(0xFF2E7D74) else Color(0xFFA8E6E0)
  val blobLightBlue = if (darkMode) Color(0xFF4A6E96) else Color(0xFFB6D7F2)
  val blobWarm = if (darkMode) Color(0xFF7C5F9E) else Color(0xFFFFC8D2)
  val alphaBlue = if (darkMode) 0.56f else 0.72f
  val alphaTeal = if (darkMode) 0.44f else 0.56f
  val alphaLightBlue = if (darkMode) 0.48f else 0.62f
  val alphaWarm = if (darkMode) 0.32f else 0.42f

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(colorStops = baseGradient)),
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val r = maxOf(w, h)

      // Blue blob — top, slow horizontal drift
      drawBlob(
        center = Offset(
          w * 0.48f + sin(p1) * w * 0.38f,
          h * 0.08f + cos(p1 * 1.15f) * h * 0.18f,
        ),
        radius = r * 0.36f,
        color = blobBlue,
        centerAlpha = alphaBlue,
      )
      // Teal blob — upper left
      drawBlob(
        center = Offset(
          w * 0.18f + sin(p2 + PI.toFloat() * 0.55f) * w * 0.30f,
          h * 0.24f + cos(p2) * h * 0.20f,
        ),
        radius = r * 0.28f,
        color = blobTeal,
        centerAlpha = alphaTeal,
      )
      // Light blue blob — upper right
      drawBlob(
        center = Offset(
          w * 0.82f + sin(p3 + PI.toFloat() * 0.9f) * w * -0.34f,
          h * 0.12f + cos(p3 * 0.9f) * h * 0.18f,
        ),
        radius = r * 0.30f,
        color = blobLightBlue,
        centerAlpha = alphaLightBlue,
      )
      // Warm blob — provides motion contrast
      drawBlob(
        center = Offset(
          w * 0.58f + sin(p4 + PI.toFloat() * 1.25f) * w * 0.28f,
          h * 0.34f + cos(p4 * 1.1f) * h * 0.16f,
        ),
        radius = r * 0.26f,
        color = blobWarm,
        centerAlpha = alphaWarm,
      )
    }

    content()
  }
}

/** Draw a soft radial blob: colored center fading to transparent edges. */
private fun DrawScope.drawBlob(
  center: Offset,
  radius: Float,
  color: Color,
  centerAlpha: Float = 0.75f,
) {
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(color.copy(alpha = centerAlpha), Color.Transparent),
      center = center,
      radius = radius,
    ),
    radius = radius,
    center = center,
  )
}
