package com.example.core.shop

/**
 * Where things sit inside a DNG preset, read from its first bytes only: the IFDs of a preset DNG
 * come right after the header, so 64 KB tell where the finished-look preview JPEG and the XMP
 * with the settings are. The shop then fetches just those bytes, not the whole 0.5–2 MB file.
 */
data class DngLayout(
    /** Baseline JPEG previews (the photo with the preset applied), largest first. */
    val previews: List<Segment>,
    /** The XMP packet (TIFF tag 700) holding the `crs:` settings, when its entry was reached. */
    val xmp: Segment?,
) {
    data class Segment(val offset: Long, val length: Int, val width: Int = 0, val height: Int = 0) {
        val end: Long get() = offset + length
    }

    companion object {
        private const val NEW_SUBFILE_TYPE = 254
        private const val WIDTH = 256
        private const val HEIGHT = 257
        private const val COMPRESSION = 259
        private const val STRIP_OFFSETS = 273
        private const val STRIP_BYTES = 279
        private const val JPEG_OFFSET = 513
        private const val JPEG_LENGTH = 514
        private const val XMP = 700
        private const val SUB_IFDS = 330

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
            var xmp: Segment? = null
            val pending = ArrayDeque<Long>().apply { add(r.u32(4)) }
            val seen = HashSet<Long>()
            while (pending.isNotEmpty()) {
                val ifd = pending.removeFirst()
                if (ifd <= 0 || ifd + 2 > head.size || !seen.add(ifd)) continue
                val count = r.u16(ifd.toInt())
                var subfile = -1L
                var width = 0
                var height = 0
                var compression = 0
                var strips: Pair<Long, Long>? = null
                var jpeg: Pair<Long, Long>? = null
                for (i in 0 until count) {
                    val e = ifd.toInt() + 2 + i * 12
                    if (e + 12 > head.size) break
                    val tag = r.u16(e)
                    val type = r.u16(e + 2)
                    val n = r.u32(e + 4)
                    // A single SHORT or LONG sits in the entry itself.
                    val single = if (type == 3) r.u16(e + 8).toLong() else r.u32(e + 8)
                    when (tag) {
                        NEW_SUBFILE_TYPE -> subfile = single
                        WIDTH -> width = single.toInt()
                        HEIGHT -> height = single.toInt()
                        COMPRESSION -> compression = single.toInt()
                        STRIP_OFFSETS -> if (n == 1L) strips = single to (strips?.second ?: -1L)
                        STRIP_BYTES -> if (n == 1L) strips = (strips?.first ?: -1L) to single
                        JPEG_OFFSET -> jpeg = single to (jpeg?.second ?: -1L)
                        JPEG_LENGTH -> jpeg = (jpeg?.first ?: -1L) to single
                        XMP -> {
                            val offset = if (n <= 4) (e + 8).toLong() else r.u32(e + 8)
                            if (xmp == null && n > 0) xmp = Segment(offset, n.toInt())
                        }
                        SUB_IFDS -> {
                            if (n == 1L) pending.add(r.u32(e + 8))
                            else {
                                val base = r.u32(e + 8)
                                for (k in 0 until n.toInt()) {
                                    val p = base + k * 4
                                    if (p + 4 <= head.size) pending.add(r.u32(p.toInt()))
                                }
                            }
                        }
                    }
                }
                // Reduced-resolution images (subfile 1) in JPEG are the previews; the main image (0) is
                // the raw data, and lossless or lossy-DNG JPEG (7 on raw, 34892) can't be shown as is.
                val isPreview = subfile == 1L && (compression == 6 || compression == 7)
                val seg = (jpeg ?: strips)?.takeIf { it.first > 0 && it.second > 0 }
                if (isPreview && seg != null) previews += Segment(seg.first, seg.second.toInt(), width, height)
                val next = ifd.toInt() + 2 + count * 12
                if (next + 4 <= head.size) pending.add(r.u32(next))
            }
            return DngLayout(previews.sortedByDescending { it.width * it.height }, xmp)
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

    private class Reader(val b: ByteArray, val little: Boolean) {
        fun u16(p: Int): Int = if (p + 2 > b.size) 0 else if (little) (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)
        else ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)

        fun u32(p: Int): Long = if (p + 4 > b.size) 0 else if (little) (u16(p).toLong() or (u16(p + 2).toLong() shl 16))
        else ((u16(p).toLong() shl 16) or u16(p + 2).toLong())
    }
}
