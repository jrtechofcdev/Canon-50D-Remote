package com.example.core.insta

import com.example.core.data.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgBodyTest {

    // A person standing in the middle: shoulders at y 0.4, hips at 0.7, mouth at 0.3.
    private fun body(leftWrist: IgPoint?, rightWrist: IgPoint? = IgPoint(0.6f, 0.75f)) = IgBody(
        nose = IgPoint(0.5f, 0.27f), mouth = IgPoint(0.5f, 0.31f),
        leftShoulder = IgPoint(0.4f, 0.4f), rightShoulder = IgPoint(0.6f, 0.4f),
        leftHip = IgPoint(0.42f, 0.7f), rightHip = IgPoint(0.58f, 0.7f),
        leftWrist = leftWrist, rightWrist = rightWrist,
    )

    @Test
    fun aLiftedHandIsWorship() {
        assertTrue(body(IgPoint(0.35f, 0.15f)).handRaised)
        // Level with the shoulder is not lifted.
        assertFalse(body(IgPoint(0.35f, 0.39f)).handRaised)
        assertFalse(body(null).handRaised)
    }

    @Test
    fun aHandOnTheChestAndAHandAtTheMouth() {
        val chest = body(IgPoint(0.48f, 0.5f))
        assertTrue(chest.handOnChest)
        assertFalse(chest.handAtMouth)
        val mic = body(IgPoint(0.52f, 0.33f))
        assertTrue(mic.handAtMouth)
        assertFalse(body(IgPoint(0.3f, 0.8f)).handOnChest)
    }

    @Test
    fun aBodyReadInsideACropComesBackInWholePictureFractions() {
        val inCrop = IgBody(leftShoulder = IgPoint(0.5f, 0.5f), rightShoulder = IgPoint(1f, 0.5f))
        val whole = inCrop.inside(IgBox(0.2f, 0.4f, 0.4f, 0.8f))
        assertEquals(0.3f, whole.leftShoulder!!.x, 1e-5f)
        assertEquals(0.6f, whole.leftShoulder!!.y, 1e-5f)
        assertEquals(0.4f, whole.rightShoulder!!.x, 1e-5f)
    }

    @Test
    fun theBodyAreaAroundAFaceStaysInThePicture() {
        val a = IgBody.areaFor(IgBox(0.45f, 0.1f, 0.55f, 0.25f))
        assertTrue(a.left >= 0f && a.top >= 0f && a.right <= 1f && a.bottom <= 1f)
        assertTrue(a.bottom > 0.7f)
        val faces = listOf(IgFace(IgBox(0f, 0f, 0.2f, 0.2f), null), IgFace(IgBox(0f, 0f, 0.02f, 0.02f), null), IgFace(IgBox(0f, 0f, 0.15f, 0.15f), null))
        assertEquals(2, IgBody.mainFaces(faces).size) // the tiny one at the back is left out
    }

    @Test
    fun measuresAndBodiesMakeTheFeatures() {
        val seen = IgSeen(
            faces = listOf(IgFace(IgBox(0.45f, 0.2f, 0.55f, 0.32f), 0.9f, eyesClosed = true)),
            bodies = listOf(body(IgPoint(0.48f, 0.5f))),
            labels = mapOf("singer" to 0.8f),
        )
        val f = IgFeatures.from(1024, 683, 70f, 80f, floatArrayOf(0.5f, 0.4f, 0.3f, 0.1f), 1f, 2f, seen, 0.5f)
        assertEquals(1, f.handsOnChest)
        assertEquals(0, f.handsRaised)
        assertEquals(1, f.bodies.size)
        assertTrue(f.praying)
        assertEquals(IgMoment.PRAYER, f.moment)
    }

    private fun photo(id: Long, score: Float?, burst: Long? = null, rejected: Boolean = false, blurry: Boolean = false) = Photo(
        id = id, session = "Culto", baseName = "IMG_$id", savedBase = "IMG_$id", capturedAt = id, jpgUri = "content://x/$id",
        score = score, burstGroup = burst, rejected = rejected, blurry = blurry,
    )

    @Test
    fun thePrefilterDropsTheBadAndKeepsTwoPerBurst() {
        val list = listOf(
            photo(1, 90f, burst = 1), photo(2, 85f, burst = 1), photo(3, 80f, burst = 1),
            photo(4, 70f, rejected = true), photo(5, 60f, blurry = true), photo(6, null), photo(7, 40f),
        )
        val ids = IgPrefilter.candidates(list).map { it.id }
        assertEquals(listOf(1L, 2L, 6L, 7L), ids) // by time
        assertEquals(listOf(1L, 2L), IgPrefilter.candidates(list, max = 2).map { it.id })
    }
}
