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
) {
    /** What the viewer shows: the JPG when there is one, else the preview pulled out of the RAW. */
    val displayUri: String? get() = jpgUri ?: previewPath?.let { "file://$it" }
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

    @Query("SELECT rawCameraName FROM photos WHERE rawCameraName IS NOT NULL UNION SELECT jpgCameraName FROM photos WHERE jpgCameraName IS NOT NULL")
    suspend fun knownCameraNames(): List<String>
}

@Database(entities = [Photo::class], version = 3, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        fun create(context: Context): PhotoDatabase =
            Room.databaseBuilder(context, PhotoDatabase::class.java, "tether50d.db")
                .addMigrations(MIGRATION_2_3)
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
    }
}
