package com.example.core.delivery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.data.PhotoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Fotografia Express: open gallery without a code, previews until released, and a clean start for the next client. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpressTest {

    private lateinit var db: PhotoDatabase
    private lateinit var server: LocalServer
    private lateinit var dir: File
    private var port = 0
    private var info = PortalInfo("Jr_Tech_OFC", true, express = true, expressPreview = true)
    private val clean = ByteArray(5_000) { 7 }
    private val marked = ByteArray(800) { 3 }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "express_test").apply { deleteRecursively(); mkdirs() }
        server = LocalServer(db.deliveryDao(), portal = { null }, thumb = { _, _ -> null }, info = { info })
        port = server.start(0)
    }

    @After
    fun tearDown() {
        server.stop()
        db.close()
    }

    private fun get(path: String): Pair<Int, ByteArray> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        val status = c.responseCode
        return status to ((if (status < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0))
    }

    /** An Express session with one prepared photo and its marked preview. */
    private fun express(withPreview: Boolean = true): Pair<DeliverySession, DeliveryItem> = runBlocking {
        val dao = db.deliveryDao()
        val s = DeliverySession(client = "Express", code = "4321", token = "e".repeat(32), createdAt = 1, express = true)
        val sid = dao.insertSession(s)
        val pid = db.photoDao().insert(Photo(session = "S", baseName = "IMG_1", savedBase = "IMG_1", capturedAt = 1))
        val iid = dao.insertItem(DeliveryItem(sessionId = sid, photoId = pid, name = "IMG_1.jpg", queuedAt = 1))
        val f = File(dir, "$iid.jpg").apply { writeBytes(clean) }
        if (withPreview) previewFileOf(f.path).writeBytes(marked)
        dao.setState(iid, DeliveryState.READY, f.path, f.length(), null, 2)
        s.copy(id = sid) to dao.item(iid)!!
    }

    @Test
    fun theGalleryOpensWithoutACodeOnlyWhileTheModuleIsOn() {
        val (s, _) = express()
        val (status, body) = get("/api/express")
        assertEquals(200, status)
        assertTrue(String(body).contains(s.token))
        info = info.copy(express = false)
        assertEquals(404, get("/api/express").first)
    }

    @Test
    fun beforeReleaseOnlyTheMarkedPreviewLeavesThePhone() {
        val (s, item) = express()
        val list = String(get("/api/s/${s.token}").second)
        assertTrue(list, list.contains("\"locked\":true") && list.contains("\"preview\":true"))
        assertEquals(403, get("/f/${s.token}/${item.id}?dl=1").first)
        assertArrayEquals(marked, get("/p/${s.token}/${item.id}").second)
        assertArrayEquals(marked, get("/t/${s.token}/${item.id}").second)
    }

    @Test
    fun withoutAPreviewYetNothingIsShown() {
        val (s, item) = express(withPreview = false)
        assertEquals(404, get("/p/${s.token}/${item.id}").first)
        assertEquals(404, get("/t/${s.token}/${item.id}").first)
    }

    @Test
    fun releasedOrPreviewOffDownloadsTheCleanPhoto() {
        val (s, item) = express()
        runBlocking { db.deliveryDao().setReleased(listOf(item.id), true) }
        assertArrayEquals(clean, get("/f/${s.token}/${item.id}?dl=1").second)
        assertArrayEquals(clean, get("/p/${s.token}/${item.id}").second)

        runBlocking { db.deliveryDao().setReleased(listOf(item.id), false) }
        assertEquals(403, get("/f/${s.token}/${item.id}").first)
        info = info.copy(expressPreview = false)
        assertEquals(200, get("/f/${s.token}/${item.id}").first)
    }

    @Test
    fun finishingClosesTheOldLink() {
        val (s, _) = express()
        runBlocking { db.deliveryDao().closeSession(s.id, 5) }
        assertEquals(404, get("/api/s/${s.token}").first)
        assertEquals(404, get("/api/express").first)
    }

    @Test
    fun version8DatabaseGainsTheExpressColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_8_9.db"
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); listOf("", "-wal", "-shm", "-journal").forEach { File(path + it).delete() } }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session` TEXT NOT NULL, `baseName` TEXT NOT NULL, `savedBase` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `jpgUri` TEXT, `rawUri` TEXT, `jpgSize` INTEGER NOT NULL, `rawSize` INTEGER NOT NULL, `previewPath` TEXT, `aperture` TEXT, `shutter` TEXT, `iso` TEXT, `favorite` INTEGER NOT NULL, `rating` INTEGER NOT NULL DEFAULT 0, `rawCameraName` TEXT, `jpgCameraName` TEXT, `presetId` INTEGER, `adjust` TEXT, `editedUri` TEXT, `clientPick` INTEGER NOT NULL DEFAULT 0, `note` TEXT, `audioNote` TEXT, `moment` TEXT, `lat` REAL, `lon` REAL, `sharpness` REAL, `blurry` INTEGER NOT NULL DEFAULT 0, `faces` INTEGER, `eyesClosed` INTEGER NOT NULL DEFAULT 0, `score` REAL, `burstGroup` INTEGER, `suggested` INTEGER NOT NULL DEFAULT 0, `rejected` INTEGER NOT NULL DEFAULT 0, `dHash` INTEGER, `crop` TEXT)")
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_session_baseName` ON `photos` (`session`, `baseName`)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `grp` TEXT NOT NULL, `settings` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `favorite` INTEGER NOT NULL DEFAULT 0, `lastUsed` INTEGER NOT NULL DEFAULT 0)")
            // The delivery tables exactly as 4.0 (version 8) made them.
            raw.execSQL("CREATE TABLE IF NOT EXISTS `delivery_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client` TEXT NOT NULL, `phone` TEXT, `code` TEXT NOT NULL, `token` TEXT NOT NULL, `event` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `closedAt` INTEGER, `lastSeenAt` INTEGER NOT NULL)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS `delivery_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `photoId` INTEGER NOT NULL, `name` TEXT NOT NULL, `state` TEXT NOT NULL, `path` TEXT, `size` INTEGER NOT NULL, `downloads` INTEGER NOT NULL, `error` TEXT, `queuedAt` INTEGER NOT NULL, `readyAt` INTEGER, `picked` INTEGER NOT NULL)")
            raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_delivery_items_sessionId_photoId` ON `delivery_items` (`sessionId`, `photoId`)")
            raw.execSQL("INSERT INTO photos (session, baseName, savedBase, capturedAt, jpgSize, rawSize, favorite) VALUES ('Evento', 'IMG_0100', 'IMG_0100', 1, 10, 0, 0)")
            raw.execSQL("INSERT INTO delivery_sessions (client, code, token, event, createdAt, expiresAt, lastSeenAt) VALUES ('Ana', '123456', '${"a".repeat(32)}', 0, 1, 0, 0)")
            raw.execSQL("INSERT INTO delivery_items (sessionId, photoId, name, state, size, downloads, queuedAt, picked) VALUES (1, 1, 'a.jpg', 'ready', 0, 2, 1, 0)")
            raw.version = 8
        }
        val room = PhotoDatabase.create(context, name)
        runBlocking {
            val session = room.deliveryDao().session(1)!!
            assertFalse(session.express)
            val item = room.deliveryDao().items(1).single()
            assertFalse(item.released)
            assertEquals(2, item.downloads)
            assertEquals(1, room.photoDao().inSession("Evento").size)
        }
        room.close()
    }
}
