package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.models.DownloadedPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM downloaded_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<DownloadedPhoto>>

    @Query("SELECT * FROM downloaded_photos WHERE handle = :handle LIMIT 1")
    suspend fun getPhotoByHandle(handle: Int): DownloadedPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: DownloadedPhoto): Long

    @Query("DELETE FROM downloaded_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("DELETE FROM downloaded_photos")
    suspend fun clearHistory()
}
