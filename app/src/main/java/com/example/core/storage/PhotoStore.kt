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

enum class FileKind(val folderName: String) { JPG("JPG"), RAW("RAW") }

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

    fun create(kind: FileKind, fileName: String, settings: Settings): Target {
        val treeUri = when (kind) {
            FileKind.JPG -> settings.jpgFolder
            FileKind.RAW -> settings.rawFolder
        }
        val subfolder = settings.sessionName.trim().takeIf { settings.sessionSubfolder && it.isNotEmpty() }
        return if (treeUri != null) createInTree(Uri.parse(treeUri), subfolder, kind, fileName)
        else createInAppFolder(kind, subfolder, fileName)
    }

    private fun createInTree(treeUri: Uri, subfolder: String?, kind: FileKind, fileName: String): Target {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Pasta ${kind.folderName} indisponível. Escolha de novo em Ajustes.")
        if (!root.canWrite()) throw IOException("Sem permissão na pasta ${kind.folderName}. Escolha de novo em Ajustes.")
        val dir = subfolder?.let { name ->
            root.findFile(name)?.takeIf { it.isDirectory } ?: root.createDirectory(name)
        } ?: root
        val file = dir.createFile(CREATE_MIME, fileName)
            ?: throw IOException("Não foi possível criar $fileName")
        val stream = context.contentResolver.openOutputStream(file.uri)
            ?: throw IOException("Não foi possível abrir $fileName")
        return Target(file.uri, stream)
    }

    private fun createInAppFolder(kind: FileKind, subfolder: String?, fileName: String): Target {
        val base = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Canon50D/${kind.folderName}")
        val dir = if (subfolder != null) File(base, subfolder) else base
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Não foi possível criar ${dir.path}")
        var file = File(dir, fileName)
        var n = 1
        while (file.exists()) file = File(dir, "${fileName.substringBeforeLast('.')}_${n++}.${fileName.substringAfterLast('.')}")
        return Target(Uri.fromFile(file), file.outputStream())
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
        val docId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }.getOrNull() ?: return uri
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val prefix = if (volume == "primary") "Armazenamento interno" else "Cartão SD"
        return if (path.isEmpty()) prefix else "$prefix/$path"
    }
}
