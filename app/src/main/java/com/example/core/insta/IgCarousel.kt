package com.example.core.insta

import com.example.core.analysis.CullMath
import kotlin.math.ceil
import kotlin.math.max

/** A photo in the running for the post. */
data class IgCandidate(
    val photoId: Long,
    val capturedAt: Long,
    val rating: IgRating,
    val dHash: Long? = null,
    val burstGroup: Long? = null,
)

/**
 * Picks the photos for one carousel and puts them in order.
 *
 * Choice: best score first, but never two frames of the same burst or two look-alikes, and no moment
 * takes more than 40 % of the post (a carousel of only the band is not the service).
 *
 * Order: the strongest frame with people as the cover, then the service as it happened (by time), with
 * no more than two of the same moment in a row, and a full church to close when there is one.
 */
object IgCarousel {

    const val DEFAULT_SIZE = 10
    const val MAX_SIZE = 20

    /** Photos under this score are left out even when the post is not full. */
    const val MIN_SCORE = 35f

    fun pick(all: List<IgCandidate>, size: Int = DEFAULT_SIZE): List<IgCandidate> {
        val n = size.coerceIn(1, MAX_SIZE)
        val cap = max(2, ceil(n * 0.4).toInt())
        val chosen = mutableListOf<IgCandidate>()
        val perMoment = mutableMapOf<IgMoment, Int>()
        for (c in all.sortedByDescending { it.rating.score }) {
            if (chosen.size >= n) break
            if (c.rating.score < MIN_SCORE) break
            if (chosen.any { same(it, c) }) continue
            val m = c.rating.moment
            if ((perMoment[m] ?: 0) >= cap) continue
            chosen += c
            perMoment[m] = (perMoment[m] ?: 0) + 1
        }
        return order(chosen)
    }

    private fun same(a: IgCandidate, b: IgCandidate) =
        (a.burstGroup != null && a.burstGroup == b.burstGroup) || CullMath.similar(a.dHash, b.dHash, 8)

    fun order(chosen: List<IgCandidate>): List<IgCandidate> {
        if (chosen.size <= 2) return chosen.sortedByDescending { it.rating.score }
        val withPeople = chosen.filter { it.rating.moment != IgMoment.DETAIL && it.rating.moment != IgMoment.CONGREGATION }
        val cover = (withPeople.ifEmpty { chosen }).maxBy { it.rating.score }
        val rest = chosen.filter { it !== cover }.toMutableList()
        val closing = rest.filter { it.rating.moment == IgMoment.CONGREGATION }.maxByOrNull { it.rating.score }
        closing?.let { rest.remove(it) }
        rest.sortBy { it.capturedAt }
        breakRuns(rest)
        return listOf(cover) + rest + listOfNotNull(closing)
    }

    /** Moves photos so no moment shows three times in a row, when another moment is there to swap in. */
    private fun breakRuns(list: MutableList<IgCandidate>) {
        var i = 2
        while (i < list.size) {
            val m = list[i].rating.moment
            if (list[i - 1].rating.moment == m && list[i - 2].rating.moment == m) {
                val j = (i + 1 until list.size).firstOrNull { list[it].rating.moment != m }
                if (j != null) list.add(i, list.removeAt(j))
            }
            i++
        }
    }
}
