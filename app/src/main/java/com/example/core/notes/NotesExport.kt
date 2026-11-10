package com.example.core.notes

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.Photo
import com.example.core.log.AppLog
import com.example.core.storage.named
import java.io.File

/**
 * Notes as files the photographer can open anywhere: in the notes folder, one folder per photo
 * ("IMG_1234/") with nota.txt and each voice note as audio_1.m4a, audio_2.m4a… The app keeps its own
 * copy to play them; this is the organised one that goes with the backup.
 */
class NotesExport(private val context: Context) {

    fun writeText(treeUri: String?, photo: Photo, text: String?) {
        val root = root(treeUri) ?: return
        runCatching { writeTextInto(root, photo, text) }.onFailure { AppLog.e("NOTAS", "Nota de ${photo.savedBase} não foi para a pasta", it) }
    }

    fun addAudio(treeUri: String?, photo: Photo, audio: File) {
        val root = root(treeUri) ?: return
        runCatching { addAudioInto(root, photo, audio) }.onFailure { AppLog.e("NOTAS", "Áudio de ${photo.savedBase} não foi para a pasta", it) }
    }

    /** nota.txt with the text, or no file when the note was cleared. */
    fun writeTextInto(root: DocumentFile, photo: Photo, text: String?) {
        val dir = folder(root, photo)
        val existing = dir.findFile(TEXT)
        if (text.isNullOrBlank()) { existing?.delete(); return }
        val file = existing ?: dir.createFile("text/plain", TEXT)?.named(TEXT) ?: return
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(text.trim().toByteArray()) }
    }

    /** The next audio_N.m4a in the photo's folder. */
    fun addAudioInto(root: DocumentFile, photo: Photo, audio: File): Uri? {
        val dir = folder(root, photo)
        val next = (dir.listFiles().mapNotNull { Regex("""audio_(\d+)\.m4a""").find(it.name.orEmpty())?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val file = dir.createFile("application/octet-stream", "audio_$next.m4a")?.named("audio_$next.m4a") ?: return null
        context.contentResolver.openOutputStream(file.uri)?.use { out -> audio.inputStream().use { it.copyTo(out) } }
        return file.uri
    }

    private fun folder(root: DocumentFile, photo: Photo): DocumentFile {
        val name = photo.savedBase.replace(Regex("[\\\\/:*?\"<>|]"), "-").ifBlank { "Foto_${photo.id}" }
        return root.findFile(name)?.takeIf { it.isDirectory } ?: root.createDirectory(name) ?: root
    }

    private fun root(treeUri: String?): DocumentFile? =
        treeUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }?.takeIf { it.canWrite() }

    companion object {
        const val TEXT = "nota.txt"
    }
}
