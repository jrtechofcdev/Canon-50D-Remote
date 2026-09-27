package com.example.core.repository

import com.example.core.database.PhotoDao
import com.example.core.models.DownloadedPhoto
import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allPhotos: Flow<List<DownloadedPhoto>> = photoDao.getAllPhotos()

    suspend fun getPhotoByHandle(handle: Int): DownloadedPhoto? {
        return photoDao.getPhotoByHandle(handle)
    }

    suspend fun insertPhoto(photo: DownloadedPhoto): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun deletePhoto(id: Long) {
        photoDao.deletePhotoById(id)
    }

    suspend fun clearAllHistory() {
        photoDao.clearHistory()
    }
}
