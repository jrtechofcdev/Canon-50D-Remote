package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.ui.cull.CullScreen
import com.example.ui.photos.ViewerScreen
import com.example.ui.theme.JrLinkTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The crash from the 3.3 log: photos arriving while a photo pager is on screen. The pager asked for
 * page 12 of a 12-item list (IndexOutOfBoundsException) and the app closed. Here photos keep arriving
 * with each pager open; the test fails if composing throws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewPhotoWhileViewingTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()

    private fun add(n: Int): Long = runBlocking {
        app.database.photoDao().insert(
            Photo(session = "Geral", baseName = "IMG_$n", savedBase = "Foto_$n", capturedAt = 1_700_000_000_000L + n * 1_000L, jpgUri = "file:///nada/$n.jpg"),
        )
    }

    private fun arriveWhileOpen() {
        repeat(6) { i ->
            add(100 + i)
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(200)
        }
        rule.waitForIdle()
    }

    @Test
    fun viewerSurvivesNewPhotos() {
        val first = (0 until 3).map { add(it) }.last()
        rule.setContent { JrLinkTheme { ViewerScreen(app, PhotoActions(app, app.store, app.export), first, onBack = {}, onCalibrate = {}) } }
        rule.waitForIdle()
        arriveWhileOpen()
    }

    @Test
    fun clientModeSurvivesNewPhotos() {
        (0 until 3).forEach { add(it) }
        rule.setContent { JrLinkTheme { com.example.ui.client.ClientModeScreen(app, onExit = {}, onCalibrate = {}) } }
        rule.waitForIdle()
        arriveWhileOpen()
    }

    @Test
    fun cullingSurvivesNewPhotos() {
        (0 until 3).forEach { add(it) }
        rule.setContent { JrLinkTheme { CullScreen(app, onBack = {}, onOpenPhoto = {}) } }
        rule.waitForIdle()
        arriveWhileOpen()
    }
}
