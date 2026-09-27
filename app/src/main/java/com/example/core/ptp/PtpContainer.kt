package com.example.core.ptp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** USB PTP container: [length u32][type u16][code u16][transactionId u32][payload]. All little-endian. */
object PtpContainer {
    const val HEADER_SIZE = 12

    const val TYPE_COMMAND = 1
    const val TYPE_DATA = 2
    const val TYPE_RESPONSE = 3
    const val TYPE_EVENT = 4

    fun command(code: Int, transactionId: Int, params: IntArray): ByteArray {
        require(params.size <= 5) { "PTP allows at most 5 parameters" }
        val size = HEADER_SIZE + params.size * 4
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(size).putShort(TYPE_COMMAND.toShort()).putShort(code.toShort()).putInt(transactionId)
        params.forEach { buf.putInt(it) }
        return buf.array()
    }

    fun data(code: Int, transactionId: Int, payload: ByteArray): ByteArray {
        val size = HEADER_SIZE + payload.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(size).putShort(TYPE_DATA.toShort()).putShort(code.toShort()).putInt(transactionId)
        buf.put(payload)
        return buf.array()
    }

    data class Header(val length: Long, val type: Int, val code: Int, val transactionId: Int)

    fun parseHeader(bytes: ByteArray, count: Int): Header {
        if (count < HEADER_SIZE) throw PtpProtocolException("Pacote curto demais ($count bytes)")
        val buf = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        return Header(
            length = buf.int.toLong() and 0xFFFFFFFFL,
            type = buf.short.toInt() and 0xFFFF,
            code = buf.short.toInt() and 0xFFFF,
            transactionId = buf.int,
        )
    }

    /** Payload of a SetDevicePropValueEx data phase for a 32-bit property value. */
    fun eosPropValue(prop: Int, value: Int): ByteArray =
        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(12).putInt(prop).putInt(value).array()
}

open class PtpException(message: String) : Exception(message)
class PtpProtocolException(message: String) : PtpException(message)
class PtpResponseException(val code: Int, op: Int) :
    PtpException("Operação 0x%04X falhou: %s".format(op, PtpResponse.name(code)))
