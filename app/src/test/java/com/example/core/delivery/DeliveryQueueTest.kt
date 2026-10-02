package com.example.core.delivery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.data.PhotoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeliveryQueueTest {

    private lateinit var db: PhotoDatabase
    private lateinit var root: File
    private var fail = false
    private val prepared = mutableListOf<Long>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        root = File(context.cacheDir, "queue_test").apply { deleteRecursively() }
    }

    @After
    fun tearDown() = db.close()

    private fun queue() = DeliveryQueue(
        db.deliveryDao(), db.photoDao(),
        DeliveryPreparer { photo, _, target ->
            prepared += photo.id
            if (fail) false else { target.writeText("jpg ${photo.id}"); true }
        },
        root, DeliveryEvents(),
    )

    private fun photo(n: Int) = runBlocking { db.photoDao().insert(Photo(session = "S", baseName = "IMG_$n", savedBase = "IMG_$n", capturedAt = n.toLong())) }

    private fun client(name: String) = runBlocking {
        db.deliveryDao().insertSession(DeliverySession(client = name, code = "12345$name".take(6), token = name.padEnd(32, '0'), createdAt = 1))
    }

    @Test
    fun aPhotoGoesToAClientOnceAndIsPreparedInOrder() = runBlocking {
        val q = queue()
        val ana = client("ana")
        val p1 = photo(1)
        val p2 = photo(2)
        assertEquals(2, q.enqueue(ana, listOf(p2, p1, p1)))
        assertEquals(0, q.enqueue(ana, listOf(p1)))
        assertTrue(q.processNext())
        assertTrue(q.processNext())
        assertFalse(q.processNext())
        assertEquals(listOf(p1, p2), prepared)
        val items = db.deliveryDao().items(ana)
        assertTrue(items.all { it.state == DeliveryState.READY && File(it.path!!).exists() })
        assertEquals("ana_IMG_1.jpg", items.first { it.photoId == p1 }.name)
        // The same photo can still go to another client.
        assertEquals(1, q.enqueue(client("bia"), listOf(p1)))
    }

    @Test
    fun aFailedPhotoWaitsForRetryAndAnEditMakesItAgain() = runBlocking {
        val q = queue()
        val ana = client("ana")
        val p1 = photo(1)
        q.enqueue(ana, listOf(p1))
        fail = true
        q.processNext()
        val failed = db.deliveryDao().items(ana).single()
        assertEquals(DeliveryState.ERROR, failed.state)
        assertFalse(q.processNext())

        fail = false
        db.deliveryDao().retry(failed.id)
        q.processNext()
        assertEquals(DeliveryState.READY, db.deliveryDao().items(ana).single().state)

        q.refresh(p1)
        assertEquals(DeliveryState.WAITING, db.deliveryDao().items(ana).single().state)
        q.processNext()
        assertEquals(3, prepared.size)
    }

    @Test
    fun closedClientGetsNothing() = runBlocking {
        val q = queue()
        val ana = client("ana")
        db.deliveryDao().closeSession(ana, 2)
        assertEquals(0, q.enqueue(ana, listOf(photo(1))))
    }

    @Test
    fun version7DatabaseGainsTheDeliveryTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_7_8.db"
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); listOf("", "-wal", "-shm", "-journal").forEach { File(path + it).delete() } }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session` TEXT NOT NULL, `baseName` TEXT NOT NULL, `savedBase` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `jpgUri` TEXT, `rawUri` TEXT, `jpgSize` INTEGER NOT NULL, `rawSize` INTEGER NOT NULL, `previewPath` TEXT, `aperture` TEXT, `shutter` TEXT, `iso` TEXT, `favorite` INTEGER NOT NULL, `rating` INTEGER NOT NULL DEFAULT 0, `rawCameraName` TEXT, `jpgCameraName` TEXT, `presetId` INTEGER, `adjust` TEXT, `editedUri` TEXT, `clientPick` INTEGER NOT NULL DEFAULT 0, `note` TEXT, `audioNote` TEXT, `moment` TEXT, `lat` REAL, `lon` REAL, `sharpness` REAL, `blurry` INTEGER NOT NULL DEFAULT 0, `faces` INTEGER, `eyesClosed` INTEGER NOT NULL DEFAULT 0, `score` REAL, `burstGroup` INTEGER, `suggested` INTEGER NOT NULL DEFAULT 0, `rejected` INTEGER NOT NULL DEFAULT 0, `dHash` INTEGER, `crop` TEXT)")
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_session_baseName` ON `photos` (`session`, `baseName`)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `grp` TEXT NOT NULL, `settings` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `favorite` INTEGER NOT NULL DEFAULT 0, `lastUsed` INTEGER NOT NULL DEFAULT 0)")
            raw.execSQL("INSERT INTO photos (session, baseName, savedBase, capturedAt, jpgSize, rawSize, favorite) VALUES ('Evento', 'IMG_0100', 'IMG_0100', 1, 10, 0, 1)")
            raw.execSQL("INSERT INTO presets (name, grp, settings, source, createdAt, updatedAt, favorite) VALUES ('Golden', 'Pack', '', 'jr_link', 1, 1, 1)")
            raw.version = 7
        }
        val room = PhotoDatabase.create(context, name)
        runBlocking {
            val photo = room.photoDao().inSession("Evento").single()
            assertTrue(photo.favorite)
            assertTrue(room.presetDao().all().single().favorite)
            val sid = room.deliveryDao().insertSession(DeliverySession(client = "Ana", code = "123456", token = "f".repeat(32), createdAt = 1))
            assertTrue(room.deliveryDao().insertItem(DeliveryItem(sessionId = sid, photoId = photo.id, name = "a.jpg", queuedAt = 1)) > 0)
            assertEquals(-1L, room.deliveryDao().insertItem(DeliveryItem(sessionId = sid, photoId = photo.id, name = "a.jpg", queuedAt = 2)))
        }
        room.close()
    }
}
