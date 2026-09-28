package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.core.data.Photo
import com.example.ui.photos.PhotosScreen
import com.example.ui.photos.ViewerScreen
import kotlinx.coroutines.runBlocking
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.settings.SettingsSectionScreen
import com.example.ui.session.SessionScreen
import com.example.ui.theme.JrLinkTheme
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens to PNG (app/build/screens) so the layout can be reviewed without a phone.
 * Not an assertion test: it fails only if a screen crashes while composing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()

    private fun save(name: String, readyWhen: androidx.compose.ui.test.SemanticsMatcher? = null) {
        if (readyWhen != null) {
            runCatching { rule.waitUntil(15_000) { rule.onAllNodes(readyWhen).fetchSemanticsNodes().isNotEmpty() } }
        }
        // Room, Coil and the histogram work on background threads. waitUntil keeps the clock and loopers
        // moving while it polls, so let it run out before drawing.
        runCatching { rule.waitUntil(8_000) { false } }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun session() {
        seedPhotos()
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme { SessionScreen(app, actions, {}, {}, {}, {}) } }
        save("sessao", androidx.compose.ui.test.hasText("Finalizar sessão"))
    }

    @Test
    fun sessionNight() {
        seedPhotos()
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme(night = true) { SessionScreen(app, actions, {}, {}, {}, {}) } }
        save("sessao_noite", androidx.compose.ui.test.hasText("Finalizar sessão"))
    }

    /** A few synthetic frames (gradients with a bright patch) so the gallery and viewer have content. */
    private fun seedPhotos() = runBlocking {
        val dao = app.database.photoDao()
        if (dao.inSession("Geral").isNotEmpty()) return@runBlocking
        // Warm/cool defocused-light scenes (bokeh), so the screens look like a real event gallery.
        val palettes = listOf(
            intArrayOf(0xFF1B1410.toInt(), 0xFFFFB45A.toInt()), intArrayOf(0xFF0E1622.toInt(), 0xFF7FC4FF.toInt()),
            intArrayOf(0xFF1E1016.toInt(), 0xFFFF7A8A.toInt()), intArrayOf(0xFF101A14.toInt(), 0xFFB8F29A.toInt()),
            intArrayOf(0xFF1A1420.toInt(), 0xFFE0A6FF.toInt()), intArrayOf(0xFF1C1810.toInt(), 0xFFFFE08A.toInt()),
        )
        palettes.forEachIndexed { i, (dark, light) ->
            val bmp = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(dark)
            val rnd = java.util.Random(i * 31L + 7)
            repeat(26) {
                val x = rnd.nextFloat() * 900f
                val y = rnd.nextFloat() * 600f
                val r = 30f + rnd.nextFloat() * 90f
                val alpha = 40 + rnd.nextInt(120)
                val c = (alpha shl 24) or (light and 0x00FFFFFF)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(x, y, r, intArrayOf(c, c, c and 0x00FFFFFF), floatArrayOf(0f, 0.8f, 1f), android.graphics.Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(x, y, r, paint)
            }
            val file = File(app.cacheDir, "sample_$i.jpg")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dao.insert(
                Photo(
                    session = "Geral", baseName = "IMG_10${40 + i}", savedBase = "IMG_10${40 + i}",
                    capturedAt = 1_700_000_000_000L + i * 60_000L, jpgUri = "file://${file.path}", rawUri = if (i % 2 == 0) "file://${file.path}" else null,
                    jpgSize = 4_200_000, rawSize = if (i % 2 == 0) 15_300_000 else 0,
                    aperture = "f/1.8", shutter = "1/200", iso = "ISO 400", favorite = i == 1, rating = i % 4,
                ),
            )
        }
    }

    @Test
    fun photos() {
        seedPhotos()
        rule.setContent { JrLinkTheme { PhotosScreen(app, onOpenPhoto = {}) } }
        rule.mainClock.advanceTimeBy(1_000)
        save("fotos", androidx.compose.ui.test.hasText("RAW+JPG"))
    }

    @Test
    fun viewer() {
        seedPhotos()
        val actions = PhotoActions(app, app.store, app.export)
        val id = runBlocking { app.database.photoDao().inSession("Geral").last().id }
        rule.setContent { JrLinkTheme { ViewerScreen(app, actions, id, onBack = {}) } }
        rule.mainClock.advanceTimeBy(1_000)
        save("visualizador")
    }

    @Test
    fun settings() {
        rule.setContent { JrLinkTheme { SettingsScreen(onOpen = {}) } }
        save("ajustes")
    }

    @Test
    fun settingsShare() {
        rule.setContent { JrLinkTheme { SettingsSectionScreen(app, SettingsSection.Share, onBack = {}) } }
        rule.mainClock.advanceTimeBy(2_000)
        val logo = androidx.compose.ui.test.hasContentDescription("Marca d'água")
        runCatching { rule.waitUntil(15_000) { rule.onAllNodes(logo).fetchSemanticsNodes().isNotEmpty() } }
        rule.onNode(androidx.compose.ui.test.hasText("Posição")).performScrollTo()
        save("ajustes_envio", logo)
    }
}
