package com.example.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.storage.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface BackupState {
    data object Idle : BackupState
    data class Running(val session: String, val done: Int, val total: Int) : BackupState
    data class Finished(val session: String, val verified: Int, val total: Int, val failed: List<String>) : BackupState
}

/** One file to copy: where it is, which folder of the backup it goes to, and its name there. */
data class BackupItem(val sourceUri: String, val folder: String, val name: String)

/**
 * Copies a whole session (RAW, JPG, edited copies, voice notes) to a second place, such as the SD card
 * or an OTG pen drive, and checks every copy by size. Files already there with the same size are skipped,
 * so running it again only finishes what is missing.
 */
class SessionBackup(private val context: Context, private val dao: PhotoDao, private val store: PhotoStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun start(session: String, treeUri: String) {
        if (_state.value is BackupState.Running) return
        scope.launch {
            val items = dao.inSession(session).flatMap(::itemsOf)
            val failed = mutableListOf<String>()
            var verified = 0
            _state.value = BackupState.Running(session, 0, items.size)
            val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            if (root == null || !root.canWrite()) {
                _state.value = BackupState.Finished(session, 0, items.size, listOf("Pasta de backup indisponível. Escolha de novo em Ajustes."))
                return@launch
            }
            val sessionDir = dir(root, session.replace(Regex("[\\\\/:*?\"<>|]"), "-"))
            items.forEachIndexed { i, item ->
                _state.value = BackupState.Running(session, i, items.size)
                if (copyAndVerify(sessionDir, item)) verified++ else failed += "${item.folder}/${item.name}"
            }
            _state.value = BackupState.Finished(session, verified, items.size, failed)
        }
    }

    fun dismiss() {
        if (_state.value is BackupState.Finished) _state.value = BackupState.Idle
    }

    private fun itemsOf(photo: Photo): List<BackupItem> = buildList {
        photo.rawUri?.let { add(BackupItem(it, "RAW", store.displayName(it))) }
        photo.jpgUri?.let { add(BackupItem(it, "JPG", store.displayName(it))) }
        photo.editedUri?.let { add(BackupItem(it, "Editadas", store.displayName(it))) }
        photo.audioNote?.let { add(BackupItem("file://$it", "Notas", File(it).name)) }
    }

    private fun dir(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name) ?: parent

    private fun copyAndVerify(sessionDir: DocumentFile, item: BackupItem): Boolean = runCatching {
        val size = sourceSize(item.sourceUri) ?: return false
        val folder = dir(sessionDir, item.folder)
        folder.findFile(item.name)?.let { existing ->
            if (existing.length() == size) return true
            existing.delete()
        }
        val target = folder.createFile("application/octet-stream", item.name) ?: return false
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            store.openInput(item.sourceUri)?.use { it.copyTo(out, 256 * 1024) } ?: return false
        } ?: return false
        // Read the size back from the destination: a pen drive pulled out mid-copy shows up here.
        DocumentFile.fromSingleUri(context, target.uri)?.length() == size
    }.getOrDefault(false)

    private fun sourceSize(uri: String): Long? {
        val parsed = Uri.parse(uri)
        return if (parsed.scheme == "file") File(parsed.path!!).takeIf { it.exists() }?.length()
        else DocumentFile.fromSingleUri(context, parsed)?.takeIf { it.exists() }?.length()
    }
}
