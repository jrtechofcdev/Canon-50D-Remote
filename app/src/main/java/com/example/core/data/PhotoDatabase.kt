package com.example.core.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One shot. RAW and JPG of the same frame share a row (same [baseName] within a session),
 * so the preview can offer both files together.
 */
@Entity(tableName = "photos", indices = [Index(value = ["session", "baseName"])])
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    /** Name the camera gave the frame, e.g. IMG_1234. Used to pair RAW and JPG. */
    val baseName: String,
    /** Name used for the saved files (the camera name, or the session name when renaming is on). */
    val savedBase: String,
    val capturedAt: Long,
    val jpgUri: String? = null,
    val rawUri: String? = null,
    val jpgSize: Long = 0,
    val rawSize: Long = 0,
    /** Local JPEG extracted from the RAW, used when only the RAW was downloaded. */
    val previewPath: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val iso: String? = null,
    val favorite: Boolean = false,
    /** 0 = no stars, 1..5. */
    @ColumnInfo(defaultValue = "0") val rating: Int = 0,
    /** File names on the camera card (e.g. IMG_1234.CR2), used to find frames still missing after a reconnect. */
    val rawCameraName: String? = null,
    val jpgCameraName: String? = null,
    /** Preset applied to this photo (null = none). */
    val presetId: Long? = null,
    /** Per-photo calibration: the full settings used for this photo when they differ from the preset. */
    val adjust: String? = null,
    /** JPG copy with the preset applied, in the "editadas" folder. */
    val editedUri: String? = null,
    /** Picked by the client (heart in client mode). */
    @ColumnInfo(defaultValue = "0") val clientPick: Boolean = false,
    val note: String? = null,
    /** Voice note, an .m4a in the app folder. */
    val audioNote: String? = null,
    /** Part of the event (Cerimônia, Recepção…) active when the frame arrived. */
    val moment: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    // Culling, filled in the background after the frame is saved.
    val sharpness: Float? = null,
    @ColumnInfo(defaultValue = "0") val blurry: Boolean = false,
    val faces: Int? = null,
    @ColumnInfo(defaultValue = "0") val eyesClosed: Int = 0,
    /** 0..100, higher is better. */
    val score: Float? = null,
    /** Frames of one burst share the id of the burst's first frame. */
    val burstGroup: Long? = null,
    @ColumnInfo(defaultValue = "0") val suggested: Boolean = false,
    @ColumnInfo(defaultValue = "0") val rejected: Boolean = false,
    /** 64-bit perceptual hash, used to find near-identical frames. */
    val dHash: Long? = null,
    /** Crop and straighten ([com.example.core.render.CropSpec] text); null = the whole frame. */
    val crop: String? = null,
) {
    /** What the viewer shows: the JPG when there is one, else the preview pulled out of the RAW. */
    val displayUri: String? get() = jpgUri ?: previewPath?.let { "file://$it" }

    /** What to show in lists and send to others: the edited copy when there is one. */
    val lookUri: String? get() = editedUri ?: displayUri
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun observe(id: Long): Flow<Photo?>

    @Query("SELECT * FROM photos WHERE session = :session AND baseName = :baseName ORDER BY capturedAt DESC LIMIT 1")
    suspend fun findLatest(session: String, baseName: String): Photo?

    @Query("SELECT COUNT(*) FROM photos WHERE session = :session")
    suspend fun countInSession(session: String): Int

    @Insert
    suspend fun insert(photo: Photo): Long

    @Update
    suspend fun update(photo: Photo)

    @Query("UPDATE photos SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE photos SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM photos WHERE session = :session ORDER BY capturedAt ASC")
    suspend fun inSession(session: String): List<Photo>

    @Query("UPDATE photos SET presetId = :presetId, adjust = :adjust WHERE id = :id")
    suspend fun setLook(id: Long, presetId: Long?, adjust: String?)

    @Query("UPDATE photos SET editedUri = :uri WHERE id = :id")
    suspend fun setEdited(id: Long, uri: String?)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: Long): Photo?

    @Query("UPDATE photos SET crop = :crop WHERE id IN (:ids)")
    suspend fun setCrop(ids: List<Long>, crop: String?)

    @Query("UPDATE photos SET clientPick = :pick WHERE id = :id")
    suspend fun setClientPick(id: Long, pick: Boolean)

    @Query("UPDATE photos SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("UPDATE photos SET audioNote = :path WHERE id = :id")
    suspend fun setAudioNote(id: Long, path: String?)

    @Query("UPDATE photos SET moment = :moment WHERE id IN (:ids)")
    suspend fun setMoment(ids: List<Long>, moment: String?)

    @Query("UPDATE photos SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("UPDATE photos SET rating = :rating WHERE id IN (:ids)")
    suspend fun setRating(ids: List<Long>, rating: Int)

    @Query("UPDATE photos SET rejected = :rejected WHERE id IN (:ids)")
    suspend fun setRejected(ids: List<Long>, rejected: Boolean)

    @Query("UPDATE photos SET sharpness = :sharpness, blurry = :blurry, faces = :faces, eyesClosed = :eyesClosed, score = :score, dHash = :dHash WHERE id = :id")
    suspend fun setAnalysis(id: Long, sharpness: Float, blurry: Boolean, faces: Int?, eyesClosed: Int, score: Float, dHash: Long)

    @Query("UPDATE photos SET burstGroup = :group, suggested = :suggested WHERE id = :id")
    suspend fun setBurst(id: Long, group: Long?, suggested: Boolean)

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<Photo>

    @Query("SELECT * FROM photos WHERE jpgUri IS NOT NULL OR previewPath IS NOT NULL ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latestWithImage(): Photo?

    suspend fun latestDisplayUri(): String? = latestWithImage()?.displayUri

    @Query("SELECT rawCameraName FROM photos WHERE rawCameraName IS NOT NULL UNION SELECT jpgCameraName FROM photos WHERE jpgCameraName IS NOT NULL")
    suspend fun knownCameraNames(): List<String>
}

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "grp") val group: String,
    /** [com.example.core.preset.PresetSettings.encode] text. */
    val settings: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Pinned to the front of every preset strip. */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Last time it was applied to a photo; recent ones come first after the favourites. */
    @ColumnInfo(defaultValue = "0") val lastUsed: Long = 0,
)

