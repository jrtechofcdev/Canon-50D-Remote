package com.example.core.ptp

/** Little-endian reader over a PTP dataset (DeviceInfo, event payloads). */
class PtpDataReader(private val data: ByteArray, var pos: Int = 0) {

    val remaining: Int get() = data.size - pos

    fun u8(): Int = data[pos++].toInt() and 0xFF

    fun u16(): Int = u8() or (u8() shl 8)

    fun u32(): Int = u16() or (u16() shl 16)

    fun u32Array(): IntArray {
        val n = u32()
        if (n < 0 || n * 4 > remaining) throw PtpProtocolException("Array inválido ($n itens)")
        return IntArray(n) { u32() }
    }

    fun u16Array(): IntArray {
        val n = u32()
        if (n < 0 || n * 2 > remaining) throw PtpProtocolException("Array inválido ($n itens)")
        return IntArray(n) { u16() }
    }

    /** PTP string: u8 char count (including the terminating NUL), then UTF-16LE chars. */
    fun string(): String {
        val count = u8()
        if (count == 0) return ""
        val sb = StringBuilder()
        repeat(count) {
            val ch = u16()
            if (ch != 0) sb.append(ch.toChar())
        }
        return sb.toString()
    }

    companion object {
        fun u32At(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)

        fun u16At(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        /** NUL-terminated ASCII string starting at [offset], bounded by [end]. */
        fun asciiAt(data: ByteArray, offset: Int, end: Int): String {
            val sb = StringBuilder()
            var i = offset
            while (i < end && data[i] != 0.toByte()) {
                sb.append((data[i].toInt() and 0xFF).toChar())
                i++
            }
            return sb.toString()
        }
    }
}

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val version: String,
    val serial: String,
    val operations: Set<Int>,
) {
    fun supports(op: Int) = op in operations

    companion object {
        fun parse(data: ByteArray): DeviceInfo {
            val r = PtpDataReader(data)
            r.u16() // standard version
            r.u32() // vendor extension id
            r.u16() // vendor extension version
            r.string() // vendor extension description
            r.u16() // functional mode
            val ops = r.u16Array().toSet()
            r.u16Array() // events
            r.u16Array() // device properties
            r.u16Array() // capture formats
            r.u16Array() // image formats
            return DeviceInfo(
                manufacturer = r.string(),
                model = r.string(),
                version = r.string(),
                serial = r.string(),
                operations = ops,
            )
        }
    }
}

/** Standard PTP ObjectInfo dataset, trimmed to what the card browser needs. */
data class ObjectInfo(
    val handle: Int,
    val storageId: Int,
    val format: Int,
    val size: Long,
    val parent: Int,
    val filename: String,
    /** "YYYYMMDDThhmmss" as written by the camera clock. */
    val captureDate: String,
) {
    val isFolder get() = format == PtpFormat.ASSOCIATION
    val isRaw get() = format == PtpFormat.CANON_CR2 || format == PtpFormat.CANON_CRW || filename.endsWith(".CR2", true)
    val isJpeg get() = format == PtpFormat.EXIF_JPEG || filename.endsWith(".JPG", true)

    /** Same shape as a capture event, so the downloader treats both alike. */
    fun asObjectAdded() = EosEventRecord.ObjectAdded(handle, storageId, format, size, parent, filename)

    companion object {
        fun parse(handle: Int, data: ByteArray): ObjectInfo {
            val r = PtpDataReader(data)
            val storage = r.u32()
            val format = r.u16()
            r.u16() // protection
            val size = r.u32().toLong() and 0xFFFFFFFFL
            r.u16() // thumb format
            r.u32() // thumb size
            r.u32(); r.u32() // thumb width/height
            r.u32(); r.u32(); r.u32() // image width/height/depth
            val parent = r.u32()
            r.u16() // association type
            r.u32() // association desc
            r.u32() // sequence number
            val name = r.string()
            val date = if (r.remaining > 0) r.string() else ""
            return ObjectInfo(handle, storage, format, size, parent, name, date)
        }
    }
}
