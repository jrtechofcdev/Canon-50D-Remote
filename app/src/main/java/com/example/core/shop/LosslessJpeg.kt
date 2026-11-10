package com.example.core.shop

/**
 * Decoder for lossless JPEG (ITU T.81, SOF3), the compression DNG uses for its lossless tiles.
 * Android's BitmapFactory only reads baseline JPEG, so the original photo of about a third of the
 * shop's presets needs this. Handles 2 to 16 bits, any number of interleaved components,
 * predictors 1 to 7, point transform and restart intervals.
 */
object LosslessJpeg {

    class Image(val width: Int, val height: Int, val components: Int, val precision: Int, val samples: IntArray)

    private class Huffman {
        // Canonical code tables: for each length, the first code and where its values start.
        val maxCode = IntArray(18) { -1 }
        val valPtr = IntArray(17)
        val minCode = IntArray(17)
        var values = IntArray(0)
        // Fast path: 9-bit lookup of (length shl 8 | value), 0 when the code is longer.
        val fast = IntArray(512)
    }

    fun decode(data: ByteArray): Image {
        val tables = arrayOfNulls<Huffman>(4)
        var width = 0
        var height = 0
        var precision = 8
        var compIds = IntArray(0)
        var restart = 0
        var p = 0
        fun u16(at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
        if (data.size < 4 || u16(0) != 0xFFD8) throw IllegalArgumentException("Não é JPEG")
        p = 2
        while (p + 4 <= data.size) {
            if (data[p] != 0xFF.toByte()) { p++; continue }
            val marker = data[p + 1].toInt() and 0xFF
            if (marker == 0xFF) { p++; continue }
            val len = u16(p + 2)
            val seg = p + 4
            when (marker) {
                0xC4 -> { // DHT, possibly several tables
                    var q = seg
                    while (q < p + 2 + len) {
                        val id = data[q].toInt() and 0x0F
                        val counts = IntArray(17) { if (it == 0) 0 else data[q + it].toInt() and 0xFF }
                        val total = counts.sum()
                        val values = IntArray(total) { data[q + 17 + it].toInt() and 0xFF }
                        tables[id and 3] = build(counts, values)
                        q += 17 + total
                    }
                }
                0xC3 -> { // SOF3
                    precision = data[seg].toInt() and 0xFF
                    height = u16(seg + 1)
                    width = u16(seg + 3)
                    val n = data[seg + 5].toInt() and 0xFF
                    compIds = IntArray(n) { data[seg + 6 + it * 3].toInt() and 0xFF }
                }
                0xDD -> restart = u16(seg)
                0xDA -> { // SOS: the entropy-coded data follows
                    val n = data[seg].toInt() and 0xFF
                    val tableFor = IntArray(n) { (data[seg + 2 + it * 2].toInt() and 0xFF) shr 4 }
                    val predictor = data[seg + 1 + n * 2].toInt() and 0xFF
                    val pointTransform = data[seg + 3 + n * 2].toInt() and 0x0F
                    val huff = Array(n) { tables[tableFor[it] and 3] ?: throw IllegalArgumentException("Tabela Huffman ausente") }
                    if (width <= 0 || height <= 0 || n != compIds.size) throw IllegalArgumentException("JPEG sem SOF3")
                    return Image(width, height, n, precision, scan(data, p + 2 + len, width, height, n, precision, predictor, pointTransform, restart, huff))
                }
                0xD9 -> break
            }
            p += 2 + len
        }
        throw IllegalArgumentException("JPEG sem dados")
    }

    private fun build(counts: IntArray, values: IntArray): Huffman {
        val h = Huffman()
        h.values = values
        var code = 0
        var k = 0
        for (len in 1..16) {
            h.valPtr[len] = k
            h.minCode[len] = code
            code += counts[len]
            k += counts[len]
            h.maxCode[len] = if (counts[len] > 0) code - 1 else -1
            code = code shl 1
        }
        h.maxCode[17] = Int.MAX_VALUE
        // Fill the 9-bit table for short codes.
        k = 0
        code = 0
        for (len in 1..16) {
            for (i in 0 until counts[len]) {
                if (len <= 9) {
                    val shift = 9 - len
                    val base = code shl shift
                    for (fill in 0 until (1 shl shift)) h.fast[base or fill] = (len shl 8) or values[k]
                }
                code++
                k++
            }
            code = code shl 1
        }
        return h
    }

    private class Bits(val d: ByteArray, var pos: Int) {
        private var acc = 0L
        private var count = 0
        private var ended = false

        private fun fill() {
            while (count <= 48) {
                var byte = 0
                if (!ended && pos < d.size) {
                    byte = d[pos].toInt() and 0xFF
                    if (byte == 0xFF) {
                        val next = if (pos + 1 < d.size) d[pos + 1].toInt() and 0xFF else 0
                        if (next == 0x00) pos += 2
                        else { ended = true; byte = 0 } // a marker: feed zeros from here
                    } else pos++
                }
                acc = (acc shl 8) or byte.toLong()
                count += 8
            }
        }

        fun peek(n: Int): Int { if (count < n) fill(); return ((acc ushr (count - n)) and ((1L shl n) - 1)).toInt() }
        fun skip(n: Int) { count -= n }
        fun get(n: Int): Int { if (n == 0) return 0; val v = peek(n); skip(n); return v }

        /** Jumps over an RSTn marker and starts reading fresh after it. */
        fun restart() {
            acc = 0; count = 0; ended = false
            while (pos + 1 < d.size && !(d[pos] == 0xFF.toByte() && (d[pos + 1].toInt() and 0xF8) == 0xD0)) pos++
            pos += 2
        }
    }

    private fun symbol(b: Bits, h: Huffman): Int {
        val look = h.fast[b.peek(9)]
        if (look != 0) { b.skip(look shr 8); return look and 0xFF }
        var code = b.get(1)
        var len = 1
        while (code > h.maxCode[len]) {
            code = (code shl 1) or b.get(1)
            len++
            if (len > 16) return 0
        }
        return h.values[h.valPtr[len] + code - h.minCode[len]]
    }

    private fun diff(b: Bits, h: Huffman): Int {
        val size = symbol(b, h)
        if (size == 0) return 0
        if (size == 16) return 32768
        val v = b.get(size)
        return if (v < (1 shl (size - 1))) v - (1 shl size) + 1 else v
    }

    private fun scan(
        d: ByteArray, start: Int, w: Int, hgt: Int, n: Int, precision: Int, predictor: Int, pt: Int, restart: Int, huff: Array<Huffman>,
    ): IntArray {
        val out = IntArray(w * hgt * n)
        val bits = Bits(d, start)
        val initial = 1 shl (precision - pt - 1)
        val mask = (1 shl precision) - 1
        val row = w * n
        var sinceRestart = 0
        var firstRow = 0 // rows since the last restart behave as the first row
        for (y in 0 until hgt) {
            for (x in 0 until w) {
                if (restart > 0 && sinceRestart == restart) {
                    bits.restart()
                    sinceRestart = 0
                    firstRow = y
                }
                val base = y * row + x * n
                for (c in 0 until n) {
                    val i = base + c
                    val pred = when {
                        (y == firstRow && x == 0) || (restart > 0 && sinceRestart == 0 && x == 0 && y == firstRow) -> initial
                        y == firstRow -> out[i - n]
                        x == 0 -> out[i - row]
                        else -> {
                            val ra = out[i - n]
                            val rb = out[i - row]
                            val rc = out[i - row - n]
                            when (predictor) {
                                1 -> ra
                                2 -> rb
                                3 -> rc
                                4 -> ra + rb - rc
                                5 -> ra + ((rb - rc) shr 1)
                                6 -> rb + ((ra - rc) shr 1)
                                7 -> (ra + rb) shr 1
                                else -> ra
                            }
                        }
                    }
                    out[i] = (pred + diff(bits, huff[c])) and mask
                }
                sinceRestart++
            }
        }
        if (pt > 0) for (i in out.indices) out[i] = out[i] shl pt
        return out
    }
}
