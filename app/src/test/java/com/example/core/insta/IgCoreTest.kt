package com.example.core.insta

import com.example.core.render.CropRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IgCoreTest {

    private fun face(cx: Float, cy: Float, size: Float = 0.12f, smile: Float? = 0.8f, yaw: Float = 0f, closed: Boolean = false) =
        IgFace(IgBox(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), smile, yaw, closed)

    /** A sharp, well lit landscape frame (3:2) with [faces]. */
    private fun shot(vararg faces: IgFace, hands: Int = 0, labels: Map<String, Float> = emptyMap(), sharp: Float = 80f, tilt: Float? = null) =
        IgFeatures(1536, 1024, sharp, sharp, faces = faces.toList(), people = faces.size, handsRaised = hands, labels = labels, tilt = tilt)

    @Test
    fun featuresSurviveTheTextRoundTrip() {
        val f = shot(face(0.4f, 0.4f), face(0.6f, 0.45f, smile = null, yaw = -30f, closed = true), hands = 1, labels = mapOf("musical instrument" to 0.81f), tilt = 1.5f)
            .copy(bodies = listOf(IgBox(0.3f, 0.3f, 0.7f, 0.95f)), brightness = 0.42f, warmth = -0.1f)
        val back = IgFeatures.decode(f.encode())
        assertNotNull(back)
        assertEquals(f.faces.size, back!!.faces.size)
        assertEquals(null, back.faces[1].smile)
        assertTrue(back.faces[1].eyesClosed)
        assertEquals(0.81f, back.labels["musical instrument"]!!, 1e-3f)
        assertEquals(f.bodies, back.bodies)
        assertEquals(1, back.handsRaised)
        assertEquals(1.5f, back.tilt!!, 1e-3f)
        // An older or broken row is measured again instead of being trusted.
        assertEquals(null, IgFeatures.decode(f.encode().replace("v=${IgFeatures.VERSION}", "v=0")))
        assertEquals(null, IgFeatures.decode("lixo"))
    }

    @Test
    fun momentsFollowHandsMusicCrowdAndChildren() {
        assertEquals(IgMoment.WORSHIP, shot(face(0.5f, 0.4f), hands = 1).moment)
        assertEquals(IgMoment.WORSHIP, shot(face(0.5f, 0.4f), labels = mapOf("musical instrument" to 0.7f)).moment)
        // Seated crowd: faces of very different sizes, many turned away.
        assertEquals(IgMoment.CONGREGATION, shot(*Array(8) { face(0.1f + it * 0.1f, 0.5f, size = 0.01f + it * 0.012f, yaw = if (it % 2 == 0) 40f else 0f) }).moment)
        assertEquals(IgMoment.CHILDREN, shot(face(0.5f, 0.4f), labels = mapOf("child" to 0.8f)).moment)
        assertEquals(IgMoment.MINISTRY, shot(face(0.5f, 0.3f), labels = mapOf("presentation" to 0.7f)).moment)
        assertEquals(IgMoment.MINISTRY, shot(face(0.5f, 0.3f)).copy(atMic = 1).moment)
        // A department lined up facing the camera.
        assertEquals(IgMoment.GROUP, shot(*Array(7) { face(0.1f + it * 0.12f, 0.4f, size = 0.06f) }).moment)
        assertEquals(IgMoment.DETAIL, shot().moment)
        assertEquals(IgMoment.PEOPLE, shot(face(0.4f, 0.4f), face(0.6f, 0.4f)).moment)
    }

    @Test
    fun aStrongWorshipFrameBeatsASoftEmptyOne() {
        val worship = IgScore.rate(shot(face(0.45f, 0.35f), face(0.6f, 0.35f), hands = 2, labels = mapOf("musician" to 0.8f)))
        val dull = IgScore.rate(shot(sharp = 35f))
        assertTrue("${worship.score} > 80", worship.score > 80f)
        assertTrue("${dull.score} < 20", dull.score < 20f)
        assertTrue(worship.reasons.contains("Mãos erguidas"))
        assertTrue(dull.warnings.contains("Pouco nítida"))
    }

    @Test
    fun closedEyesAndATiltedHorizonCostPointsAndAreSaid() {
        val open = IgScore.rate(shot(face(0.5f, 0.4f)))
        val closed = IgScore.rate(shot(face(0.5f, 0.4f, closed = true), tilt = 4f))
        assertTrue(closed.score < open.score)
        assertTrue(closed.warnings.containsAll(listOf("Olhos fechados", "Horizonte torto")))
    }

    @Test
    fun eyesClosedInPrayerAreTheMomentNotABlink() {
        val prayer = shot(face(0.5f, 0.4f, closed = true)).copy(handsOnChest = 1)
        val blink = shot(face(0.5f, 0.4f, closed = true))
        assertEquals(IgMoment.PRAYER, prayer.moment)
        val r = IgScore.rate(prayer)
        assertTrue(r.reasons.contains("Momento de oração"))
        assertFalse(r.warnings.any { "olhos" in it.lowercase() })
        assertTrue(r.score > IgScore.rate(blink).score + 20f)
        // A bowed head counts too; a posed group with someone blinking does not.
        assertTrue(shot(face(0.5f, 0.4f, closed = true).copy(pitch = -20f)).praying)
        assertFalse(shot(*Array(5) { face(0.1f + it * 0.2f, 0.4f, closed = it == 0) }).praying)
    }

    @Test
    fun theFeedShapeIsTheOneTheChurchPosts() {
        val squares = List(10) { shot(face(0.5f, 0.4f)).copy(width = 1080, height = 1080) } + List(3) { shot(face(0.5f, 0.4f)).copy(width = 1080, height = 1440) }
        val style = IgStyle.learn(squares)
        assertEquals(CropRatio.IG_SQUARE, style.feedRatio)
        assertEquals(CropRatio.IG_SQUARE.id, IgScore.rate(shot(face(0.5f, 0.4f)), style = style).feed.spec.ratio)
        assertEquals(CropRatio.IG_34, IgStyle.learn(List(9) { shot().copy(width = 1080, height = 1440) }).feedRatio)
        assertEquals(1440, IgCrop.feed(shot(face(0.5f, 0.4f)), CropRatio.IG_34).spec.targetH)
        assertEquals(CropRatio.IG_PORTRAIT, IgStyle.EMPTY.feedRatio)
    }

    @Test
    fun feedCropKeepsTheHeadsAndFollowsThePerson() {
        val right = shot(face(0.8f, 0.4f))
        val frame = IgCrop.feed(right)
        assertTrue(frame.fits)
        assertEquals(CropRatio.IG_PORTRAIT.id, frame.spec.ratio)
        assertEquals(1080, frame.spec.targetW)
        assertEquals(1350, frame.spec.targetH)
        // 4:5 in pixels on a 3:2 photo.
        val pixelAspect = frame.spec.width * 1536 / (frame.spec.height * 1024)
        assertEquals(0.8f, pixelAspect, 0.01f)
        assertTrue("moved to the right: ${frame.spec.left}", frame.spec.left > 0.3f)
        assertTrue(right.faces[0].head.inside(IgBox(frame.spec.left, frame.spec.top, frame.spec.right, frame.spec.bottom)))
    }

    @Test
    fun storiesSaySoWhenAGroupDoesNotFit() {
        val group = shot(face(0.1f, 0.4f), face(0.5f, 0.4f), face(0.9f, 0.4f))
        assertFalse(IgCrop.story(group).fits)
        assertTrue(IgCrop.story(shot(face(0.5f, 0.4f))).fits)
        assertEquals(1920, IgCrop.story(group).spec.targetH)
        assertTrue(IgScore.rate(group).warnings.contains("O recorte do feed corta alguém"))
    }

    @Test
    fun theChurchStyleRewardsPhotosLikeItsPosts() {
        // The church posts warm, close portraits.
        val refs = List(12) { i -> shot(face(0.5f, 0.4f, size = 0.2f + i * 0.005f)).copy(brightness = 0.55f + i * 0.004f, warmth = 0.3f, saturation = 0.45f) }
        val style = IgStyle.learn(refs)
        assertEquals(12, style.count)
        val alike = shot(face(0.5f, 0.4f, size = 0.22f)).copy(brightness = 0.57f, warmth = 0.3f, saturation = 0.45f)
        val other = shot(*Array(9) { face(0.1f + it * 0.09f, 0.6f, size = 0.03f) }).copy(brightness = 0.2f, warmth = -0.4f, saturation = 0.1f)
        assertTrue(style.match(alike) > style.match(other) + 0.3f)
        assertTrue(IgScore.rate(alike, style = style).reasons.contains("Com a cara da igreja"))
        assertEquals(style.encode(), IgStyle.decode(style.encode())!!.encode())
        // Too few references: neutral.
        assertEquals(0.5f, IgStyle.learn(refs.take(3)).match(alike), 0f)
    }

    @Test
    fun choicesTeachTheWeightsAndStayNearTheDefaults() {
        val smiles = IgScore.signals(shot(face(0.5f, 0.4f, smile = 1f)))
        var w = IgWeights.DEFAULT
        repeat(30) { w = w.learn(smiles, approved = false) }
        assertTrue(w.w[IgSignal.SMILE.ordinal] < IgWeights.DEFAULT.w[IgSignal.SMILE.ordinal])
        assertTrue(w.score(smiles) < IgWeights.DEFAULT.score(smiles))
        assertEquals(30, w.lessons)
        // Many lessons still don't run away.
        repeat(2000) { w = w.learn(smiles, approved = false) }
        assertTrue(w.w.all { it.isFinite() && it > -10f && it < 10f })
        assertEquals(w.encode(), IgWeights.decode(w.encode())!!.encode())
    }

    private fun cand(id: Long, score: Float, moment: IgMoment, at: Long = id, hash: Long? = null, burst: Long? = null): IgCandidate {
        val f = when (moment) {
            IgMoment.WORSHIP -> shot(face(0.5f, 0.4f), hands = 1)
            IgMoment.CONGREGATION -> shot(*Array(8) { face(0.1f + it * 0.1f, 0.5f, size = 0.01f + it * 0.012f, yaw = if (it % 2 == 0) 40f else 0f) })
            IgMoment.DETAIL -> shot()
            else -> shot(face(0.4f, 0.4f), face(0.6f, 0.4f))
        }
        return IgCandidate(id, at, IgScore.rate(f).copy(score = score), hash, burst)
    }

    @Test
    fun theCarouselSkipsLookAlikesAndBurstsAndCapsAMoment() {
        val list = listOf(
            cand(1, 95f, IgMoment.WORSHIP, burst = 7),
            cand(2, 94f, IgMoment.WORSHIP, burst = 7), // same burst
            cand(3, 93f, IgMoment.WORSHIP, hash = 0b1111L),
            cand(4, 92f, IgMoment.WORSHIP, hash = 0b1110L), // looks like 3
            cand(5, 91f, IgMoment.WORSHIP),
            cand(6, 90f, IgMoment.WORSHIP),
            cand(7, 80f, IgMoment.PEOPLE),
            cand(8, 70f, IgMoment.DETAIL),
            cand(9, 20f, IgMoment.PEOPLE), // too weak
        )
        val picked = IgCarousel.pick(list, size = 5)
        val ids = picked.map { it.photoId }
        assertTrue(2L !in ids)
        assertTrue(4L !in ids)
        assertTrue(9L !in ids)
        // 40 % of 5 = 2 worship frames at most.
        assertEquals(2, picked.count { it.rating.moment == IgMoment.WORSHIP })
        assertEquals(listOf(1L, 3L, 7L, 8L).toSet(), ids.toSet())
    }

    @Test
    fun theCarouselOpensWithPeopleTellsTheServiceInOrderAndClosesWithTheChurch() {
        val list = listOf(
            cand(1, 70f, IgMoment.DETAIL, at = 100),
            cand(2, 90f, IgMoment.CONGREGATION, at = 500),
            cand(3, 85f, IgMoment.WORSHIP, at = 300),
            cand(4, 80f, IgMoment.PEOPLE, at = 200),
            cand(5, 75f, IgMoment.PEOPLE, at = 400),
        )
        val order = IgCarousel.order(list).map { it.photoId }
        assertEquals(3L, order.first()) // best with people, not the crowd
        assertEquals(2L, order.last()) // the full church closes
        assertEquals(listOf(1L, 4L, 5L), order.subList(1, 4)) // by time in between
    }

    @Test
    fun noMomentShowsThreeTimesInARowWhenAnotherCanSwapIn() {
        val list = listOf(cand(1, 99f, IgMoment.PEOPLE, at = 0)) +
            (2L..5L).map { cand(it, 80f, IgMoment.WORSHIP, at = it) } + cand(6, 70f, IgMoment.DETAIL, at = 9) + cand(7, 70f, IgMoment.PEOPLE, at = 10)
        val moments = IgCarousel.order(list).map { it.rating.moment }
        for (i in 2 until moments.size) assertFalse(moments.toString(), moments[i] == moments[i - 1] && moments[i] == moments[i - 2])
    }

    @Test
    fun captionUsesOnlyWhatTheAppKnowsAndWhatWasTyped() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val at = LocalDateTime.of(2026, 11, 21, 19, 30).atZone(zone).toInstant().toEpochMilli()
        val notes = IgCaption.Notes(theme = "Estamos em uma grande obra", verse = "Neemias 6:3", preacher = "Pr. Fulano")
        val facts = IgCaption.Facts("Culto da Família", "@iead_estrela_de_luz", at, listOf(IgMoment.WORSHIP, IgMoment.MINISTRY, IgMoment.GROUP), notes, credit = "@fotografo", zone = zone)
        val a = IgCaption.suggest(facts, 0)
        assertTrue(a, a.startsWith("Culto da Família\nSábado, 21 de novembro · @iead_estrela_de_luz\n\nTema: Estamos em uma grande obra\nNeemias 6:3 📖"))
        assertTrue(a, a.contains("Momentos de louvor, Palavra e comunhão.\nA Palavra foi ministrada por Pr. Fulano."))
        // Worship was not typed: its line is gone, not left as "No louvor: ."
        assertFalse(a, a.contains("No louvor"))
        assertTrue(a, a.contains("Uma noite abençoada"))
        assertTrue(a, a.contains("Fotos: @fotografo"))
        assertTrue(a, a.endsWith("#iead_estrela_de_luz"))
        assertTrue(IgCaption.suggest(facts, 2).startsWith("Que noite na presença do Senhor!"))
        assertEquals(IgCaption.suggest(facts, 0), IgCaption.suggest(facts, 3)) // wraps
        // With nothing but the event, nothing dangles.
        val bare = IgCaption.suggest(IgCaption.Facts("Culto da Família", null, at, emptyList(), zone = zone), 0)
        assertEquals("Culto da Família\nSábado, 21 de novembro\n\nUma noite abençoada pelo poder de Deus, do início ao fim. 🙌", bare)
        assertEquals("#estreladeluz", IgCaption.hashtag("Estrela de Luz"))
        assertEquals(notes, IgCaption.Notes.decode(notes.encode()))
        assertEquals(listOf("A", "B"), IgCaption.templates("A\n---\nB"))
        assertEquals(IgCaption.DEFAULT_TEMPLATES, IgCaption.templates("  "))
    }
}
