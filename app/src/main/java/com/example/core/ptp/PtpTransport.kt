package com.example.core.ptp

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.Build
import java.io.IOException

/** Raw bulk pipe to the camera. Blocking; always called from PtpSession under its lock. */
interface PtpTransport {
    /** Size of each read; a multiple of the endpoint's max packet size. */
    val readBufferSize: Int

    fun write(data: ByteArray, timeoutMs: Int)

    /** One bulk read. Returns the byte count, 0 for a zero-length packet. Throws on timeout or error. */
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    fun close()
}

class UsbPtpTransport(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
) : PtpTransport {

    // Before Android 9 a single bulkTransfer is capped at 16 KB.
    private val maxTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) 256 * 1024 else 16 * 1024

    override val readBufferSize: Int = maxTransfer - maxTransfer % endpointIn.maxPacketSize

    override fun write(data: ByteArray, timeoutMs: Int) {
        var offset = 0
        while (offset < data.size) {
            val len = minOf(maxTransfer, data.size - offset)
            val sent = connection.bulkTransfer(endpointOut, data, offset, len, timeoutMs)
            if (sent < 0) throw IOException("Falha ao enviar dados USB")
            offset += sent
        }
        // A container that ends exactly on a packet boundary needs a zero-length packet to terminate it.
        if (data.isNotEmpty() && data.size % endpointOut.maxPacketSize == 0) {
            connection.bulkTransfer(endpointOut, ByteArray(0), 0, timeoutMs)
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val n = connection.bulkTransfer(endpointIn, buffer, buffer.size, timeoutMs)
        if (n < 0) throw IOException("Sem resposta da câmera (timeout USB)")
        return n
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
