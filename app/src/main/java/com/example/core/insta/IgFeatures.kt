package com.example.core.insta

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A box in fractions of the picture (0..1), so it does not depend on the size the photo was read at. */
data class IgBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = max(0f, width) * max(0f, height)
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2

    fun union(o: IgBox) = IgBox(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun clamp() = IgBox(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
    fun inside(o: IgBox, slack: Float = 0.002f) =
        left >= o.left - slack && top >= o.top - slack && right <= o.right + slack && bottom <= o.bottom + slack
}

/** A face as the Instagram picker needs it: where it is, the smile, where it looks, and blinks. */
data class IgFace(
    val box: IgBox,
    val smile: Float?,
    /** Head turn left/right in degrees (0 = facing the camera). */
    val yaw: Float = 0f,
    val eyesClosed: Boolean = false,
    /** Head up/down in degrees; clearly negative is a bowed head. */
    val pitch: Float = 0f,
) {
    /** Head and hair: the face box grown up and to the sides, so a crop never cuts the top of the head. */
    val head: IgBox
        get() = IgBox(box.left - box.width * 0.25f, box.top - box.height * 0.45f, box.right + box.width * 0.25f, box.bottom + box.height * 0.15f).clamp()
}

/** The church moments the picker knows, as seen on what churches post. */
enum class IgMoment(val label: String) {
    /** Hands lifted, the band, a choir singing. */
    WORSHIP("Louvor"),
    /** Eyes closed with a hand on the chest or lifted, or the head bowed. */
    PRAYER("Oração"),
    /** One person at the front with the microphone: the preacher or a singer. */
    MINISTRY("Ministração"),
    /** Departments, choirs and families lined up facing the camera. */
    GROUP("Grupos e corais"),
    CHILDREN("Crianças"),
    BAPTISM("Batismo"),
    PEOPLE("Pessoas"),
    /** Banners, letters, the Lord's Supper trays, the Bible: no one in focus. */
    DETAIL("Detalhes"),
    /** The church full: a wide shot of the people seated or standing. */
    CONGREGATION("Congregação"),
}

/**
 * Everything measured on one photo. Made on the phone (ML Kit + pixel maths) and kept as text in
 * the database, so a second "Analisar" is instant. Pure data: the scoring runs the same in tests.
 */
data class IgFeatures(
    /** Picture size as analysed; only the shape matters. */
    val width: Int,
    val height: Int,
    /** 0..100, the whole frame and the best face. */
    val sharpness: Float,
    val faceSharpness: Float? = null,
    /** Mean luma 0..1, its spread 0..1, mean saturation 0..1, warmth −1 (blue) .. 1 (orange). */
    val brightness: Float = 0.5f,
    val contrast: Float = 0.2f,
    val saturation: Float = 0.3f,
    val warmth: Float = 0f,
    /** Percent of blown and of near-black pixels. */
    val clipped: Float = 0f,
    val dark: Float = 0f,
    val faces: List<IgFace> = emptyList(),
    /** People found by the pose model, and how many of them have a hand above the shoulder. */
    val people: Int = 0,
    val handsRaised: Int = 0,
    /** How many have a hand on the chest (prayer, worship) and a hand at the mouth (a microphone). */
    val handsOnChest: Int = 0,
    val atMic: Int = 0,
    /** Boxes of the bodies the pose model found, for the crop. */
    val bodies: List<IgBox> = emptyList(),
    /** Scene labels (lower case, English as ML Kit gives them) with their confidence. */
    val labels: Map<String, Float> = emptyMap(),
    /** Horizon tilt in degrees when one was found. */
    val tilt: Float? = null,
) {
    val aspect: Float get() = if (height <= 0) 1f else width.toFloat() / height
    val eyesClosed: Int get() = faces.count { it.eyesClosed }
    val largestFace: Float get() = faces.maxOfOrNull { it.box.area } ?: 0f

    /**
     * Closed eyes that are prayer, not a blink: few people, and a hand lifted or on the chest, or the
     * head bowed. In a church that is one of the strongest frames, so it is not marked down.
     */
    val praying: Boolean
        get() = eyesClosed > 0 && faces.size <= 3 &&
            (handsRaised > 0 || handsOnChest > 0 || faces.any { it.eyesClosed && it.pitch < -12f })

    /** Faces of a similar size side by side, most facing the camera: a group posing. */
    private val lineUp: Boolean
        get() {
            if (faces.size < 4) return false
            val sizes = faces.map { it.box.area }.sorted()
            val even = sizes[sizes.size / 4] >= sizes[sizes.size * 3 / 4] * 0.35f
            return even && faces.count { abs(it.yaw) < 25f } >= faces.size * 0.6f
        }

    fun label(vararg names: String): Float = names.maxOfOrNull { labels[it] ?: 0f } ?: 0f

    /** What the frame shows, from the labels, the faces and the poses. */
    val moment: IgMoment
        get() {
            val bigFace = largestFace
            val music = label("musical instrument", "musician", "guitar", "drum", "piano", "microphone", "singer", "concert", "music", "keyboard")
            return when {
                label("swimwear", "swimming pool", "pool", "water", "river", "lake") >= 0.6f && faces.isNotEmpty() -> IgMoment.BAPTISM
                praying -> IgMoment.PRAYER
                atMic >= 1 && faces.size <= 3 -> IgMoment.MINISTRY
                handsRaised >= 1 -> IgMoment.WORSHIP
                music >= 0.55f -> IgMoment.WORSHIP
                label("child", "baby", "toddler") >= 0.6f && faces.size <= 6 -> IgMoment.CHILDREN
                lineUp && bigFace >= 0.002f -> IgMoment.GROUP
                faces.size >= 6 || label("crowd", "audience") >= 0.6f -> IgMoment.CONGREGATION
                faces.size == 1 && label("stage", "event", "speaker", "speech", "presentation") >= 0.5f -> IgMoment.MINISTRY
                faces.isEmpty() && people == 0 -> IgMoment.DETAIL
                bigFace < 0.004f && people <= 1 -> IgMoment.DETAIL
                else -> IgMoment.PEOPLE
            }
        }

    /** Plain text, one value per line, so it survives app updates (unknown keys are skipped). */
    fun encode(): String = buildString {
        fun f(v: Float) = String.format(Locale.US, "%.4f", v)
        fun box(b: IgBox) = "${f(b.left)},${f(b.top)},${f(b.right)},${f(b.bottom)}"
        appendLine("v=$VERSION")
        appendLine("size=$width,$height")
        appendLine("sharp=${f(sharpness)}")
        faceSharpness?.let { appendLine("faceSharp=${f(it)}") }
        appendLine("light=${f(brightness)},${f(contrast)},${f(saturation)},${f(warmth)},${f(clipped)},${f(dark)}")
        faces.forEach { appendLine("face=${box(it.box)},${it.smile?.let(::f) ?: "-"},${f(it.yaw)},${if (it.eyesClosed) 1 else 0},${f(it.pitch)}") }
        appendLine("people=$people,$handsRaised,$handsOnChest,$atMic")
        bodies.forEach { appendLine("body=${box(it)}") }
        labels.forEach { (k, v) -> appendLine("label=${f(v)},${k.replace('\n', ' ')}") }
        tilt?.let { appendLine("tilt=${f(it)}") }
    }

    companion object {
        /** Bumped when the analysis changes, so old cached rows are measured again. */
        const val VERSION = 1

        fun decode(text: String?): IgFeatures? {
            if (text.isNullOrBlank()) return null
            var w = 0; var h = 0; var sharp = 0f; var faceSharp: Float? = null
            var light = floatArrayOf(0.5f, 0.2f, 0.3f, 0f, 0f, 0f)
            val faces = mutableListOf<IgFace>(); val bodies = mutableListOf<IgBox>(); val labels = linkedMapOf<String, Float>()
            var people = 0; var hands = 0; var chest = 0; var mic = 0; var tilt: Float? = null; var version = 0
            fun box(p: List<String>, at: Int) = IgBox(p[at].toFloat(), p[at + 1].toFloat(), p[at + 2].toFloat(), p[at + 3].toFloat())
            return runCatching {
                text.lineSequence().forEach { line ->
                    val eq = line.indexOf('=')
                    if (eq <= 0) return@forEach
                    val key = line.substring(0, eq)
                    val value = line.substring(eq + 1)
                    val p = value.split(',')
                    when (key) {
                        "v" -> version = value.toInt()
                        "size" -> { w = p[0].toInt(); h = p[1].toInt() }
                        "sharp" -> sharp = value.toFloat()
                        "faceSharp" -> faceSharp = value.toFloat()
                        "light" -> light = FloatArray(6) { p.getOrNull(it)?.toFloatOrNull() ?: light[it] }
                        "face" -> faces += IgFace(box(p, 0), p[4].toFloatOrNull(), p[5].toFloat(), p[6] == "1", p.getOrNull(7)?.toFloatOrNull() ?: 0f)
                        "people" -> { people = p[0].toInt(); hands = p[1].toInt(); chest = p.getOrNull(2)?.toInt() ?: 0; mic = p.getOrNull(3)?.toInt() ?: 0 }
                        "body" -> bodies += box(p, 0)
                        "label" -> labels[value.substringAfter(',')] = p[0].toFloat()
                        "tilt" -> tilt = value.toFloat()
                    }
                }
                if (version != VERSION || w <= 0 || h <= 0) return null
                IgFeatures(w, h, sharp, faceSharp, light[0], light[1], light[2], light[3], light[4], light[5], faces, people, hands, chest, mic, bodies, labels, tilt)
            }.getOrNull()
        }

        /** Mean luma, its spread, mean saturation and warmth of ARGB pixels (any size, sampled). */
        fun tone(pixels: IntArray): FloatArray {
            if (pixels.isEmpty()) return floatArrayOf(0.5f, 0.2f, 0.3f, 0f)
            val step = max(1, pixels.size / 40_000)
            var n = 0; var sum = 0.0; var sumSq = 0.0; var sat = 0.0; var warm = 0.0
            var i = 0
            while (i < pixels.size) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val y = (r * 77 + g * 150 + b * 29) shr 8
                val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
                sum += y; sumSq += y.toDouble() * y
                sat += if (hi == 0) 0.0 else (hi - lo).toDouble() / hi
                warm += (r - b).toDouble()
                n++; i += step
            }
            val mean = sum / n
            val sd = kotlin.math.sqrt(max(0.0, sumSq / n - mean * mean))
            return floatArrayOf((mean / 255).toFloat(), (sd / 128).toFloat().coerceIn(0f, 1f), (sat / n).toFloat(), (warm / n / 128).toFloat().coerceIn(-1f, 1f))
        }

        /**
         * True when a hand is lifted: the wrist above the shoulder (y grows downwards) by a margin of
         * [torso] (shoulder to hip), so a hand resting on the chest does not count.
         */
        fun handUp(wristY: Float?, shoulderY: Float?, torso: Float): Boolean =
            wristY != null && shoulderY != null && shoulderY - wristY > abs(torso) * 0.1f

        /** A wrist on the chest: between the shoulders and a little below them, close to the middle of the body. */
        fun handOnChest(wristX: Float, wristY: Float, leftShoulderX: Float, rightShoulderX: Float, shoulderY: Float, torso: Float): Boolean {
            val lo = min(leftShoulderX, rightShoulderX); val hi = max(leftShoulderX, rightShoulderX)
            val t = abs(torso)
            return wristX in lo..hi && wristY > shoulderY - t * 0.05f && wristY < shoulderY + t * 0.55f
        }

        /** A wrist at the mouth, closer than half the shoulder width: almost always a microphone in church. */
        fun handAtMouth(wristX: Float, wristY: Float, mouthX: Float, mouthY: Float, shoulderWidth: Float): Boolean {
            val dx = wristX - mouthX; val dy = wristY - mouthY
            return kotlin.math.sqrt(dx * dx + dy * dy) < abs(shoulderWidth) * 0.5f
        }
    }
}
