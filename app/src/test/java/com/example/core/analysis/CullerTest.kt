package com.example.core.analysis

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.data.PhotoDatabase
import com.example.core.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CullerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Face detection stand-in: one face, eyes closed when the file name says "blink". */
    private class FakeFaces(val closedFor: (Int) -> Boolean) : FaceAnalyzer {
        var calls = 0
        override suspend fun detect(bitmap: Bitmap): List<FaceInfo> {
            calls++
            return listOf(FaceInfo(Region(bitmap.width / 4, bitmap.height / 4, bitmap.width * 3 / 4, bitmap.height * 3 / 4), closedFor(calls)))
        }
    }

    private fun jpeg(name: String, blur: Int, seed: Long = 7): String {
        val w = 600; val h = 400
        val rnd = Random(seed)
        val base = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val v = ((if ((x / 6 + y / 6) % 2 == 0) 50 else 200) + rnd.nextInt(24)).coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        var bmp = Bitmap.createBitmap(base, w, h, Bitmap.Config.ARGB_8888)
        if (blur > 0) {
            // Downscale and upscale: a cheap, strong blur.
            val small = Bitmap.createScaledBitmap(bmp, w / blur, h / blur, true)
            bmp = Bitmap.createScaledBitmap(small, w, h, true)
        }
        val file = File(context.cacheDir, "$name.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return "file://${file.path}"
    }

    @Test
    fun flagsBlurAndBlinkAndGroupsTheBurst() = runBlocking {
        val db = PhotoDatabase.create(context, "culler_test.db")
        val dao = db.photoDao()
        val settings = SettingsRepository(context)
        val faces = FakeFaces { call -> call == 2 }
        val culler = Culler(context, dao, settings, faces)

        val t = 1_700_000_000_000L
        val sharp = dao.insert(Photo(session = "Teste", baseName = "A", savedBase = "A", capturedAt = t, jpgUri = jpeg("a", 0)))
        val blink = dao.insert(Photo(session = "Teste", baseName = "B", savedBase = "B", capturedAt = t + 2_000, jpgUri = jpeg("b", 0)))
        val soft = dao.insert(Photo(session = "Teste", baseName = "C", savedBase = "C", capturedAt = t + 4_000, jpgUri = jpeg("c", 8)))
        listOf(sharp, blink, soft).forEach(culler::enqueue)
        withTimeout(60_000) { while (culler.pending.value > 0) delay(50) }

        val a = dao.get(sharp)!!
        val b = dao.get(blink)!!
        val c = dao.get(soft)!!
        assertFalse("sharp frame flagged: ${a.sharpness}", a.blurry)
        assertTrue("blurred frame not flagged: ${c.sharpness}", c.blurry)
        assertEquals(1, b.eyesClosed)
        assertTrue(a.score!! > b.score!!)
        // A and B are the same picture two seconds apart: one group, and the sharp open-eyes frame wins.
        assertEquals(a.burstGroup, b.burstGroup)
        assertTrue(a.suggested)
        assertFalse(b.suggested)
        db.close()
    }
}
