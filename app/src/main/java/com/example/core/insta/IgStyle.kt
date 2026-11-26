package com.example.core.insta

import com.example.core.render.CropRatio
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The look of what the church already posts, learned from its own Instagram photos: light, colour,
 * how many people and how close, and which moments. A new photo close to that gets a bonus.
 */
data class IgStyle(
    val count: Int,
    val mean: FloatArray,
    val spread: FloatArray,
    /** Share of each [IgMoment] among the references. */
    val moments: FloatArray,
    /** Share of square, 3:4, 4:5 and landscape among the posts. */
    val formats: FloatArray = FloatArray(4),
) {
    /** The feed shape the church posts most (4:5 when nothing was learned). */
    val feedRatio: CropRatio
        get() {
            if (count < MIN_COUNT) return CropRatio.IG_PORTRAIT
            val best = (0..2).maxBy { formats[it] }
            return if (formats[best] <= 0f) CropRatio.IG_PORTRAIT else IgCrop.FEED_RATIOS[best]
        }

    /** 0..1: how much [f] looks like the church's posts (0.5 when nothing was learned). */
    fun match(f: IgFeatures): Float {
        if (count < MIN_COUNT) return 0.5f
        val v = vector(f)
        var sum = 0f
        for (i in v.indices) {
            val z = (v[i] - mean[i]) / max(spread[i], FLOOR[i])
            sum += z * z
        }
        val look = exp(-0.5f * sum / v.size)
        // A moment they post a lot weighs in too; one they never post weighs a little less.
        val share = moments[f.moment.ordinal]
        val moment = (0.6f + share * 2f).coerceAtMost(1.2f)
        return (look * moment).coerceIn(0f, 1f)
    }

    fun encode(): String = buildString {
        fun row(a: FloatArray) = a.joinToString(",") { String.format(Locale.US, "%.5f", it) }
        appendLine("count=$count")
        appendLine("mean=${row(mean)}")
        appendLine("spread=${row(spread)}")
        appendLine("moments=${row(moments)}")
        appendLine("formats=${row(formats)}")
    }

    override fun equals(other: Any?) = other is IgStyle && other.count == count && other.mean.contentEquals(mean) &&
        other.spread.contentEquals(spread) && other.moments.contentEquals(moments) && other.formats.contentEquals(formats)

    override fun hashCode() = count * 31 + mean.contentHashCode()

    companion object {
        /** Fewer references than this teach nothing reliable. */
        const val MIN_COUNT = 8

        /** Names of the measured values, for the "Estilo da igreja" card. */
        val NAMES = listOf("brilho", "contraste", "saturação", "tom quente", "pessoas", "rosto maior")
        private val FLOOR = floatArrayOf(0.06f, 0.04f, 0.05f, 0.06f, 0.35f, 0.5f)

        val EMPTY = IgStyle(0, FloatArray(NAMES.size), FloatArray(NAMES.size), FloatArray(IgMoment.entries.size))

        fun vector(f: IgFeatures) = floatArrayOf(
            f.brightness, f.contrast, f.saturation, f.warmth,
            ln(1f + f.faces.size), // 0, 1, a few, a crowd
            ln(1f + f.largestFace * 100f), // close-up vs far
            // Not the shape: references are already cropped for Instagram, new photos are not.
        )

        /** 0 square, 1 3:4, 2 4:5, 3 landscape or anything else. */
        fun format(aspect: Float): Int = when {
            kotlin.math.abs(aspect - 1f) < 0.03f -> 0
            kotlin.math.abs(aspect - 0.75f) < 0.02f -> 1
            kotlin.math.abs(aspect - 0.8f) < 0.02f -> 2
            else -> 3
        }

        /** Flyers and screenshots the page also posts say nothing about its photos and are skipped. */
        fun learn(all: List<IgFeatures>): IgStyle {
            val refs = all.filterNot { it.graphic }
            if (refs.isEmpty()) return EMPTY
            val vs = refs.map(::vector)
            val n = vs.first().size
            val mean = FloatArray(n) { i -> vs.map { it[i] }.average().toFloat() }
            val spread = FloatArray(n) { i -> sqrt(vs.map { (it[i] - mean[i]).let { d -> d * d } }.average()).toFloat() }
            val moments = FloatArray(IgMoment.entries.size)
            val formats = FloatArray(4)
            refs.forEach { moments[it.moment.ordinal] += 1f / refs.size; formats[format(it.aspect)] += 1f / refs.size }
            return IgStyle(refs.size, mean, spread, moments, formats)
        }

        fun decode(text: String?): IgStyle? {
            if (text.isNullOrBlank()) return null
            val map = text.lineSequence().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
            fun row(k: String, size: Int) = map[k]?.split(',')?.map { it.toFloat() }?.takeIf { it.size == size }?.toFloatArray()
            return runCatching {
                IgStyle(
                    map.getValue("count").toInt(),
                    row("mean", NAMES.size) ?: return null,
                    row("spread", NAMES.size) ?: return null,
                    row("moments", IgMoment.entries.size) ?: return null,
                    row("formats", 4) ?: FloatArray(4),
                )
            }.getOrNull()
        }
    }
}
