package com.example.core.camera

import com.example.core.ptp.PtpDataSink
import com.example.core.ptp.PtpException
import com.example.core.ptp.PtpOp
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpResponseException
import com.example.core.ptp.PtpSession
import kotlinx.coroutines.delay

/**
 * Downloads a file in blocks (GetPartialObject, 0x101B) instead of one GetObject. A whole CR2 (15–25 MB)
 * held the USB for seconds: live view froze after every photo and the next shot waited for the download.
 * Each block is its own transaction, so between two blocks live view, the shutter and the event poll
 * get their turn. [startAt] resumes a file that broke off midway.
 */
object PartialDownload {
    /** 1 MB: about 50–150 ms on USB 2.0, the longest live view goes without a frame during a download. */
    const val CHUNK = 1 shl 20
    private const val BUSY_RETRY_MS = 100L
    private const val BUSY_LIMIT_MS = 5_000L
    const val CHUNK_TIMEOUT_MS = 5_000

    /**
     * Writes bytes [startAt] until [size] of [handle] to [sink]. [sink] gets one begin([size]) and then
     * every byte in order. [beforeChunk] runs before each block (the app waits there while the shutter works).
     */
    suspend fun run(
        ptp: PtpSession,
        handle: Int,
        size: Long,
        sink: PtpDataSink,
        startAt: Long = 0L,
        chunk: Int = CHUNK,
        beforeChunk: suspend () -> Unit = {},
    ) {
        require(size > 0 && startAt in 0..size) { "tamanho $size, início $startAt" }
        sink.begin(size)
        var offset = startAt
        while (offset < size) {
            beforeChunk()
            val want = minOf(chunk.toLong(), size - offset).toInt()
            var got = 0L
            // Every data container calls begin() with its own length: it must not reach the real sink.
            val block = object : PtpDataSink {
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    sink.write(buffer, offset, length)
                    got += length
                }
            }
            var busyMs = 0L
            while (true) {
                try {
                    ptp.transact(PtpOp.GET_PARTIAL_OBJECT, handle, offset.toInt(), want, dataIn = block, timeoutMs = CHUNK_TIMEOUT_MS)
                    break
                } catch (e: PtpResponseException) {
                    // Busy while the camera writes the next photo to the card: ask for the same block again.
                    val busy = e.code == PtpResponse.DEVICE_BUSY || e.code == PtpResponse.NOT_READY
                    if (!busy || got > 0 || busyMs >= BUSY_LIMIT_MS) throw e
                    delay(BUSY_RETRY_MS)
                    busyMs += BUSY_RETRY_MS
                }
            }
            if (got == 0L) throw PtpException("A câmera não mandou dados a partir do byte $offset de $size")
            offset += got
        }
    }
}
