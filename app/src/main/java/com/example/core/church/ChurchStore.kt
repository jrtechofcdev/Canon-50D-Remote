package com.example.core.church

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A congregation the photographer covers often: its look and colours, copied into each new event of it.
 * Colours are "#RRGGBB" text, as the media page uses them.
 */
@Entity(tableName = "church_congregations")
data class Congregation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accent: String = ChurchTheme.ACCENT,
    val secondary: String = ChurchTheme.SECONDARY,
    /** A line under the event name on the media page (theme, verse…). */
    val motto: String? = null,
    /** Logo picked from the phone (content URI), shown on the media page. */
    val logoUri: String? = null,
    /** Look of the "pronta" version: a preset and/or its own calibration. */
    val presetId: Long? = null,
    val adjust: String? = null,
    /** What someone who joins can do, before the photographer changes it ([ChurchPerm] flags). */
    val defaultPerms: Int = ChurchPerm.DEFAULT,
    val createdAt: Long,
)

/**
 * One service, crusade or party. Its photos go to the media page while it is open; only one event is open.
 * [token] goes in the link and QR; [code] is typed by those who come in (when [codeOn]).
 */
@Entity(tableName = "church_events")
data class ChurchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val congregationId: Long? = null,
    val name: String,
    val congregation: String,
    val date: Long,
    val accent: String = ChurchTheme.ACCENT,
    val secondary: String = ChurchTheme.SECONDARY,
    val motto: String? = null,
    val logoUri: String? = null,
    val presetId: Long? = null,
    val adjust: String? = null,
    /** "Melhorar" on the "pronta" version of this event. */
    val enhanceReady: Boolean = false,
    /** Ways in: the link, the QR code and the 4-digit code. With the code off, entry is free. */
    val linkOn: Boolean = true,
    val qrOn: Boolean = true,
    val codeOn: Boolean = true,
    val code: String,
    val token: String,
    val defaultPerms: Int = ChurchPerm.DEFAULT,
    val createdAt: Long,
    val closedAt: Long? = null,
    /** When the event closes by itself (null: only when the photographer closes it). */
    val autoCloseAt: Long? = null,
    /** Presets the media may preview and download with, as "3,7,12"; null = all of them. */
    val presets: String? = null,
    /** Who last changed the event's look ("Ana · Mídia" or "Fotógrafo") and when, for the notice on the page. */
    val lookBy: String? = null,
    val lookAt: Long? = null,
) {
    val isOpen get() = closedAt == null
}

/**
 * One person of the media team, by the name and role they typed. The role is only a label: what they can
 * do is in [perms], set by the photographer.
 */
@Entity(
    tableName = "church_members",
    indices = [Index(value = ["eventId"]), Index(value = ["token"], unique = true)],
)
data class ChurchMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val name: String,
    val role: String,
    /** Device token, kept by the page (cookie and storage). */
    val token: String,
    val status: String = MemberStatus.ACTIVE,
    val perms: Int = ChurchPerm.DEFAULT,
    val device: String? = null,
    val ip: String? = null,
    val joinedAt: Long,
    val lastSeenAt: Long = 0,
    /** [ChurchRank]: given by the photographer; the role typed at the door gives nothing. */
    @ColumnInfo(defaultValue = "0") val rank: Int = ChurchRank.VIEWER,
    /** An admin's own look (preset and/or full settings), and the one before it for "Reverter". */
    val lookPresetId: Long? = null,
    val lookAdjust: String? = null,
    val prevPresetId: Long? = null,
    val prevAdjust: String? = null,
    /** An independent admin keeps their own look when the main admin changes the event's. */
    @ColumnInfo(defaultValue = "0") val independent: Boolean = false,
) {
    /** "Ana · Mídia", how the person appears in the panel and the log. */
    val label get() = if (role.isBlank()) name else "$name · $role"
    val isActive get() = status == MemberStatus.ACTIVE
}

object MemberStatus {
    const val ACTIVE = "active"
    /** Cannot come back (by device and by name). */
    const val BLOCKED = "blocked"
    /** Taken out now; may come in again with the code. */
    const val KICKED = "kicked"
}

/** Who leads the looks: the main admin sets the event's, admins may keep their own, viewers follow. */
object ChurchRank {
    const val VIEWER = 0
    const val ADMIN = 1
    const val MAIN = 2

    fun label(rank: Int) = when (rank) {
        MAIN -> "ADM principal"
        ADMIN -> "ADM"
        else -> "Só ver"
    }
}

