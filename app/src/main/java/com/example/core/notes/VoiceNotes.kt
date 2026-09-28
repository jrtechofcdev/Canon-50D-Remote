package com.example.core.notes

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Short voice notes per photo: AAC in .m4a, kept in the app folder (backed up with the session). */
class VoiceNotes(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var recording: File? = null
    private var player: MediaPlayer? = null

    val dir: File get() = File(context.getExternalFilesDir(null), "Notas").apply { mkdirs() }

    fun start(photoBase: String): Boolean {
        stopPlayback()
        val file = File(dir, "${photoBase}_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recording = file
            true
        }.getOrElse { r.release(); false }
    }

    /** Stops and returns the file, or null when it was too short to keep (a tap instead of a hold). */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = recording
        recorder = null
        recording = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || file == null || file.length() < 2_000) {
            file?.delete()
            return null
        }
        return file
    }

    fun play(path: String, onDone: () -> Unit) {
        stopPlayback()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { onDone(); stopPlayback() }
                prepare()
                start()
            }
        }.getOrNull()
        if (player == null) onDone()
    }

    fun stopPlayback() {
        player?.runCatching { stop(); release() }
        player = null
    }

    fun delete(path: String) {
        File(path).delete()
    }
}
