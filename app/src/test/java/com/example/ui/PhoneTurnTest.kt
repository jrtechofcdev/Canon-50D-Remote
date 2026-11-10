package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.components.PhoneTurned
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The photo turns with the phone; the screen around it doesn't. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneTurnTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aQuarterTurnGivesThePhotoTheScreenSidewaysAndBack() {
        val turn = mutableIntStateOf(0)
        var inside = IntSize.Zero
        var outside = IntSize.Zero
        var px = 0
        rule.setContent {
            px = with(LocalDensity.current) { 100.dp.roundToPx() }
            Box(Modifier.size(width = 100.dp, height = 200.dp).onSizeChanged { outside = it }) {
                PhoneTurned(turn.intValue) {
                    Box(Modifier.fillMaxSize().onSizeChanged { inside = it })
                }
            }
        }
        rule.waitForIdle()
        assertEquals(IntSize(px, 2 * px), inside)

        // Phone held sideways: the photo gets the screen's height as its width, the screen keeps its size.
        turn.intValue = 90
        rule.waitForIdle()
        assertEquals(IntSize(2 * px, px), inside)
        assertEquals(IntSize(px, 2 * px), outside)

        turn.intValue = 180
        rule.waitForIdle()
        assertEquals(IntSize(px, 2 * px), inside)
    }
}
