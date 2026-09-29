package com.example.core.tether

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionState
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.data.SettingsRepository
import com.example.core.ptp.EosEventRecord
import com.example.core.ptp.ObjectInfo
import com.example.core.ptp.StreamSink
import com.example.core.storage.Cr2Preview
import com.example.core.storage.FileKind
import com.example.core.storage.PhotoStore
import com.example.core.render.Looks
import com.example.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val looks: Looks,
    private val presetName: suspend (Long) -> String?,
    private val location: () -> Pair<Double, Double>? = { null },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + AppLog.handler("DOWNLOAD"))
    private class Job(val obj: EosEventRecord.ObjectAdded, val force: Boolean, val attempt: Int = 0)

    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val queued = AtomicInteger(0)

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    private val _received = MutableStateFlow(0)
    val receivedThisSession: StateFlow<Int> = _received.asStateFlow()

    private val _saved = MutableSharedFlow<Photo>(extraBufferCapacity = 16)
    val saved: SharedFlow<Photo> = _saved.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Frames found on the card after a reconnect, waiting for the user to confirm (when "perguntar antes" is on). */
    private val _pendingOnCard = MutableStateFlow<List<ObjectInfo>>(emptyList())
    val pendingOnCard: StateFlow<List<ObjectInfo>> = _pendingOnCard.asStateFlow()

    init {
        scope.launch {
            camera.newObjects.collect { enqueue(it, force = false) }
        }
        scope.launch {
            camera.state.collect { if (it is ConnectionState.Connected) checkForMissedFrames() }
        }
        scope.launch {
            for (job in queue) {
                try {
                    process(job.obj, job.force)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Throwable, not Exception: an OutOfMemoryError here used to end this loop (and the app).
                    AppLog.e("DOWNLOAD", "Falha ao salvar ${job.obj.filename} (tentativa ${job.attempt + 1}) · ${AppLog.memory()}", e)
                    if (job.attempt < MAX_RETRIES && camera.state.value is ConnectionState.Connected) {
                        // Usually a busy camera or a brief USB hiccup: try again after the queue drains a bit.
                        delay(1_000L * (job.attempt + 1))
                        queued.incrementAndGet()
                        queue.send(Job(job.obj, job.force, job.attempt + 1))
                    } else {
                        _errors.tryEmit("Falha ao salvar ${job.obj.filename}: ${e.message}")
                    }
                } finally {
                    queued.decrementAndGet()
                    _transfer.value = null
                }
            }
        }
    }

    /** Adds a frame to the download queue. [force] ignores the RAW/JPG filter (manual picks from the card). */
    fun enqueue(obj: EosEventRecord.ObjectAdded, force: Boolean) {
        queued.incrementAndGet()
        queue.trySend(Job(obj, force))
    }

    fun downloadPending() {
        _pendingOnCard.value.forEach { enqueue(it.asObjectAdded(), force = false) }
        _pendingOnCard.value = emptyList()
    }

    fun dismissPending() {
        _pendingOnCard.value = emptyList()
    }

    /**
     * After a reconnect, finds frames shot while the cable was out (or whose download failed):
     * files on the card numbered after the newest one already received in this session.
     */
    private suspend fun checkForMissedFrames() {
        val settings = settingsRepository.current()
        if (!settings.autoResend || !camera.supportsCardListing) return
        val sessionPhotos = dao.inSession(settings.sessionKey)
        val lastNumber = sessionPhotos.flatMap { listOfNotNull(it.rawCameraName, it.jpgCameraName) }
            .mapNotNull(::frameNumber).maxOrNull() ?: return
        val known = dao.knownCameraNames().toSet()
        val missing = runCatching { camera.listCardFiles() }.getOrElse { return }
            .filter { file ->
                val n = frameNumber(file.filename) ?: return@filter false
                n > lastNumber && file.filename !in known &&
                    ((file.isRaw && settings.downloadMode.raw) || (file.isJpeg && settings.downloadMode.jpg))
            }
        if (missing.isEmpty()) return
        if (settings.resendAsk) {
            _pendingOnCard.value = missing
        } else {
            _errors.tryEmit("Baixando ${missing.size} arquivo(s) feitos enquanto o cabo estava solto")
            missing.forEach { enqueue(it.asObjectAdded(), force = false) }
        }
    }

    private suspend fun process(obj: EosEventRecord.ObjectAdded, force: Boolean) {
        val settings = settingsRepository.current()
        val kind = when {
            obj.isRaw -> FileKind.RAW
            obj.isJpeg -> FileKind.JPG
            else -> return
        }
        if (!force && kind == FileKind.RAW && !settings.downloadMode.raw) return
        if (!force && kind == FileKind.JPG && !settings.downloadMode.jpg) return

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

        // RAW and JPG of one frame stay in the same moment even if it changed between the two files.
        val moment = existing?.moment ?: settings.currentMoment.takeIf { settings.momentsEnabled && it.isNotBlank() }
        val target = store.create(kind, fileName, settings, moment)
        val started = System.currentTimeMillis()
        AppLog.i("DOWNLOAD", "Baixando ${obj.filename} (${obj.size / 1024} KB) como $fileName")
        try {
            BufferedOutputStream(target.stream, 256 * 1024).use { out ->
                var lastPercent = -1
                camera.download(obj.handle, StreamSink(out) { received, total ->
                    // Progress changes only once per percent: every USB chunk used to redraw the screen and the notification.
                    val size = if (total > 0) total else obj.size
                    val percent = if (size > 0) (received * 100 / size).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _transfer.value = Transfer(fileName, received, size, queued.get() - 1)
                    }
                })
            }
        } catch (e: Exception) {
            store.delete(target.uri.toString())
            throw e
        }

        val uri = target.uri.toString()
        AppLog.i("DOWNLOAD", "$fileName salvo em ${System.currentTimeMillis() - started} ms")
        val exif = readExif(target.uri)
        var photo = existing ?: Photo(
            session = session, baseName = cameraBase, savedBase = savedBase, capturedAt = System.currentTimeMillis(), moment = moment,
        ).let { p -> if (settings.gpsEnabled) location()?.let { (lat, lon) -> p.copy(lat = lat, lon = lon) } ?: p else p }
        photo = when (kind) {
            FileKind.JPG -> photo.copy(jpgUri = uri, jpgSize = obj.size, jpgCameraName = obj.filename)
            FileKind.RAW -> photo.copy(rawUri = uri, rawSize = obj.size, rawCameraName = obj.filename)
            FileKind.EDITED, FileKind.SELECTED -> photo // never downloaded from the camera
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

        // The session look goes on every new frame; the file itself stays as the camera made it.
        if (settings.applyPresetToNew && settings.sessionPresetId > 0 && photo.presetId == null) {
            photo = photo.copy(presetId = settings.sessionPresetId, adjust = settings.sessionAdjust.ifBlank { null })
        }

        val id = if (photo.id == 0L) dao.insert(photo) else photo.id.also { dao.update(photo) }
        _received.update { it + 1 }
        _saved.tryEmit(photo.copy(id = id))

        // Edited copy from the JPG (or from the RAW's embedded preview when no JPG is coming).
        val copySource = kind == FileKind.JPG || !settings.downloadMode.jpg
        if (settings.saveEditedCopy && photo.presetId != null && copySource) {
            scope.launch {
                runCatching {
                    val name = photo.presetId?.let { presetName(it) }
                    looks.writeEditedCopy(photo.copy(id = id), name)
                }.onFailure {
                    AppLog.e("EDITADA", "Cópia editada de ${photo.savedBase} falhou · ${AppLog.memory()}", it)
                    _errors.tryEmit("Não foi possível salvar a cópia editada de ${photo.savedBase}: ${it.message}")
                }
            }
        }
    }

    private suspend fun extractPreview(uri: Uri, session: String, base: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val jpeg = context.contentResolver.openInputStream(uri)?.use { Cr2Preview.extract(it) } ?: return@runCatching null
            val file = store.previewFile(session, base)
            file.writeBytes(jpeg)
            // The JPEG inside the CR2 has no orientation of its own: the camera's (vertical photo or not)
            // lives in the CR2 header. Without copying it, vertical frames showed lying down.
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                runCatching {
                    ExifInterface(file.absolutePath).apply {
                        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                        saveAttributes()
                    }
                }.onFailure { AppLog.w("DOWNLOAD", "Rotação da prévia do RAW não gravada", it) }
            }
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
        private const val MAX_RETRIES = 2

        /** 1234 from IMG_1234.CR2 or _MG_1234.JPG. */
        fun frameNumber(name: String): Int? = Regex("(\\d{4})\\.[A-Za-z0-9]+$").find(name)?.groupValues?.get(1)?.toIntOrNull()
        private const val PAIR_WINDOW_MS = 120_000L
    }
}
