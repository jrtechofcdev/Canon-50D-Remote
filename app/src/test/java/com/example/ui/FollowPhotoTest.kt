package com.example.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.data.SettingKeys
import com.example.ui.components.ScreenOverride
import com.example.ui.photos.ViewerScreen
import com.example.ui.theme.JrLinkTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The viewer turns the screen with the photo: standing for a vertical frame, lying for a horizontal one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowPhotoTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()

    private fun photo(name: String, w: Int, h: Int): Long = runBlocking {
        val file = File(app.cacheDir, "$name.jpg")
        file.outputStream().use { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        app.database.photoDao().insert(Photo(session = "Geral", baseName = name, savedBase = name, capturedAt = System.nanoTime(), jpgUri = "file://${file.path}"))
    }

    private fun waitForOverride(expected: Int?) {
        runCatching { rule.waitUntil(5_000) { ScreenOverride.orientation.value == expected } }
    }

    @Test
    fun verticalPhotoStandsTheScreenUp() {
        runBlocking { app.settings.set(SettingKeys.screenOrientation, "landscape_left") }
        val id = photo("vertical", 400, 600)
        rule.setContent { JrLinkTheme { ViewerScreen(app, PhotoActions(app, app.store, app.export), id, onBack = {}, onCalibrate = {}) } }
        waitForOverride(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ScreenOverride.orientation.value)
    }

    @Test
    fun horizontalPhotoLaysItDownAndOffLeavesTheChoice() {
        runBlocking { app.settings.set(SettingKeys.screenOrientation, "portrait") }
        val id = photo("horizontal", 600, 400)
        rule.setContent { JrLinkTheme { ViewerScreen(app, PhotoActions(app, app.store, app.export), id, onBack = {}, onCalibrate = {}) } }
        waitForOverride(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ScreenOverride.orientation.value)

        runBlocking { app.settings.set(SettingKeys.rotateWithPhoto, false) }
        waitForOverride(null)
        assertNull(ScreenOverride.orientation.value)
    }
}