/** What one person may do, as bit flags. */
object ChurchPerm {
    const val VIEW = 1
    const val DOWNLOAD_ORIGINAL = 2
    const val DOWNLOAD_READY = 4
    const val DOWNLOAD_RAW = 8
    const val DOWNLOAD_ANY = DOWNLOAD_ORIGINAL or DOWNLOAD_READY or DOWNLOAD_RAW
    /** Browse the released presets on the page, preview them live and download the "pronta" with one. */
    const val PRESETS = 16
    /** See the preset shop and install a preset into the photographer's app. */
    const val STORE = 32
    /** Admins only: the full editor on the page (the app's panels), saving their own edits. */
    const val EDITOR = 64
    const val DEFAULT = VIEW or DOWNLOAD_ANY

    fun has(perms: Int, flag: Int) = perms and flag == flag
    fun with(perms: Int, flag: Int, on: Boolean) = if (on) perms or flag else perms and flag.inv()
}

/** A photo hidden from one person, or given only to them. */
@Entity(tableName = "church_photo_rules", primaryKeys = ["memberId", "photoId"])
data class MemberPhotoRule(
    val memberId: Long,
    val photoId: Long,
    val rule: String,
)

object PhotoRule {
    const val HIDDEN = "hidden"
    const val EXTRA = "extra"
}

/** A photo of the event; [hidden] takes it off the media page for everyone. */
@Entity(tableName = "church_event_photos", primaryKeys = ["eventId", "photoId"])
data class EventPhoto(
    val eventId: Long,
    val photoId: Long,
    val addedAt: Long,
    val hidden: Boolean = false,
)

/** One line of the event's log: who (name · role, or the photographer), what and from where. */
@Entity(tableName = "church_audit", indices = [Index(value = ["eventId"])])
data class ChurchAudit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val memberId: Long? = null,
    val who: String,
    val action: String,
    val detail: String? = null,
    val ip: String? = null,
    val at: Long,
)

/** The event's look over time: each change by the main admin or the photographer, to go back to one. */
@Entity(tableName = "church_looks", indices = [Index(value = ["eventId"])])
data class ChurchLook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val presetId: Long? = null,
    val adjust: String? = null,
    val byWho: String,
    val at: Long,
)

/** A look one admin sends to another admin, or to the photographer ([toMemberId] null). */
@Entity(tableName = "church_shares", indices = [Index(value = ["eventId"])])
data class ChurchShare(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val fromMemberId: Long? = null,
    val fromLabel: String,
    val toMemberId: Long? = null,
    val presetId: Long? = null,
    val adjust: String? = null,
    val at: Long,
    /** Applied or dismissed by who got it. */
    val doneAt: Long? = null,
)

object ChurchTheme {
    const val ACCENT = "#00FCA8"
    const val SECONDARY = "#1CDAFF"

    /** "#RRGGBB" or null when [text] is not a colour (the page never gets a broken style). */
    fun color(text: String?): String? = text?.trim()?.uppercase()?.takeIf { Regex("#[0-9A-F]{6}").matches(it) }
}

@Dao
interface ChurchDao {
    // Congregations.
    @Query("SELECT * FROM church_congregations ORDER BY name COLLATE NOCASE")
    fun observeCongregations(): Flow<List<Congregation>>

    @Query("SELECT * FROM church_congregations WHERE id = :id")
    suspend fun congregation(id: Long): Congregation?

    @Insert
    suspend fun insertCongregation(c: Congregation): Long

    @Update
    suspend fun updateCongregation(c: Congregation)

    @Query("DELETE FROM church_congregations WHERE id = :id")
    suspend fun deleteCongregation(id: Long)

    // Events.
    @Query("SELECT * FROM church_events ORDER BY (closedAt IS NULL) DESC, date DESC, id DESC")
    fun observeEvents(): Flow<List<ChurchEvent>>

    @Query("SELECT * FROM church_events WHERE id = :id")
    fun observeEvent(id: Long): Flow<ChurchEvent?>

    @Query("SELECT * FROM church_events WHERE id = :id")
    suspend fun event(id: Long): ChurchEvent?

