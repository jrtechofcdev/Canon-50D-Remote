package com.example.core.storage

import java.io.InputStream

/**
 * Pulls the full-size JPEG that Canon embeds in every CR2.
 * In a CR2, IFD0 is a normal TIFF directory whose StripOffsets/StripByteCounts point at that JPEG,
 * and it sits near the start of the file, so a forward-only stream (SAF) is enough.
 */
object Cr2Preview {
    private const val TAG_STRIP_OFFSETS = 0x0111
    private const val TAG_STRIP_BYTE_COUNTS = 0x0117
    private const val HEADER_READ = 64 * 1024

    fun extract(input: InputStream): ByteArray? {
        val head = readUpTo(input, HEADER_READ)
        val (offset, length) = locateJpeg(head) ?: return null
        if (length <= 0 || length > 40 * 1024 * 1024) return null

        val out = ByteArray(length)
        var copied = 0
        if (offset < head.size) {
            copied = minOf(length, head.size - offset)
            System.arraycopy(head, offset, out, 0, copied)
        } else {
            skipFully(input, (offset - head.size).toLong())
        }
        while (copied < length) {
            val n = input.read(out, copied, length - copied)
            if (n < 0) return null
            copied += n
        }
        return if (out.size > 2 && out[0] == 0xFF.toByte() && out[1] == 0xD8.toByte()) out else null
    }

    /** Returns (offset, length) of the IFD0 strip, or null when this does not look like a TIFF-based RAW. */
    fun locateJpeg(head: ByteArray): Pair<Int, Int>? {
        if (head.size < 16) return null
        val little = head[0] == 'I'.code.toByte() && head[1] == 'I'.code.toByte()
        val big = head[0] == 'M'.code.toByte() && head[1] == 'M'.code.toByte()
        if (!little && !big) return null
        fun u16(p: Int) = if (little) (head[p].toInt() and 0xFF) or ((head[p + 1].toInt() and 0xFF) shl 8)
        else ((head[p].toInt() and 0xFF) shl 8) or (head[p + 1].toInt() and 0xFF)
        fun u32(p: Int) = if (little) u16(p) or (u16(p + 2) shl 16) else (u16(p) shl 16) or u16(p + 2)

        val ifd = u32(4)
        if (ifd <= 0 || ifd + 2 > head.size) return null
        val count = u16(ifd)
        var offset = -1
        var length = -1
        for (i in 0 until count) {
            val entry = ifd + 2 + i * 12
            if (entry + 12 > head.size) break
            val tag = u16(entry)
            val type = u16(entry + 2)
            val value = if (type == 3) u16(entry + 8) else u32(entry + 8)
            when (tag) {
                TAG_STRIP_OFFSETS -> offset = value
                TAG_STRIP_BYTE_COUNTS -> length = value
            }
        }
        return if (offset > 0 && length > 0) offset to length else null
    }

    private fun readUpTo(input: InputStream, max: Int): ByteArray {
        val buf = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = input.read(buf, total, max - total)
            if (n < 0) break
            total += n
        }
        return if (total == max) buf else buf.copyOf(total)
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val scratch = ByteArray(8192)
        while (left > 0) {
            val n = input.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
            if (n < 0) return
            left -= n
        }
    }
}
