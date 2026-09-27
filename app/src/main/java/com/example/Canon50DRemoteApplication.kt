package com.example

import android.app.Application
import com.example.core.camera.CameraManager
import com.example.core.database.AppDatabase
import com.example.core.download.DownloadManager
import com.example.core.repository.PhotoRepository
import kotlinx.coroutines.launch

class Canon50DRemoteApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var photoRepository: PhotoRepository
        private set

    lateinit var cameraManager: CameraManager
        private set

    lateinit var downloadManager: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        database = AppDatabase.getDatabase(this)
        photoRepository = PhotoRepository(database.photoDao())
        cameraManager = CameraManager(this)
        downloadManager = DownloadManager(this, photoRepository)

        // Automatically subscribe DownloadManager to listen to new photos from CameraManager
        cameraManager.observeCapturedPhotos(downloadManager)
    }
}

// Extension to allow CameraManager to trigger automatic download of captured photos
fun CameraManager.observeCapturedPhotos(downloadManager: DownloadManager) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    scope.launch {
        photoCapturedEvent.collect { photo ->
            // Trigger automatic download
            downloadManager.downloadPhoto(photo, isRealUsb = (connectionState.value == CameraManager.ConnectionState.CONNECTED_REAL_USB))
        }
    }
}
