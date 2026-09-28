package com.example.core.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.data.SettingsRepository
import com.example.core.preset.PresetRepository
import com.example.core.preset.PresetSettings
import com.example.core.storage.FileKind
import com.example.core.storage.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A photo's "look": the preset applied to it, or its own calibration. Renders looks for the screen
 * (cached, long edge up to [PREVIEW_EDGE]) and writes the full-size edited JPG copy.
 */
class Looks(
    private val context: Context,
    private val engine: PresetEngine,
    private val presets: PresetRepository,
    private val photos: PhotoDao,
    private val store: PhotoStore,
    private val settings: SettingsRepository,
) {
    // Rendered previews, keyed by photo, settings and size. ~12 MB each at most; 6 of them fit a 4 GB phone well.
    private val cache = object : LruCache<String, Bitmap>(6) {}
    private val copyLock = Mutex()

    /** The settings that apply to [photo], or null when it has no look. */
    suspend fun settingsFor(photo: Photo): PresetSettings? {
        photo.adjust?.let { return PresetSettings.decode(it) }
        val id = photo.presetId ?: return null
        return presets.get(id)?.settings
    }

    /** The photo rendered with its look, or null when it has none (show the file itself then). */
    suspend fun display(photo: Photo, maxEdge: Int = PREVIEW_EDGE): Bitmap? {
        val s = settingsFor(photo) ?: return null
        val source = photo.displayUri ?: return null
        return render(source, s, maxEdge)
    }

    /** Any photo with any settings, e.g. the editor preview or a preset's thumbnail. */
    suspend fun render(sourceUri: String, s: PresetSettings, maxEdge: Int): Bitmap? {
        val key = "$sourceUri|${s.encode().hashCode()}|$maxEdge"
        cache.get(key)?.let { return it }
        val base = base(sourceUri, maxEdge) ?: return null
        val out = engine.render(base, RenderParams.from(s))
        cache.put(key, out)
        return out
    }

    private val baseCache = LruCache<String, Bitmap>(3)

    // Small renders for lists (preset tab, strips): ~270 KB each, capped by bytes so they never push out the previews.
    private val thumbs = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** [sourceUri] with [s] applied, long edge [THUMB_EDGE]. */
    suspend fun thumb(sourceUri: String, s: PresetSettings): Bitmap? {
        val key = "$sourceUri|${s.encode().hashCode()}"
        thumbs.get(key)?.let { return it }
        val base = base(sourceUri, THUMB_EDGE) ?: return null
        return engine.render(base, RenderParams.from(s)).also { thumbs.put(key, it) }
    }

    /** The decoded photo before any look, cached so dragging a slider does not decode the file again. */
    suspend fun base(sourceUri: String, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$sourceUri|$maxEdge"
        baseCache.get(key) ?: BitmapLoader.load(context, Uri.parse(sourceUri), maxEdge)?.also { baseCache.put(key, it) }
    }

    /**
     * Writes (or rewrites) the full-size JPG with the look applied into the "editadas" folder and records it
     * on the photo. The original file is never touched. Returns the new URI, or null if there is no look.
     */
    suspend fun writeEditedCopy(photo: Photo, presetName: String?): String? = copyLock.withLock {
        val cfg = settings.current()
        val s = settingsFor(photo) ?: return@withLock null
        val source = photo.displayUri ?: return@withLock null
        val full = withContext(Dispatchers.IO) { BitmapLoader.load(context, Uri.parse(source), 0) } ?: return@withLock null
        val rendered = engine.render(full, RenderParams.from(s))
        full.recycle()
        val suffix = presetName?.let { "_" + it.replace(Regex("[\\\\/:*?\"<>|]"), "-").replace(' ', '-') } ?: "_editada"
        val uri = withContext(Dispatchers.IO) {
            photo.editedUri?.let(store::delete)
            val target = store.create(FileKind.EDITED, "${photo.savedBase}$suffix.jpg", cfg, photo.moment)
            target.stream.use { rendered.compress(Bitmap.CompressFormat.JPEG, cfg.editedQuality.coerceIn(60, 100), it) }
            target.uri.toString()
        }
        rendered.recycle()
        photos.setEdited(photo.id, uri)
        uri
    }

    /**
     * Gives [photo] a look (a preset, optionally with its own calibration in [adjust]) or removes it (both null).
     * Rewrites the edited copy when that is on; a copy left from the previous look is removed so it never lies.
     */
    suspend fun setLook(photo: Photo, presetId: Long?, adjust: String?) {
        photos.setLook(photo.id, presetId, adjust)
        val updated = photo.copy(presetId = presetId, adjust = adjust)
        if (settings.current().saveEditedCopy && (presetId != null || adjust != null)) {
            writeEditedCopy(updated, presetId?.let { presets.get(it)?.name })
        } else if (photo.editedUri != null) {
            withContext(Dispatchers.IO) { store.delete(photo.editedUri) }
            photos.setEdited(photo.id, null)
        }
    }

    companion object {
        const val PREVIEW_EDGE = 2048
        const val EDITOR_EDGE = 1440
        const val THUMB_EDGE = 320
    }
}
