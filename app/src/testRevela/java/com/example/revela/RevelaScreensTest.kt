package com.example.revela

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.ui.theme.JrLinkTheme
import com.example.ui.theme.Tone
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders Revela's own screens to PNG (app/build/screens/revela) for review. Like the JR ScreenshotTest,
 * it fails only if a screen crashes while composing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RevelaScreensTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()
    private val studio = Settings(brandName = "Ana Lima Fotografia", brandSetupDone = true, sessionName = "Casamento Bia e Leo")

    private fun save(name: String) {
        runCatching { rule.waitUntil(8_000) { false } }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screens/revela").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun welcome() {
        rule.setContent { JrLinkTheme { BrandSetupScreen(app, Settings(), firstRun = true, onDone = {}, onBack = null) } }
        save("boas_vindas")
    }

    @Test
    fun captureNoCamera() {
        rule.setContent { JrLinkTheme { CaptureScreen(app, studio, {}, {}, {}, {}) } }
        save("captura_sem_camera")
    }

    @Test
    fun captureWithPhotos() {
        seedPhotos()
        rule.setContent { JrLinkTheme { CaptureScreen(app, studio, {}, {}, {}, {}) } }
        save("captura")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun captureLandscape() {
        seedPhotos()
        rule.setContent { JrLinkTheme { CaptureScreen(app, studio, {}, {}, {}, {}) } }
        save("captura_deitado")
    }

    @Test
    fun captureStudioColour() {
        seedPhotos()
        val pink = Color(0xFFF06292)
        rule.setContent { JrLinkTheme(accent = pink) { CaptureScreen(app, studio.copy(brandColor = pink.toArgb()), {}, {}, {}, {}) } }
        save("captura_cor_do_estudio")
    }

    @Test
    fun captureSafelight() {
        rule.setContent { JrLinkTheme(night = true) { CaptureScreen(app, studio, {}, {}, {}, {}) } }
        save("captura_luz_vermelha")
    }

    @Test
    fun menu() {
        val places = listOf(
            Place("capture", "Captura", "Câmera, disparo e última foto"),
            Place("photos", "Galeria", "6"),
            Place("cull", "Seleção", "Marcar e rejeitar, uma por uma"),
            Place("presets", "Presets", "Os seus looks de edição"),
            Place("delivery", "Entrega", "No ar"),
            Place("express", "Express", "Desligada"),
            Place("live", "Live view"),
            Place("card", "Cartão da câmera"),
            Place("client", "Modo cliente"),
        )
        val footer = listOf(Place("brand", "Sua marca"), Place("settings", "Ajustes"))
        rule.setContent {
            JrLinkTheme { Box(Modifier.fillMaxSize().background(Tone.Background)) { RevelaDrawer(studio, places, footer, "capture") {} } }
        }
        save("menu")
    }

    /** Bokeh frames, like the JR screenshot test, so the capture screen has something to show. */
    private fun seedPhotos() = runBlocking {
        val dao = app.database.photoDao()
        if (dao.inSession("Geral").isNotEmpty()) return@runBlocking
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
                val c = ((40 + rnd.nextInt(120)) shl 24) or (light and 0x00FFFFFF)
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
                    capturedAt = 1_700_000_000_000L + i * 60_000L, jpgUri = "file://${file.path}",
                    jpgSize = 4_200_000, aperture = "f/1.8", shutter = "1/200", iso = "ISO 400",
                ),
            )
        }
    }
}
