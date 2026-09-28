package com.example.core.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A photo's crop, like Lightroom's: quarter turns and flips first, then a straighten [angle]
 * (degrees, −45…45), then an upright rectangle [left]…[bottom] given as fractions of the turned
 * picture. The rectangle always lies inside the straightened picture, so no empty corners show.
 * [ratio] is the aspect preset it was made with; [targetW]×[targetH] the pixel size that preset asks for.
 */
data class CropSpec(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val angle: Float = 0f,
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val ratio: String = CropRatio.FREE.id,
    val targetW: Int = 0,
    val targetH: Int = 0,
) {
    val isIdentity: Boolean
        get() = left <= 0.0005f && top <= 0.0005f && right >= 0.9995f && bottom >= 0.9995f &&
            abs(angle) < 0.01f && quarterTurns % 4 == 0 && !flipH && !flipV

    val width get() = right - left
    val height get() = bottom - top

    fun encode(): String = String.format(
        Locale.US, "v1;%.5f;%.5f;%.5f;%.5f;%.3f;%d;%d;%d;%s;%d;%d",
        left, top, right, bottom, angle, ((quarterTurns % 4) + 4) % 4, if (flipH) 1 else 0, if (flipV) 1 else 0, ratio, targetW, targetH,
    )

    companion object {
        fun decode(text: String?): CropSpec? {
            val p = text?.split(';') ?: return null
            if (p.size < 12 || p[0] != "v1") return null
            return runCatching {
                CropSpec(
                    p[1].toFloat(), p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat(), p[6].toInt(),
                    p[7] == "1", p[8] == "1", p[9], p[10].toInt(), p[11].toInt(),
                )
            }.getOrNull()
        }
    }
}

enum class CropGroup(val label: String) { Basic("Básicos"), Social("Redes sociais"), Print("Impressão") }

/**
 * Aspect presets. [w]:[h] is the shape; [targetW]×[targetH] the size the network wants (0 = keep the
 * photo's resolution). Print sizes are portrait; the swap button turns any of them to landscape.
 */
