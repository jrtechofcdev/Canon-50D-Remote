package com.example.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.data.PhotoDatabase
import com.example.core.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aFolderThatStoppedWorkingSendsPhotosToTheAppFolderAndSaysSo() {
        val store = PhotoStore(context)
        // A folder whose permission is gone (deleted, card removed): it can't be opened any more.
        val gone = Settings(jpgFolder = "content://com.android.externalstorage.documents/tree/primary%3ASumiu")
        val target = store.create(FileKind.JPG, "IMG_0001.JPG", gone)
        target.stream.use { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals("file", target.uri.scheme)
        assertTrue(FileKind.JPG in store.lostFolders.value)

        // Picking the folder again (or using the app folder on purpose) clears the warning.
        store.create(FileKind.JPG, "IMG_0002.JPG", Settings()).stream.close()
        assertTrue(FileKind.JPG in store.lostFolders.value)
        runCatching { store.takePersistablePermission(android.net.Uri.parse(gone.jpgFolder)) }
        assertEquals(emptySet<FileKind>(), store.lostFolders.value)
    }

    @Test
    fun theSameCameraNameInAnotherSessionIsANewPhoto() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.photoDao()
        dao.insert(Photo(session = "Casamento", baseName = "IMG_0001", savedBase = "IMG_0001", capturedAt = 1, jpgCameraName = "IMG_0001.JPG"))
        assertEquals(1, dao.countCameraName("Casamento", "IMG_0001.JPG"))
        // After 9999 the camera starts at 0001 again: in a new session that name is a new frame, not a repeat.
        assertEquals(0, dao.countCameraName("Formatura", "IMG_0001.JPG"))
        assertEquals(listOf("IMG_0001.JPG"), dao.cameraNamesIn("Casamento"))
        db.close()
    }
}
