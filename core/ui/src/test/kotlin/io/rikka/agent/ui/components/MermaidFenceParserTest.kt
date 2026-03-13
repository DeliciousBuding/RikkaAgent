package io.rikka.agent.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidFenceParserTest {

  @Test
  fun `hasMermaidFence detects mermaid fence`() {
    val markdown = """
      # Title

      ```mermaid
      graph TD
      A-->B
      ```
    """.trimIndent()

    assertTrue(MermaidFenceParser.hasMermaidFence(markdown))
  }

  @Test
  fun `split separates markdown and mermaid segments`() {
    val markdown = """
      Intro text

      ```mermaid
      graph TD
      A-->B
      ```

      Outro text
    """.trimIndent()

    val segments = MermaidFenceParser.split(markdown)

    assertEquals(3, segments.size)
    assertEquals(MermaidSegmentKind.Markdown, segments[0].kind)
    assertEquals(MermaidSegmentKind.Mermaid, segments[1].kind)
    assertEquals("graph TD\nA-->B", segments[1].content)
    assertEquals(MermaidSegmentKind.Markdown, segments[2].kind)
  }

  @Test
  fun `split keeps unclosed fence as markdown`() {
    val markdown = """
      before
      ```mermaid
      graph TD
      A-->B
    """.trimIndent()

    val segments = MermaidFenceParser.split(markdown)

    assertEquals(2, segments.size)
    assertTrue(segments.all { it.kind == MermaidSegmentKind.Markdown })
    assertTrue(segments.joinToString("\n") { it.content }.contains("```mermaid"))
  }

  @Test
  fun `hasMermaidFence false for plain markdown`() {
    val markdown = "# Title\n\nregular text"
    assertFalse(MermaidFenceParser.hasMermaidFence(markdown))
  }
}
