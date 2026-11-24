package com.example.ui.delivery

import androidx.test.core.app.ApplicationProvider
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.render.Enhance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Melhorar in batch (Entrega, Express, church panel) follows the gallery's rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnhanceBatchTest {
    private val app get() = ApplicationProvider.getApplicationContext<TetherApp>()

    @Test
    fun anyPhotoWithoutItTurnsItOnForAllThenAllWithItTurnOff() = runBlocking {
        val dao = app.database.photoDao()
        val a = dao.insert(Photo(session = "S", baseName = "A", savedBase = "A", capturedAt = 1))
        val b = dao.insert(Photo(session = "S", baseName = "B", savedBase = "B", capturedAt = 2, enhance = Enhance.STRONG))
        assertEquals("2 fotos melhoradas", toggleEnhance(app, listOf(a, b, a)))
        val level = app.settings.current().enhanceLevel
        assertEquals(listOf(level, level), listOf(dao.get(a)!!.enhance, dao.get(b)!!.enhance))
        assertEquals("Melhorar tirado de 2 fotos", toggleEnhance(app, listOf(a, b)))
        assertEquals(listOf(Enhance.OFF, Enhance.OFF), listOf(dao.get(a)!!.enhance, dao.get(b)!!.enhance))
    }
}
