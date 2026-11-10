package com.example.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoTrashTest {
    @Test
    fun deletedPhotosHideAtOnceAndAreErasedAfterTheWait() = runTest {
        val erased = mutableListOf<Long>()
        val trash = PhotoTrash(backgroundScope, undoMs = 1_000) { erased += it }
        trash.delete(listOf(1, 2))
        assertEquals(setOf(1L, 2L), trash.pending.value)
        advanceTimeBy(999); runCurrent()
        assertTrue("nothing erased before the wait ends", erased.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(1L, 2L), erased)
        assertTrue(trash.pending.value.isEmpty())
    }

    @Test
    fun undoBringsThePhotosBackAndNothingIsErased() = runTest {
        val erased = mutableListOf<Long>()
        val trash = PhotoTrash(backgroundScope, undoMs = 1_000) { erased += it }
        val batch = trash.delete(listOf(5))!!
        assertTrue(trash.undo(batch))
        assertTrue(trash.pending.value.isEmpty())
        advanceTimeBy(5_000); runCurrent()
        assertTrue(erased.isEmpty())
    }

    @Test
    fun undoIsTooLateOnceTheFilesAreGone() = runTest {
        val trash = PhotoTrash(backgroundScope, undoMs = 1_000) { }
        val batch = trash.delete(listOf(7))!!
        advanceTimeBy(1_001); runCurrent()
        assertFalse(trash.undo(batch))
    }

    @Test
    fun eachDeleteIsUndoneOnItsOwn() = runTest {
        val erased = mutableListOf<Long>()
        val trash = PhotoTrash(backgroundScope, undoMs = 1_000) { erased += it }
        val first = trash.delete(listOf(1))!!
        trash.delete(listOf(2))
        trash.undo(first)
        assertEquals(setOf(2L), trash.pending.value)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(listOf(2L), erased)
    }

    @Test
    fun aPhotoAlreadyWaitingIsNotDeletedTwice() = runTest {
        val erased = mutableListOf<Long>()
        val trash = PhotoTrash(backgroundScope, undoMs = 1_000) { erased += it }
        trash.delete(listOf(3))
        assertNull(trash.delete(listOf(3)))
        advanceTimeBy(1_001); runCurrent()
        assertEquals(listOf(3L), erased)
    }
}
