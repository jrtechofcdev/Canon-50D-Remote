package com.example.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the screens on the design tokens (DESIGN.md): colours come from `Tone` and corners from the
 * theme's shapes. What is still written by hand is counted here, and the count may only go down. A new
 * screen that adds a raw colour fails this test; lower the number when cleaning one up.
 */
class DesignTokensTest {
    /** Hand-written values still in `ui/` (outside `ui/theme`), as of the 5.0 redesign. */
    private val allowed = mapOf(
        // Draw code (Canvas, crop frame, histograms, peaking, the opening animation) where Tone can't be read.
        "Color(0x" to 32,
        "Color.White" to 17,
        // Full black behind a photo or the live view, on purpose (no grey cast around the picture).
        "Color.Black" to 15,
        // The rotation dial, QR plates and a few round handles.
        "RoundedCornerShape(" to 12,
    )

    @Test
    fun screensUseTheTokens() {
        // Unit tests run from the module folder.
        val ui = File("src/main/java/com/example/ui")
        val files = ui.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.contains("/theme/") }.toList()
        assertTrue("ui/ not found from ${File(".").absolutePath}", files.isNotEmpty())
        val problems = allowed.mapNotNull { (pattern, max) ->
            val count = files.sumOf { f -> f.readText().windowed(pattern.length).count { it == pattern } }
            if (count > max) "$pattern: $count (máximo $max). Use Tone, Space e MaterialTheme.shapes." else null
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
