package com.example.core.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Records the live view the phone receives into an H.264 MP4, with no audio (the 50D has no
 * microphone). The size is the camera's live view picture, fixed by the first frame and rounded
 * down to a multiple of 16; later frames of another size (camera zoom) are fitted into it.
 *
 * Not thread-safe: every call must come from the same single thread (see [VideoController]).
 * Each frame carries the time it arrived, so the file keeps the real pace even when the live view
 * fps varies.
 */
class LiveRecorder(private val output: File, private val bitRate: Int) {

    class Stats(val frames: Int, val dropped: Int, val width: Int, val height: Int, val encodeMsAvg: Float, val bytes: Long, val codecName: String)

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var muxerStarted = false
    private var width = 0
    private var height = 0
    private var startNs = -1L
    private var lastPtsUs = -1L
    private var frames = 0
    private var dropped = 0
    private var encodeNsTotal = 0L
    private var bytesOut = 0L
    private var codecName = ""
    private val info = MediaCodec.BufferInfo()

    private var decodeTarget: Bitmap? = null
    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var pixels = IntArray(0)
    private var yRow = ByteArray(0)

    val started get() = codec != null

    fun stats() = Stats(frames, dropped, width, height, if (frames > 0) encodeNsTotal / frames / 1_000_000f else 0f, bytesOut, codecName)

