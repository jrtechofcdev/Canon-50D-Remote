package com.example.core.ptp

sealed interface EosEventRecord {
    data class ObjectAdded(
        val handle: Int,
        val storageId: Int,
        val format: Int,
        val size: Long,
        val parent: Int,
        val filename: String,
    ) : EosEventRecord {
        val isFolder get() = format == PtpFormat.ASSOCIATION
        val isRaw get() = format == PtpFormat.CANON_CR2 || format == PtpFormat.CANON_CRW ||
            filename.endsWith(".CR2", ignoreCase = true)
        val isJpeg get() = format == PtpFormat.EXIF_JPEG || filename.endsWith(".JPG", ignoreCase = true)
    }

    data class PropertyChanged(val prop: Int, val value: Int) : EosEventRecord

    data class AvailableValues(val prop: Int, val values: List<Int>) : EosEventRecord
}

/**
 * Parses the data returned by EOS GetEvent (0x9116): a list of [size u32][type u32][fields...] records
 * terminated by a record of type 0. Offsets match libgphoto2's ptp_unpack_CANON_changes.
 */
object EosEventParser {
    private const val OA_HANDLE = 0x08
    private const val OA_STORAGE = 0x0C
    private const val OA_FORMAT = 0x10
    private const val OA_SIZE = 0x1C
    private const val OA_PARENT = 0x20
    private const val OA_NAME = 0x28

    fun parse(data: ByteArray): List<EosEventRecord> {
        val out = mutableListOf<EosEventRecord>()
        var pos = 0
        while (pos + 8 <= data.size) {
            val size = PtpDataReader.u32At(data, pos)
            val type = PtpDataReader.u32At(data, pos + 4)
            if (size < 8 || type == 0 || pos + size > data.size) break
            parseRecord(data, pos, size, type)?.let(out::add)
            pos += size
        }
        return out
    }

    private fun parseRecord(d: ByteArray, start: Int, size: Int, type: Int): EosEventRecord? = when (type) {
        EosEvent.OBJECT_ADDED_EX -> if (size >= OA_NAME) {
            EosEventRecord.ObjectAdded(
                handle = PtpDataReader.u32At(d, start + OA_HANDLE),
                storageId = PtpDataReader.u32At(d, start + OA_STORAGE),
                format = PtpDataReader.u16At(d, start + OA_FORMAT),
                size = PtpDataReader.u32At(d, start + OA_SIZE).toLong() and 0xFFFFFFFFL,
                parent = PtpDataReader.u32At(d, start + OA_PARENT),
                filename = PtpDataReader.asciiAt(d, start + OA_NAME, start + size),
            )
        } else null

        EosEvent.PROP_VALUE_CHANGED -> if (size >= 16) {
            EosEventRecord.PropertyChanged(
                prop = PtpDataReader.u32At(d, start + 8),
                value = PtpDataReader.u32At(d, start + 12),
            )
        } else null

        EosEvent.AVAIL_LIST_CHANGED -> if (size >= 20) {
            val prop = PtpDataReader.u32At(d, start + 8)
            val count = PtpDataReader.u32At(d, start + 16)
            val maxCount = (size - 20) / 4
            val values = (0 until count.coerceIn(0, maxCount)).map { PtpDataReader.u32At(d, start + 20 + it * 4) }
            EosEventRecord.AvailableValues(prop, values)
        } else null

        else -> null
    }
}
