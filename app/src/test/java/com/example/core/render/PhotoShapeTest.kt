package com.example.core.render

import android.content.pm.ActivityInfo
import com.example.ui.components.ScreenTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoShapeTest {

    @Test
    fun cameraOrientationDecidesTheShape() {
        assertFalse(PhotoShape.shape(4752, 3168, 0, null))   // held level
        assertTrue(PhotoShape.shape(4752, 3168, 90, null))   // camera turned: EXIF says rotate 90°
        assertTrue(PhotoShape.shape(4752, 3168, 270, null))
    }

    @Test
    fun cropCanTurnOrReshape() {
        assertTrue(PhotoShape.shape(4752, 3168, 0, CropSpec(quarterTurns = 1)))
        // A tall 4:5 cut out of a horizontal frame is vertical.
        assertTrue(PhotoShape.shape(4752, 3168, 0, CropSpec(left = 0.3f, right = 0.7f)))
    }

    @Test
    fun turningFourTimesComesBack() {
        val start = CropSpec(left = 0.1f, top = 0.2f, right = 0.6f, bottom = 0.9f)
        var c: CropSpec = start
        repeat(4) { c = PhotoShape.turnedClockwise(c) }
        assertEquals(start.left, c.left, 1e-5f)
        assertEquals(start.top, c.top, 1e-5f)
        assertEquals(start.right, c.right, 1e-5f)
        assertEquals(start.bottom, c.bottom, 1e-5f)
        assertEquals(0, c.quarterTurns)
        val once = PhotoShape.turnedClockwise(null)
        assertEquals(1, once.quarterTurns)
        assertTrue(PhotoShape.shape(4752, 3168, 0, once))
    }

    @Test
    fun screenFollowsThePhotoOnTheChosenSide() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ScreenTurn.forPhoto(ScreenTurn.Left, portrait = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ScreenTurn.forPhoto(ScreenTurn.Portrait, portrait = false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, ScreenTurn.forPhoto(ScreenTurn.Right, portrait = false))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, ScreenTurn.forPhoto(ScreenTurn.UpsideDown, portrait = true))
        assertNull(ScreenTurn.forPhoto(ScreenTurn.Auto, portrait = true))
        assertEquals(ScreenTurn.Portrait, ScreenTurn.of("desconhecido"))
    }
}