enum class CropRatio(
    val id: String,
    val label: String,
    val detail: String,
    val group: CropGroup,
    val w: Float,
    val h: Float,
    val targetW: Int = 0,
    val targetH: Int = 0,
) {
    FREE("free", "Livre", "Qualquer formato", CropGroup.Basic, 0f, 0f),
    ORIGINAL("orig", "Original", "Mesma proporção da foto", CropGroup.Basic, -1f, -1f),
    R1_1("1x1", "1:1", "Quadrado", CropGroup.Basic, 1f, 1f),
    R3_2("3x2", "3:2", "Proporção da 50D", CropGroup.Basic, 3f, 2f),
    R4_3("4x3", "4:3", "Tela de celular antigo, tablet", CropGroup.Basic, 4f, 3f),
    R16_9("16x9", "16:9", "Vídeo, TV, YouTube", CropGroup.Basic, 16f, 9f),

    IG_SQUARE("ig_sq", "Instagram quadrado", "1080 × 1080", CropGroup.Social, 1f, 1f, 1080, 1080),
    IG_PORTRAIT("ig_45", "Instagram retrato", "4:5 · 1080 × 1350 (ocupa mais tela no feed)", CropGroup.Social, 4f, 5f, 1080, 1350),
    IG_LANDSCAPE("ig_191", "Instagram paisagem", "1,91:1 · 1080 × 566", CropGroup.Social, 1.91f, 1f, 1080, 566),
    STORY("story", "Stories · Reels · Status", "9:16 · 1080 × 1920 (Instagram, Facebook, WhatsApp, TikTok)", CropGroup.Social, 9f, 16f, 1080, 1920),
    FB_POST("fb_post", "Facebook post", "1,91:1 · 1200 × 630", CropGroup.Social, 1.91f, 1f, 1200, 630),
    FB_COVER("fb_cover", "Capa do Facebook", "820 × 312", CropGroup.Social, 820f, 312f, 1640, 624),
    WA_PROFILE("wa_profile", "Foto de perfil", "1:1 · WhatsApp, Instagram, Facebook", CropGroup.Social, 1f, 1f, 640, 640),
    YT_THUMB("yt", "Miniatura YouTube", "16:9 · 1280 × 720", CropGroup.Social, 16f, 9f, 1280, 720),

    P10x15("10x15", "10 × 15", "Foto comum", CropGroup.Print, 10f, 15f),
    P13x18("13x18", "13 × 18", "Porta-retrato", CropGroup.Print, 13f, 18f),
    P15x21("15x21", "15 × 21", "Álbum, porta-retrato", CropGroup.Print, 15f, 21f),
    P20x25("20x25", "20 × 25", "Quadro", CropGroup.Print, 20f, 25f),
    P20x30("20x30", "20 × 30", "Quadro, pôster pequeno", CropGroup.Print, 20f, 30f),
    A4("a4", "A4", "21 × 29,7", CropGroup.Print, 21f, 29.7f),
    P3x4("3x4", "3 × 4", "Documento", CropGroup.Print, 3f, 4f),
    ;

    val locked: Boolean get() = this != FREE

    /** Social formats come in their own orientation (Stories tall, covers wide); the rest follow the photo. */
    fun defaultLandscape(photoW: Float, photoH: Float): Boolean = when {
        group == CropGroup.Social -> w >= h
        w == h -> true
        else -> photoW >= photoH
    }

    /** Pixel size the network wants for a frame of this orientation, or 0 × 0 to keep the photo's resolution. */
    fun target(portrait: Boolean): Pair<Int, Int> {
        if (targetW <= 0) return 0 to 0
        if (targetW == targetH) return targetW to targetH
        val big = maxOf(targetW, targetH)
        val small = minOf(targetW, targetH)
        return if (portrait) small to big else big to small
    }

    /** The widest centred crop of this shape on a [photoW]×[photoH] picture, ready to save. */
    fun centred(photoW: Float, photoH: Float, landscape: Boolean = defaultLandscape(photoW, photoH)): CropSpec {
        val spec = CropGeometry.largest(CropSpec(), photoW, photoH, CropGeometry.aspect(this, photoW, photoH, landscape))
        val (tw, th) = target(portrait = spec.height * photoH > spec.width * photoW)
        return spec.copy(ratio = id, targetW = tw, targetH = th)
    }

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: FREE
    }
}

/** Pure geometry of a crop over a picture of [w]×[h] pixels (after the quarter turns). */
object CropGeometry {

    /** True when every corner of the rectangle lies inside the picture rotated by [angle] degrees. */
    fun inside(spec: CropSpec, w: Float, h: Float): Boolean {
        if (spec.left < -1e-4f || spec.top < -1e-4f || spec.right > 1.0001f || spec.bottom > 1.0001f) return false
        if (spec.right <= spec.left || spec.bottom <= spec.top) return false
        val rad = Math.toRadians(-spec.angle.toDouble())
        val c = cos(rad)
        val s = sin(rad)
        val cx = w / 2.0
        val cy = h / 2.0
        for ((fx, fy) in listOf(spec.left to spec.top, spec.right to spec.top, spec.right to spec.bottom, spec.left to spec.bottom)) {
            // A frame corner, turned back by the straighten angle, must land on the picture.
            val x = fx * w - cx
            val y = fy * h - cy
            val px = x * c - y * s + cx
            val py = x * s + y * c + cy
            if (px < -0.5 || py < -0.5 || px > w + 0.5 || py > h + 0.5) return false
        }
        return true
    }

    /** Pixel aspect (width / height) the rectangle must keep, or null when free. */
    fun aspect(ratio: CropRatio, w: Float, h: Float, landscape: Boolean): Float? = when {
        ratio == CropRatio.FREE -> null
        ratio == CropRatio.ORIGINAL -> w / h
        else -> {
            val a = ratio.w / ratio.h
            // Presets are stored in their natural orientation; the swap button flips them.
            if (landscape == (a >= 1f)) a else 1f / a
        }
    }

