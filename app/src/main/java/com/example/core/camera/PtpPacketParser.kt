package com.example.core.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PtpPacketParser {
    fun parse(data: ByteArray): PtpPacket? {
        if (data.size < 12) return null
        
        try {
            val buffer = ByteBuffer.wrap(data).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            val length = buffer.int
            val containerType = buffer.short
            val code = buffer.short
            val transactionId = buffer.int

            val remainingBytes = data.size - 12
            if (remainingBytes <= 0) {
                return PtpPacket(length, containerType, code, transactionId)
            }

            return if (containerType == PtpPacket.TYPE_DATA) {
                // For data packets, the rest of the bytes represent the file/information payload
                val payload = ByteArray(remainingBytes)
                buffer.get(payload)
                PtpPacket(length, containerType, code, transactionId, payload = payload)
            } else {
                // For command, response, or event, remaining bytes are typically parameters (4-bytes each)
                val paramCount = remainingBytes / 4
                val params = IntArray(paramCount)
                for (i in 0 until paramCount) {
                    params[i] = buffer.int
                }
                PtpPacket(length, containerType, code, transactionId, parameters = params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
