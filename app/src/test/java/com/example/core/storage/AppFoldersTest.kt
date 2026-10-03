package com.example.core.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.notes.NotesExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFoldersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun freshDir(name: String) = File(context.cacheDir, name).apply { deleteRecursively(); mkdirs() }

    @Test
    fun createsTheWholeStructureOnceAndReusesIt() {
        val phoneRoot = freshDir("raiz")
        val (base, first) = AppFolders.create(DocumentFile.fromFile(phoneRoot))
        assertEquals(AppFolders.ROOT_NAME, base.name)
        val root = File(phoneRoot, AppFolders.ROOT_NAME)
        AppFolders.layout.forEach { f -> assertTrue(f.path.joinToString("/"), File(root, f.path.joinToString("/")).isDirectory) }
        assertTrue(first.created >= AppFolders.layout.size)
        assertEquals(AppFolders.layout.count { it.key != null }, first.linked)

        // Picking the "JR C50D Sync" folder itself (or running again) creates nothing new.
        val (_, again) = AppFolders.create(DocumentFile.fromFile(root))
        assertEquals(0, again.created)
        assertEquals(first.uris.size, again.uris.size)
    }

    @Test
    fun secondCardKeepsACheckedCopyPerSessionAndFormat() {
        val source = File(freshDir("origem"), "IMG_0001.JPG").apply { writeBytes(ByteArray(5_000) { it.toByte() }) }
        val card = freshDir("espelho")
        val mirror = MirrorCopy(context)
        mirror.copyInto(DocumentFile.fromFile(card), "file://${source.path}", listOf("Casamento Ana", "JPG"), "IMG_0001.JPG")
        val copy = File(card, "Casamento Ana/JPG/IMG_0001.JPG")
        assertTrue(copy.exists())
        assertEquals(source.length(), copy.length())
        // Same file again: kept, no duplicate.
        mirror.copyInto(DocumentFile.fromFile(card), "file://${source.path}", listOf("Casamento Ana", "JPG"), "IMG_0001.JPG")
        assertEquals(1, File(card, "Casamento Ana/JPG").listFiles()!!.size)
    }

    @Test
    fun notesGoToOneFolderPerPhoto() {
        val notes = freshDir("notas")
        val export = NotesExport(context)
        val a = Photo(id = 1, session = "S", baseName = "IMG_1", savedBase = "IMG_0001", capturedAt = 0)
        val b = Photo(id = 2, session = "S", baseName = "IMG_2", savedBase = "IMG_0002", capturedAt = 0)
        val audio = File(freshDir("gravacao"), "take.m4a").apply { writeBytes(ByteArray(300) { 7 }) }
        listOf(a, b).forEach { p ->
            export.writeTextInto(DocumentFile.fromFile(notes), p, "mãe da noiva")
            export.addAudioInto(DocumentFile.fromFile(notes), p, audio)
        }
        export.addAudioInto(DocumentFile.fromFile(notes), a, audio)
        assertEquals("mãe da noiva", File(notes, "IMG_0001/nota.txt").readText())
        assertTrue(File(notes, "IMG_0001/audio_1.m4a").exists())
        assertTrue(File(notes, "IMG_0001/audio_2.m4a").exists())
        assertTrue(File(notes, "IMG_0002/audio_1.m4a").exists())
        // Clearing the text removes nota.txt.
        export.writeTextInto(DocumentFile.fromFile(notes), a, "")
        assertFalse(File(notes, "IMG_0001/nota.txt").exists())
    }
}
