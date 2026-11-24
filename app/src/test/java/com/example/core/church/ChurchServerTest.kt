package com.example.core.church

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.Photo
import com.example.core.data.PhotoDatabase
import kotlinx.coroutines.flow.first
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
    private val previewPresets = mutableListOf<Long?>()
    private val fileLooks = mutableListOf<ReadyLook?>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        repo = ChurchRepository(db.churchDao())
        val backend = object : ChurchBackend {
            override val repo = this@ChurchServerTest.repo
            override fun asset(name: String) = if (name == "index.html") "<html>igreja</html>".byteInputStream() else null
            override suspend fun photo(id: Long) = db.photoDao().get(id)?.let { ChurchPhotoInfo(it.id, it.savedBase, true, it.rawUri != null) }
            override suspend fun file(id: Long, kind: ChurchFile, look: ReadyLook?): ServedFile? {
                fileLooks += look
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
            override suspend fun params(presetId: Long) = if (presetId == 3L) null else com.example.core.render.RenderParamsJson.encode(com.example.core.render.RenderParams.IDENTITY)
            override suspend fun base(id: Long, member: ChurchMember, marked: Boolean) = ServedFile("b.jpg", 4) { "base".byteInputStream() }
            override suspend fun preview(id: Long, member: ChurchMember, marked: Boolean, ready: Boolean, look: ReadyLook?): ServedFile {
                previewPresets += look?.presetId
                marks += marked
                readies += ready
                return ServedFile("p.jpg", 3) { "pre".byteInputStream() }
            }
            override suspend fun logo(event: ChurchEvent): ServedFile? = null
            override suspend fun presets() = listOf(ChurchPresetInfo(1, "Quente", "Meus"), ChurchPresetInfo(2, "P&B", "Meus"), ChurchPresetInfo(3, "Frio", "Loja"))
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
    fun thePageKnowsThePersonsRank() {
        val e = event()
        val m = join(e)
        assertTrue(call("/api/c/state?m=$m").text.contains("\"rank\":0"))
        runBlocking { repo.setRank(db.churchDao().memberByToken(m)!!.id, ChurchRank.ADMIN) }
        val state = call("/api/c/state?m=$m").text
        assertTrue(state, state.contains("\"rank\":1") && state.contains("\"rankLabel\":\"ADM\""))
    }

    @Test
    fun onlyPeopleWithPresetsSeeTheReleasedOnes() {
        val e = event()
        val m = join(e)
        assertEquals(403, call("/api/c/presets?m=$m").status)
        val id = runBlocking { db.churchDao().memberByToken(m)!!.id }
        runBlocking { repo.setPerm(id, ChurchPerm.PRESETS, true) }
        assertTrue(call("/api/c/presets?m=$m").text.contains("\"name\":\"Frio\""))
        runBlocking { repo.setReleasedPresets(e.id, setOf(1, 2)) }
        val list = call("/api/c/presets?m=$m").text
        assertTrue(list, list.contains("Quente") && list.contains("P&B") && !list.contains("Frio"))
        assertTrue(call("/api/c/state?m=$m").text.contains("\"presets\":true"))
    }

    @Test
    fun theLivePreviewGetsTheShaderTheNumbersAndTheBaseOnlyForReleasedPresets() {
        val e = event()
        val p = photo(1)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        // Without the Presets switch nothing of it is served.
        assertEquals(403, call("/api/c/shader?m=$m").status)
        assertEquals(403, call("/api/c/params?m=$m&preset=1").status)
        assertEquals(403, call("/b/$m/$p").status)
        assertEquals(403, call("/p/$m/$p?preset=1").status)
        runBlocking {
            repo.setPerm(db.churchDao().memberByToken(m)!!.id, ChurchPerm.PRESETS, true)
            repo.setReleasedPresets(e.id, setOf(1, 3))
        }
        val shader = call("/api/c/shader?m=$m").text
        assertTrue(shader.contains("#version 300 es") && shader.contains("\"fragment\""))
        val params = call("/api/c/params?m=$m&preset=1")
        assertEquals(200, params.status)
        assertTrue(params.text.contains("\"uExposure\":") && params.text.contains("\"lut\":[0,0,0,1,1,1"))
        assertEquals(403, call("/api/c/params?m=$m&preset=2").status)
        assertEquals(404, call("/api/c/params?m=$m&preset=3").status)
        assertEquals("base", call("/b/$m/$p").text)
        assertEquals(200, call("/p/$m/$p?preset=1").status)
        assertEquals(403, call("/p/$m/$p?preset=2").status)
        assertEquals(listOf<Long?>(1L), previewPresets)
    }

    @Test
    fun theProntaGoesWithThePickedPresetWhenReleasedAndTheLogSaysWhich() {
        val e = event()
        val p = photo(1)
        runBlocking { repo.addPhoto(e.id, p) }
        val m = join(e)
        val id = runBlocking { db.churchDao().memberByToken(m)!!.id }
        // Without the Presets switch a picked preset is refused, not quietly ignored.
        assertEquals(403, call("/f/$m/$p?v=pronta&preset=1&dl=1").status)
        runBlocking { repo.setPerm(id, ChurchPerm.PRESETS, true); repo.setReleasedPresets(e.id, setOf(1)) }
        assertEquals(200, call("/f/$m/$p?v=pronta&preset=1&dl=1").status)
        assertEquals(403, call("/f/$m/$p?v=pronta&preset=2&dl=1").status)
        assertEquals(200, call("/f/$m/$p?v=pronta&dl=1").status)
        // The original never takes a look.
        assertEquals(200, call("/f/$m/$p?v=original&preset=1&dl=1").status)
        assertEquals(listOf(ReadyLook(1, null), null, null), fileLooks)
        assertTrue(eventually { runBlocking { db.churchDao().fullLog(e.id) }.any { it.detail == "IMG_1.JPG (pronta, Quente)" } })
    }

    @Test
    fun onlyTheMainAdminChangesTheEventsLookAndEveryPageSeesWho() {
        val e = event()
        val ana = join(e)
        val bia = join(e, name = "Bia")
        val anaId = runBlocking { db.churchDao().memberByToken(ana)!!.id }
        assertEquals(403, call("/api/c/look?m=$ana&preset=1", "POST").status)
        runBlocking { repo.setRank(anaId, ChurchRank.MAIN) }
        assertEquals(200, call("/api/c/look?m=$ana&preset=1", "POST").status)
        val state = call("/api/c/state?m=$bia").text
        assertTrue(state, state.contains("\"look\":{\"preset\":1,\"name\":\"Quente\",\"by\":\"Ana · Mídia\""))
        assertEquals(200, call("/api/c/look?m=$ana&preset=none", "POST").status)
        assertTrue(call("/api/c/state?m=$bia").text.contains("\"name\":\"o da foto\""))
        val history = runBlocking { db.churchDao().observeLooks(e.id).first() }
        assertEquals(listOf<Long?>(null, 1L), history.map { it.presetId })
        runBlocking { repo.setReleasedPresets(e.id, setOf(2)) }
        assertEquals(403, call("/api/c/look?m=$ana&preset=1", "POST").status)
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
