package com.example.core.video

import android.content.Context
import com.example.core.camera.CameraManager
import com.example.core.log.AppLog
import com.example.core.ptp.ObjectInfo
import com.example.core.ptp.StreamSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File

data class VideoDownload(val name: String, val received: Long, val total: Long) {
    val progress get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Videos already on the camera's card (.MOV and others): finds them, downloads them in 1 MB pieces
 * and hands them to the [library] for the gallery. Nothing here makes the camera film.
 */
class VideoController(
    context: Context,
    private val camera: CameraManager,
    val library: VideoLibrary,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler("VIDEO"))
    private val prefs = context.getSharedPreferences("video_lab", Context.MODE_PRIVATE)

    private val _cardVideos = MutableStateFlow<List<ObjectInfo>>(emptyList())
    val cardVideos: StateFlow<List<ObjectInfo>> = _cardVideos.asStateFlow()

    private val _download = MutableStateFlow<VideoDownload?>(null)
    val download: StateFlow<VideoDownload?> = _download.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** A card search in progress, for the buttons to wait. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Videos the camera announces are downloaded on their own. */
    var autoDownload: Boolean
        get() = prefs.getBoolean("auto_download", true)
        set(value) { prefs.edit().putBoolean("auto_download", value).apply() }

    @Volatile private var cancelDownload = false

    init {
        scope.launch {
            camera.newObjects.collect { obj ->
                if (!obj.isVideo) return@collect
                VideoLog.i("CAMERA", "A câmera anunciou um vídeo: ${obj.filename} (${obj.size / 1024} KB, formato 0x%04X)".format(obj.format))
                if (autoDownload) downloadVideo(ObjectInfo(obj.handle, obj.storageId, obj.format, obj.size, obj.parent, obj.filename, ""))
            }
        }
        scope.launch(Dispatchers.IO) { runCatching { library.refresh() } }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    /** Lists the newest files on the card and keeps the videos among them. */
    suspend fun scanCard(limit: Int = SCAN_LIMIT): List<ObjectInfo> {
        _busy.value = "Procurando vídeos no cartão…"
        try {
            if (!camera.supportsCardListing) throw IllegalStateException("A câmera não permite listar o cartão pela USB")
            var seen = 0
            val started = System.currentTimeMillis()
            val files = camera.listCardFiles(newestFirst = true, stopAfterOld = 1, isOld = { seen++ >= limit })
            val videos = files.filter { it.isVideo }.sortedByDescending { it.filename }
            VideoLog.i("CARTAO", "${files.size} arquivo(s) lidos em ${System.currentTimeMillis() - started} ms, ${videos.size} vídeo(s)" +
                if (videos.isNotEmpty()) ": " + videos.joinToString { "${it.filename} ${sizeLabel(it.size)}" } else "")
            _cardVideos.value = videos
            return videos
        } finally {
            _busy.value = null
        }
    }

    fun downloadVideo(obj: ObjectInfo) = scope.launch(Dispatchers.IO) {
        if (_download.value != null) {
            say("Já há um vídeo baixando. Espere terminar.")
            return@launch
        }
        cancelDownload = false
        val temp = File(library.workDir(), obj.filename)
        val started = System.currentTimeMillis()
        VideoLog.i("DOWNLOAD", "${obj.filename}: ${sizeLabel(obj.size)}, formato 0x%04X · %s".format(obj.format, VideoLog.resources(library.freeBytes())))
        _download.value = VideoDownload(obj.filename, 0, obj.size)
        try {
            var lastPercent = -1
            BufferedOutputStream(temp.outputStream(), 1 shl 20).use { out ->
                camera.downloadInChunks(obj.handle, obj.size, StreamSink(out) { received, total ->
                    val size = if (total > 0) total else obj.size
                    val percent = if (size > 0) (received * 100 / size).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _download.value = VideoDownload(obj.filename, received, size)
                    }
                }, keepGoing = { !cancelDownload })
            }
            val ms = (System.currentTimeMillis() - started).coerceAtLeast(1)
            VideoLog.i("DOWNLOAD", "%s recebido em %.1f s (%.1f MB/s)".format(obj.filename, ms / 1000f, temp.length() / 1_048_576f / (ms / 1000f)))
            library.publish(temp, obj.filename)
            say("${obj.filename} baixado")
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Throwable) {
            temp.delete()
            VideoLog.e("DOWNLOAD", "${obj.filename} não foi baixado", e)
            say("${obj.filename} não foi baixado: ${e.message}")
        } finally {
            _download.value = null
        }
    }

    fun cancelDownload() {
        cancelDownload = true
        VideoLog.i("DOWNLOAD", "Cancelamento pedido")
    }

    companion object {
        private const val SCAN_LIMIT = 80

        fun sizeLabel(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            else -> "${bytes / 1024} KB"
        }
    }
}
