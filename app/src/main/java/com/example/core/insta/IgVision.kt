package com.example.core.insta

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** The models behind the Instagram picker; a fake in tests. */
interface IgVision {
    suspend fun look(bitmap: Bitmap): IgSeen
}

/**
 * Google ML Kit with the three models bundled in the APK (faces, body pose, scene labels): free,
 * offline, on the phone. The pose model sees one person per call, so it runs around each of the main
 * faces (at most three), or once on the whole frame when there is no face.
 */
class MlKitVision : IgVision {
    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.04f)
                .build(),
        )
    }
    private val poseDetector by lazy {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
    }
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build())
    }

    override suspend fun look(bitmap: Bitmap): IgSeen {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = faceDetector.process(image).await().orEmpty().map { f ->
            val b = f.boundingBox
            val l = f.leftEyeOpenProbability
            val r = f.rightEyeOpenProbability
            IgFace(
                box = IgBox(b.left / w, b.top / h, b.right / w, b.bottom / h).clamp(),
                smile = f.smilingProbability,
                yaw = f.headEulerAngleY,
                eyesClosed = l != null && r != null && l < EYE_OPEN && r < EYE_OPEN,
                pitch = f.headEulerAngleX,
            )
        }
        val labels = labeler.process(image).await().orEmpty()
            .associate { it.text.lowercase(Locale.ROOT) to it.confidence }
        val areas = IgBody.mainFaces(faces).map { IgBody.areaFor(it.box) }.ifEmpty { listOf(IgBox(0f, 0f, 1f, 1f)) }
        val bodies = areas.mapNotNull { area -> pose(bitmap, area) }
        return IgSeen(faces, bodies, labels)
    }

    private suspend fun pose(bitmap: Bitmap, area: IgBox): IgBody? {
        val x = (area.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (area.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val cw = (area.width * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val ch = (area.height * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        if (cw < 48 || ch < 48) return null
        val part = if (x == 0 && y == 0 && cw == bitmap.width && ch == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, x, y, cw, ch)
        try {
            val pose = poseDetector.process(InputImage.fromBitmap(part, 0)).await() ?: return null
            fun point(type: Int): IgPoint? = pose.getPoseLandmark(type)
                ?.takeIf { it.inFrameLikelihood >= 0.5f }
                ?.let { IgPoint(it.position.x / cw, it.position.y / ch) }
            val mouth = listOfNotNull(point(PoseLandmark.LEFT_MOUTH), point(PoseLandmark.RIGHT_MOUTH))
                .takeIf { it.isNotEmpty() }?.let { m -> IgPoint(m.map { it.x }.average().toFloat(), m.map { it.y }.average().toFloat()) }
            val body = IgBody(
                nose = point(PoseLandmark.NOSE), mouth = mouth,
                leftShoulder = point(PoseLandmark.LEFT_SHOULDER), rightShoulder = point(PoseLandmark.RIGHT_SHOULDER),
                leftHip = point(PoseLandmark.LEFT_HIP), rightHip = point(PoseLandmark.RIGHT_HIP),
                leftWrist = point(PoseLandmark.LEFT_WRIST), rightWrist = point(PoseLandmark.RIGHT_WRIST),
            )
            return body.takeIf { it.seen }?.inside(IgBox(x / bitmap.width.toFloat(), y / bitmap.height.toFloat(), (x + cw) / bitmap.width.toFloat(), (y + ch) / bitmap.height.toFloat()))
        } finally {
            if (part !== bitmap) part.recycle()
        }
    }

    private companion object {
        const val EYE_OPEN = 0.3f
    }
}

/** A Play services task as a suspending call; null when it failed. */
private suspend fun <T> Task<T>.await(): T? = runCatching {
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
    }
}.getOrNull()
