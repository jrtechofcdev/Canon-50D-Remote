package com.example.core.insta

import com.example.core.render.CropRatio
import com.example.core.render.CropSpec

/** One photo in the post being prepared (or one of the other good ones). */
data class IgItem(
    val photoId: Long,
    val capturedAt: Long,
    val score: Float,
    val moment: IgMoment,
    val reasons: List<String>,
    val warnings: List<String>,
    /** Width / height of the photo, to draw the frames over it. */
    val aspect: Float,
    val feed: CropSpec,
    /** Null: the 9:16 would cut someone. */
    val story: CropSpec?,
    /** The score inputs, for learning from the photographer's choice; null when loaded from a sent post. */
    val signals: FloatArray? = null,
) {
    override fun equals(other: Any?) = other is IgItem && other.photoId == photoId && other.feed == feed && other.story == story &&
        other.score == score && other.reasons == reasons
    override fun hashCode() = photoId.hashCode()

    fun toPick(eventId: Long, position: Int) =
        ChurchIgPick(eventId, photoId, position, score, moment.name, reasons.joinToString(" · "), feed.encode(), story?.encode())

    companion object {
        fun of(c: IgCandidate, aspect: Float) = IgItem(
            c.photoId, c.capturedAt, c.rating.score, c.rating.moment, c.rating.reasons, c.rating.warnings, aspect,
            c.rating.feed.spec, c.rating.story.spec.takeIf { c.rating.story.fits }, c.rating.signals,
        )

        fun of(p: ChurchIgPick, capturedAt: Long, aspect: Float) = IgItem(
            p.photoId, capturedAt, p.score, runCatching { IgMoment.valueOf(p.moment) }.getOrDefault(IgMoment.PEOPLE),
            p.reasons.split(" · ").filter { it.isNotBlank() }, emptyList(), aspect,
            CropSpec.decode(p.feedCrop) ?: CropSpec(), CropSpec.decode(p.storyCrop),
        )
    }
}

/**
 * The post the photographer is putting together for an event: the carousel in order, the other good
 * photos to swap in, and the caption. Every change returns a new draft (the screen just shows it).
 */
data class IgDraft(
    val eventId: Long,
    val chosen: List<IgItem>,
    val others: List<IgItem>,
    val caption: String = "",
    /** True once the photographer typed in the caption: new suggestions then don't overwrite it. */
    val captionEdited: Boolean = false,
    val notes: IgCaption.Notes = IgCaption.Notes(),
    /** Which caption model is showing ("Outra sugestão" moves it). */
    val model: Int = 0,
) {
    val moments: List<IgMoment> get() = chosen.map { it.moment }.distinct()

    fun remove(id: Long): IgDraft {
        val item = chosen.firstOrNull { it.photoId == id } ?: return this
        return copy(chosen = chosen - item, others = (others + item).sortedByDescending { it.score })
    }

    /** Adds one of the others at the end (or does nothing when the post is full). */
    fun add(id: Long, max: Int = IgCarousel.MAX_SIZE): IgDraft {
        val item = others.firstOrNull { it.photoId == id } ?: return this
        if (chosen.size >= max) return this
        return copy(chosen = chosen + item, others = others - item)
    }

    fun move(id: Long, by: Int): IgDraft {
        val i = chosen.indexOfFirst { it.photoId == id }
        if (i < 0) return this
        val j = (i + by).coerceIn(0, chosen.size - 1)
        if (i == j) return this
        val list = chosen.toMutableList()
        list.add(j, list.removeAt(i))
        return copy(chosen = list)
    }

    fun cover(id: Long): IgDraft = move(id, -chosen.size)

    fun crop(id: Long, feed: CropSpec? = null, story: CropSpec? = null): IgDraft =
        copy(chosen = chosen.map { if (it.photoId == id) it.copy(feed = feed ?: it.feed, story = story ?: it.story) else it })

    companion object {
        fun from(eventId: Long, result: IgResult, aspects: Map<Long, Float>): IgDraft {
            val picked = result.picked.map { IgItem.of(it, aspects[it.photoId] ?: 1.5f) }
            val ids = picked.map { it.photoId }.toSet()
            val rest = result.candidates.filter { it.photoId !in ids && it.rating.score >= IgCarousel.MIN_SCORE }
                .take(40).map { IgItem.of(it, aspects[it.photoId] ?: 1.5f) }
            return IgDraft(eventId, picked, rest)
        }
    }
}

/** Moving a frame by hand: same size, new centre, kept inside the photo. */
fun CropSpec.centredAt(cx: Float, cy: Float): CropSpec {
    val w = width
    val h = height
    val left = (cx - w / 2).coerceIn(0f, 1f - w)
    val top = (cy - h / 2).coerceIn(0f, 1f - h)
    return copy(left = left, top = top, right = left + w, bottom = top + h)
}

/** The feed shape of a frame, for its label ("1:1", "3:4", "4:5"). */
val CropSpec.shapeLabel: String
    get() = when (CropRatio.of(ratio)) {
        CropRatio.IG_SQUARE -> "1:1"
        CropRatio.IG_34 -> "3:4"
        CropRatio.IG_PORTRAIT -> "4:5"
        CropRatio.STORY -> "9:16"
        else -> ""
    }
