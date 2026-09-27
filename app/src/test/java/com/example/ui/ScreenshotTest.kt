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

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun session() {
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme { SessionScreen(app, actions, {}, {}, {}, {}) } }
        save("sessao")
    }

    @Test
    fun sessionNight() {
        val actions = PhotoActions(app, app.store, app.export)
        rule.setContent { JrLinkTheme(night = true) { SessionScreen(app, actions, {}, {}, {}, {}) } }
        save("sessao_noite")
    }

    /** A few synthetic frames (gradients with a bright patch) so the gallery and viewer have content. */
    private fun seedPhotos() = runBlocking {
        val dao = app.database.photoDao()
        if (dao.inSession("Geral").isNotEmpty()) return@runBlocking
        val colors = listOf(0xFF3A5A40.toInt(), 0xFF6B4E3D.toInt(), 0xFF2E4A62.toInt(), 0xFF7A6A52.toInt(), 0xFF4B3F5E.toInt(), 0xFF5E6B3A.toInt())
        colors.forEachIndexed { i, c ->
            val bmp = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(c)
            canvas.drawCircle(600f, 220f, 120f, Paint().apply { color = android.graphics.Color.WHITE })
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
        save("fotos")
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
        save("ajustes_envio")
    }
}
