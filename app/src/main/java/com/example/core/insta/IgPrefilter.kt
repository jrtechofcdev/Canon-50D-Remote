package com.example.core.insta

import com.example.core.data.Photo

/**
 * The quick first pass, from what the culling already measured: no rejected or shaken frames, at
 * most two frames of each burst (the second may be the one with the eyes closed in prayer), and a
 * cap so the models run on the photos that can win, not on all of them.
 */
object IgPrefilter {

    const val MAX_CANDIDATES = 150

    fun candidates(photos: List<Photo>, max: Int = MAX_CANDIDATES): List<Photo> {
        val usable = photos.filter { !it.rejected && it.displayUri != null && !(it.score != null && it.blurry) }
        val perBurst = mutableMapOf<Long, Int>()
        val kept = usable
            .sortedByDescending { it.score ?: NO_SCORE }
            .filter { p ->
                val g = p.burstGroup ?: return@filter true
                val n = perBurst[g] ?: 0
                perBurst[g] = n + 1
                n < 2
            }
        return kept.take(max).sortedBy { it.capturedAt }
    }

    /** A frame the culling hasn't scored yet sits in the middle: it still gets its chance. */
    private const val NO_SCORE = 55f
}
