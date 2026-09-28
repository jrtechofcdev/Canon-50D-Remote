package com.example.core.analysis

import android.content.Context
import android.net.Uri
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.data.SettingsRepository
import com.example.core.render.BitmapLoader
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
import kotlin.math.abs

/** What the analysis found worth telling the photographer right away. */
data class CullWarning(val photo: Photo, val blurry: Boolean, val eyesClosed: Int, val faces: Int)

/**
 * Local "AI" culling. Each frame is analysed once, in the background, one at a time so downloads keep
 * priority: sharpness, faces and closed eyes (ML Kit, offline), exposure, a similarity hash, then
 * a 0-100 score. Near-identical frames are grouped and the best of each group is suggested.
 */
class Culler(
    private val context: Context,
    private val dao: PhotoDao,
    private val settings: SettingsRepository,
    private val faces: FaceAnalyzer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Long>(Channel.UNLIMITED)

    private val _warnings = MutableSharedFlow<CullWarning>(extraBufferCapacity = 8)
    val warnings: SharedFlow<CullWarning> = _warnings.asSharedFlow()

    /** Frames waiting for analysis, for the "Analisando…" line in the culling screen. */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    init {
        scope.launch {
            for (id in queue) {
                runCatching { dao.get(id)?.let { analyze(it, alert = true) } }
                _pending.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    fun enqueue(id: Long) {
        _pending.update { it + 1 }
        queue.trySend(id)
    }

    /** Older frames (from before 2.6, or received with the analysis off) that still have no score. */
    suspend fun analyzeMissing(session: String) {
        dao.inSession(session).filter { it.score == null && it.displayUri != null }.forEach { enqueue(it.id) }
    }

    private suspend fun analyze(photo: Photo, alert: Boolean) {
        val cfg = settings.current()
        val uri = photo.displayUri ?: return
        val bitmap = BitmapLoader.load(context, Uri.parse(uri), EDGE) ?: return
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val found = if (cfg.eyesCheck) faces.detect(bitmap) else null
        bitmap.recycle()

        val gray = CullMath.luma(pixels)
        val overall = CullMath.sharpness(gray, w, h)
        val faceRegions = found.orEmpty().map { it.region }
        val faceSharpness = if (faceRegions.isNotEmpty()) CullMath.sharpness(gray, w, h, faceRegions) else null
        val decisive = faceSharpness ?: overall
        val closed = found.orEmpty().count { it.eyesClosed }
        val clipped = ImageAnalyzer.measure(pixels, 250).clippedCount * 100f / pixels.size
        val score = CullMath.score(overall, found?.size, closed, clipped, CullMath.percentDark(gray), faceSharpness)
        val blurry = decisive < cfg.blurThreshold
        val hash = CullMath.dHash(gray, w, h)

        dao.setAnalysis(photo.id, CullMath.rounded(decisive), blurry, found?.size, closed, CullMath.rounded(score), hash)
        regroup(photo.session)

        if (alert && ((cfg.blurAlert && blurry) || (cfg.eyesCheck && closed > 0))) {
            _warnings.tryEmit(CullWarning(photo, cfg.blurAlert && blurry, closed, found?.size ?: 0))
        }
    }

    /**
     * Groups consecutive near-identical frames of the session (same pose, a burst) and marks the best
     * of each group as suggested. Single frames are suggested when they are simply good.
     */
    private suspend fun regroup(session: String) {
        val frames = dao.inSession(session).filter { it.score != null }
        var group = mutableListOf<Photo>()
        val groups = mutableListOf<List<Photo>>()
        for (p in frames) {
            val prev = group.lastOrNull()
            if (prev != null && abs(p.capturedAt - prev.capturedAt) <= BURST_WINDOW_MS && CullMath.similar(prev.dHash, p.dHash)) {
                group += p
            } else {
                if (group.isNotEmpty()) groups += group
                group = mutableListOf(p)
            }
        }
        if (group.isNotEmpty()) groups += group

        for (g in groups) {
            if (g.size == 1) {
                val p = g.first()
                val good = (p.score ?: 0f) >= GOOD_SCORE && !p.blurry && p.eyesClosed == 0
                if (p.burstGroup != null || p.suggested != good) dao.setBurst(p.id, null, good)
            } else {
                val best = g.maxBy { it.score ?: 0f }
                val id = g.first().id
                g.forEach { p ->
                    val suggested = p.id == best.id && !best.blurry
                    if (p.burstGroup != id || p.suggested != suggested) dao.setBurst(p.id, id, suggested)
                }
            }
        }
    }

    companion object {
        /** Analysis size: enough for focus on faces, light on a 4 GB phone. */
        const val EDGE = 1024
        const val GOOD_SCORE = 70f
        /** Frames of one pose usually arrive within this window (downloads add a few seconds per frame). */
        const val BURST_WINDOW_MS = 20_000L
    }
}
