package com.example.core.insta

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/** The inputs of the score, each 0..1, with the words shown when it helped. */
enum class IgSignal(val reason: String) {
    SHARP("Nítida"),
    LIGHT("Boa luz"),
    EYES("Olhos abertos"),
    PEOPLE("Pessoas"),
    SMILE("Sorrisos"),
    FACING("Rostos de frente"),
    CLOSE("Bem perto"),
    HANDS("Mãos erguidas"),
    PRAYER("Momento de oração"),
    MIC("Ministração"),
    MUSIC("Louvor"),
    CROWD("Igreja cheia"),
    FEED("Cabe no feed"),
    STORY("Cabe no Stories"),
    THIRDS("Boa composição"),
    LEVEL("Reta"),
    STYLE("Com a cara da igreja"),
}

/** A scored photo: 0..100, the moment, the words that explain it, and the crops. */
data class IgRating(
    val score: Float,
    val moment: IgMoment,
    val reasons: List<String>,
    val warnings: List<String>,
    val feed: IgFrame,
    val story: IgFrame,
    val signals: FloatArray,
)

/**
 * Weights of the score, as in a small logistic model: z = bias + Σ weight × signal, score = 100·σ(z).
 * The defaults are a church photographer's eye (moment and emotion first, then technique). Each
 * "approve" or "remove" in the module nudges them ([learn]), pulled back towards the defaults so a
 * handful of choices cannot wreck it.
 */
data class IgWeights(val bias: Float, val w: FloatArray, val lessons: Int = 0) {

    fun z(x: FloatArray): Float {
        var z = bias
        for (i in x.indices) z += w[i] * x[i]
        return z
    }

    fun score(x: FloatArray): Float = 100f / (1f + exp(-z(x)))

    /** One choice: [approved] true when kept, false when removed. */
    fun learn(x: FloatArray, approved: Boolean, rate: Float = 0.15f): IgWeights {
        val p = 1f / (1f + exp(-z(x)))
        val err = (if (approved) 1f else 0f) - p
        val nw = FloatArray(w.size) { i -> w[i] + rate * (err * x[i] - PULL * (w[i] - DEFAULT.w[i])) }
        val nb = bias + rate * (err - PULL * (bias - DEFAULT.bias))
        return IgWeights(nb, nw, lessons + 1)
    }

    fun encode(): String = buildString {
        appendLine("lessons=$lessons")
        appendLine(String.format(Locale.US, "bias=%.5f", bias))
        IgSignal.entries.forEachIndexed { i, s -> appendLine(String.format(Locale.US, "%s=%.5f", s.name, w[i])) }
    }

    override fun equals(other: Any?) = other is IgWeights && other.bias == bias && other.w.contentEquals(w) && other.lessons == lessons
    override fun hashCode() = w.contentHashCode() * 31 + bias.hashCode()

    companion object {
        private const val PULL = 0.05f

        val DEFAULT = IgWeights(
            bias = -8f,
            w = FloatArray(IgSignal.entries.size) { i ->
                when (IgSignal.entries[i]) {
                    IgSignal.SHARP -> 2.0f
                    IgSignal.LIGHT -> 1.2f
                    IgSignal.EYES -> 1.6f
                    IgSignal.PEOPLE -> 0.6f
                    IgSignal.SMILE -> 1.4f
                    IgSignal.FACING -> 0.6f
                    IgSignal.CLOSE -> 0.8f
                    IgSignal.HANDS -> 1.6f
                    IgSignal.PRAYER -> 1.6f
                    IgSignal.MIC -> 0.8f
                    IgSignal.MUSIC -> 0.8f
                    IgSignal.CROWD -> 0.6f
                    IgSignal.FEED -> 0.8f
                    IgSignal.STORY -> 0.3f
                    IgSignal.THIRDS -> 0.4f
                    IgSignal.LEVEL -> 0.4f
                    IgSignal.STYLE -> 1.2f
                }
            },
        )

        fun decode(text: String?): IgWeights? {
            if (text.isNullOrBlank()) return null
            val map = text.lineSequence().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return runCatching {
                IgWeights(
                    map.getValue("bias").toFloat(),
                    FloatArray(IgSignal.entries.size) { i -> map[IgSignal.entries[i].name]?.toFloat() ?: DEFAULT.w[i] },
                    map["lessons"]?.toInt() ?: 0,
                )
            }.getOrNull()
        }
    }
}

