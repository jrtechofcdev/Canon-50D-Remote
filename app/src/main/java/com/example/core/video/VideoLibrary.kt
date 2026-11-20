package com.example.core.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.SettingsRepository
import com.example.core.ptp.PtpFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** One video file in the gallery. */
data class VideoItem(
    val uri: String,
    val name: String,
    val size: Long,
    val modified: Long,
) {
    /** Recorded by the phone from the live view (LV_…mp4), as opposed to filmed and saved by the camera. */
    val fromPhone get() = name.startsWith(PHONE_PREFIX)

    /** Magic Lantern RAW video (.MLV/.RAW) needs a computer program; Android can't play it. */
    val playable get() = name.substringAfterLast('.', "").uppercase() !in setOf("MLV", "RAW")

    val extension get() = name.substringAfterLast('.', "").uppercase()

    companion object {
        const val PHONE_PREFIX = "LV_"
    }
}

/**
 * Where videos live: "09 Vídeos" inside the app's folder structure when it was created in
 * Ajustes → Salvamento, otherwise Android/data/<app>/files/Movies/Canon50D/Videos.
 * Kept apart from the photo database on purpose: culling, presets and delivery only ever see photos.
 */
class VideoLibrary(private val context: Context, private val settings: SettingsRepository) {

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    private val thumbs = LruCache<String, Bitmap>(24)

    /** Phone recordings are written here first (MediaMuxer needs a path) and moved when done. */
    fun workDir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Canon50D/Gravando").apply { mkdirs() }

    fun localDir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Canon50D/Videos").apply { mkdirs() }

    /** "09 Vídeos" in the app's folder, created on first use; null when the app folder isn't set. */
    private suspend fun treeDir(create: Boolean): DocumentFile? {
        val appFolder = settings.current().appFolder ?: return null
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(appFolder)) }.getOrNull()
            ?.takeIf { it.exists() && it.canWrite() } ?: return null
        return root.findFile(FOLDER_NAME)?.takeIf { it.isDirectory }
            ?: if (create) root.createDirectory(FOLDER_NAME) else null
    }

    suspend fun refresh(): List<VideoItem> = withContext(Dispatchers.IO) {
        val local = localDir().listFiles().orEmpty()
            .filter { it.isFile && PtpFormat.isVideo(0, it.name) }
            .map { VideoItem(Uri.fromFile(it).toString(), it.name, it.length(), it.lastModified()) }
        val tree = runCatching {
            treeDir(create = false)?.listFiles().orEmpty()
                .filter { it.isFile && PtpFormat.isVideo(0, it.name.orEmpty()) }
                .map { VideoItem(it.uri.toString(), it.name.orEmpty(), it.length(), it.lastModified()) }
        }.onFailure { VideoLog.w("GALERIA", "Não deu para ler a pasta 09 Vídeos: ${it.message}") }.getOrDefault(emptyList())
        val all = (local + tree).sortedByDescending { it.modified }
        _items.value = all
        all
    }

    /**
     * Puts a finished file in the library: copied into "09 Vídeos" when the app folder exists (the temp
     * file is then deleted), else moved into [localDir]. Returns the item as listed.
     */
    suspend fun publish(temp: File, name: String): VideoItem = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val tree = runCatching { treeDir(create = true) }.getOrNull()
        val item = if (tree != null) {
            val doc = tree.createFile("application/octet-stream", name) ?: throw IOException("Não deu para criar $name em 09 Vídeos")
            context.contentResolver.openOutputStream(doc.uri)?.use { out -> temp.inputStream().use { it.copyTo(out, 1 shl 20) } }
                ?: throw IOException("Não deu para abrir $name em 09 Vídeos")
            temp.delete()
            VideoItem(doc.uri.toString(), doc.name ?: name, doc.length(), System.currentTimeMillis())
        } else {
            var target = File(localDir(), name)
            var n = 1
            while (target.exists()) target = File(localDir(), "${name.substringBeforeLast('.')}_${n++}.${name.substringAfterLast('.')}")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = false)
                temp.delete()
            }
            VideoItem(Uri.fromFile(target).toString(), target.name, target.length(), target.lastModified())
        }
        VideoLog.i("GALERIA", "${item.name} guardado (${item.size / 1024} KB) em ${if (tree != null) "09 Vídeos" else "pasta do app"} em ${System.currentTimeMillis() - started} ms")
        refresh()
        item
    }

    suspend fun delete(item: VideoItem): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uri)
        val ok = runCatching {
            if (uri.scheme == "file") File(uri.path!!).delete()
            else DocumentFile.fromSingleUri(context, uri)?.delete() == true
        }.getOrDefault(false)
        VideoLog.i("GALERIA", "${item.name} ${if (ok) "apagado" else "não pôde ser apagado"}")
        thumbs.remove(item.uri)
        refresh()
        ok
    }

    /** A URI other apps can open (players, WhatsApp): app files go through the FileProvider. */
    fun shareableUri(item: VideoItem): Uri {
        val uri = Uri.parse(item.uri)
        return if (uri.scheme == "file") FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path!!)) else uri
    }

    fun mime(item: VideoItem): String = when (item.extension) {
        "MP4", "M4V" -> "video/mp4"
        "MOV" -> "video/quicktime"
        "AVI" -> "video/x-msvideo"
        "3GP" -> "video/3gpp"
        "MKV" -> "video/x-matroska"
        "MPG", "MPEG" -> "video/mpeg"
        else -> "application/octet-stream"
    }

    /** A frame from the start of the video plus its length, or nulls when Android can't read it. */
    suspend fun preview(item: VideoItem): Pair<Bitmap?, Long?> = withContext(Dispatchers.IO) {
        if (!item.playable) return@withContext null to null
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(item.uri)
            if (uri.scheme == "file") retriever.setDataSource(uri.path) else retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val thumb = thumbs.get(item.uri) ?: retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { full -> scaled(full, 360).also { thumbs.put(item.uri, it) } }
            thumb to duration
        } catch (e: Throwable) {
            VideoLog.w("GALERIA", "Sem prévia de ${item.name}: ${e.message}")
            null to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaled(bitmap: Bitmap, maxSide: Int): Bitmap {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        if (small !== bitmap) bitmap.recycle()
        return small
    }

    fun freeBytes(): Long? = runCatching { workDir().usableSpace }.getOrNull()

    companion object {
        const val FOLDER_NAME = "09 Videos"
    }
}
