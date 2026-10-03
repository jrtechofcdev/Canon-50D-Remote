package com.example.ui.intro

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.theme.JrLinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the opening frame by frame (app/build/intro) so it can be reviewed, or turned into a video,
 * without a phone. Fails only if a frame crashes while drawing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IntroFramesTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun frames() = render("build/intro")

    /** 1080 × 1920, the size of a story, for posting the opening. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun storyFrames() = render("build/intro_story")

    private fun render(path: String) {
        val step = System.getenv("INTRO_STEP")?.toIntOrNull() ?: 250
        val frame = mutableIntStateOf(0)
        rule.setContent { JrLinkTheme { BrandIntro(onDone = {}, frozenAt = frame.intValue) } }
        val dir = File(path).apply { deleteRecursively(); mkdirs() }
        var n = 0
        for (t in 0..2750 step step) {
            frame.intValue = t
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(dir, "frame_%03d.png".format(n++)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
