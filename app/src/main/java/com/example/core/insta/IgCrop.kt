package com.example.core.insta

import com.example.core.render.CropRatio
import com.example.core.render.CropSpec
import kotlin.math.max
import kotlin.math.min

/** A crop for one Instagram format, and whether everyone who matters is inside it. */
data class IgFrame(val spec: CropSpec, val fits: Boolean)

/**
 * Feed (1:1, 3:4 or 4:5) and Stories (9:16) crops placed on the people: the heads (never cut) and, when there are
 * no faces, the bodies the pose model found. Straight frames only; the original crop is not touched.
 */
object IgCrop {

    /** The feed shapes a church may post in; which one comes from its own posts ([IgStyle.feedRatio]). */
    val FEED_RATIOS = listOf(CropRatio.IG_SQUARE, CropRatio.IG_34, CropRatio.IG_PORTRAIT)

    fun feed(f: IgFeatures, ratio: CropRatio = CropRatio.IG_PORTRAIT): IgFrame = frame(f, ratio)

    fun story(f: IgFeatures): IgFrame = frame(f, CropRatio.STORY)

    /** What has to stay inside the frame. Null when nothing was found (then the centre is used). */
    fun subject(f: IgFeatures): IgBox? {
        // Faces far in the back of a crowd don't steer the crop; the ones that are at least a third of the biggest do.
        val big = f.largestFace
        val heads = f.faces.filter { it.box.area >= big / 3f }.map { it.head }
        val boxes = heads.ifEmpty { f.bodies }
        return boxes.reduceOrNull { a, b -> a.union(b) }
    }

    fun frame(f: IgFeatures, ratio: CropRatio): IgFrame {
        val pixelAspect = ratio.w / ratio.h // width / height in pixels
        val photoAspect = f.aspect
        // Size of the biggest frame of that shape, in fractions of the photo.
        val fw: Float
        val fh: Float
        if (pixelAspect <= photoAspect) { fh = 1f; fw = pixelAspect / photoAspect } else { fw = 1f; fh = photoAspect / pixelAspect }
        val s = subject(f)
        // Centre on the people; with a group wider than the frame, keep the heads' middle.
        val cx = s?.cx ?: 0.5f
        // People read best a little above the middle: aim at the upper third of the subject.
        val cy = s?.let { it.top + it.height * 0.4f } ?: 0.5f
        val left = (cx - fw / 2).coerceIn(0f, 1f - fw)
        val top = (cy - fh / 2).coerceIn(0f, 1f - fh)
        val box = IgBox(left, top, left + fw, top + fh)
        val fits = s == null || s.inside(box)
        val (tw, th) = ratio.target(portrait = ratio.h >= ratio.w)
        val spec = CropSpec(left = r(box.left), top = r(box.top), right = r(box.right), bottom = r(box.bottom), ratio = ratio.id, targetW = tw, targetH = th)
        return IgFrame(spec, fits)
    }

    /** Thirds: 1 when the subject's centre sits on a third line of the feed frame, 0 when far off. */
    fun thirds(f: IgFeatures, frame: CropSpec): Float {
        val s = subject(f) ?: return 0.5f
        val x = ((s.cx - frame.left) / max(1e-3f, frame.width)).coerceIn(0f, 1f)
        val y = ((s.top + s.height * 0.3f - frame.top) / max(1e-3f, frame.height)).coerceIn(0f, 1f)
        val dx = min(min(kotlin.math.abs(x - 1f / 3), kotlin.math.abs(x - 2f / 3)), kotlin.math.abs(x - 0.5f))
        val dy = min(kotlin.math.abs(y - 1f / 3), kotlin.math.abs(y - 0.5f))
        return (1f - (dx + dy) * 3f).coerceIn(0f, 1f)
    }

    private fun r(v: Float) = (v * 10000).toInt() / 10000f
}
