package com.example.core.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PhotoFormat {
    JPG, RAW, BOTH;
    
    companion object {
        fun fromPtpFormat(code: Int): PhotoFormat {
            return when (code) {
                0x3801 -> JPG
                0x3005 -> RAW // Canon RAW CR2 format
                else -> JPG
            }
        }
    }
}

@Entity(tableName = "downloaded_photos")
data class DownloadedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handle: Int, // PTP object handle
    val filename: String,
    val filePath: String,
    val fileSize: Long,
    val format: String, // "JPG" or "RAW"
    val timestamp: Long,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val cameraModel: String = "Canon EOS 50D",
    val folderName: String = "Default"
)

data class CameraProperties(
    val iso: String = "400",
    val aperture: String = "f/4.0",
    val shutterSpeed: String = "1/125",
    val exposureCompensation: String = "0",
    val whiteBalance: String = "Auto",
    val imageQuality: String = "RAW + Fine JPG",
    val driveMode: String = "Single Shot",
    val batteryLevel: Int = 85, // percentage
    val firmwareVersion: String = "v1.0.8",
    val freeSpaceGb: Double = 14.2,
    val totalPhotosCount: Int = 342,
    val isLiveViewActive: Boolean = false
)

data class EventModeConfig(
    val isActive: Boolean = false,
    val eventName: String = "Studio Session",
    val autoRenamePattern: String = "STUDIO_50D_###",
    val targetFolder: String = "Eventos",
    val autoDownloadJpg: Boolean = true,
    val autoDownloadRaw: Boolean = false
)

data class IntervalometerConfig(
    val intervalSeconds: Int = 5,
    val totalShots: Int = 100,
    val currentShot: Int = 0,
    val isRunning: Boolean = false
)

data class BulbConfig(
    val exposureSeconds: Int = 30,
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false
)
