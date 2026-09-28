package com.example.core.ptp

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.IOException

/** Raw bulk pipe to the camera. Blocking; always called from PtpSession under its lock. */
interface PtpTransport {
    /** Size of each read; a multiple of the endpoint's max packet size. */
    val readBufferSize: Int

    fun write(data: ByteArray, timeoutMs: Int)

    /** One bulk read. Returns the byte count, 0 for a zero-length packet. Throws on timeout or error. */
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    /**
     * Brings the link back to a clean state before a new session: resets the PTP function,
     * clears stalled endpoints and throws away any reply a previous attempt left unread.
     */
    fun recover() {}

    fun close()
}

class UsbPtpTransport(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
) : PtpTransport {

    // Android documents larger bulk transfers from 9 on, but many phones' USB host drivers still reject
    // anything over 16 KB on the spot (bulkTransfer returns -1 instantly). 16 KB works everywhere.
    private val maxTransfer = 16 * 1024

    override val readBufferSize: Int = maxTransfer - maxTransfer % endpointIn.maxPacketSize

    override fun write(data: ByteArray, timeoutMs: Int) {
        var offset = 0
        while (offset < data.size) {
            val len = minOf(maxTransfer, data.size - offset)
            val started = System.currentTimeMillis()
            val sent = connection.bulkTransfer(endpointOut, data, offset, len, timeoutMs)
            if (sent < 0) throw IOException("Falha ao enviar dados USB (${describe(started, timeoutMs)})")
            offset += sent
        }
        // A container that ends exactly on a packet boundary needs a zero-length packet to terminate it.
        if (data.isNotEmpty() && data.size % endpointOut.maxPacketSize == 0) {
            connection.bulkTransfer(endpointOut, ByteArray(0), 0, timeoutMs)
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val started = System.currentTimeMillis()
        val n = connection.bulkTransfer(endpointIn, buffer, buffer.size, timeoutMs)
        if (n < 0) throw IOException("Sem resposta da câmera (${describe(started, timeoutMs)})")
        return n
    }

    /** "timeout" when the camera really stayed silent, "recusado pelo USB" when Android failed at once. */
    private fun describe(started: Long, timeoutMs: Int): String {
        val elapsed = System.currentTimeMillis() - started
        return if (elapsed >= timeoutMs - 200) "timeout USB ${elapsed} ms" else "recusado pelo USB em ${elapsed} ms"
    }

    override fun recover() {
        // PTP class request "Device Reset" (bmRequestType 0x21, bRequest 0x66) aborts any half-finished transaction.
        connection.controlTransfer(0x21, 0x66, 0, usbInterface.id, null, 0, 1_000)
        clearHalt(endpointIn)
        clearHalt(endpointOut)
        // Drain a reply left over from an earlier attempt, so it isn't taken for the next command's answer.
        val scratch = ByteArray(readBufferSize)
        repeat(8) {
            if (connection.bulkTransfer(endpointIn, scratch, scratch.size, 150) <= 0) return
        }
    }

    // CLEAR_FEATURE(ENDPOINT_HALT) on one endpoint.
    private fun clearHalt(endpoint: UsbEndpoint) {
        connection.controlTransfer(0x02, 0x01, 0, endpoint.address, null, 0, 1_000)
    }

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        runCatching { connection.close() }
    }

    companion object {
        /** Finds the still-image (PTP) interface and its bulk endpoints. */
        fun endpointsOf(iface: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
            var input: UsbEndpoint? = null
            var output: UsbEndpoint? = null
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) input = ep else output = ep
            }
            return if (input != null && output != null) input to output else null
        }
    }
}