    @Query("SELECT * FROM church_events WHERE closedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun openEvent(): ChurchEvent?

    @Query("SELECT * FROM church_events WHERE closedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    fun observeOpenEvent(): Flow<ChurchEvent?>

    @Insert
    suspend fun insertEvent(e: ChurchEvent): Long

    @Update
    suspend fun updateEvent(e: ChurchEvent)

    @Query("UPDATE church_events SET closedAt = :time WHERE closedAt IS NULL AND id != :keep")
    suspend fun closeOthers(keep: Long, time: Long)

    @Query("DELETE FROM church_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    // Members.
    @Query("SELECT * FROM church_members WHERE eventId = :eventId ORDER BY lastSeenAt DESC, joinedAt DESC")
    fun observeMembers(eventId: Long): Flow<List<ChurchMember>>

    @Query("SELECT * FROM church_members WHERE eventId = :eventId")
    suspend fun members(eventId: Long): List<ChurchMember>

    @Query("SELECT * FROM church_members WHERE token = :token LIMIT 1")
    suspend fun memberByToken(token: String): ChurchMember?

    @Query("SELECT * FROM church_members WHERE id = :id")
    suspend fun member(id: Long): ChurchMember?

    @Query("SELECT COUNT(*) FROM church_members WHERE eventId = :eventId AND status = 'blocked' AND name = :name COLLATE NOCASE")
    suspend fun blockedByName(eventId: Long, name: String): Int

    @Insert
    suspend fun insertMember(m: ChurchMember): Long

    @Update
    suspend fun updateMember(m: ChurchMember)

    @Query("UPDATE church_members SET lastSeenAt = :time, ip = :ip WHERE id = :id")
    suspend fun touchMember(id: Long, time: Long, ip: String?)

    @Query("DELETE FROM church_members WHERE eventId = :eventId")
    suspend fun deleteMembersOf(eventId: Long)

    // Photos and rules.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPhoto(p: EventPhoto): Long

    @Query("DELETE FROM church_event_photos WHERE eventId = :eventId AND photoId = :photoId")
    suspend fun removePhoto(eventId: Long, photoId: Long)

    @Query("UPDATE church_event_photos SET hidden = :hidden WHERE eventId = :eventId AND photoId IN (:photoIds)")
    suspend fun setHidden(eventId: Long, photoIds: List<Long>, hidden: Boolean)

    @Query("SELECT * FROM church_event_photos WHERE eventId = :eventId ORDER BY addedAt DESC")
    suspend fun photos(eventId: Long): List<EventPhoto>

    @Query("SELECT * FROM church_event_photos WHERE eventId = :eventId ORDER BY addedAt DESC")
    fun observePhotos(eventId: Long): Flow<List<EventPhoto>>

    @Query("DELETE FROM church_event_photos WHERE eventId = :eventId")
    suspend fun deletePhotosOf(eventId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRule(r: MemberPhotoRule)

    @Query("DELETE FROM church_photo_rules WHERE memberId = :memberId AND photoId = :photoId")
    suspend fun clearRule(memberId: Long, photoId: Long)

    @Query("SELECT * FROM church_photo_rules WHERE memberId = :memberId")
    suspend fun rules(memberId: Long): List<MemberPhotoRule>

    @Query("SELECT * FROM church_photo_rules WHERE memberId = :memberId")
    fun observeRules(memberId: Long): Flow<List<MemberPhotoRule>>

    @Query("DELETE FROM church_photo_rules WHERE memberId IN (SELECT id FROM church_members WHERE eventId = :eventId)")
    suspend fun deleteRulesOf(eventId: Long)

    // Log.
    @Insert
    suspend fun log(a: ChurchAudit): Long

    @Query("SELECT * FROM church_audit WHERE eventId = :eventId ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeLog(eventId: Long, limit: Int = 500): Flow<List<ChurchAudit>>

    @Query("SELECT * FROM church_audit WHERE eventId = :eventId ORDER BY at ASC, id ASC")
    suspend fun fullLog(eventId: Long): List<ChurchAudit>

    @Query("DELETE FROM church_audit WHERE eventId = :eventId")
    suspend fun deleteLogOf(eventId: Long)

    // Looks: the event's history and the looks admins share.
    @Insert
    suspend fun insertLook(l: ChurchLook): Long

    @Query("SELECT * FROM church_looks WHERE eventId = :eventId ORDER BY at DESC, id DESC")
    fun observeLooks(eventId: Long): Flow<List<ChurchLook>>

    @Query("DELETE FROM church_looks WHERE eventId = :eventId")
    suspend fun deleteLooksOf(eventId: Long)

    @Insert
    suspend fun insertShare(s: ChurchShare): Long

    @Update
    suspend fun updateShare(s: ChurchShare)

    @Query("SELECT * FROM church_shares WHERE id = :id")
    suspend fun share(id: Long): ChurchShare?

    @Query("SELECT * FROM church_shares WHERE eventId = :eventId AND toMemberId = :memberId AND doneAt IS NULL ORDER BY at DESC")
    suspend fun sharesTo(eventId: Long, memberId: Long): List<ChurchShare>

    @Query("SELECT * FROM church_shares WHERE eventId = :eventId AND toMemberId IS NULL ORDER BY at DESC")
    fun observeSharesToPhotographer(eventId: Long): Flow<List<ChurchShare>>

    @Query("DELETE FROM church_shares WHERE eventId = :eventId")
    suspend fun deleteSharesOf(eventId: Long)
}
