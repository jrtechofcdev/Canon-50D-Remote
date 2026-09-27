package com.example.core.data

import android.content.Context
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

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Photo::class], version = 2, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        fun create(context: Context): PhotoDatabase =
            Room.databaseBuilder(context, PhotoDatabase::class.java, "tether50d.db")
                // Version 1 only ever held simulator entries, so dropping it loses nothing real.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
