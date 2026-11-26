package com.example.core.insta

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.core.analysis.CullMath
import com.example.core.analysis.ImageAnalyzer
import com.example.core.analysis.Region
import com.example.core.data.Photo
import com.example.core.log.AppLog
import com.example.core.render.BitmapLoader
import com.example.core.render.HorizonDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** How far "Analisar" got, for the progress bar and the time left. */
data class IgProgress(val done: Int, val total: Int, val startedAt: Long, val stage: String) {
    /** Milliseconds left, from the pace so far; null before the first photo. */
    fun left(now: Long): Long? = if (done <= 0 || done >= total) null else (now - startedAt) / done * (total - done)
}

/** The result of one analysis: every candidate scored, and the carousel the picker suggests. */
data class IgResult(val candidates: List<IgCandidate>, val picked: List<IgCandidate>, val style: IgStyle)

/**
 * The Instagram picker on the phone. Reads each candidate at 1024 px, runs the three ML Kit models,
 * measures light and sharpness, scores with the learned weights and the church's style, and builds the
 * carousel. Measures are cached per photo, so a second "Analisar" is quick. Nothing leaves the phone.
 *
 * Learned data lives in `filesDir/ig/`: `modelo.txt` (the weights), `estilo-<congregação>.txt` (the look
 * of its posts) and `voz-<congregação>.txt` (hashtags and photo credit from its captions).
 */
