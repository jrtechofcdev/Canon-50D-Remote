package com.example.core.shop

/**
 * Where things sit inside a DNG preset, read from its first bytes only: the IFDs of a preset DNG
 * come right after the header, so the first KBs tell where the finished-look preview JPEG, the XMP
 * with the settings and the original photo (without the preset) are. The shop then fetches just
 * those bytes, not the whole 0.5–5 MB file.
 */
data class DngLayout(
    /** Baseline JPEG previews (the photo with the preset applied), largest first. */
    val previews: List<Segment>,
    /** The XMP packet (TIFF tag 700) holding the `crs:` settings, when its entry was reached. */
    val xmp: Segment?,
    /** The photo before the preset (LinearRaw), at each size the file keeps, smallest first. */
    val originals: List<RawImage> = emptyList(),
    /** ColorMatrix1 (XYZ → camera), row by row, when present. */
    val colorMatrix: DoubleArray? = null,
    /** AsShotNeutral: the camera values of a white object. */
    val neutral: DoubleArray? = null,
) {
    data class Segment(val offset: Long, val length: Int, val width: Int = 0, val height: Int = 0) {
        val end: Long get() = offset + length
    }

    /**
     * One copy of the original photo, stored in tiles (or strips, read as full-width tiles).
     * [compression] 34892 is a normal JPEG per tile; 7 is lossless JPEG.
     */
    data class RawImage(
        val width: Int,
        val height: Int,
        val compression: Int,
        val tileWidth: Int,
        val tileHeight: Int,
        val offsets: LongArray,
        val counts: LongArray,
        val bits: Int,
        val samples: Int,
        /** LinearizationTable: stored value → linear value. */
        val linearization: IntArray?,
        val black: Double,
        val white: Double,
    ) {
        val start: Long get() = offsets.minOrNull() ?: 0
        val end: Long get() = offsets.indices.maxOfOrNull { offsets[it] + counts[it] } ?: 0
        /** Bytes to fetch for the whole image (one range from the first tile to the last). */
        val span: Long get() = end - start
        val maxEdge: Int get() = maxOf(width, height)
    }

    companion object {
        private const val NEW_SUBFILE_TYPE = 254
        private const val WIDTH = 256
        private const val HEIGHT = 257
        private const val BITS = 258
        private const val COMPRESSION = 259
        private const val PHOTOMETRIC = 262
        private const val STRIP_OFFSETS = 273
        private const val SAMPLES = 277
        private const val ROWS_PER_STRIP = 278
        private const val STRIP_BYTES = 279
        private const val TILE_WIDTH = 322
        private const val TILE_HEIGHT = 323
        private const val TILE_OFFSETS = 324
        private const val TILE_BYTES = 325
        private const val JPEG_OFFSET = 513
        private const val JPEG_LENGTH = 514
        private const val XMP = 700
        private const val SUB_IFDS = 330
        private const val LINEARIZATION = 50712
        private const val BLACK_LEVEL = 50714
        private const val WHITE_LEVEL = 50717
        private const val COLOR_MATRIX_1 = 50721
        private const val AS_SHOT_NEUTRAL = 50728
        private const val LINEAR_RAW = 34892

        /** Reads what [head] (the start of the file) reaches; entries pointing past it are skipped. */
        fun read(head: ByteArray): DngLayout {
            if (head.size < 8) return DngLayout(emptyList(), null)
            val little = when {
                head[0] == 'I'.code.toByte() && head[1] == 'I'.code.toByte() -> true
                head[0] == 'M'.code.toByte() && head[1] == 'M'.code.toByte() -> false
                else -> return DngLayout(emptyList(), null)
            }
            val r = Reader(head, little)
            val previews = mutableListOf<Segment>()
            val originals = mutableListOf<RawImage>()
            var xmp: Segment? = null
            var matrix: DoubleArray? = null
            var neutral: DoubleArray? = null
            val pending = ArrayDeque<Long>().apply { add(r.u32(4)) }
            val seen = HashSet<Long>()
            while (pending.isNotEmpty()) {
                val ifd = pending.removeFirst()
                if (ifd <= 0 || ifd + 2 > head.size || !seen.add(ifd)) continue
                val count = r.u16(ifd.toInt())
                val tags = HashMap<Int, Entry>()
                for (i in 0 until count) {
                    val e = ifd.toInt() + 2 + i * 12
                    if (e + 12 > head.size) break
                    val entry = Entry(r.u16(e + 2), r.u32(e + 4), e)
                    val tag = r.u16(e)
                    tags[tag] = entry
                    when (tag) {
                        XMP -> {
                            val offset = if (entry.count <= 4) (e + 8).toLong() else r.u32(e + 8)
                            if (xmp == null && entry.count > 0) xmp = Segment(offset, entry.count.toInt())
                        }
                        SUB_IFDS -> r.longs(entry)?.forEach { pending.add(it) }
                    }
                }
                val first = { tag: Int -> tags[tag]?.let { r.longs(it)?.firstOrNull() } }
                val subfile = first(NEW_SUBFILE_TYPE) ?: 0L
                val compression = first(COMPRESSION)?.toInt() ?: 1
                val width = first(WIDTH)?.toInt() ?: 0
                val height = first(HEIGHT)?.toInt() ?: 0
                if (matrix == null) matrix = tags[COLOR_MATRIX_1]?.let { r.doubles(it) }?.takeIf { it.size == 9 }
                if (neutral == null) neutral = tags[AS_SHOT_NEUTRAL]?.let { r.doubles(it) }?.takeIf { it.size == 3 }

                // Reduced-resolution images (subfile 1) in JPEG are the previews; the main image (0) is
                // the raw data, and lossless or lossy-DNG JPEG (7 on raw, 34892) can't be shown as is.
                val photometric = first(PHOTOMETRIC)?.toInt() ?: 0
                if (photometric != LINEAR_RAW && subfile == 1L && (compression == 6 || compression == 7)) {
                    val jpeg = first(JPEG_OFFSET)?.let { o -> first(JPEG_LENGTH)?.let { o to it } }
                    val strips = tags[STRIP_OFFSETS]?.takeIf { it.count == 1L }?.let { first(STRIP_OFFSETS)!! to (first(STRIP_BYTES) ?: -1L) }
                    val seg = (jpeg ?: strips)?.takeIf { it.first > 0 && it.second > 0 }
                    if (seg != null) previews += Segment(seg.first, seg.second.toInt(), width, height)
                }
                if (photometric == LINEAR_RAW && width > 0 && height > 0 && (compression == 7 || compression == LINEAR_RAW)) {
                    rawImage(r, tags, width, height, compression)?.let { originals += it }
                }
                val next = ifd.toInt() + 2 + count * 12
                if (next + 4 <= head.size) pending.add(r.u32(next))
            }
            return DngLayout(
                previews.sortedByDescending { it.width * it.height },
                xmp,
                originals.sortedBy { it.width * it.height },
                matrix,
                neutral,
            )
        }

        private fun rawImage(r: Reader, tags: Map<Int, Entry>, width: Int, height: Int, compression: Int): RawImage? {
            val tiled = tags.containsKey(TILE_OFFSETS)
            val offsets = tags[if (tiled) TILE_OFFSETS else STRIP_OFFSETS]?.let { r.longs(it) } ?: return null
            val counts = tags[if (tiled) TILE_BYTES else STRIP_BYTES]?.let { r.longs(it) } ?: return null
            if (offsets.isEmpty() || offsets.size != counts.size) return null
            val tileW = if (tiled) tags[TILE_WIDTH]?.let { r.longs(it)?.firstOrNull() }?.toInt() ?: return null else width
            val tileH = if (tiled) tags[TILE_HEIGHT]?.let { r.longs(it)?.firstOrNull() }?.toInt() ?: return null
            else tags[ROWS_PER_STRIP]?.let { r.longs(it)?.firstOrNull() }?.toInt()?.coerceAtMost(height) ?: height
            val across = (width + tileW - 1) / tileW
            val down = (height + tileH - 1) / tileH
            if (tileW <= 0 || tileH <= 0 || across * down != offsets.size) return null
            val bits = tags[BITS]?.let { r.longs(it)?.firstOrNull() }?.toInt() ?: 8
            val samples = tags[SAMPLES]?.let { r.longs(it)?.firstOrNull() }?.toInt() ?: 3
            if (samples != 3) return null
            val table = tags[LINEARIZATION]?.let { r.longs(it) }?.map { it.toInt() }?.toIntArray()
            val black = tags[BLACK_LEVEL]?.let { r.doubles(it) }?.firstOrNull() ?: 0.0
            val white = tags[WHITE_LEVEL]?.let { r.doubles(it) }?.firstOrNull() ?: ((1 shl (table?.let { 16 } ?: bits)) - 1).toDouble()
            return RawImage(width, height, compression, tileW, tileH, offsets.toLongArray(), counts.toLongArray(), bits, samples, table, black, white)
        }

        /** Baseline or progressive JPEG (SOF0/1/2), which BitmapFactory decodes; lossless (SOF3) isn't. */
        fun isViewableJpeg(bytes: ByteArray): Boolean {
            if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return false
            var p = 2
            while (p + 4 <= bytes.size) {
                if (bytes[p] != 0xFF.toByte()) return false
                val marker = bytes[p + 1].toInt() and 0xFF
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) return true
                if (marker == 0xC3 || marker == 0xDA) return false
                val len = ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
                p += 2 + len
            }
            return false
        }
    }

    /** One IFD entry: its TIFF type, value count and where the entry sits. */
    private class Entry(val type: Int, val count: Long, val at: Int)

    private class Reader(val b: ByteArray, val little: Boolean) {
        fun u16(p: Int): Int = if (p < 0 || p + 2 > b.size) 0 else if (little) (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)
        else ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)

        fun u32(p: Int): Long = if (p < 0 || p + 4 > b.size) 0 else if (little) (u16(p).toLong() or (u16(p + 2).toLong() shl 16))
        else ((u16(p).toLong() shl 16) or u16(p + 2).toLong())

        private fun size(type: Int) = when (type) { 1, 2, 6, 7 -> 1; 3, 8 -> 2; 4, 9, 11 -> 4; 5, 10, 12 -> 8; else -> 0 }

        /** Where the values are: inside the entry when they fit in 4 bytes, else at the offset it holds. */
        private fun valuesAt(e: Entry): Int? {
            val total = size(e.type) * e.count
            if (total <= 0 || e.count > 1_000_000) return null
            val at = if (total <= 4) e.at + 8 else u32(e.at + 8).toInt()
            return at.takeIf { it >= 0 && at + total <= b.size }
        }

        /** BYTE, SHORT or LONG values; null when they lie past the bytes read. */
        fun longs(e: Entry): List<Long>? {
            val at = valuesAt(e) ?: return null
            return List(e.count.toInt()) { i ->
                when (e.type) {
                    1, 7 -> (b[at + i].toLong() and 0xFF)
                    3 -> u16(at + i * 2).toLong()
                    4 -> u32(at + i * 4)
                    else -> 0L
                }
            }
        }

        /** Numbers of any type, rationals divided out. */
        fun doubles(e: Entry): DoubleArray? {
            val at = valuesAt(e) ?: return null
            return DoubleArray(e.count.toInt()) { i ->
                when (e.type) {
                    3 -> u16(at + i * 2).toDouble()
                    4 -> u32(at + i * 4).toDouble()
                    5 -> u32(at + i * 8).toDouble() / u32(at + i * 8 + 4).coerceAtLeast(1)
                    10 -> u32(at + i * 8).toInt().toDouble() / u32(at + i * 8 + 4).toInt().let { if (it == 0) 1 else it }
                    else -> 0.0
                }
            }
        }
    }
}
