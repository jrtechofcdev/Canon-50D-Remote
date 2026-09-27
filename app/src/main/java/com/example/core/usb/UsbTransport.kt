package com.example.core.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class UsbTransport(
    private val connection: UsbDeviceConnection,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint
) {
    private val TAG = "UsbTransport"

    suspend fun sendBulkOut(data: ByteArray, timeout: Int = 5000): Int = withContext(Dispatchers.IO) {
        try {
            connection.bulkTransfer(endpointOut, data, data.size, timeout)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending bulk transfer Out", e)
            -1
        }
    }

    suspend fun receiveBulkIn(timeout: Int = 5000): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(512 * 1024)
            val bytesRead = connection.bulkTransfer(endpointIn, buffer, buffer.size, timeout)
            if (bytesRead < 0) return@withContext null

            // Parse total length of package from PTP header (first 4 bytes)
            val totalLength = (buffer[0].toInt() and 0xFF) or
                    ((buffer[1].toInt() and 0xFF) shl 8) or
                    ((buffer[2].toInt() and 0xFF) shl 16) or
                    ((buffer[3].toInt() and 0xFF) shl 24)

            if (totalLength <= 0 || totalLength > 100 * 1024 * 1024) {
                // Return whatever we read if length looks corrupted or invalid
                return@withContext buffer.copyOf(bytesRead)
            }

            if (bytesRead >= totalLength) {
                return@withContext buffer.copyOf(bytesRead)
            }

            // If the file is larger than the single read buffer, loop to read the remaining packets
            val fullData = ByteArray(totalLength)
            System.arraycopy(buffer, 0, fullData, 0, bytesRead)
            var totalReceived = bytesRead

            while (totalReceived < totalLength) {
                val remaining = totalLength - totalReceived
                val chunk = ByteArray(minOf(remaining, 512 * 1024))
                val read = connection.bulkTransfer(endpointIn, chunk, chunk.size, timeout)
                if (read < 0) {
                    Log.w(TAG, "Incomplete multi-transfer: read $totalReceived of $totalLength")
                    break
                }
                System.arraycopy(chunk, 0, fullData, totalReceived, read)
                totalReceived += read
            }
            fullData
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving bulk transfer In", e)
            null
        }
    }
}
