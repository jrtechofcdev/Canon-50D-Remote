package com.example.core.analysis

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A face found in the frame, with whether its eyes look closed. */
data class FaceInfo(val region: Region, val eyesClosed: Boolean)

interface FaceAnalyzer {
    suspend fun detect(bitmap: Bitmap): List<FaceInfo>?
}

/**
 * Google ML Kit face detection with the model bundled in the APK: free, offline, on the phone.
 * Faces smaller than 6 % of the frame width (people far in the background) are ignored.
 */
class MlKitFaceAnalyzer : FaceAnalyzer {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.06f)
                .build(),
        )
    }

    override suspend fun detect(bitmap: Bitmap): List<FaceInfo>? = runCatching {
        suspendCancellableCoroutine { cont ->
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { faces ->
                    cont.resume(
                        faces.map { f ->
                            val b = f.boundingBox
                            val left = f.leftEyeOpenProbability
                            val right = f.rightEyeOpenProbability
                            // Both eyes must read as closed: one squinting eye in a smile is not a blink.
                            val closed = left != null && right != null && left < EYE_OPEN && right < EYE_OPEN
                            FaceInfo(Region(b.left, b.top, b.right, b.bottom), closed)
                        },
                    )
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }.getOrNull()

    private companion object {
        const val EYE_OPEN = 0.3f
    }
}
