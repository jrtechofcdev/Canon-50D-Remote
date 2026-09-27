package com.example.core.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PtpPacket(
    val length: Int,
    val containerType: Short,
    val code: Short,
    val transactionId: Int,
    val parameters: IntArray = IntArray(0),
    val payload: ByteArray = ByteArray(0)
) {
    fun toByteArray(): ByteArray {
        val totalSize = 12 + (parameters.size * 4) + payload.size
        val buffer = ByteBuffer.allocate(totalSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(totalSize)
            putShort(containerType)
            putShort(code)
            putInt(transactionId)
            for (param in parameters) {
                putInt(param)
            }
            if (payload.isNotEmpty()) {
                put(payload)
            }
        }
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PtpPacket) return false
        if (length != other.length) return false
        if (containerType != other.containerType) return false
        if (code != other.code) return false
        if (transactionId != other.transactionId) return false
        if (!parameters.contentEquals(other.parameters)) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + containerType
        result = 31 * result + code
        result = 31 * result + transactionId
        result = 31 * result + parameters.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val TYPE_COMMAND: Short = 1
        const val TYPE_DATA: Short = 2
        const val TYPE_RESPONSE: Short = 3
        const val TYPE_EVENT: Short = 4

        fun createCommand(code: Short, transactionId: Int, vararg params: Int): PtpPacket {
            val totalSize = 12 + (params.size * 4)
            return PtpPacket(
                length = totalSize,
                containerType = TYPE_COMMAND,
                code = code,
                transactionId = transactionId,
                parameters = params
            )
        }
    }
}
