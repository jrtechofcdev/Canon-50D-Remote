package com.example.core.insta

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The measures of one photo ([IgFeatures.encode]), so a second "Analisar" does not run the models again. */
@Entity(tableName = "ig_features")
data class IgFeatureRow(
    @PrimaryKey val photoId: Long,
    val version: Int,
    val data: String,
    val at: Long,
)

/**
 * A photo chosen for the event's Instagram post, in carousel order. The media page shows them once the
 * photographer sends ([com.example.core.church.ChurchEvent.igSentAt]).
 */
@Entity(tableName = "church_ig_picks", primaryKeys = ["eventId", "photoId"], indices = [Index("eventId")])
data class ChurchIgPick(
    val eventId: Long,
    val photoId: Long,
    val position: Int,
    val score: Float,
    /** [IgMoment] name. */
    val moment: String,
    /** The words shown on the photo, joined by " · ". */
    val reasons: String,
    /** [com.example.core.render.CropSpec] text of the feed and Stories frames; null story = does not fit. */
    val feedCrop: String,
    val storyCrop: String?,
)

@Dao
interface InstaDao {
    @Query("SELECT * FROM ig_features WHERE photoId = :photoId")
    suspend fun feature(photoId: Long): IgFeatureRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFeature(row: IgFeatureRow)

    @Query("DELETE FROM ig_features WHERE photoId NOT IN (SELECT id FROM photos)")
    suspend fun dropOrphanFeatures()

    @Query("SELECT * FROM church_ig_picks WHERE eventId = :eventId ORDER BY position")
    suspend fun picks(eventId: Long): List<ChurchIgPick>

    @Query("SELECT * FROM church_ig_picks WHERE eventId = :eventId ORDER BY position")
    fun observePicks(eventId: Long): Flow<List<ChurchIgPick>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPicks(picks: List<ChurchIgPick>)

    @Query("DELETE FROM church_ig_picks WHERE eventId = :eventId")
    suspend fun clearPicks(eventId: Long)

    @Transaction
    suspend fun replacePicks(eventId: Long, picks: List<ChurchIgPick>) {
        clearPicks(eventId)
        insertPicks(picks.mapIndexed { i, p -> p.copy(eventId = eventId, position = i) })
    }

    @Query("UPDATE church_events SET igCaption = :caption, igNotes = :notes, igSentAt = :sentAt WHERE id = :eventId")
    suspend fun setPost(eventId: Long, caption: String?, notes: String?, sentAt: Long?)
}
