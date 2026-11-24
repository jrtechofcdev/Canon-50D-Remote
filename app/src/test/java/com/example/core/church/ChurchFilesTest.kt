package com.example.core.church

import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The copies prepared for the media live in one folder per event and leave with it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChurchFilesTest {
    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()
    private val root get() = File(app.cacheDir, "church")

    private fun fake(path: String, bytes: Int) = File(root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test
    fun anEventsCopiesAreMeasuredAndCleared() = runBlocking {
        val a = app.church.repo.newEvent("Culto", null)
        val b = app.church.repo.newEvent("Cruzada", null)
        fake("${a.id}/1_thumb.jpg", 3_000)
        fake("${a.id}/1_abc.jpg", 7_000)
        val other = fake("${b.id}/2_view.jpg", 5_000)
        assertEquals(10_000L, app.church.filesSize(a.id))
        app.church.clearFiles(a.id)
        assertEquals(0L, app.church.filesSize(a.id))
        assertTrue("another event keeps its copies", other.exists())
    }

    @Test
    fun leftoversOfDeletedEventsAndOfVersion54Go() = runBlocking {
        val kept = app.church.repo.newEvent("Festa", null)
        val keep = fake("${kept.id}/1_thumb.jpg", 100)
        val loose = fake("7_12_ab12.jpg", 100)
        val gone = fake("99999/1_thumb.jpg", 100)
        app.church.clearLeftovers()
        assertTrue(keep.exists())
        assertFalse(loose.exists())
        assertFalse(gone.exists())
    }
}
