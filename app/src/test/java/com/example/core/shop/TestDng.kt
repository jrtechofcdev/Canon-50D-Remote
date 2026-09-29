package com.example.core.shop

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds DNG presets in memory and serves them like the API, for tests and screenshots. */
object TestDng {

    fun xmp(exposure: String = "+0.50", contrast: String = "+12") =
        """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
        <rdf:Description rdf:about="" xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
        crs:Exposure2012="$exposure" crs:Contrast2012="$contrast" crs:Vibrance="-13"/></rdf:RDF></x:xmpmeta>"""

    fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

    /**
     * Laid out like the API's presets: IFD0 with the XMP, a SubIFD with the preview (baseline JPEG,
     * compression 7), and [gap] bytes of "raw data" before it, so the preview can sit past the first 64 KB.
     */
    fun build(preview: ByteArray, width: Int, height: Int, xmp: String = xmp(), gap: Int = 1_000): ByteArray {
        val xmpBytes = xmp.toByteArray()
        val ifd0 = 8
        val sub = ifd0 + 2 + 3 * 12 + 4
        val xmpAt = sub + 2 + 6 * 12 + 4
        val previewAt = xmpAt + xmpBytes.size + gap
        val buf = ByteBuffer.allocate(previewAt + preview.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(ifd0)
        fun entry(tag: Int, type: Int, count: Int, value: Int) {
            buf.putShort(tag.toShort()).putShort(type.toShort()).putInt(count)
            if (type == 3 && count == 1) buf.putShort(value.toShort()).putShort(0) else buf.putInt(value)
        }
        buf.position(ifd0)
        buf.putShort(3)
        entry(254, 4, 1, 1) // uncompressed thumbnail: not a preview to use
        entry(330, 4, 1, sub)
        entry(700, 1, xmpBytes.size, xmpAt)
        buf.putInt(0)
        buf.position(sub)
        buf.putShort(6)
        entry(254, 4, 1, 1)
        entry(256, 4, 1, width)
        entry(257, 4, 1, height)
        entry(259, 3, 1, 7)
        entry(273, 4, 1, previewAt)
        entry(279, 4, 1, preview.size)
        buf.putInt(0)
        buf.position(xmpAt)
        buf.put(xmpBytes)
        buf.position(previewAt)
        buf.put(preview)
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