    /** The biggest rectangle of [aspect] (or the whole picture when free) centred on ([cx], [cy]) that fits. */
    fun largest(base: CropSpec, w: Float, h: Float, aspect: Float?, cx: Float = 0.5f, cy: Float = 0.5f): CropSpec {
        // Start from the largest shape of that aspect in the unrotated picture, then shrink until it fits.
        var fw: Float
        var fh: Float
        if (aspect == null) {
            fw = 1f; fh = 1f
        } else if (aspect >= w / h) {
            fw = 1f; fh = (w / aspect) / h
        } else {
            fh = 1f; fw = (h * aspect) / w
        }
        var lo = 0f
        var hi = 1f
        repeat(30) {
            val mid = (lo + hi) / 2
            val candidate = centred(base, cx, cy, fw * mid, fh * mid)
            if (inside(candidate, w, h)) lo = mid else hi = mid
        }
        val fitted = centred(base, cx, cy, fw * lo, fh * lo)
        return if (inside(fitted, w, h)) fitted else largest(base, w, h, aspect) // centre too close to an edge: use the middle
    }

    private fun centred(base: CropSpec, cx: Float, cy: Float, fw: Float, fh: Float) =
        base.copy(left = cx - fw / 2, top = cy - fh / 2, right = cx + fw / 2, bottom = cy + fh / 2)

    /**
     * Moves from [from] towards [to] as far as the crop stays valid (inside the picture, not tiny).
     * Used for every drag: a handle pushed past an edge stops at the edge instead of jumping back.
     */
    fun towards(from: CropSpec, to: CropSpec, w: Float, h: Float, minSize: Float = 0.08f): CropSpec {
        fun ok(s: CropSpec) = inside(s, w, h) && s.width >= minSize && s.height >= minSize
        if (ok(to)) return to
        var lo = 0f
        var hi = 1f
        repeat(20) {
            val mid = (lo + hi) / 2
            if (ok(lerp(from, to, mid))) lo = mid else hi = mid
        }
        return if (lo > 0f) lerp(from, to, lo) else from
    }

    private fun lerp(a: CropSpec, b: CropSpec, t: Float) = a.copy(
        left = a.left + (b.left - a.left) * t,
        top = a.top + (b.top - a.top) * t,
        right = a.right + (b.right - a.right) * t,
        bottom = a.bottom + (b.bottom - a.bottom) * t,
    )

    /** After a straighten change, shrink the rectangle about its centre (keeping its shape) until it fits. */
    fun fitAfterRotation(spec: CropSpec, w: Float, h: Float): CropSpec {
        if (inside(spec, w, h)) return spec
        val cx = (spec.left + spec.right) / 2
        val cy = (spec.top + spec.bottom) / 2
        var lo = 0f
        var hi = 1f
        repeat(30) {
            val mid = (lo + hi) / 2
            val c = centred(spec, cx, cy, spec.width * mid, spec.height * mid)
            if (inside(c, w, h)) lo = mid else hi = mid
        }
        val c = centred(spec, cx, cy, spec.width * lo, spec.height * lo)
        return if (lo > 0.05f && inside(c, w, h)) c else largest(spec, w, h, spec.width * w / (spec.height * h))
    }

    /** Size of the picture after quarter turns. */
    fun turnedSize(w: Int, h: Int, quarterTurns: Int): Pair<Int, Int> = if (quarterTurns % 2 == 0) w to h else h to w
}

