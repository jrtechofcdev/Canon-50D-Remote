package com.example.core.live

/**
 * Reads the JPEG out of a Canon EOS GetViewFinderData reply. The reply is a list of records
 * [uint32 length][uint32 type][payload]; type 1 is the live view picture. Some firmwares send the
 * JPEG with a different wrapper, so a bare SOI…EOI search is the fallback.
 */
object LiveViewParser {
    const val TYPE_JPEG = 1

    fun jpeg(data: ByteArray): ByteArray? {
        var off = 0
        while (off + 8 <= data.size) {
            val len = u32(data, off)
            val type = u32(data, off + 4)
            if (len < 8 || off.toLong() + len > data.size) break
            if (type == TYPE_JPEG.toLong() && len > 8 && isJpeg(data, off + 8)) return data.copyOfRange(off + 8, off + len.toInt())
            off += len.toInt()
        }
        val start = indexOf(data, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        if (start < 0) return null
        for (i in data.size - 2 downTo start + 2) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) return data.copyOfRange(start, i + 2)
        }
        return null
    }

    private fun isJpeg(d: ByteArray, at: Int) = at + 1 < d.size && d[at] == 0xFF.toByte() && d[at + 1] == 0xD8.toByte()

    private fun u32(d: ByteArray, at: Int): Long =
        (d[at].toLong() and 0xFF) or ((d[at + 1].toLong() and 0xFF) shl 8) or ((d[at + 2].toLong() and 0xFF) shl 16) or ((d[at + 3].toLong() and 0xFF) shl 24)

    private fun indexOf(d: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..d.size - pattern.size) {
            for (j in pattern.indices) if (d[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}
