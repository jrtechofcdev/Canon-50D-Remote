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
 * Learned data lives in `filesDir/ig/`: `modelo.txt` (the weights) and `estilo-<congregação>.txt`.
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

    /** An approve or remove in the module: the weights move a little towards the photographer's eye. */
    fun learn(rating: IgRating, approved: Boolean) {
        write("modelo.txt", weights().learn(rating.signals, approved).encode())
    }

    fun forgetChoices() { File(dir, "modelo.txt").delete() }

    private fun styleFile(congregationId: Long?) = "estilo-${congregationId ?: "geral"}.txt"

    /** The congregation's own style, else the general one (references imported without a congregation). */
    fun style(congregationId: Long?): IgStyle =
        IgStyle.decode(read(styleFile(congregationId)))?.takeIf { it.count > 0 }
            ?: IgStyle.decode(read(styleFile(null))) ?: IgStyle.EMPTY

    fun saveStyle(congregationId: Long?, style: IgStyle) = write(styleFile(congregationId), style.encode())

    fun clearStyle(congregationId: Long?) { File(dir, styleFile(congregationId)).delete() }

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

    /** Learns the church's look from its own posts (pictures already decoded by the importer). */
    suspend fun learnStyle(congregationId: Long?, pictures: Sequence<suspend () -> Bitmap?>, onEach: (Int) -> Unit = {}): IgStyle {
        val refs = mutableListOf<IgFeatures>()
        for (load in pictures) {
            val bmp = runCatching { load() }.getOrNull() ?: continue
            try { refs += measure(bmp) } catch (e: Exception) { AppLog.e("INSTAGRAM", "Referência não lida", e) } finally { bmp.recycle() }
            onEach(refs.size)
        }
        val style = IgStyle.learn(refs)
        if (style.count > 0) saveStyle(congregationId, style)
        return style
    }

    companion object {
        const val EDGE = 1024
    }
}