    /** Opens the encoder for frames of [frameWidth]×[frameHeight]. Throws when the phone can't encode it. */
    fun start(frameWidth: Int, frameHeight: Int) {
        width = (frameWidth / 16 * 16).coerceAtLeast(16)
        height = (frameHeight / 16 * 16).coerceAtLeast(16)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codecName = encoder.name
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
        } catch (e: Throwable) {
            runCatching { encoder.release() }
            throw e
        }
        codec = encoder
        muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvasBitmap = target
        canvas = Canvas(target)
        pixels = IntArray(width * height)
        yRow = ByteArray(width)
        VideoLog.i("GRAVADOR", "Encoder $codecName aberto: ${width}x$height (quadro da câmera ${frameWidth}x$frameHeight), ${bitRate / 1000} kbps, sem áudio, arquivo ${output.name}")
    }

    /** Decodes the live view JPEG and encodes it. Returns false when the frame was skipped. */
    fun encodeJpeg(jpeg: ByteArray, arrivedNs: Long): Boolean {
        val decoded = decode(jpeg) ?: run {
            dropped++
            VideoLog.w("GRAVADOR", "Quadro ${frames + dropped} não decodificou (${jpeg.size} bytes), pulado")
            return false
        }
        return encode(decoded, arrivedNs)
    }

    private fun decode(jpeg: ByteArray): Bitmap? {
        val reuse = decodeTarget
        val opts = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inBitmap = reuse
        }
        val bitmap = runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) }.getOrNull()
            ?: runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inMutable = true }) }.getOrNull()
            ?: return null
        decodeTarget = bitmap
        return bitmap
    }

    private fun encode(frame: Bitmap, arrivedNs: Long): Boolean {
        val encoder = codec ?: return false
        val began = System.nanoTime()
        val index = encoder.dequeueInputBuffer(INPUT_WAIT_US)
        if (index < 0) {
            // Encoder still busy with earlier frames: drop this one rather than fall behind the camera.
            dropped++
            drain(endOfStream = false)
            return false
        }
        drawFitted(frame)
        val image = encoder.getInputImage(index)
        val size = width * height * 3 / 2
        if (image != null) {
            writeYuv(image)
        } else {
            // Some encoders give no Image: write plain I420, which YUV420Flexible accepts.
            val buffer = encoder.getInputBuffer(index) ?: run {
                dropped++
                encoder.queueInputBuffer(index, 0, 0, nextPts(arrivedNs), 0)
                return false
            }
            writeI420(buffer)
        }
        encoder.queueInputBuffer(index, 0, size, nextPts(arrivedNs), 0)
        frames++
        drain(endOfStream = false)
        encodeNsTotal += System.nanoTime() - began
        return true
    }

    private fun nextPts(arrivedNs: Long): Long {
        if (startNs < 0) startNs = arrivedNs
        var pts = (arrivedNs - startNs) / 1000
        if (pts <= lastPtsUs) pts = lastPtsUs + 1
        lastPtsUs = pts
        return pts
    }

    /** The frame scaled into the encoder size, keeping its proportion (black bars if it differs). */
    private fun drawFitted(frame: Bitmap) {
        val c = canvas ?: return
        val scale = minOf(width.toFloat() / frame.width, height.toFloat() / frame.height)
        val w = (frame.width * scale).toInt()
        val h = (frame.height * scale).toInt()
        val left = (width - w) / 2
        val top = (height - h) / 2
        if (w != width || h != height) c.drawColor(Color.BLACK)
        c.drawBitmap(frame, null, Rect(left, top, left + w, top + h), paint)
        canvasBitmap?.getPixels(pixels, 0, width, 0, 0, width, height)
    }

    // BT.601 limited range, what H.264 players expect from a phone encoder.
    private fun yOf(p: Int): Int { val r = p shr 16 and 0xFF; val g = p shr 8 and 0xFF; val b = p and 0xFF; return ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16 }
    private fun uOf(p: Int): Int { val r = p shr 16 and 0xFF; val g = p shr 8 and 0xFF; val b = p and 0xFF; return ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128 }
    private fun vOf(p: Int): Int { val r = p shr 16 and 0xFF; val g = p shr 8 and 0xFF; val b = p and 0xFF; return ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128 }

    private fun writeYuv(image: android.media.Image) {
        val planes = image.planes
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        for (row in 0 until height) {
            val base = row * width
            if (yPlane.pixelStride == 1) {
                for (col in 0 until width) yRow[col] = yOf(pixels[base + col]).toByte()
                yBuffer.position(row * yPlane.rowStride)
                yBuffer.put(yRow, 0, width)
            } else {
                for (col in 0 until width) yBuffer.put(row * yPlane.rowStride + col * yPlane.pixelStride, yOf(pixels[base + col]).toByte())
            }
        }
        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val p = pixels[(row * 2) * width + col * 2]
                uBuffer.put(row * uPlane.rowStride + col * uPlane.pixelStride, uOf(p).toByte())
                vBuffer.put(row * vPlane.rowStride + col * vPlane.pixelStride, vOf(p).toByte())
            }
        }
    }

    private fun writeI420(buffer: java.nio.ByteBuffer) {
        buffer.clear()
        for (i in 0 until width * height) buffer.put(yOf(pixels[i]).toByte())
        for (row in 0 until height / 2) for (col in 0 until width / 2) buffer.put(uOf(pixels[row * 2 * width + col * 2]).toByte())
        for (row in 0 until height / 2) for (col in 0 until width / 2) buffer.put(vOf(pixels[row * 2 * width + col * 2]).toByte())
    }

    private fun drain(endOfStream: Boolean) {
        val encoder = codec ?: return
        val mux = muxer ?: return
        val deadline = System.currentTimeMillis() + if (endOfStream) EOS_WAIT_MS else 0
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || System.currentTimeMillis() > deadline) {
                        if (endOfStream) VideoLog.w("GRAVADOR", "O encoder não confirmou o fim do arquivo a tempo")
                        return
                    }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        track = mux.addTrack(encoder.outputFormat)
                        mux.start()
                        muxerStarted = true
                        VideoLog.i("GRAVADOR", "Formato de saída: ${encoder.outputFormat}")
                    }
                }
                index >= 0 -> {
                    val data = encoder.getOutputBuffer(index)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (data != null && info.size > 0 && muxerStarted && !config) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        mux.writeSampleData(track, data, info)
                        bytesOut += info.size
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /**
     * Ends the file. Returns true when it holds at least one frame; an empty file is deleted.
     * Always releases the encoder and muxer, even after an error.
     */
    fun finish(): Boolean {
        var ok = false
        try {
            val encoder = codec
            if (encoder != null) {
                val index = encoder.dequeueInputBuffer(EOS_WAIT_MS * 1000)
                if (index >= 0) encoder.queueInputBuffer(index, 0, 0, lastPtsUs + 1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(endOfStream = true)
            }
            if (muxerStarted) {
                muxer?.stop()
                ok = frames > 0
            }
        } catch (e: Throwable) {
            VideoLog.e("GRAVADOR", "Erro ao fechar o arquivo ${output.name}", e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            codec = null
            muxer = null
            canvasBitmap?.recycle()
            canvasBitmap = null
            canvas = null
            decodeTarget = null
        }
        if (!ok) output.delete()
        return ok
    }

    private companion object {
        const val INPUT_WAIT_US = 20_000L
        const val EOS_WAIT_MS = 3_000L
    }
}