class InstaPicker(
    private val context: Context,
    private val dao: InstaDao,
    private val vision: IgVision,
    private val dir: File = File(context.filesDir, "ig"),
) {
    private val _progress = MutableStateFlow<IgProgress?>(null)
    val progress: StateFlow<IgProgress?> = _progress.asStateFlow()

    /** One analysis at a time: the models are heavy and share the CPU with the downloads. */
    private val running = Mutex()

    // --- Learned data -------------------------------------------------------------------------

    fun weights(): IgWeights = IgWeights.decode(read("modelo.txt")) ?: IgWeights.DEFAULT

    fun forgetChoices() { File(dir, "modelo.txt").delete() }

    private fun styleFile(congregationId: Long?) = "estilo-${congregationId ?: "geral"}.txt"

    /** The congregation's own style, else the general one (references imported without a congregation). */
    fun style(congregationId: Long?): IgStyle =
        IgStyle.decode(read(styleFile(congregationId)))?.takeIf { it.count > 0 }
            ?: IgStyle.decode(read(styleFile(null))) ?: IgStyle.EMPTY

    fun saveStyle(congregationId: Long?, style: IgStyle) = write(styleFile(congregationId), style.encode())

    fun clearStyle(congregationId: Long?) {
        File(dir, styleFile(congregationId)).delete()
        File(dir, voiceFile(congregationId)).delete()
    }

    private fun voiceFile(congregationId: Long?) = "voz-${congregationId ?: "geral"}.txt"

    /** The hashtags and photo credit learned from the church's captions, else the general ones. */
    fun voice(congregationId: Long?): IgExport.Voice? =
        IgExport.Voice.decode(read(voiceFile(congregationId))) ?: IgExport.Voice.decode(read(voiceFile(null)))

    fun saveVoice(congregationId: Long?, voice: IgExport.Voice) = write(voiceFile(congregationId), voice.encode())

    private fun read(name: String): String? = runCatching { File(dir, name).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun write(name: String, text: String) {
        runCatching { dir.mkdirs(); File(dir, name).writeText(text) }.onFailure { AppLog.e("INSTAGRAM", "Não gravou $name", it) }
    }

    // --- Measuring ----------------------------------------------------------------------------

    /** The measures of [photo], from the cache or read now. Null when it can't be read. */
    suspend fun measure(photo: Photo): IgFeatures? {
        dao.feature(photo.id)?.takeIf { it.version == IgFeatures.VERSION }?.let { row -> IgFeatures.decode(row.data)?.let { return it } }
        val uri = photo.displayUri ?: return null
        val bitmap = withContext(Dispatchers.IO) { BitmapLoader.load(context, Uri.parse(uri), EDGE) } ?: return null
        val f = try { measure(bitmap) } finally { bitmap.recycle() }
        dao.putFeature(IgFeatureRow(photo.id, IgFeatures.VERSION, f.encode(), System.currentTimeMillis()))
        return f
    }

    /** Everything on one decoded picture (also used for the church's own posts). */
    suspend fun measure(bitmap: Bitmap): IgFeatures {
        val w = bitmap.width
        val h = bitmap.height
        val seen = vision.look(bitmap)
        return withContext(Dispatchers.Default) {
            val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
            val gray = CullMath.luma(pixels)
            val regions = seen.faces.map { Region((it.box.left * w).toInt(), (it.box.top * h).toInt(), (it.box.right * w).toInt(), (it.box.bottom * h).toInt()) }
            val overall = CullMath.sharpness(gray, w, h)
            val faceSharp = if (regions.isNotEmpty()) CullMath.sharpness(gray, w, h, regions) else null
            val clipped = ImageAnalyzer.measure(pixels, 250).clippedCount * 100f / pixels.size
            val tilt = runCatching { HorizonDetector.detect(pixels, w, h, 8f) }.getOrNull()
            IgFeatures.from(w, h, overall, faceSharp, IgFeatures.tone(pixels), clipped, CullMath.percentDark(gray), seen, tilt)
        }
    }

    // --- Analysing an event -------------------------------------------------------------------

    /**
     * Scores the event's [photos] and picks a carousel of [size]. Returns null when another analysis
     * is running.
     */
    suspend fun analyze(photos: List<Photo>, congregationId: Long?, size: Int = IgCarousel.DEFAULT_SIZE): IgResult? {
        if (!running.tryLock()) return null
        try {
            val started = System.currentTimeMillis()
            val list = IgPrefilter.candidates(photos)
            val weights = weights()
            val style = style(congregationId)
            val out = mutableListOf<IgCandidate>()
            list.forEachIndexed { i, p ->
                _progress.value = IgProgress(i, list.size, started, "Analisando as fotos")
                val f = runCatching { measure(p) }.onFailure { AppLog.e("INSTAGRAM", "Foto ${p.id} não foi analisada", it) }.getOrNull()
                if (f != null) out += IgCandidate(p.id, p.capturedAt, IgScore.rate(f, weights, style), p.dHash, p.burstGroup)
            }
            val picked = IgCarousel.pick(out, size)
            AppLog.i("INSTAGRAM", "IG ${list.size} fotos em ${System.currentTimeMillis() - started} ms · ${picked.size} escolhidas")
            return IgResult(out.sortedByDescending { it.rating.score }, picked, style)
        } finally {
            _progress.value = null
            running.unlock()
        }
    }

    // --- The post being prepared ----------------------------------------------------------------

    private val _draft = MutableStateFlow<IgDraft?>(null)

    /** The post on screen; kept while the app runs, so leaving the screen loses nothing. */
    val draft: StateFlow<IgDraft?> = _draft.asStateFlow()

    fun update(change: (IgDraft) -> IgDraft) { _draft.value = _draft.value?.let(change) }

    /** Takes a photo out of the post: the model learns this kind of frame is not the photographer's pick. */
    fun remove(id: Long) {
        val d = _draft.value ?: return
        d.chosen.firstOrNull { it.photoId == id }?.signals?.let { learn(it, approved = false) }
        _draft.value = d.remove(id)
    }

    /** Puts one of the other good photos in the post: the model learns it was a good pick. */
    fun add(id: Long, max: Int) {
        val d = _draft.value ?: return
        d.others.firstOrNull { it.photoId == id }?.signals?.let { learn(it, approved = true) }
        _draft.value = d.add(id, max)
    }

    private fun learn(signals: FloatArray, approved: Boolean) = write("modelo.txt", weights().learn(signals, approved).encode())

    /** A new draft from an analysis ([aspects]: width / height of each photo). */
    fun startDraft(eventId: Long, result: IgResult, aspects: Map<Long, Float>) {
        _draft.value = IgDraft.from(eventId, result, aspects)
    }

    /** The post already sent for the event, when there is no draft in memory (the app was closed). */
    suspend fun loadSent(eventId: Long, notes: String?, caption: String?, photos: Map<Long, Photo>) {
        if (_draft.value?.eventId == eventId) return
        val picks = dao.picks(eventId)
        if (picks.isEmpty()) { _draft.value = null; return }
        val items = picks.mapNotNull { p ->
            val photo = photos[p.photoId] ?: return@mapNotNull null
            val f = dao.feature(p.photoId)?.let { IgFeatures.decode(it.data) }
            IgItem.of(p, photo.capturedAt, f?.aspect ?: 1.5f)
        }
        _draft.value = IgDraft(eventId, items, emptyList(), caption.orEmpty(), captionEdited = !caption.isNullOrBlank(), notes = IgCaption.Notes.decode(notes))
    }

    fun clearDraft() { _draft.value = null }

    /** Saves the carousel and the caption for the media page ([sentAt] null takes it off the page). */
    suspend fun save(d: IgDraft, sentAt: Long?) {
        dao.replacePicks(d.eventId, d.chosen.mapIndexed { i, it -> it.toPick(d.eventId, i) })
        dao.setPost(d.eventId, d.caption.ifBlank { null }, d.notes.encode(), sentAt)
    }

    /** Takes the post off the media page; the draft stays to be sent again. */
    suspend fun unsend(d: IgDraft) = dao.setPost(d.eventId, d.caption.ifBlank { null }, d.notes.encode(), null)

    companion object {
        const val EDGE = 1024
    }
}