/** Cuts a bitmap with a [CropSpec]. The source is not recycled. */
object CropRenderer {
    fun apply(src: Bitmap, spec: CropSpec, maxEdge: Int = 0): Bitmap {
        if (spec.isIdentity && spec.targetW == 0 && maxEdge == 0) return src
        // 1. Quarter turns and flips.
        val turn = Matrix().apply {
            postRotate(90f * (spec.quarterTurns % 4))
            postScale(if (spec.flipH) -1f else 1f, if (spec.flipV) -1f else 1f)
        }
        val turned = if (spec.quarterTurns % 4 == 0 && !spec.flipH && !spec.flipV) src
        else Bitmap.createBitmap(src, 0, 0, src.width, src.height, turn, true)
        val w = turned.width.toFloat()
        val h = turned.height.toFloat()

        // 2. Straighten and cut in one draw: the frame's pixels, pulled from the rotated picture.
        val cw = max(1f, spec.width * w)
        val ch = max(1f, spec.height * h)
        var scale = 1f
        if (spec.targetW > 0 && spec.targetH > 0) scale = min(spec.targetW / cw, spec.targetH / ch).coerceAtMost(1f)
        if (maxEdge > 0) scale = min(scale, maxEdge / max(cw, ch)).coerceAtMost(1f)
        val out = Bitmap.createBitmap(max(1, (cw * scale).roundToInt()), max(1, (ch * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postRotate(spec.angle, w / 2, h / 2)
            postTranslate(-spec.left * w, -spec.top * h)
            postScale(scale, scale)
        }
        Canvas(out).drawBitmap(turned, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (turned !== src) turned.recycle()
        return out
    }
}

/**
 * Finds how tilted the horizon is, like Lightroom's Auto straighten: strong edges near horizontal
 * (horizon, tables, windows) and near vertical (walls, doors) vote for an angle.
 * Returns the correction in degrees to apply, or null when the picture has no clear lines.
 */
object HorizonDetector {
    fun detect(source: IntArray, width: Int, height: Int, maxTilt: Float = 20f): Float? {
        val gray = blur(source, width, height)
        val bins = FloatArray((maxTilt * 2 * 4).toInt() + 1) // 0.25° bins from -maxTilt to +maxTilt
        var total = 0f
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val gx = (gray[i - width + 1] + 2 * gray[i + 1] + gray[i + width + 1]) - (gray[i - width - 1] + 2 * gray[i - 1] + gray[i + width - 1])
                val gy = (gray[i + width - 1] + 2 * gray[i + width] + gray[i + width + 1]) - (gray[i - width - 1] + 2 * gray[i - width] + gray[i - width + 1])
                val mag = sqrt((gx * gx + gy * gy).toFloat())
                if (mag < 60f) continue
                // Line direction is perpendicular to the gradient; fold it to a tilt around 0° (horizontal) or 90° (vertical).
                var deg = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat() + 90f
                deg = ((deg % 180f) + 180f) % 180f
                val tilt = when {
                    deg <= maxTilt -> deg
                    deg >= 180f - maxTilt -> deg - 180f
                    abs(deg - 90f) <= maxTilt -> deg - 90f
                    else -> continue
                }
                val b = ((tilt + maxTilt) * 4).roundToInt().coerceIn(0, bins.lastIndex)
                bins[b] += mag
                total += mag
            }
        }
        if (total <= 0f) return null
        // Smooth over ±0.5° and take the strongest direction.
        var best = -1
        var bestScore = 0f
        for (b in bins.indices) {
            var s = 0f
            for (k in -2..2) s += bins.getOrElse(b + k) { 0f }
            if (s > bestScore) { bestScore = s; best = b }
        }
        if (best < 0 || bestScore / total < 0.08f) return null
        val tilt = best / 4f - maxTilt
        // In screen coordinates y grows downward, so a line tilted +t needs a −t turn to level.
        return if (abs(tilt) < 0.1f) 0f else -tilt
    }

    /** 5 × 5 box blur, twice: noise and pixel steps stop voting, long edges keep their direction. */
    private fun blur(src: IntArray, w: Int, h: Int): IntArray {
        fun pass(a: IntArray): IntArray {
            val out = IntArray(a.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0
                var n = 0
                for (dy in -2..2) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -2..2) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        s += a[yy * w + xx]; n++
                    }
                }
                out[y * w + x] = s / n
            }
            return out
        }
        return pass(pass(src))
    }
}
