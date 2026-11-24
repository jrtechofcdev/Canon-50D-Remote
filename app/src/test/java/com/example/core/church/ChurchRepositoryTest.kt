package com.example.core.church

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChurchRepositoryTest {

    private lateinit var db: PhotoDatabase
    private lateinit var repo: ChurchRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoDatabase::class.java).allowMainThreadQueries().build()
        repo = ChurchRepository(db.churchDao()) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun photo(n: Int) = runBlocking { db.photoDao().insert(Photo(session = "S", baseName = "IMG_$n", savedBase = "IMG_$n", capturedAt = n.toLong())) }

    private suspend fun join(e: ChurchEvent, code: String? = e.code, name: String = "Ana", role: String = "Mídia", ip: String = "10.0.0.2", via: String = ChurchVia.QR, token: String? = e.token) =
        repo.join(via, token, code, name, role, ip, "Chrome", null)

    @Test
    fun withTheCodeANameAndARoleSomeoneComesIn() = runBlocking {
        val e = repo.newEvent("Culto de domingo", null)
        val r = join(e)
        assertTrue(r is JoinResult.Ok)
        val m = (r as JoinResult.Ok).member
        assertEquals("Ana · Mídia", m.label)
        assertEquals(ChurchPerm.DEFAULT, m.perms)
        assertEquals("Ana · Mídia", db.churchDao().fullLog(e.id).last().who)
    }

    @Test
    fun theDefaultDecidesWhatNewcomersMayDoAndCanBeAppliedToAll() = runBlocking {
        val e = repo.newEvent("Culto", null)
        val early = (join(e) as JoinResult.Ok).member
        val blocked = (join(e, name = "Caio", ip = "10.0.0.3") as JoinResult.Ok).member
        repo.block(blocked.id)
        // From now on, newcomers only see.
        listOf(ChurchPerm.DOWNLOAD_ORIGINAL, ChurchPerm.DOWNLOAD_READY, ChurchPerm.DOWNLOAD_RAW).forEach { repo.setDefaultPerm(e.id, it, false) }
        val late = (join(e, name = "Bia", ip = "10.0.0.4") as JoinResult.Ok).member
        assertEquals(ChurchPerm.VIEW, late.perms)
        // Those already inside keep theirs until the photographer applies the default to all.
        assertEquals(ChurchPerm.DEFAULT, db.churchDao().member(early.id)!!.perms)
        assertEquals(1, repo.applyDefaultToAll(e.id))
        assertEquals(ChurchPerm.VIEW, db.churchDao().member(early.id)!!.perms)
        assertEquals(ChurchPerm.DEFAULT, db.churchDao().member(blocked.id)!!.perms)
        val log = db.churchDao().fullLog(e.id).map { it.action }
        assertTrue(log.toString(), "Quem entrar: baixar RAW desligado" in log && "Permissões de quem entra aplicadas a todos" in log)
    }

    @Test
    fun anEditedCongregationShapesTheNextEventAndDeletingItKeepsTheEvents() = runBlocking {
        val id = repo.saveCongregation(Congregation(name = "AD Centro", createdAt = 0))
        val c = db.churchDao().congregation(id)!!
        repo.saveCongregation(c.copy(name = "AD Centro Sede", accent = "#F2B544", motto = "Ide", defaultPerms = ChurchPerm.VIEW))
        val e = repo.newEvent("Santa Ceia", id)
        assertEquals("AD Centro Sede", e.congregation)
        assertEquals("#F2B544", e.accent)
        assertEquals(ChurchPerm.VIEW, (join(e) as JoinResult.Ok).member.perms)
        repo.deleteCongregation(id)
        assertNull(db.churchDao().congregation(id))
        assertEquals("AD Centro Sede", db.churchDao().event(e.id)!!.congregation)
    }

    @Test
    fun theEventsNameAndDateCanBeFixedLater() = runBlocking {
        val e = repo.newEvent("Culto de domigo", null)
        val v = repo.version
        repo.editEvent(e.id, "  Culto   de domingo ", 1_764_000_000_000L)
        val fixed = db.churchDao().event(e.id)!!
        assertEquals("Culto de domingo", fixed.name)
        assertEquals(1_764_000_000_000L, fixed.date)
        assertTrue(repo.version > v)
        assertEquals("Evento editado", db.churchDao().fullLog(e.id).last().action)
        // A blank name is ignored instead of erasing it.
        repo.editEvent(e.id, "   ", fixed.date)
        assertEquals("Culto de domingo", db.churchDao().event(e.id)!!.name)
    }

    @Test
    fun anEventClosesByItselfAtItsDeadlineAndReopensWithoutIt() = runBlocking {
        val e = repo.newEvent("Vigília", null)
        repo.setAutoClose(e.id, 3)
        now += 2 * 3_600_000L
        assertFalse(repo.closeIfDue())
        now += 3_600_000L
        assertTrue(repo.closeIfDue())
        assertNull(db.churchDao().openEvent())
        assertEquals("Evento encerrado sozinho (validade)", db.churchDao().fullLog(e.id).last().action)
        repo.open(e.id)
        assertNull(db.churchDao().event(e.id)!!.autoCloseAt)
        assertFalse(repo.closeIfDue())
    }

    @Test
    fun aWrongCodeIsRefusedAndTriesAreLimited() = runBlocking {
        val e = repo.newEvent("Cruzada", null)
        repeat(5) { assertTrue(join(e, code = "0000".takeIf { it != e.code } ?: "1111") is JoinResult.Denied) }
        val blocked = join(e) as JoinResult.Denied
        assertTrue(blocked.message, blocked.message.contains("tentativas"))
        // Another device is not held back by this one.
        assertTrue(join(e, ip = "10.0.0.9") is JoinResult.Ok)
    }

    @Test
    fun withTheCodeOffEntryIsFreeButNameAndRoleAreStillAsked() = runBlocking {
        val e = repo.newEvent("Festa", null)
        repo.setAccess(e.id, linkOn = true, qrOn = true, codeOn = false)
        assertTrue(join(e, code = null, via = ChurchVia.ADDRESS, token = null) is JoinResult.Ok)
        assertTrue(join(e, code = null, name = " ") is JoinResult.Denied)
        assertTrue(join(e, code = null, role = "") is JoinResult.Denied)
    }

    @Test
    fun aWayInTurnedOffIsRefused() = runBlocking {
        val e = repo.newEvent("Culto", null)
        repo.setAccess(e.id, linkOn = false, qrOn = true, codeOn = true)
        assertTrue(join(e, via = ChurchVia.LINK) is JoinResult.Denied)
        assertTrue(join(e, via = ChurchVia.QR) is JoinResult.Ok)
        assertTrue(join(e, via = ChurchVia.QR, token = "outro") is JoinResult.Denied)
    }

    @Test
    fun kickedLosesTheDeviceAndBlockedCannotComeBackEvenByName() = runBlocking {
        val e = repo.newEvent("Culto", null)
        val ana = (join(e) as JoinResult.Ok).member
        repo.kick(ana.id)
        assertNull(repo.memberFor(ana.token, null))
        // Kicked may come in again with the code.
        val again = (join(e) as JoinResult.Ok).member
        repo.block(again.id)
        assertNull(repo.memberFor(again.token, null))
        val back = join(e, name = "ana") as JoinResult.Denied
        assertEquals(ChurchRepository.BLOCKED, back.message)
    }

    @Test
    fun theCodeCanBeSetOrReset() = runBlocking {
        val e = repo.newEvent("Culto", null)
        assertEquals("2468", repo.resetCode(e.id, custom = "2468"))
        val ana = (join(e, code = "2468") as JoinResult.Ok).member
        val fresh = repo.resetCode(e.id, kickAll = true)!!
        assertTrue(ChurchRules.validCode(fresh) && fresh != "2468")
        assertNull(repo.memberFor(ana.token, null))
    }

    @Test
    fun eachPersonSeesTheEventMinusHiddenPlusExtras() = runBlocking {
        val e = repo.newEvent("Culto", null)
        val p1 = photo(1); val p2 = photo(2); val p3 = photo(3); val extra = photo(4)
        now = 2_000; repo.addPhoto(e.id, p1)
        now = 3_000; repo.addPhoto(e.id, p2)
        now = 4_000; repo.addPhoto(e.id, p3)
        val ana = (join(e) as JoinResult.Ok).member
        val bia = (join(e, name = "Bia", ip = "10.0.0.3") as JoinResult.Ok).member
        assertEquals(listOf(p3, p2, p1), repo.visiblePhotos(ana))
        repo.setRule(ana.id, p2, PhotoRule.HIDDEN)
        repo.setRule(ana.id, extra, PhotoRule.EXTRA)
        repo.hideForAll(e.id, listOf(p3), true)
        assertEquals(listOf(extra, p1), repo.visiblePhotos(db.churchDao().member(ana.id)!!))
        assertEquals(listOf(p2, p1), repo.visiblePhotos(bia))
        repo.setPerm(bia.id, ChurchPerm.VIEW, false)
        assertTrue(repo.visiblePhotos(db.churchDao().member(bia.id)!!).isEmpty())
    }

    @Test
    fun downloadsFollowEachPermission() = runBlocking {
        val e = repo.newEvent("Culto", null)
        val ana = (join(e) as JoinResult.Ok).member
        ChurchFile.entries.forEach { assertTrue(repo.canDownload(ana, it)) }
        repo.setPerm(ana.id, ChurchPerm.DOWNLOAD_RAW, false)
        val now = db.churchDao().member(ana.id)!!
        assertFalse(repo.canDownload(now, ChurchFile.RAW))
        assertTrue(repo.canDownload(now, ChurchFile.ORIGINAL))
        assertTrue(db.churchDao().fullLog(e.id).any { it.who == "Fotógrafo → Ana · Mídia" && it.action.contains("baixar RAW") })
    }

    @Test
    fun aNewEventClosesTheOtherAndTakesItsCongregationsLook() = runBlocking {
        val cid = repo.saveCongregation(Congregation(name = "AD Central", accent = "#FF8800", motto = "Ide por todo o mundo", presetId = 7, createdAt = 0))
        val first = repo.newEvent("Culto", null)
        val second = repo.newEvent("Cruzada", cid)
        assertFalse(db.churchDao().event(first.id)!!.isOpen)
        val open = db.churchDao().openEvent()!!
        assertEquals(second.id, open.id)
        assertEquals("AD Central", open.congregation)
        assertEquals("#FF8800", open.accent)
        assertEquals(7L, open.presetId)
        assertTrue(join(first) is JoinResult.Denied)
    }

    @Test
    fun themeColoursAreCheckedBeforeTheyReachThePage() {
        assertEquals("#00FCA8", ChurchTheme.color("#00fca8"))
        assertNull(ChurchTheme.color("red; } body { display:none"))
    }

    @Test
    fun theVersionMovesOnEveryChange() = runBlocking {
        val e = repo.newEvent("Culto", null)
        val v = repo.version
        repo.addPhoto(e.id, photo(1))
        assertTrue(repo.version > v)
        val v2 = repo.version
        repo.addPhoto(e.id, db.churchDao().photos(e.id).first().photoId)
        assertEquals("the same photo twice changes nothing", v2, repo.version)
    }

    @Test
    fun version11DatabaseGainsTheChurchTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_11_12.db"
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); listOf("", "-wal", "-shm", "-journal").forEach { File(path + it).delete() } }
        // Start from version 10 to go through both new migrations (enhance column, then the church tables).
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session` TEXT NOT NULL, `baseName` TEXT NOT NULL, `savedBase` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `jpgUri` TEXT, `rawUri` TEXT, `jpgSize` INTEGER NOT NULL, `rawSize` INTEGER NOT NULL, `previewPath` TEXT, `aperture` TEXT, `shutter` TEXT, `iso` TEXT, `favorite` INTEGER NOT NULL, `rating` INTEGER NOT NULL DEFAULT 0, `rawCameraName` TEXT, `jpgCameraName` TEXT, `presetId` INTEGER, `adjust` TEXT, `editedUri` TEXT, `clientPick` INTEGER NOT NULL DEFAULT 0, `note` TEXT, `audioNote` TEXT, `moment` TEXT, `lat` REAL, `lon` REAL, `sharpness` REAL, `blurry` INTEGER NOT NULL DEFAULT 0, `faces` INTEGER, `eyesClosed` INTEGER NOT NULL DEFAULT 0, `score` REAL, `burstGroup` INTEGER, `suggested` INTEGER NOT NULL DEFAULT 0, `rejected` INTEGER NOT NULL DEFAULT 0, `dHash` INTEGER, `crop` TEXT)")
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_session_baseName` ON `photos` (`session`, `baseName`)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `grp` TEXT NOT NULL, `settings` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `favorite` INTEGER NOT NULL DEFAULT 0, `lastUsed` INTEGER NOT NULL DEFAULT 0)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS `delivery_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client` TEXT NOT NULL, `phone` TEXT, `code` TEXT NOT NULL, `token` TEXT NOT NULL, `event` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `closedAt` INTEGER, `lastSeenAt` INTEGER NOT NULL, `express` INTEGER NOT NULL DEFAULT 0)")
            raw.execSQL("CREATE TABLE IF NOT EXISTS `delivery_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `photoId` INTEGER NOT NULL, `name` TEXT NOT NULL, `state` TEXT NOT NULL, `path` TEXT, `size` INTEGER NOT NULL, `downloads` INTEGER NOT NULL, `error` TEXT, `queuedAt` INTEGER NOT NULL, `readyAt` INTEGER, `picked` INTEGER NOT NULL, `released` INTEGER NOT NULL DEFAULT 0, `variant` INTEGER NOT NULL DEFAULT 0, `look` TEXT, `label` TEXT)")
            raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_delivery_items_sessionId_photoId_variant` ON `delivery_items` (`sessionId`, `photoId`, `variant`)")
            raw.execSQL("INSERT INTO photos (session, baseName, savedBase, capturedAt, jpgSize, rawSize, favorite, iso) VALUES ('Culto', 'IMG_0200', 'IMG_0200', 1, 10, 0, 1, '3200')")
            raw.version = 10
        }
        val room = PhotoDatabase.create(context, name)
        runBlocking {
            val photo = room.photoDao().inSession("Culto").single()
            assertTrue(photo.favorite)
            assertEquals(0, photo.enhance)
            val r = ChurchRepository(room.churchDao())
            val e = r.newEvent("Culto", null)
            r.addPhoto(e.id, photo.id)
            assertNotNull(room.churchDao().openEvent())
            assertEquals(listOf(photo.id), room.churchDao().photos(e.id).map { it.photoId })
            // Version 13: the deadline column.
            r.setAutoClose(e.id, 4)
            assertNotNull(room.churchDao().event(e.id)!!.autoCloseAt)
        }
        room.close()
    }
}
