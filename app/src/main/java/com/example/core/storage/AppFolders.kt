package com.example.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.Preferences
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import com.example.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's folder structure, created in one go inside the folder the photographer picks
 * ("JR C50D Sync" at the root of the phone) and linked to every setting that saves something.
 * The same layout as the zip handed out in 3.7, plus Notas and the second-card copy.
 */
object AppFolders {

    const val ROOT_NAME = "JR C50D Sync"

    /** One folder: where it sits, what goes in it, and which setting points at it. */
    data class Folder(val path: List<String>, val label: String, val key: Preferences.Key<String>?)

    val layout: List<Folder> = listOf(
        Folder(listOf("01 Fotos", "JPG"), "Fotos JPG", SettingKeys.jpgFolder),
        Folder(listOf("01 Fotos", "RAW"), "Fotos RAW", SettingKeys.rawFolder),
        Folder(listOf("01 Fotos", "Editadas"), "Cópias editadas", SettingKeys.editedFolder),
        Folder(listOf("02 Presets"), "Presets (.xmp)", SettingKeys.presetsFolder),
        Folder(listOf("02 Presets", "JR_LR_Presets", "JR_LR_Presets_DNG"), "Loja: DNG", null),
        Folder(listOf("02 Presets", "JR_LR_Presets", "JR_LR_Presets_XMP"), "Loja: XMP", null),
        Folder(listOf("03 Marca dagua"), "Marca d'água", null),
        Folder(listOf("04 Notas"), "Notas das fotos", SettingKeys.notesFolder),
        Folder(listOf("05 Backup", "Espelho"), "2º cartão (cópia de cada foto)", SettingKeys.mirrorFolder),
        Folder(listOf("05 Backup", "Sessoes"), "Backup da sessão", SettingKeys.backupFolder),
        Folder(listOf("05 Backup", "Ajustes do app"), "Backup dos ajustes", null),
        Folder(listOf("06 Logs"), "Logs", SettingKeys.logFolder),
    )

    /** The folders a setting points at, for the checklist in Salvamento. */
    val linked: List<Folder> get() = layout.filter { it.key != null }

    data class Result(val created: Int, val linked: Int, val uris: Map<List<String>, Uri>)

    /**
     * Creates what is missing under [root] (existing folders are reused, nothing is deleted) and
     * returns each folder's URI. When [root] is not already called "JR C50D Sync", the structure
     * goes into a "JR C50D Sync" folder inside it, so picking the phone's root works too.
     */
    fun create(root: DocumentFile): Pair<DocumentFile, Result> {
        var created = 0
        fun dir(parent: DocumentFile, name: String): DocumentFile =
            parent.findFile(name)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(name)?.also { created++ }
                ?: throw IllegalStateException("Não deu para criar a pasta $name")
        val base = if (root.name == ROOT_NAME) root else dir(root, ROOT_NAME)
        val uris = layout.associate { folder -> folder.path to folder.path.fold(base) { parent, name -> dir(parent, name) }.uri }
        return base to Result(created, layout.count { it.key != null }, uris)
    }

    /** Creates the structure in the picked folder and points every setting at its folder. */
    suspend fun createAndLink(context: Context, settings: SettingsRepository, tree: Uri): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.canWrite() }
            ?: throw IllegalStateException("Sem permissão nessa pasta. Escolha de novo.")
        val (base, result) = create(root)
        settings.setOrClear(SettingKeys.appFolder, base.uri.toString())
        layout.forEach { folder -> folder.key?.let { key -> settings.setOrClear(key, result.uris[folder.path]?.toString()) } }
        // With its folder ready, the second card starts working right away.
        settings.set(SettingKeys.mirrorEnabled, true)
        AppLog.i("PASTAS", "Estrutura criada: ${result.created} pastas novas, ${result.linked} ligadas")
        result
    }

    /** Opens the folder picker on the phone's root ("Armazenamento interno"). */
    val phoneRoot: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:")
}
