package com.example.core.delivery

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
 * One client of the local delivery: what they see in the portal is only the photos of their own
 * session. The [token] goes in the client's QR code; the 6-digit [code] is typed when there is no QR.
 */
@Entity(tableName = "delivery_sessions")
data class DeliverySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val client: String,
    val phone: String? = null,
    val code: String,
    val token: String,
    /** The open gallery of an event: everyone with the event code sees it. */
    val event: Boolean = false,
    val createdAt: Long,
    /** 0 = no time limit. */
    val expiresAt: Long = 0,
    val closedAt: Long? = null,
    /** Last time the portal asked for this session, to show who is online. */
    val lastSeenAt: Long = 0,
    /** Fotografia Express: no code, everyone on the Wi-Fi sees it; preview until the photographer releases. */
    @ColumnInfo(defaultValue = "0") val express: Boolean = false,
) {
    val isOpen: Boolean get() = closedAt == null

    fun isValid(now: Long): Boolean = isOpen && (expiresAt == 0L || now < expiresAt)
}

/** One photo waiting, being prepared or ready for one client. */
@Entity(
    tableName = "delivery_items",
    indices = [Index(value = ["sessionId", "photoId"], unique = true)],
)
data class DeliveryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val photoId: Long,
    /** File name the client gets, e.g. Ana_IMG_1234.jpg. */
    val name: String,
    val state: String = DeliveryState.WAITING,
    /** Prepared file in the app cache. */
    val path: String? = null,
    val size: Long = 0,
    val downloads: Int = 0,
    val error: String? = null,
    val queuedAt: Long,
    val readyAt: Long? = null,
    /** Hearted by the client in the portal. */
    val picked: Boolean = false,
    /** Fotografia Express: paid and released by the photographer, so it downloads without the preview mark. */
    @ColumnInfo(defaultValue = "0") val released: Boolean = false,
)

/** Item states, in the order an item goes through them. */
object DeliveryState {
    const val WAITING = "waiting"
    const val PREPARING = "preparing"
    const val READY = "ready"
    const val DOWNLOADED = "downloaded"
    const val ERROR = "error"

    /** Ready for the portal: prepared, downloaded or not. */
    val served = listOf(READY, DOWNLOADED)
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM delivery_sessions WHERE express = :express ORDER BY (closedAt IS NULL) DESC, createdAt DESC")
    fun observeSessions(express: Boolean): Flow<List<DeliverySession>>

    @Query("SELECT * FROM delivery_sessions WHERE closedAt IS NULL AND express = 0 ORDER BY createdAt DESC")
    suspend fun openSessions(): List<DeliverySession>

    @Query("SELECT * FROM delivery_sessions WHERE id = :id")
    suspend fun session(id: Long): DeliverySession?

    @Query("SELECT * FROM delivery_sessions WHERE token = :token AND express = :express AND closedAt IS NULL LIMIT 1")
    suspend fun sessionByToken(token: String, express: Boolean): DeliverySession?

    /** Codes are only for the Entrega local; Express has no code. */
    @Query("SELECT * FROM delivery_sessions WHERE code = :code AND express = 0 AND closedAt IS NULL LIMIT 1")
    suspend fun sessionByCode(code: String): DeliverySession?

    @Query("SELECT * FROM delivery_sessions WHERE express = 1 AND closedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun openExpress(): DeliverySession?

    @Query("UPDATE delivery_items SET released = :on WHERE id IN (:ids)")
    suspend fun setReleased(ids: List<Long>, on: Boolean)

    @Query("UPDATE delivery_items SET released = 1 WHERE sessionId = :sessionId")
    suspend fun releaseAll(sessionId: Long)

    @Query("SELECT code FROM delivery_sessions WHERE closedAt IS NULL")
    suspend fun openCodes(): List<String>

    @Insert
    suspend fun insertSession(session: DeliverySession): Long

    @Update
    suspend fun updateSession(session: DeliverySession)

    @Query("UPDATE delivery_sessions SET closedAt = :time WHERE id = :id AND closedAt IS NULL")
    suspend fun closeSession(id: Long, time: Long)

    @Query("UPDATE delivery_sessions SET closedAt = :time WHERE closedAt IS NULL AND express = 0 AND expiresAt > 0 AND expiresAt <= :time")
    suspend fun closeExpired(time: Long): Int

    @Query("UPDATE delivery_sessions SET lastSeenAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM delivery_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM delivery_items WHERE sessionId = :sessionId")
    suspend fun deleteItemsOf(sessionId: Long)

    /** Returns -1 when the photo is already in that session (a photo goes to a client once). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: DeliveryItem): Long

    @Query("SELECT * FROM delivery_items WHERE id = :id")
    suspend fun item(id: Long): DeliveryItem?

    @Query("SELECT * FROM delivery_items WHERE sessionId = :sessionId ORDER BY queuedAt DESC, id DESC")
    suspend fun items(sessionId: Long): List<DeliveryItem>

    @Query("SELECT * FROM delivery_items WHERE photoId = :photoId")
    suspend fun itemsOfPhoto(photoId: Long): List<DeliveryItem>

    @Query("SELECT i.* FROM delivery_items i JOIN delivery_sessions s ON s.id = i.sessionId WHERE s.express = :express ORDER BY i.queuedAt DESC, i.id DESC")
    fun observeItems(express: Boolean): Flow<List<DeliveryItem>>

    @Query("SELECT i.* FROM delivery_items i JOIN delivery_sessions s ON s.id = i.sessionId WHERE i.state = 'waiting' AND s.express = :express ORDER BY i.queuedAt ASC, i.id ASC LIMIT 1")
    suspend fun nextWaiting(express: Boolean): DeliveryItem?

    @Query("UPDATE delivery_items SET state = :state, path = :path, size = :size, error = :error, readyAt = :readyAt WHERE id = :id")
    suspend fun setState(id: Long, state: String, path: String?, size: Long, error: String?, readyAt: Long?)

    /** After a crash or a stop mid-way, what was being prepared starts again. */
    @Query("UPDATE delivery_items SET state = 'waiting' WHERE state = 'preparing' AND sessionId IN (SELECT id FROM delivery_sessions WHERE express = :express)")
    suspend fun resetPreparing(express: Boolean)

    @Query("UPDATE delivery_items SET state = 'waiting', error = NULL WHERE id = :id")
    suspend fun retry(id: Long)

    @Query("UPDATE delivery_items SET state = 'waiting', error = NULL WHERE state = 'error' AND sessionId IN (SELECT id FROM delivery_sessions WHERE express = :express)")
    suspend fun retryAll(express: Boolean)

    /** Files cleared on stop: the items go back to waiting so they are made again on the next start. */
    @Query("UPDATE delivery_items SET state = 'waiting', path = NULL, size = 0, readyAt = NULL WHERE state IN ('ready', 'downloaded') AND sessionId IN (SELECT id FROM delivery_sessions WHERE express = :express)")
    suspend fun forgetFiles(express: Boolean)

    @Query("UPDATE delivery_items SET downloads = downloads + 1, state = 'downloaded' WHERE id = :id")
    suspend fun countDownload(id: Long)

    @Query("UPDATE delivery_items SET picked = :picked WHERE id = :id")
    suspend fun setPicked(id: Long, picked: Boolean)

    @Query("DELETE FROM delivery_items WHERE id = :id")
    suspend fun deleteItem(id: Long)
}
