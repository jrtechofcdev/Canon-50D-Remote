package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.ui.client.ClientModeScreen
import com.example.ui.client.HOLD_TO_EXIT_MS
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.JrLinkTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ClientExitAndDevTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()

    private fun clientMode(): IntArray {
        runBlocking {
            app.settings.set(SettingKeys.clientExitHold, true)
            app.settings.set(SettingKeys.clientPin, "")
        }
        val exits = intArrayOf(0)
        rule.setContent { JrLinkTheme { ClientModeScreen(app, onExit = { exits[0]++ }, onCalibrate = {}) } }
        // The screen draws once the settings and photos are loaded.
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("As fotos aparecem", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // The ring's animation would otherwise run to the end while the test waits for idle.
        rule.mainClock.autoAdvance = false
        return exits
    }

    @Test
    fun holdingOneFingerStillLeavesClientMode() {
        val exits = clientMode()
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_TO_EXIT_MS + 500)
        rule.waitForIdle()
        assertEquals(1, exits[0])
    }

    @Test
    fun liftingEarlyStays() {
        val exits = clientMode()
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_TO_EXIT_MS / 2)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(HOLD_TO_EXIT_MS)
        rule.waitForIdle()
        assertEquals(0, exits[0])
    }

    @Test
    fun sevenTapsOnTheBrandUnlockDeveloperMode() {
        runBlocking { app.settings.set(SettingKeys.developerMode, false) }
        rule.setContent { JrLinkTheme { SettingsScreen(onOpen = {}) } }
        val brand = rule.onNode(hasText("por Jr_Tech_OFC", substring = true), useUnmergedTree = true)
        repeat(7) { brand.performClick(); rule.waitForIdle() }
        runCatching { rule.waitUntil(5_000) { runBlocking { app.settings.current().developerMode } } }
        assertTrue(runBlocking { app.settings.current().developerMode })
    }
}
