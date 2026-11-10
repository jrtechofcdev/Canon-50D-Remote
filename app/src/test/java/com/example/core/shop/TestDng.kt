package com.example.core.shop

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Builds DNG presets in memory and serves them like the API, for tests and screenshots. */
object TestDng {

    fun xmp(exposure: String = "+0.50", contrast: String = "+12") =
        """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
        crs:Exposure2012="$exposure" crs:Contrast2012="$contrast" crs:Vibrance="-13"/></rdf:RDF></x:xmpmeta>"""

    fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

    /** The linearization the API's DNGs carry: the sRGB curve undone, 8 bits → 16 bits. */
    val SRGB_TO_LINEAR = IntArray(256) { v ->
        val s = v / 255.0
        val l = if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        (l * 65535 + 0.5).toInt()
    }

    /**
     * Laid out like the API's presets: IFD0 with the XMP, a SubIFD with the preview (baseline JPEG,
     * compression 7), and [gap] bytes of "raw data" before it, so the preview can sit past the first
     * KBs. With [original], a second SubIFD holds the photo before the preset as LinearRaw in one
     * lossy JPEG tile, with the linearization table, like the real files.
     */
    fun build(
        preview: ByteArray,
        width: Int,
        height: Int,
        xmp: String = xmp(),
        gap: Int = 1_000,
        original: Triple<ByteArray, Int, Int>? = null,
    ): ByteArray {
        val xmpBytes = xmp.toByteArray()
        val subCount = if (original != null) 2 else 1
        val ifd0 = 8
        val subList = ifd0 + 2 + 3 * 12 + 4
        val sub = subList + 4 * subCount
        val raw = sub + 2 + 6 * 12 + 4
        val rawEntries = 12
        val table = raw + (if (original != null) 2 + rawEntries * 12 + 4 else 0)
        val xmpAt = table + (if (original != null) 512 else 0)
        val previewAt = xmpAt + xmpBytes.size + gap
        val originalAt = previewAt + preview.size
        val buf = ByteBuffer.allocate(originalAt + (original?.first?.size ?: 0)).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(ifd0)
        fun entry(tag: Int, type: Int, count: Int, value: Int) {
            buf.putShort(tag.toShort()).putShort(type.toShort()).putInt(count)
            if (type == 3 && count == 1) buf.putShort(value.toShort()).putShort(0) else buf.putInt(value)
        }
        buf.position(ifd0)
        buf.putShort(3)
        entry(254, 4, 1, 1) // uncompressed thumbnail: not a preview to use
        entry(330, 4, subCount, if (subCount == 1) sub else subList)
        entry(700, 1, xmpBytes.size, xmpAt)
        buf.putInt(0)
        buf.position(subList)
        buf.putInt(sub)
        if (original != null) buf.putInt(raw)
        buf.position(sub)
        buf.putShort(6)
        entry(254, 4, 1, 1)
        entry(256, 4, 1, width)
        entry(257, 4, 1, height)
        entry(259, 3, 1, 7)
        entry(273, 4, 1, previewAt)
        entry(279, 4, 1, preview.size)
        buf.putInt(0)
        if (original != null) {
            val (bytes, w, h) = original
            buf.position(raw)
            buf.putShort(rawEntries.toShort())
            entry(254, 4, 1, 0)
            entry(256, 4, 1, w)
            entry(257, 4, 1, h)
            entry(258, 3, 1, 8)
            entry(259, 3, 1, 34892)
            entry(262, 3, 1, 34892)
            entry(277, 3, 1, 3)
            entry(322, 4, 1, w)
            entry(323, 4, 1, h)
            entry(324, 4, 1, originalAt)
            entry(325, 4, 1, bytes.size)
            entry(50712, 3, 256, table)
            buf.putInt(0)
            buf.position(table)
            SRGB_TO_LINEAR.forEach { buf.putShort(it.toShort()) }
        }
        buf.position(xmpAt)
        buf.put(xmpBytes)
        buf.position(previewAt)
        buf.put(preview)
        original?.let { buf.position(originalAt); buf.put(it.first) }
        return buf.array()
    }

    /** Serves files from memory like a server with Range support, and counts what went over the wire. */
    class Server(val files: Map<String, ByteArray>) : ShopFetcher {
        var online = true
        var sent = 0L
        override fun get(url: String, range: LongRange?): Fetched {
            if (!online) throw IOException("sem rede")
            val file = files[url] ?: throw ShopException("404")
            if (range == null) return Fetched(0, file).also { sent += file.size }
            val end = minOf(range.last + 1, file.size.toLong()).toInt()
            val part = file.copyOfRange(range.first.toInt(), end)
            sent += part.size
            return Fetched(range.first, part)
        }
    }
}
