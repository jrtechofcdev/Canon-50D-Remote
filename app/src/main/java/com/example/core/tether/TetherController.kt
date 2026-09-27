package com.example.core.tether

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.core.camera.CameraManager
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.data.SettingsRepository
import com.example.core.ptp.EosEventRecord
import com.example.core.ptp.StreamSink
import com.example.core.storage.Cr2Preview
import com.example.core.storage.FileKind
import com.example.core.storage.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.concurrent.atomic.AtomicInteger

data class Transfer(val fileName: String, val received: Long, val total: Long, val queued: Int) {
    val progress: Float get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Auto-save: every new frame the camera announces is downloaded, written to the RAW or JPG folder
 * picked in Ajustes, and recorded so the preview can show it.
 */
class TetherController(
    private val context: Context,
    private val camera: CameraManager,
    private val settingsRepository: SettingsRepository,
    private val store: PhotoStore,
    private val dao: PhotoDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<EosEventRecord.ObjectAdded>(Channel.UNLIMITED)
    private val queued = AtomicInteger(0)

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    private val _received = MutableStateFlow(0)
    val receivedThisSession: StateFlow<Int> = _received.asStateFlow()

    private val _saved = MutableSharedFlow<Photo>(extraBufferCapacity = 16)
    val saved: SharedFlow<Photo> = _saved.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        scope.launch {
            camera.newObjects.collect {
                queued.incrementAndGet()
                queue.send(it)
            }
        }
        scope.launch {
            for (obj in queue) {
                try {
                    process(obj)
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed", e)
                    _errors.tryEmit("Falha ao salvar ${obj.filename}: ${e.message}")
                } finally {
                    queued.decrementAndGet()
                    _transfer.value = null
                }
            }
        }
    }

    private suspend fun process(obj: EosEventRecord.ObjectAdded) {
        val settings = settingsRepository.current()
        val kind = when {
            obj.isRaw -> FileKind.RAW
            obj.isJpeg -> FileKind.JPG
            else -> return
        }
        if (kind == FileKind.RAW && !settings.downloadMode.raw) return
        if (kind == FileKind.JPG && !settings.downloadMode.jpg) return

        val session = settings.sessionKey
        val cameraBase = obj.filename.substringBeforeLast('.').ifBlank { "IMG_${obj.handle}" }
        val extension = obj.filename.substringAfterLast('.', if (kind == FileKind.RAW) "CR2" else "JPG")

        // RAW and JPG of one frame arrive back to back with the same camera name; pair them in one row.
        val existing = dao.findLatest(session, cameraBase)?.takeIf {
            System.currentTimeMillis() - it.capturedAt < PAIR_WINDOW_MS &&
                (if (kind == FileKind.RAW) it.rawUri == null else it.jpgUri == null)
        }
        val savedBase = existing?.savedBase ?: if (settings.renameWithSession && settings.sessionName.isNotBlank()) {
            "%s_%04d".format(settings.sessionName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-"), dao.countInSession(session) + 1)
        } else cameraBase
        val fileName = "$savedBase.$extension"

        val target = store.create(kind, fileName, settings)
        try {
            BufferedOutputStream(target.stream, 256 * 1024).use { out ->
                camera.download(obj.handle, StreamSink(out) { received, total ->
                    _transfer.value = Transfer(fileName, received, if (total > 0) total else obj.size, queued.get() - 1)
                })
            }
        } catch (e: Exception) {
            store.delete(target.uri.toString())
            throw e
        }

        val uri = target.uri.toString()
        val exif = readExif(target.uri)
        var photo = existing ?: Photo(session = session, baseName = cameraBase, savedBase = savedBase, capturedAt = System.currentTimeMillis())
        photo = when (kind) {
            FileKind.JPG -> photo.copy(jpgUri = uri, jpgSize = obj.size)
            FileKind.RAW -> photo.copy(rawUri = uri, rawSize = obj.size)
        }
        photo = photo.copy(
            aperture = exif?.aperture ?: photo.aperture,
            shutter = exif?.shutter ?: photo.shutter,
            iso = exif?.iso ?: photo.iso,
        )
        // Until (or unless) a JPG arrives, show the JPEG that Canon embeds in the CR2.
        if (kind == FileKind.RAW && photo.jpgUri == null) {
            photo = photo.copy(previewPath = extractPreview(target.uri, session, savedBase))
        }

        val id = if (photo.id == 0L) dao.insert(photo) else photo.id.also { dao.update(photo) }
        _received.update { it + 1 }
        _saved.tryEmit(photo.copy(id = id))
    }

    private suspend fun extractPreview(uri: Uri, session: String, base: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val jpeg = context.contentResolver.openInputStream(uri)?.use { Cr2Preview.extract(it) } ?: return@runCatching null
            val file = store.previewFile(session, base)
            file.writeBytes(jpeg)
            file.absolutePath
        }.getOrNull()
    }

    private data class ExifSummary(val aperture: String?, val shutter: String?, val iso: String?)

    private fun readExif(uri: Uri): ExifSummary? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
            ExifSummary(
                aperture = if (fNumber > 0) "f/%s".format(trim(fNumber)) else null,
                shutter = when {
                    exposure <= 0 -> null
                    exposure >= 0.3 -> "${trim(exposure)}\""
                    else -> "1/${Math.round(1 / exposure)}"
                },
                iso = if (iso > 0) "ISO $iso" else null,
            )
        }
    }.getOrNull()

    private fun trim(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    fun resetSessionCounter() {
        _received.value = 0
    }

    companion object {
        private const val TAG = "TetherController"
        private const val PAIR_WINDOW_MS = 120_000L
    }
}
