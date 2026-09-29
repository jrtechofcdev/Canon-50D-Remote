package com.example.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.Settings
import java.io.File
import java.io.IOException
import java.io.OutputStream

enum class FileKind(val folderName: String) { JPG("JPG"), RAW("RAW"), EDITED("Editadas"), SELECTED("Escolhidas") }

// Files are created as octet-stream so the storage provider keeps the camera's name ("IMG_1234.JPG")
// instead of appending its own extension; the media scanner still types them by extension.
private const val CREATE_MIME = "application/octet-stream"

/**
 * Writes downloaded files into the folders chosen in Ajustes (Storage Access Framework).
 * With no folder chosen, files go to Android/data/<app>/files/Pictures/Canon50D/{JPG,RAW},
 * which is readable by the app and shareable, but removed if the app is uninstalled.
 */
class PhotoStore(private val context: Context) {

    class Target(val uri: Uri, val stream: OutputStream)

    /**
     * [moment] adds a "02 Cerimônia" level under the session folder when "subpasta por momento" is on.
     * Picks ([FileKind.SELECTED]) go to an "Escolhidas" folder inside the JPG folder's session.
     */
    fun create(kind: FileKind, fileName: String, settings: Settings, moment: String? = null): Target {
        val treeUri = when (kind) {
            FileKind.JPG, FileKind.SELECTED -> settings.jpgFolder
            FileKind.RAW -> settings.rawFolder
            FileKind.EDITED -> settings.editedFolder
        }
        val path = subfolders(settings, moment).let { if (kind == FileKind.SELECTED && treeUri != null) it + kind.folderName else it }
        return if (treeUri != null) createInTree(Uri.parse(treeUri), path, kind, fileName)
        else createInAppFolder(kind, path, fileName)
    }

    private fun createInTree(treeUri: Uri, path: List<String>, kind: FileKind, fileName: String): Target {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Pasta ${kind.folderName} indisponível. Escolha de novo em Ajustes.")
        if (!root.canWrite()) throw IOException("Sem permissão na pasta ${kind.folderName}. Escolha de novo em Ajustes.")
        val dir = path.fold(root) { parent, name ->
            parent.findFile(name)?.takeIf { it.isDirectory } ?: parent.createDirectory(name)
                ?: throw IOException("Não foi possível criar a pasta $name")
        }
        val file = dir.createFile(CREATE_MIME, fileName)
            ?: throw IOException("Não foi possível criar $fileName")
        val stream = context.contentResolver.openOutputStream(file.uri)
            ?: throw IOException("Não foi possível abrir $fileName")
        return Target(file.uri, stream)
    }

    private fun createInAppFolder(kind: FileKind, path: List<String>, fileName: String): Target {
        val base = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Canon50D/${kind.folderName}")
        val dir = path.fold(base) { parent, name -> File(parent, name) }
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Não foi possível criar ${dir.path}")
        var file = File(dir, fileName)
        var n = 1
        while (file.exists()) file = File(dir, "${fileName.substringBeforeLast('.')}_${n++}.${fileName.substringAfterLast('.')}")
        return Target(Uri.fromFile(file), file.outputStream())
    }

    /** Copies a saved file to [kind]'s folder (e.g. the client's picks). Returns the new file's URI. */
    fun copy(sourceUri: String, kind: FileKind, settings: Settings, moment: String? = null): String {
        val name = displayName(sourceUri).ifBlank { "foto_${System.currentTimeMillis()}" }
        val target = create(kind, name, settings, moment)
        target.stream.use { out ->
            (openInput(sourceUri) ?: throw IOException("Não foi possível ler $name")).use { it.copyTo(out, 256 * 1024) }
        }
        return target.uri.toString()
    }

    fun openInput(uri: String) = Uri.parse(uri).let { parsed ->
        if (parsed.scheme == "file") File(parsed.path!!).inputStream() else context.contentResolver.openInputStream(parsed)
    }

    fun delete(uri: String) {
        val parsed = Uri.parse(uri)
        runCatching {
            if (parsed.scheme == "file") File(parsed.path!!).delete()
            else DocumentsContract.deleteDocument(context.contentResolver, parsed)
        }
    }

    fun previewFile(session: String, baseName: String): File {
        val dir = File(context.cacheDir, "previews").apply { mkdirs() }
        return File(dir, "${session.hashCode()}_$baseName.jpg")
    }

    /** A URI other apps can read: SAF URIs as-is, app-folder files through the FileProvider. */
    fun shareableUri(uri: String): Uri {
        val parsed = Uri.parse(uri)
        return if (parsed.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(parsed.path!!))
        } else parsed
    }

    fun displayName(uri: String): String {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") return File(parsed.path!!).name
        return DocumentFile.fromSingleUri(context, parsed)?.name ?: parsed.lastPathSegment.orEmpty()
    }

    fun takePersistablePermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Human-readable path of a chosen tree, e.g. "Pictures/Clientes/RAW". */
    fun describeTree(uri: String?): String {
        if (uri == null) return "Pasta do app (padrão)"
        val parsed = Uri.parse(uri)
        // A folder the app created inside a chosen one ("Pastas do app") is a document of that tree.
        val docId = runCatching {
            if (parsed.pathSegments.size >= 4 && parsed.pathSegments[2] == "document") DocumentsContract.getDocumentId(parsed)
            else DocumentsContract.getTreeDocumentId(parsed)
        }.getOrNull() ?: return uri
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val prefix = if (volume == "primary") "Armazenamento interno" else "Cartão SD"
        return if (path.isEmpty()) prefix else "$prefix/$path"
    }

    companion object {
        /** Session folder, then the moment folder ("02 Cerimônia", numbered in the order of the list). */
        fun subfolders(settings: Settings, moment: String?): List<String> = buildList {
            settings.sessionName.trim().takeIf { settings.sessionSubfolder && it.isNotEmpty() }?.let { add(safe(it)) }
            if (settings.momentsEnabled && settings.momentSubfolder && !moment.isNullOrBlank()) {
                val index = settings.moments.indexOf(moment)
                add(if (index >= 0) "%02d %s".format(index + 1, safe(moment)) else safe(moment))
            }
        }

        private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
    }
}
