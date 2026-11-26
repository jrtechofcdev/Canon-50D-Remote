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
            override suspend fun store() = listOf(ChurchStoreItem(70, "Dourado", "Retrato", false), ChurchStoreItem(71, "Neblina", "Externo", true))
            override suspend fun storeInstall(id: Long): Result<ChurchPresetInfo> =
                if (id == 70L) Result.success(ChurchPresetInfo(9, "Dourado", "Meus presets")) else Result.failure(IllegalStateException("Precisa de internet no celular do fotógrafo para baixar este preset"))
            override fun editorSpec() = """{"tabs":[]}"""
            override suspend fun lookText(look: ReadyLook) = look.adjust ?: look.presetId?.let { "Exposure2012=+0.${it}0\n" } ?: ""
            override suspend fun presets() = listOf(ChurchPresetInfo(1, "Quente", "Meus"), ChurchPresetInfo(2, "P&B", "Meus"), ChurchPresetInfo(3, "Frio", "Loja"))
            override suspend fun igPost(event: ChurchEvent) = db.instaDao().picks(event.id).takeIf { it.isNotEmpty() }
                ?.let { ps -> ChurchIgPost(event.igCaption.orEmpty(), ps.map { ChurchIgPhoto(it.photoId, "1:1", it.storyCrop != null) }) }
            override suspend fun igFile(event: ChurchEvent, photoId: Long, story: Boolean, position: Int) =
                ServedFile("%02d_%s_IMG.jpg".format(position, if (story) "stories" else "feed"), 3) { "ig!".byteInputStream() }
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

    private fun call(path: String, method: String = "GET", range: String? = null, body: String? = null): Reply {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        range?.let { c.setRequestProperty("Range", it) }
        body?.let { text ->
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(text.toByteArray()) }
        }
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
    fun anAdminKeepsTheirOwnLookGoesIndependentAndReverts() {
        val e = event()
        val m = join(e)
        val id = runBlocking { db.churchDao().memberByToken(m)!!.id }
        assertEquals(403, call("/api/c/mylook?m=$m&preset=1", "POST").status)
        runBlocking { repo.setRank(id, ChurchRank.ADMIN) }
        assertEquals(200, call("/api/c/mylook?m=$m&preset=1", "POST").status)
        // Nothing before the first own look.
        assertEquals(409, call("/api/c/revert?m=$m", "POST").status)
        assertEquals(200, call("/api/c/mylook?m=$m&preset=2", "POST").status)
        var state = call("/api/c/state?m=$m").text
        assertTrue(state, state.contains("\"own\":{\"preset\":2,\"name\":\"P&B\"}") && state.contains("\"canRevert\":true") && state.contains("\"independent\":false"))
        // Following the event, the "pronta" is the event's; independent, it is their own.
        call("/f/$m/${photo(1).also { p -> runBlocking { repo.addPhoto(e.id, p) } }}?v=pronta&dl=1")
        assertEquals(200, call("/api/c/independent?m=$m&on=1", "POST").status)
        val p2 = photo(2).also { p -> runBlocking { repo.addPhoto(e.id, p) } }
        call("/f/$m/$p2?v=pronta&dl=1")
        assertEquals(listOf(null, ReadyLook(2, null)), fileLooks)
        assertEquals(200, call("/api/c/revert?m=$m", "POST").status)
        state = call("/api/c/state?m=$m").text
        assertTrue(state, state.contains("\"own\":{\"preset\":1"))
    }

    @Test
    fun adminsShareLooksWithEachOtherAndThePhotographer() {
        val e = event()
        val ana = join(e)
        val bia = join(e, name = "Bia")
        val caio = join(e, name = "Caio")
        val (anaId, biaId) = runBlocking { db.churchDao().memberByToken(ana)!!.id to db.churchDao().memberByToken(bia)!!.id }
        runBlocking { repo.setRank(anaId, ChurchRank.ADMIN); repo.setRank(biaId, ChurchRank.MAIN) }
        // A viewer can't share; nor can one send to a viewer.
        assertEquals(403, call("/api/c/share?m=$caio&preset=1&to=fotografo", "POST").status)
        val caioId = runBlocking { db.churchDao().memberByToken(caio)!!.id }
        assertEquals(400, call("/api/c/share?m=$ana&preset=1&to=$caioId", "POST").status)
        assertEquals(200, call("/api/c/share?m=$ana&preset=1&to=$biaId", "POST").status)
        assertEquals(200, call("/api/c/share?m=$ana&preset=2&to=fotografo", "POST").status)
        // Ana sees Bia among the admins; Bia sees the look waiting.
        assertTrue(call("/api/c/state?m=$ana").text.contains("\"admins\":[{\"id\":$biaId,\"label\":\"Bia · Mídia\"}]"))
        val state = call("/api/c/state?m=$bia").text
        val shareId = Regex("\"shares\":\\[\\{\"id\":(\\d+),\"from\":\"Ana · Mídia\",\"name\":\"Quente\"").find(state)!!.groupValues[1]
        // The main admin applying it: it becomes the event's look.
        assertEquals(404, call("/api/c/share/accept?m=$ana&id=$shareId", "POST").status)
        assertEquals(200, call("/api/c/share/accept?m=$bia&id=$shareId", "POST").status)
        assertEquals(1L, runBlocking { db.churchDao().openEvent()!!.presetId })
        assertTrue(call("/api/c/state?m=$bia").text.contains("\"shares\":[]"))
        val toPhotographer = runBlocking { db.churchDao().observeSharesToPhotographer(e.id).first() }
        assertEquals(listOf(2L), toPhotographer.map { it.presetId })
    }

    @Test
    fun theShopIsOnlyForWhoHasTheSwitchAndInstallsIntoTheApp() {
        val e = event()
        val m = join(e)
        assertEquals(403, call("/api/c/store?m=$m").status)
        runBlocking {
            repo.setPerm(db.churchDao().memberByToken(m)!!.id, ChurchPerm.STORE, true)
            repo.setReleasedPresets(e.id, setOf(1))
        }
        assertTrue(call("/api/c/store?m=$m").text.contains("{\"id\":70,\"name\":\"Dourado\",\"category\":\"Retrato\",\"installed\":false}"))
        val ok = call("/api/c/store/install?m=$m&id=70", "POST")
        assertEquals(200, ok.status)
        // The event released only some presets: the new one joins them.
        assertEquals(setOf(1L, 9L), runBlocking { ChurchRules.released(db.churchDao().openEvent()!!) })
        val failed = call("/api/c/store/install?m=$m&id=71", "POST")
        assertEquals(409, failed.status)
        assertTrue(failed.text.contains("internet"))
        assertTrue(runBlocking { db.churchDao().fullLog(e.id) }.any { it.action == "Instalou da Loja" && it.detail == "Dourado" })
    }

    @Test
    fun theEditorIsForAdminsAndThePhoneTurnsTheirLookIntoTheShadersNumbers() {
        val e = event()
        val m = join(e)
        val id = runBlocking { db.churchDao().memberByToken(m)!!.id }
        val look = "Exposure2012=0.5\nContrast2012=30\nBogus=7\ncurve:ToneCurvePV2012=0,10;128,140;255,250\n"
        assertEquals(403, call("/api/c/editor?m=$m").status)
        assertEquals(403, call("/api/c/params?m=$m", "POST", body = look).status)
        runBlocking { repo.setRank(id, ChurchRank.ADMIN) }
        assertEquals("{\"tabs\":[]}", call("/api/c/editor?m=$m").text)
        val params = call("/api/c/params?m=$m", "POST", body = look)
        assertEquals(200, params.status)
        assertEquals(
            com.example.core.render.RenderParamsJson.encode(com.example.core.render.RenderParams.from(ChurchRules.cleanLook(look)!!)),
            params.text,
        )
        // Where an edit starts.
        runBlocking { repo.setOwnLook(id, null, "Exposure2012=+1.00\n", "edição") }
        assertEquals("Exposure2012=+1.00\n", call("/api/c/settings?m=$m&from=own").text)
        assertEquals("Exposure2012=+0.10\n", call("/api/c/settings?m=$m&from=preset&id=1").text)
        // The photographer can still turn the editor off for an admin.
        runBlocking { repo.setPerm(id, ChurchPerm.EDITOR, false) }
        assertEquals(403, call("/api/c/editor?m=$m").status)
        assertTrue(call("/api/c/state?m=$m").text.contains("\"editor\":false"))
    }

    @Test
    fun anEditFromThePageIsSavedAsTheAdminsLookTheEventsLookOrSentToThePhotographer() {
        val e = event()
        val ana = join(e)
        val bia = join(e, name = "Bia")
        val (anaId, biaId) = runBlocking { db.churchDao().memberByToken(ana)!!.id to db.churchDao().memberByToken(bia)!!.id }
        val edit = "Exposure2012=0.4\nSaturation=-20\nBogus=1\n"
        runBlocking { repo.setRank(anaId, ChurchRank.ADMIN); repo.setRank(biaId, ChurchRank.MAIN) }
        // An admin keeps the edit as their own look, and their independent "pronta" goes with it.
        assertEquals(200, call("/api/c/mylook?m=$ana", "POST", body = edit).status)
        val clean = ChurchRules.cleanLook(edit)!!.encode()
        assertEquals(clean, runBlocking { db.churchDao().member(anaId)!!.lookAdjust })
        assertTrue(call("/api/c/state?m=$ana").text.contains("\"own\":{\"preset\":null,\"name\":\"minha edição\"}"))
        call("/api/c/independent?m=$ana&on=1", "POST")
        val p = photo(1).also { runBlocking { repo.addPhoto(e.id, it) } }
        call("/f/$ana/$p?v=pronta&dl=1")
        assertEquals(listOf(ReadyLook(null, clean)), fileLooks)
        // The main admin's edit becomes the event's look, with the notice.
        assertEquals(200, call("/api/c/look?m=$bia", "POST", body = edit).status)
        assertEquals(clean, runBlocking { db.churchDao().openEvent()!!.adjust })
        assertTrue(call("/api/c/state?m=$ana").text.contains("\"name\":\"edição\",\"by\":\"Bia · Mídia\""))
        // Sent to the photographer, it arrives with its settings.
        assertEquals(200, call("/api/c/share?m=$ana&to=fotografo", "POST", body = edit).status)
        assertEquals(clean, runBlocking { db.churchDao().observeSharesToPhotographer(e.id).first().single().adjust })
        // Without the Editor switch an edit is refused, not quietly turned into nothing.
        runBlocking { repo.setPerm(anaId, ChurchPerm.EDITOR, false) }
        assertEquals(403, call("/api/c/mylook?m=$ana", "POST", body = edit).status)
        assertEquals(403, call("/api/c/share?m=$ana&to=fotografo", "POST", body = edit).status)
    }

    @Test
    fun theInstagramPostShowsOnlyOnceSentAndOnlyWhatThePersonMaySee() {
        val e = event()
        val ana = join(e)
        val anaId = runBlocking { db.churchDao().memberByToken(ana)!!.id }
        val a = photo(1).also { runBlocking { repo.addPhoto(e.id, it) } }
        val b = photo(2).also { runBlocking { repo.addPhoto(e.id, it) } }
        val c = photo(3).also { runBlocking { repo.addPhoto(e.id, it) } }
        runBlocking {
            repo.setRule(anaId, c, PhotoRule.HIDDEN)
            db.instaDao().replacePicks(e.id, listOf(
                com.example.core.insta.ChurchIgPick(e.id, b, 0, 90f, "WORSHIP", "Mãos erguidas", "v1", "v1"),
                com.example.core.insta.ChurchIgPick(e.id, a, 1, 80f, "PEOPLE", "Sorrisos", "v1", null),
                com.example.core.insta.ChurchIgPick(e.id, c, 2, 70f, "DETAIL", "", "v1", null),
            ))
        }
        // Chosen but not sent: nothing on the page, nothing to download.
        assertFalse(call("/api/c/state?m=$ana").text.contains("\"ig\""))
        assertEquals(404, call("/f/$ana/$b?v=ig&k=feed&dl=1").status)
        runBlocking { db.instaDao().setPost(e.id, "Culto abençoado", null, 1000); repo.changed() }
        val state = call("/api/c/state?m=$ana").text
        assertTrue(state, state.contains("\"ig\":{\"caption\":\"Culto abençoado\",\"download\":true,\"at\":1000,\"photos\":[{\"id\":$b,\"shape\":\"1:1\",\"story\":true},{\"id\":$a,\"shape\":\"1:1\",\"story\":false}]}"))
        // The feed file comes numbered in carousel order and the download is in the log.
        val feed = call("/f/$ana/$b?v=ig&k=feed&dl=1")
        assertEquals(200, feed.status)
        assertEquals("ig!", feed.text)
        assertTrue(feed.headers["Content-Disposition"].toString().contains("01_feed_IMG.jpg"))
        assertEquals(200, call("/f/$ana/$b?v=ig&k=story&dl=1").status)
        assertTrue(eventually { runBlocking { repo.dao.fullLog(e.id) }.any { it.action == "Baixou para o Instagram" } })
        // No Stories version, a photo hidden from her, a photo not in the post.
        assertEquals(404, call("/f/$ana/$a?v=ig&k=story&dl=1").status)
        assertEquals(404, call("/f/$ana/$c?v=ig&k=feed&dl=1").status)
        // Without the "pronta" she sees the post but cannot download it.
        runBlocking { repo.setPerm(anaId, ChurchPerm.DOWNLOAD_READY, false) }
        assertTrue(call("/api/c/state?m=$ana").text.contains("\"download\":false"))
        assertEquals(403, call("/f/$ana/$b?v=ig&k=feed&dl=1").status)
        // Taken off the page.
        runBlocking { db.instaDao().setPost(e.id, "Culto abençoado", null, null); repo.changed() }
        assertFalse(call("/api/c/state?m=$ana").text.contains("\"ig\""))
    }

    @Test
    fun aTooBigBodyIsRefused() {
        val e = event()
        val m = join(e)
        assertEquals(400, call("/api/c/params?m=$m", "POST", body = "x".repeat(40_000)).status)
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
