package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The whole app (tabs + capture button), as opposed to ScreenshotTest's single screens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MainScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun save(name: String) {
        runCatching { rule.waitUntil(6_000) { false } }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun photosTab() {
        rule.onNode(hasText("Fotos")).performClick()
        save("app_fotos")
    }

    @Test
    fun cullingTab() {
        // The tab, not the Culling tile of the Session's tools.
        val tab = androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab,
        )
        rule.onNode(hasText("Culling") and tab).performClick()
        save("app_culling")
    }

    /** Express gets its tab once it is switched on; Ajustes stays pinned at the end of the bar. */
    @Test
    fun expressTab() {
        kotlinx.coroutines.runBlocking {
            rule.activity.application.let { it as com.example.TetherApp }.settings.set(com.example.core.data.SettingKeys.expressEnabled, true)
        }
        rule.waitUntil(8_000) { rule.onAllNodes(hasText("Express")).fetchSemanticsNodes().isNotEmpty() }
        val tab = androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Tab,
        )
        rule.onNode(hasText("Express") and tab).performClick()
        rule.waitUntil(8_000) { rule.onAllNodes(hasText("Fotografia Express")).fetchSemanticsNodes().isNotEmpty() }
        save("app_express")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun sessionLyingDown() = save("app_sessao_deitado")
}
