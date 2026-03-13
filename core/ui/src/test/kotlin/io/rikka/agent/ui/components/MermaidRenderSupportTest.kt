package io.rikka.agent.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidRenderSupportTest {

  @Test
  fun `mermaidTheme maps light and dark`() {
    assertEquals("default", MermaidRenderSupport.mermaidTheme(false))
    assertEquals("dark", MermaidRenderSupport.mermaidTheme(true))
  }

  @Test
  fun `buildHtml escapes dangerous characters`() {
    val html = MermaidRenderSupport.buildHtml("graph TD\nA--><script>alert(1)</script>", "dark")
    assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    assertTrue(html.contains("<pre id=\"src\""))
    assertTrue(html.contains("window.mermaid"))
    assertTrue(html.contains("theme: 'dark'"))
  }
}