@Dao
interface PresetDao {
    @Query("UPDATE presets SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE presets SET lastUsed = :time WHERE id = :id")
    suspend fun markUsed(id: Long, time: Long)

    @Query("UPDATE presets SET grp = :group WHERE id IN (:ids)")
    suspend fun setGroup(ids: List<Long>, group: String)

    @Query("UPDATE presets SET grp = :to WHERE grp = :from")
    suspend fun renameGroup(from: String, to: String)

    @Query("SELECT * FROM presets ORDER BY grp, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY grp, name COLLATE NOCASE")
    suspend fun all(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun get(id: Long): PresetEntity?

    @Query("SELECT * FROM presets WHERE id = :id")
    fun observe(id: Long): Flow<PresetEntity?>

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun count(): Int

    @Insert
    suspend fun insert(preset: PresetEntity): Long

    @Update
    suspend fun update(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE photos SET presetId = NULL WHERE presetId = :id")
    suspend fun detachFromPhotos(id: Long)
}

@Database(entities = [Photo::class, PresetEntity::class], version = 7, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun presetDao(): PresetDao

    companion object {
        /** [name] is only changed by tests, which must not share the file the app itself opens. */
        fun create(context: Context, name: String = "tether50d.db"): PhotoDatabase =
            Room.databaseBuilder(context, PhotoDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                // Version 1 only ever held simulator entries, so dropping it loses nothing real.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()

        /** Adds stars and camera file names without touching existing photos. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN rawCameraName TEXT")
                db.execSQL("ALTER TABLE photos ADD COLUMN jpgCameraName TEXT")
            }
        }

        /** Favourite presets and when each was last used, to sort the preset strips. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE presets ADD COLUMN lastUsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Crop and straighten per photo. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN crop TEXT")
            }
        }

        /** Client picks, notes, moments, location and the culling results. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "clientPick INTEGER NOT NULL DEFAULT 0", "note TEXT", "audioNote TEXT", "moment TEXT",
                    "lat REAL", "lon REAL", "sharpness REAL", "blurry INTEGER NOT NULL DEFAULT 0", "faces INTEGER",
                    "eyesClosed INTEGER NOT NULL DEFAULT 0", "score REAL", "burstGroup INTEGER",
                    "suggested INTEGER NOT NULL DEFAULT 0", "rejected INTEGER NOT NULL DEFAULT 0", "dHash INTEGER",
                ).forEach { db.execSQL("ALTER TABLE photos ADD COLUMN $it") }
            }
        }

        /** Presets table, plus the look (preset and calibration) and edited copy of each photo. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN presetId INTEGER")
                db.execSQL("ALTER TABLE photos ADD COLUMN adjust TEXT")
                db.execSQL("ALTER TABLE photos ADD COLUMN editedUri TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `grp` TEXT NOT NULL, `settings` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
            }
        }
    }
}