object IgScore {

    fun signals(f: IgFeatures, style: IgStyle = IgStyle.EMPTY, feed: IgFrame = IgCrop.feed(f, style.feedRatio), story: IgFrame = IgCrop.story(f)): FloatArray {
        val faces = f.faces
        val x = FloatArray(IgSignal.entries.size)
        fun set(s: IgSignal, v: Float) { x[s.ordinal] = v.coerceIn(0f, 1f) }
        set(IgSignal.SHARP, (f.faceSharpness ?: f.sharpness) / 100f)
        set(IgSignal.LIGHT, 1f - (f.clipped / 5f).coerceIn(0f, 1f) * 0.6f - (f.dark / 40f).coerceIn(0f, 1f) * 0.4f)
        // Eyes closed in prayer are the moment, not a blink.
        set(IgSignal.EYES, if (faces.isEmpty() || f.praying) 1f else 1f - f.eyesClosed.toFloat() / faces.size)
        set(IgSignal.PEOPLE, if (faces.isNotEmpty()) min(1f, 0.5f + faces.size / 6f) else if (f.people > 0) 0.4f else 0f)
        val smiles = faces.mapNotNull { it.smile }
        set(IgSignal.SMILE, if (smiles.isEmpty()) 0f else smiles.sortedDescending().take(3).average().toFloat())
        set(IgSignal.FACING, if (faces.isEmpty()) 0f else faces.count { abs(it.yaw) < 25f }.toFloat() / faces.size)
        // A face of about 3 % of the frame is a person you can read; much smaller is far away, much bigger a headshot.
        set(IgSignal.CLOSE, if (faces.isEmpty()) 0f else 1f - abs(ln(f.largestFace / 0.03f)) / 3f)
        set(IgSignal.HANDS, min(1f, f.handsRaised / 2f))
        set(IgSignal.PRAYER, if (f.praying) 1f else 0f)
        set(IgSignal.MIC, if (f.atMic > 0 && faces.size in 1..3) 1f else 0f)
        set(IgSignal.MUSIC, f.label("musical instrument", "musician", "guitar", "drum", "piano", "microphone", "singer", "concert", "music", "keyboard"))
        set(IgSignal.CROWD, maxOf(f.label("crowd", "audience"), min(1f, faces.size / 10f)))
        set(IgSignal.FEED, if (feed.fits) 1f else 0f)
        set(IgSignal.STORY, if (story.fits) 1f else 0f)
        set(IgSignal.THIRDS, IgCrop.thirds(f, feed.spec))
        set(IgSignal.LEVEL, f.tilt?.let { 1f - abs(it) / 5f } ?: 1f)
        set(IgSignal.STYLE, style.match(f))
        return x
    }

    fun rate(f: IgFeatures, weights: IgWeights = IgWeights.DEFAULT, style: IgStyle = IgStyle.EMPTY): IgRating {
        val feed = IgCrop.feed(f, style.feedRatio)
        val story = IgCrop.story(f)
        val x = signals(f, style, feed, story)
        val score = weights.score(x)
        val reasons = IgSignal.entries
            .filter { x[it.ordinal] >= 0.6f && weights.w[it.ordinal] > 0.3f }
            .filter { it !in QUIET && (it != IgSignal.STYLE || style.count >= IgStyle.MIN_COUNT) }
            .sortedByDescending { weights.w[it.ordinal] * x[it.ordinal] }
            .take(4)
            .map { if (it == IgSignal.SHARP && f.faceSharpness != null) "Nítida no rosto" else it.reason }
        val warnings = buildList {
            if (f.eyesClosed > 0 && !f.praying) add(if (f.eyesClosed == f.faces.size) "Olhos fechados" else "Alguém de olhos fechados")
            if ((f.tilt ?: 0f).let { abs(it) >= 2f }) add("Horizonte torto")
            if (!feed.fits) add("O recorte do feed corta alguém")
            if (x[IgSignal.SHARP.ordinal] < 0.5f) add("Pouco nítida")
        }
        return IgRating(score, f.moment, reasons, warnings, feed, story, x)
    }

    /** Signals that are expected and would only fill the list of reasons. */
    private val QUIET = setOf(IgSignal.EYES, IgSignal.FEED, IgSignal.LEVEL, IgSignal.PEOPLE, IgSignal.LIGHT)
}
