package com.example.core.download

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.util.Log
import com.example.core.models.DownloadedPhoto
import com.example.core.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DownloadManager(
    private val context: Context,
    private val repository: PhotoRepository
) {
    private val TAG = "DownloadManager"

    data class DownloadTask(
        val filename: String,
        val progress: Float, // 0.0f to 1.0f
        val isDownloading: Boolean = false,
        val speedMbS: Double = 0.0,
        val sizeBytes: Long = 0
    )

    private val _currentTask = MutableStateFlow<DownloadTask?>(null)
    val currentTask: StateFlow<DownloadTask?> = _currentTask.asStateFlow()

    private val _downloadHistory = MutableStateFlow<List<DownloadedPhoto>>(emptyList())
    val downloadHistory = _downloadHistory.asStateFlow()

    suspend fun downloadPhoto(photo: DownloadedPhoto, isRealUsb: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download for ${photo.filename} (Size: ${photo.fileSize} bytes)...")
            
            _currentTask.value = DownloadTask(
                filename = photo.filename,
                progress = 0f,
                isDownloading = true,
                sizeBytes = photo.fileSize
            )

            // Setup download destination folder
            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val subfolder = File(picturesDir, photo.folderName)
            if (!subfolder.exists()) {
                subfolder.mkdirs()
            }
            val targetFile = File(subfolder, photo.filename)

            if (isRealUsb) {
                // Real USB download simulation or actual stream write
                // Here we simulate the stream transfer over USB block-by-block to update progress
                val bufferSize = 1024 * 64
                var written: Long = 0
                val total = photo.fileSize
                val out = FileOutputStream(targetFile)
                
                while (written < total) {
                    val chunk = minOf(bufferSize.toLong(), total - written).toInt()
                    // write dummy buffer or actual USB bulk chunk
                    out.write(ByteArray(chunk))
                    written += chunk
                    
                    val progressVal = written.toFloat() / total.toFloat()
                    val speed = kotlin.random.Random.Default.nextDouble(12.5, 18.5) // USB 2.0 speed approximation (MB/s)
                    _currentTask.value = _currentTask.value?.copy(progress = progressVal, speedMbS = speed)
                    delay(5) // simulate physical network/bus delay
                }
                out.close()
            } else {
                // Simulator download: generate a visually gorgeous graphic matching the camera shot details
                val steps = 20
                for (i in 1..steps) {
                    delay(50) // visual loading speed
                    val progressVal = i.toFloat() / steps.toFloat()
                    _currentTask.value = _currentTask.value?.copy(
                        progress = progressVal,
                        speedMbS = 32.4 // Fast simulated local transfer speed
                    )
                }

                // Actually generate the visual photo mockup
                generateScenicMockup(targetFile, photo)
            }

            // Save completed photo state to Room Database
            val completedPhoto = photo.copy(filePath = targetFile.absolutePath, timestamp = System.currentTimeMillis())
            repository.insertPhoto(completedPhoto)

            Log.d(TAG, "Successfully downloaded and saved: ${targetFile.absolutePath}")
            _currentTask.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading photo", e)
            _currentTask.value = null
        }
    }

    private fun generateScenicMockup(file: File, photo: DownloadedPhoto) {
        val width = 1920
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // 1. Draw a beautiful cosmic scenic background based on photo handle/time
        val random = Random(photo.handle.toLong())
        val isSunset = random.nextBoolean()

        val gradient = if (isSunset) {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(255, 94, 58), Color.rgb(76, 0, 112),
                Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(18, 24, 48), Color.rgb(12, 12, 16),
                Shader.TileMode.CLAMP
            )
        }
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Stars / ambient details
        paint.color = Color.WHITE
        for (i in 0..120) {
            val starX = random.nextFloat() * width
            val starY = random.nextFloat() * (height * 0.7f)
            val starRadius = random.nextFloat() * 3f + 1f
            paint.alpha = random.nextInt(100, 255)
            canvas.drawCircle(starX, starY, starRadius, paint)
        }
        paint.alpha = 255

        // Mountain ranges
        paint.color = Color.rgb(22, 18, 36)
        val mountainPath1 = Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, height * 0.65f)
            lineTo(width * 0.3f, height * 0.45f)
            lineTo(width * 0.55f, height * 0.7f)
            lineTo(width * 0.8f, height * 0.5f)
            lineTo(width.toFloat(), height * 0.75f)
            lineTo(width.toFloat(), height.toFloat())
            close()
        }
        canvas.drawPath(mountainPath1, paint)

        paint.color = Color.rgb(12, 8, 20)
        val mountainPath2 = Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, height * 0.8f)
            lineTo(width * 0.45f, height * 0.6f)
            lineTo(width * 0.7f, height * 0.82f)
            lineTo(width.toFloat(), height * 0.7f)
            lineTo(width.toFloat(), height.toFloat())
            close()
        }
        canvas.drawPath(mountainPath2, paint)

        // Draw camera info overlays in the mockup
        paint.color = Color.WHITE
        paint.textSize = 55f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("CANON EOS 50D REMOTE", 80f, 150f, paint)

        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Arquivo: ${photo.filename}", 80f, 220f, paint)
        canvas.drawText("Pasta: /Pictures/Canon50D/${photo.folderName}", 80f, 270f, paint)
        canvas.drawText("Formato: ${photo.format} | Tamanho: %.2f MB".format(photo.fileSize / (1024.0 * 1024.0)), 80f, 320f, paint)

        // Draw camera EXIF telemetry card at the bottom right
        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRoundRect(width - 700f, height - 380f, width - 60f, height - 60f, 24f, 24f, paint)

        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val telemetryX = width - 640f
        var telemetryY = height - 310f

        canvas.drawText("EXIF METADATA (50D)", telemetryX, telemetryY, paint)
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        paint.color = Color.rgb(180, 180, 180)
        telemetryY += 50f
        canvas.drawText("Abertura: ${photo.aperture ?: "N/A"}", telemetryX, telemetryY, paint)
        telemetryY += 45f
        canvas.drawText("Exposição: ${photo.shutterSpeed ?: "N/A"} s", telemetryX, telemetryY, paint)
        telemetryY += 45f
        canvas.drawText("ISO: ${photo.iso ?: "N/A"}", telemetryX, telemetryY, paint)
        telemetryY += 45f
        
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(photo.timestamp))
        canvas.drawText("Data: $dateStr", telemetryX, telemetryY, paint)

        // Watermark icon/logo
        paint.color = Color.rgb(211, 47, 47) // Canon red color accent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawCircle(width - 150f, 130f, 40f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(width - 150f, 130f, 12f, paint)

        // Save to file
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
        out.close()
    }
}
