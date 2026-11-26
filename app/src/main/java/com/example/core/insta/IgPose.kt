package com.example.core.insta

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What the models saw on one picture. Boxes and points are fractions of it. */
data class IgSeen(
    val faces: List<IgFace> = emptyList(),
    val bodies: List<IgBody> = emptyList(),
    val labels: Map<String, Float> = emptyMap(),
)

/** A body landmark in picture fractions (y grows downwards); null when the model didn't see it. */
data class IgPoint(val x: Float, val y: Float)

/** One person's body, as the pose model gives it. */
data class IgBody(
    val nose: IgPoint? = null,
    val mouth: IgPoint? = null,
    val leftShoulder: IgPoint? = null,
    val rightShoulder: IgPoint? = null,
    val leftHip: IgPoint? = null,
    val rightHip: IgPoint? = null,
    val leftWrist: IgPoint? = null,
    val rightWrist: IgPoint? = null,
) {
    private val shoulders get() = listOfNotNull(leftShoulder, rightShoulder)
    private val shoulderY get() = shoulders.map { it.y }.average().toFloat()
    private val shoulderWidth get() = if (leftShoulder != null && rightShoulder != null) abs(leftShoulder.x - rightShoulder.x) else 0f

    /** Shoulder to hip, or a guess from the shoulder width when the hips are out of the frame. */
    private val torso: Float
        get() {
            val hips = listOfNotNull(leftHip, rightHip)
            return if (hips.isNotEmpty() && shoulders.isNotEmpty()) abs(hips.map { it.y }.average().toFloat() - shoulderY)
            else shoulderWidth * 1.4f
        }

    private val wrists get() = listOfNotNull(leftWrist, rightWrist)

    val seen: Boolean get() = shoulders.isNotEmpty()

    /** A hand above the shoulders: hands lifted in worship. */
    val handRaised: Boolean
        get() = seen && wrists.any { IgFeatures.handUp(it.y, shoulderY, torso) }

    /** A hand on the chest, as in prayer or while singing. */
    val handOnChest: Boolean
        get() = leftShoulder != null && rightShoulder != null &&
            wrists.any { IgFeatures.handOnChest(it.x, it.y, leftShoulder.x, rightShoulder.x, shoulderY, torso) }

    /** A hand at the mouth: a microphone, in church nearly always. */
    val handAtMouth: Boolean
        get() {
            val m = mouth ?: nose ?: return false
            return shoulderWidth > 0f && wrists.any { IgFeatures.handAtMouth(it.x, it.y, m.x, m.y, shoulderWidth) }
        }

    /** The box of everything seen, for the crop when the face was not found. */
    val box: IgBox?
        get() {
            val pts = listOfNotNull(nose, mouth, leftShoulder, rightShoulder, leftHip, rightHip, leftWrist, rightWrist)
            if (pts.size < 3) return null
            val pad = max(0.02f, shoulderWidth * 0.3f)
            return IgBox(pts.minOf { it.x } - pad, pts.minOf { it.y } - pad * 2, pts.maxOf { it.x } + pad, pts.maxOf { it.y } + pad).clamp()
        }

    /** The same body measured inside a part of the picture ([area]), back in whole-picture fractions. */
    fun inside(area: IgBox): IgBody {
        fun IgPoint?.map() = this?.let { IgPoint(area.left + it.x * area.width, area.top + it.y * area.height) }
        return IgBody(nose.map(), mouth.map(), leftShoulder.map(), rightShoulder.map(), leftHip.map(), rightHip.map(), leftWrist.map(), rightWrist.map())
    }

    companion object {
        /**
         * The part of the picture around a face where its body is: wide enough for lifted arms, down to
         * the waist. The pose model sees one person per call, so each of the main faces gets its own look.
         */
        fun areaFor(face: IgBox): IgBox {
            val w = face.width
            val h = face.height
            return IgBox(face.left - w * 2.2f, face.top - h * 2.2f, face.right + w * 2.2f, face.bottom + h * 4.5f).clamp()
                .let { if (it.width <= 0.01f || it.height <= 0.01f) face else it }
        }

        /** The faces worth a body read: the biggest few, never the tiny ones at the back. */
        fun mainFaces(faces: List<IgFace>, limit: Int = 3): List<IgFace> {
            val big = faces.maxOfOrNull { it.box.area } ?: return emptyList()
            return faces.filter { it.box.area >= big / 4f }.sortedByDescending { it.box.area }.take(min(limit, faces.size))
        }
    }
}
