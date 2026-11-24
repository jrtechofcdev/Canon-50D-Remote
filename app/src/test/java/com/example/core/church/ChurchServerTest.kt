package com.example.core.church

import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChurchServerTest {

    private lateinit var db: PhotoDatabase
    private lateinit var repo: ChurchRepository
    private lateinit var server: ChurchServer
    private var port = 0
    private val jpg = ByteArray(5_000) { (it % 199).toByte() }
    private val raw = ByteArray(9_000) { (it % 97).toByte() }
    private val marks = mutableListOf<Boolean>()
    private val thumbMarks = mutableListOf<Boolean>()
    private val readies = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        repo = ChurchRepository(db.churchDao())
        val backend = object : ChurchBackend {
            override val repo = this@ChurchServerTest.repo
            override fun asset(name: String) = if (name == "index.html") "<html>igreja</html>".byteInputStream() else null
            override suspend fun photo(id: Long) = db.photoDao().get(id)?.let { ChurchPhotoInfo(it.id, it.savedBase, true, it.rawUri != null) }
            override suspend fun file(id: Long, kind: ChurchFile): ServedFile? {
                val p = db.photoDao().get(id) ?: return null
                return when (kind) {
                    ChurchFile.RAW -> p.rawUri?.let { ServedFile("${p.savedBase}.CR2", raw.size.toLong()) { raw.inputStream() } }
                    else -> ServedFile("${p.savedBase}.JPG", jpg.size.toLong()) { jpg.inputStream() }
                }
            }
            override suspend fun thumb(id: Long, member: ChurchMember, marked: Boolean): ServedFile {
                thumbMarks += marked
                return ServedFile("t.jpg", 3) { "jpg".byteInputStream() }
            }
            override suspend fun preview(id: Long, member: ChurchMember, marked: Boolean, ready: Boolean): ServedFile {
                marks += marked
                readies += ready
                return ServedFile("p.jpg", 3) { "pre".byteInputStream() }
            }
            override suspend fun logo(event: ChurchEvent): ServedFile? = null
        }
        server = ChurchServer(backend)
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

    private fun eventually(check: () -> Boolean): Boolean {
        repeat(40) { if (check()) return true; Thread.sleep(50) }
        return check()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun event(): ChurchEvent = runBlocking { repo.newEvent("Culto da família", null) }

    private fun photo(n: Int, withRaw: Boolean = true): Long = runBlocking {
        db.photoDao().insert(Photo(session = "S", baseName = "IMG_$n", savedBase = "IMG_$n", capturedAt = n.toLong(), jpgUri = "file:///j$n", rawUri = if (withRaw) "file:///r$n" else null))
    }

    private fun join(e: ChurchEvent, name: String = "Ana", role: String = "Mídia"): String {
        val r = call("/api/c/join?via=qr&e=${e.token}&code=${e.code}&name=${enc(name)}&role=${enc(role)}", "POST")
        assertEquals(r.text, 200, r.status)
        return Regex("\"token\":\"([0-9a-f]{32})\"").find(r.text)!!.groupValues[1]
    }

    @Test
    fun theDoorTellsTheEventAndAsksForTheCode() {
        val e = event()
        val r = call("/api/c/hello?e=${e.token}")
        assertTrue(r.text, r.text.contains("\"name\":\"Culto da família\"") && r.text.contains("\"code\":true"))
        // The code never goes to the page.
        assertFalse(r.text.contains(e.code))
        val wrong = call("/api/c/join?via=qr&e=${e.token}&code=0000x&name=Ana&role=M%C3%ADdia", "POST")
        assertEquals(403, wrong.status)
    }

    @Test
    fun aPersonSeesThePhotosAndDownloadsTheThreeVersions() {
        val e = event()
        val p = photo(1)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        val state = call("/api/c/state?m=$m")
        assertTrue(state.text, state.text.contains("\"name\":\"IMG_1\"") && state.text.contains("\"raw\":true"))
        val original = call("/f/$m/$p?v=original&dl=1")
        assertEquals(200, original.status)
        assertArrayEquals(jpg, original.body)
        assertTrue(original.headers["Content-Disposition"]!!.first().contains("IMG_1.JPG"))
        val rawReply = call("/f/$m/$p?v=raw&dl=1")
        assertArrayEquals(raw, rawReply.body)
        assertEquals("image/x-canon-cr2", rawReply.headers["Content-Type"]!!.first())
        // Written right after the last byte left, so it may land a moment after the reply was read.
        assertTrue(eventually { runBlocking { db.churchDao().fullLog(e.id) }.any { it.who == "Ana · Mídia" && it.action == "Baixou" && it.detail == "IMG_1.CR2 (RAW)" } })
    }

    @Test
    fun aRawDownloadResumesFromWhereItStopped() {
        val e = event()
        val p = photo(1)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        val part = call("/f/$m/$p?v=raw&dl=1", range = "bytes=4000-")
        assertEquals(206, part.status)
        assertArrayEquals(raw.copyOfRange(4000, raw.size), part.body)
    }

    @Test
    fun withoutPermissionTheFileIsRefusedAndThePreviewCarriesTheName() {
        val e = event()
        val p = photo(1)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        val member = runBlocking { db.churchDao().memberByToken(m)!! }
        runBlocking {
            repo.setPerm(member.id, ChurchPerm.DOWNLOAD_ORIGINAL, false)
            repo.setPerm(member.id, ChurchPerm.DOWNLOAD_READY, false)
            repo.setPerm(member.id, ChurchPerm.DOWNLOAD_RAW, false)
        }
        assertEquals(403, call("/f/$m/$p?v=original&dl=1").status)
        assertEquals(200, call("/p/$m/$p").status)
        assertEquals(listOf(true), marks)
        // The grid too: a screenshot of the thumbnails also carries the name.
        assertEquals(200, call("/t/$m/$p").status)
        assertEquals(listOf(true), thumbMarks)
        val state = call("/api/c/state?m=$m").text
        assertTrue(state.contains("\"original\":false") && state.contains("\"raw\":false"))
        assertTrue(eventually { runBlocking { db.churchDao().fullLog(e.id) }.any { it.action == "Tentou baixar sem permissão" } })
    }

    @Test
    fun hiddenPhotosAndOtherPeoplesExtrasAreNotServed() {
        val e = event()
        val shared = photo(1); val hidden = photo(2); val extra = photo(3)
        runBlocking { repo.addPhoto(e.id, shared); repo.addPhoto(e.id, hidden) }
        val ana = join(e)
        val bia = join(e, name = "Bia")
        val anaId = runBlocking { db.churchDao().memberByToken(ana)!!.id }
        runBlocking {
            repo.setRule(anaId, hidden, PhotoRule.HIDDEN)
            repo.setRule(anaId, extra, PhotoRule.EXTRA)
        }
        assertEquals(404, call("/f/$ana/$hidden?v=original").status)
        assertEquals(200, call("/f/$ana/$extra?v=original").status)
        assertEquals(404, call("/f/$bia/$extra?v=original").status)
        assertEquals(200, call("/t/$bia/$hidden").status)
    }

    @Test
    fun kickedOrBlockedPeopleAreSentBackToTheDoor() {
        val e = event()
        val m = join(e)
        val id = runBlocking { db.churchDao().memberByToken(m)!!.id }
        runBlocking { repo.block(id) }
        val r = call("/api/c/state?m=$m")
        assertEquals(401, r.status)
        assertTrue(r.text.contains("bloqueado"))
        assertEquals(401, call("/api/c/state?m=${"0".repeat(32)}").status)
    }

    @Test
    fun nothingNewAnswersShortly() {
        val e = event()
        val m = join(e)
        val first = call("/api/c/state?m=$m").text
        val v = Regex("\"v\":(\\d+)").find(first)!!.groupValues[1]
        assertTrue(call("/api/c/state?m=$m&v=$v").text.contains("\"same\":true"))
        runBlocking { repo.addPhoto(e.id, photo(9)) }
        assertFalse(call("/api/c/state?m=$m&v=$v").text.contains("\"same\""))
    }

    @Test
    fun thePageCanShowTheProntaBeforeDownloading() {
        val e = event()
        val p = photo(1)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        assertEquals(200, call("/p/$m/$p").status)
        assertEquals(200, call("/p/$m/$p?v=pronta").status)
        assertEquals(listOf(false, true), readies)
        // Seen once in the log, though it was opened twice.
        val seen = runBlocking { db.churchDao().fullLog(e.id) }.filter { it.action == ChurchServer.VIEWED }
        assertEquals(1, seen.size)
        assertEquals("IMG_1", seen.single().detail)
    }

    @Test
    fun aRawIsMissingUntilTheCameraSendsIt() {
        val e = event()
        val p = photo(1, withRaw = false)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        assertEquals(404, call("/f/$m/$p?v=raw").status)
    }
}
