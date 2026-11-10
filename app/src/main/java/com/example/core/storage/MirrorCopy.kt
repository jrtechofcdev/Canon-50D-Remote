package com.example.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

/**
 * The "second card": copies a saved original into another folder tree and checks the copy has the
 * same size, so a failing card or folder is noticed on the photo that had the problem.
 */
class MirrorCopy(private val context: Context) {

    /** Copies [source] to [treeUri]/[path]/[fileName]; an existing file of the same size is kept. */
    fun copy(source: String, treeUri: String, path: List<String>, fileName: String): Uri {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.takeIf { it.canWrite() }
            ?: throw IOException("Sem permissão na pasta do 2º cartão. Escolha de novo em Ajustes → Salvamento.")
        return copyInto(root, source, path, fileName)
    }

    /** The part that works on any folder, also a plain one in tests. */
    fun copyInto(root: DocumentFile, source: String, path: List<String>, fileName: String): Uri {
        val dir = path.filter { it.isNotBlank() }.fold(root) { parent, name ->
            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
            parent.findFile(safe)?.takeIf { it.isDirectory } ?: parent.createDirectory(safe)
                ?: throw IOException("Não deu para criar a pasta $safe no 2º cartão")
        }
        val expected = size(source)
        dir.findFile(fileName)?.let { existing -> if (existing.length() == expected) return existing.uri else existing.delete() }
        val file = dir.createFile("application/octet-stream", fileName)?.named(fileName) ?: throw IOException("Não deu para criar $fileName no 2º cartão")
        (open(source) ?: throw IOException("Não deu para ler $fileName")).use { input ->
            (context.contentResolver.openOutputStream(file.uri) ?: throw IOException("Não deu para gravar $fileName")).use { input.copyTo(it, 256 * 1024) }
        }
        val written = file.length()
        if (expected > 0 && written != expected) {
            file.delete()
            throw IOException("cópia com tamanho diferente ($written de $expected bytes)")
        }
        return file.uri
    }

    private fun open(uri: String) = Uri.parse(uri).let { if (it.scheme == "file") File(it.path!!).inputStream() else context.contentResolver.openInputStream(it) }

    private fun size(uri: String): Long = Uri.parse(uri).let { parsed ->
        if (parsed.scheme == "file") File(parsed.path!!).length()
        else DocumentFile.fromSingleUri(context, parsed)?.length() ?: -1L
    }
}

/**
 * Some providers add an extension of their own to a new file ("nota.txt.txt"); put the exact name back.
 */
fun DocumentFile.named(name: String): DocumentFile {
    if (this.name != name) renameTo(name)
    return this
}
