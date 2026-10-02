package com.example.core.delivery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.data.PhotoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalServerTest {

    private lateinit var db: PhotoDatabase
    private lateinit var server: LocalServer
    private lateinit var dir: File
    private var port = 0
    private val events = DeliveryEvents()
    private val picks = mutableListOf<Pair<Long, Boolean>>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "server_test").apply { deleteRecursively(); mkdirs() }
        server = LocalServer(
            dao = db.deliveryDao(),
            portal = { name -> if (name == "index.html") "<html>portal</html>".byteInputStream() else null },
            thumb = { null },
            info = { PortalInfo("Jr_Tech_OFC", true) },
            onPick = { item, on -> picks += item.photoId to on },
        )
        port = server.start(0)
    }

    @After
    fun tearDown() {
        server.stop()
        db.close()
    }

    private class Reply(val status: Int, val body: ByteArray, val headers: Map<String, List<String>>) {
        val text get() = String(body)
    }

    private fun call(path: String, method: String = "GET", range: String? = null): Reply {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        range?.let { c.setRequestProperty("Range", it) }
        val status = c.responseCode
        val body = (if (status < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Reply(status, body, c.headerFields.filterKeys { it != null })
    }

    /** Two clients, each with one ready photo. */
    private fun twoClients(): Triple<DeliverySession, DeliverySession, ByteArray> = runBlocking {
        val dao = db.deliveryDao()
        val photos = db.photoDao()
        val a = DeliverySession(client = "Ana", code = "111222", token = "a".repeat(32), createdAt = 1)
        val b = DeliverySession(client = "Bia", code = "333444", token = "b".repeat(32), createdAt = 1)
        val aId = dao.insertSession(a)
        val bId = dao.insertSession(b)
        val bytes = ByteArray(10_000) { (it % 251).toByte() }
        listOf(aId, bId).forEach { sid ->
            val pid = photos.insert(Photo(session = "S", baseName = "IMG_$sid", savedBase = "IMG_$sid", capturedAt = sid))
            val itemId = dao.insertItem(DeliveryItem(sessionId = sid, photoId = pid, name = "foto$sid.jpg", queuedAt = 1))
            val f = File(dir, "$itemId.jpg").apply { writeBytes(bytes) }
            dao.setState(itemId, DeliveryState.READY, f.path, f.length(), null, 2)
        }
        Triple(a.copy(id = aId), b.copy(id = bId), bytes)
    }

    @Test
    fun portalAndPingAnswer() {
        assertEquals("<html>portal</html>", call("/").text)
        assertEquals(200, call("/api/ping").status)
        assertEquals(404, call("/../../etc/passwd").status)
        assertEquals(404, call("/f/x/1").status)
    }

    @Test
    fun aClientSeesOnlyTheirOwnPhotos() {
        val (a, b, _) = twoClients()
        val listA = call("/api/s/${a.token}").text
        assertTrue(listA, listA.contains("\"client\":\"Ana\"") && listA.contains("foto${a.id}.jpg") && !listA.contains("foto${b.id}.jpg"))
        val itemOfB = runBlocking { db.deliveryDao().items(b.id).single() }
        assertEquals(404, call("/f/${a.token}/${itemOfB.id}").status)
        assertEquals(200, call("/f/${b.token}/${itemOfB.id}").status)
        assertEquals(404, call("/api/s/${"c".repeat(32)}").status)
    }

    @Test
    fun loginByCodeAndBlockAfterFiveWrongTries() {
        val (a, _, _) = twoClients()
        val ok = call("/api/login?code=111222")
        assertEquals(200, ok.status)
        assertTrue(ok.text.contains(a.token))
        repeat(5) { assertEquals(401, call("/api/login?code=999999").status) }
        assertEquals(429, call("/api/login?code=111222").status)
    }

    @Test
    fun closedSessionStopsWorking() {
        val (a, _, _) = twoClients()
        runBlocking { db.deliveryDao().closeSession(a.id, 5) }
        assertEquals(404, call("/api/s/${a.token}").status)
        assertEquals(401, call("/api/login?code=111222").status)
    }

    @Test
    fun rangeResumesADownloadAndTheWholeOneCounts() {
        val (a, _, bytes) = twoClients()
        val item = runBlocking { db.deliveryDao().items(a.id).single() }
        val part = call("/f/${a.token}/${item.id}?dl=1", range = "bytes=100-199")
        assertEquals(206, part.status)
        assertArrayEquals(bytes.copyOfRange(100, 200), part.body)
        assertEquals("bytes 100-199/10000", part.headers["Content-Range"]?.first())
        assertEquals(0, runBlocking { db.deliveryDao().item(item.id)!!.downloads })

        val rest = call("/f/${a.token}/${item.id}?dl=1", range = "bytes=200-")
        assertArrayEquals(bytes.copyOfRange(200, bytes.size), rest.body)
        // The server counts right after the last byte leaves, so give it a moment.
        var after = runBlocking { db.deliveryDao().item(item.id)!! }
        repeat(40) { if (after.downloads == 0) { Thread.sleep(50); after = runBlocking { db.deliveryDao().item(item.id)!! } } }
        assertEquals(1, after.downloads)
        assertEquals(DeliveryState.DOWNLOADED, after.state)

        assertEquals(416, call("/f/${a.token}/${item.id}", range = "bytes=20000-").status)
    }

    @Test
    fun heartsReachThePhoto() {
        val (a, _, _) = twoClients()
        val item = runBlocking { db.deliveryDao().items(a.id).single() }
        assertEquals(200, call("/api/s/${a.token}/pick/${item.id}?on=1", method = "POST").status)
        assertEquals(listOf(item.photoId to true), picks)
        assertTrue(runBlocking { db.deliveryDao().item(item.id)!!.picked })
    }
}
