package com.example.core.insta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.core.log.AppLog
import com.example.core.render.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream
import kotlin.math.max

/** What an import learned. */
data class IgImported(val photos: Int, val style: IgStyle, val voice: IgExport.Voice?)

/**
 * Brings the church's own posts in: from the ZIP of "Baixar suas informações" (photos and captions)
 * or from photos picked in the gallery. Pictures are read small, measured and dropped; only the
 * numbers and the caption habits stay, in `filesDir/ig/`.
 */
class InstaImport(private val context: Context, private val picker: InstaPicker) {

    /** Up to this many posts are measured; a few hundred say all there is about a page's look. */
    private val limit = 300

    suspend fun fromZip(uri: Uri, congregationId: Long?, onProgress: (done: Int, total: Int) -> Unit): IgImported? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // First pass: only the names, to know how many photos there are (the ZIP is read as a stream).
        val total = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                ZipInputStream(s.buffered()).use { zip -> generateSequence { zip.nextEntry }.count { !it.isDirectory && IgExport.isPostImage(it.name) } }
            }
        }.onFailure { AppLog.e("INSTAGRAM", "ZIP não lido", it) }.getOrNull() ?: return@withContext null
        if (total == 0) return@withContext IgImported(0, IgStyle.EMPTY, null)

        val captions = mutableListOf<String>()
        val refs = mutableListOf<IgFeatures>()
        var seen = 0
        resolver.openInputStream(uri)?.use { s ->
            ZipInputStream(s.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    when {
                        IgExport.isPostList(e.name) -> captions += zip.readBytes().toString(Charsets.UTF_8)
                        IgExport.isPostImage(e.name) && seen < limit -> {
                            seen++
                            val bmp = decode(zip.readBytes())
                            if (bmp != null) {
                                try { refs += picker.measure(bmp) } catch (x: Exception) { AppLog.e("INSTAGRAM", "Post ${e.name} não medido", x) } finally { bmp.recycle() }
                            }
                            withContext(Dispatchers.Main) { onProgress(seen, minOf(total, limit)) }
                        }
                    }
                }
            }
        }
        val voice = captions.takeIf { it.isNotEmpty() }?.let { IgExport.voice(it) }
        finish(congregationId, refs, voice)
    }

    suspend fun fromImages(uris: List<Uri>, congregationId: Long?, onProgress: (done: Int, total: Int) -> Unit): IgImported {
        val list = uris.take(limit)
        val refs = mutableListOf<IgFeatures>()
        list.forEachIndexed { i, u ->
            val bmp = withContext(Dispatchers.IO) { BitmapLoader.load(context, u, InstaPicker.EDGE) }
            if (bmp != null) {
                try { refs += picker.measure(bmp) } catch (x: Exception) { AppLog.e("INSTAGRAM", "Foto da galeria não medida", x) } finally { bmp.recycle() }
            }
            onProgress(i + 1, list.size)
        }
        return finish(congregationId, refs, null)
    }

    private fun finish(congregationId: Long?, refs: List<IgFeatures>, voice: IgExport.Voice?): IgImported {
        val style = IgStyle.learn(refs)
        if (style.count > 0) picker.saveStyle(congregationId, style)
        if (voice != null && (voice.hashtags.isNotEmpty() || voice.credit != null)) picker.saveVoice(congregationId, voice)
        AppLog.i("INSTAGRAM", "Estilo aprendido de ${style.count} posts; hashtags ${voice?.hashtags.orEmpty().size}")
        return IgImported(refs.size, style, voice)
    }

    /** A post photo (JPG, WEBP, or HEIC on Android 9+) read with its long edge near [InstaPicker.EDGE]. */
    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= InstaPicker.EDGE) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Removes what was learned for [congregationId] (style and caption habits). */
    fun forget(congregationId: Long?) = picker.clearStyle(congregationId)
}
