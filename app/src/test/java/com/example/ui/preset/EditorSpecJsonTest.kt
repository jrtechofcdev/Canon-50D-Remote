package com.example.ui.preset

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The page's editor gets every control the app's editor has, with the same ranges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorSpecJsonTest {
    @Test
    fun everySliderOfTheAppIsInTheJson() {
        val text = EditorSpecJson.encode()
        // Kept for the page check in a browser (app/src/test/web).
        java.io.File("build/look-parity").apply { mkdirs() }.resolve("editor.json").writeText(text)
        val json = JSONObject(text)
        val tabs = json.getJSONArray("tabs")
        assertEquals(EditorTab.entries.size, tabs.length())
        val keys = HashMap<String, JSONObject>()
        for (t in 0 until tabs.length()) {
            val groups = tabs.getJSONObject(t).getJSONArray("groups")
            for (g in 0 until groups.length()) {
                val sliders = groups.getJSONObject(g).getJSONArray("sliders")
                for (i in 0 until sliders.length()) sliders.getJSONObject(i).let { keys[it.getString("key")] = it }
            }
        }
        val app = with(EditorControls) { light + wbRelative + presence + effects + vignette + grain + sharpening + noise + parametric + calibration + gradingCommon }
        app.forEach { spec ->
            val s = keys[spec.key] ?: error("${spec.key} missing")
            assertEquals(spec.min.toDouble(), s.getDouble("min"), 0.0)
            assertEquals(spec.max.toDouble(), s.getDouble("max"), 0.0)
            assertEquals(spec.default.toDouble(), s.getDouble("def"), 0.0)
        }
        val color = (0 until tabs.length()).map { tabs.getJSONObject(it) }.first { it.getString("id") == "color" }
        assertEquals(8, color.getJSONArray("mixer").length())
        assertTrue(color.getJSONArray("mixer").getJSONObject(0).getString("color").matches(Regex("#[0-9A-F]{6}")))
        assertEquals(2, json.getJSONArray("kelvin").length())
    }
}
