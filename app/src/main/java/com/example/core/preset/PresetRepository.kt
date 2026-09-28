package com.example.core.preset

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.PresetDao
import com.example.core.data.PresetEntity
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Presets live in the database and, when a presets folder is chosen, also as .xmp files there
 * (the "pasta do projeto"): saving writes the file, and files dropped in the folder are imported.
 */
class PresetRepository(
    private val context: Context,
    private val dao: PresetDao,
    private val settings: SettingsRepository,
) {
    val presets: Flow<List<Preset>> = dao.observeAll().map { list -> list.map { it.toPreset() } }

    fun observe(id: Long): Flow<Preset?> = dao.observe(id).map { it?.toPreset() }

    suspend fun get(id: Long): Preset? = dao.get(id)?.toPreset()

    suspend fun all(): List<Preset> = dao.all().map { it.toPreset() }

    /** Installs the starter pack the first time the app runs with presets. */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        StarterPresets.all.forEach { save(it) }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun markUsed(id: Long) = dao.markUsed(id, System.currentTimeMillis())

    /**
     * Categories: every group that has presets, plus the empty ones the photographer created
     * (kept in Ajustes so a new event category can exist before its first preset).
     */
    val categories: Flow<List<String>> = combine(presets, settings.settings) { list, s ->
        (list.map { it.group } + s.presetCategories).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .sortedWith(compareBy<String> { it != StarterPresets.GROUP }.thenBy { it.lowercase() })
    }

    suspend fun addCategory(name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        val s = settings.current()
        if (clean !in s.presetCategories) settings.set(SettingKeys.presetCategories, (s.presetCategories + clean).joinToString("|"))
    }

    suspend fun renameCategory(from: String, to: String) = withContext(Dispatchers.IO) {
        val clean = to.trim().take(40)
        if (clean.isEmpty() || clean == from) return@withContext
        dao.renameGroup(from, clean)
        val s = settings.current()
        settings.set(SettingKeys.presetCategories, s.presetCategories.map { if (it == from) clean else it }.distinct().joinToString("|"))
        dao.all().filter { it.group == clean }.forEach { runCatching { writeToFolder(it.toPreset()) } }
    }

    /** Removes a category; its presets move to "Meus presets" (nothing is deleted). */
    suspend fun deleteCategory(name: String) = withContext(Dispatchers.IO) {
        dao.renameGroup(name, DEFAULT_CATEGORY)
        val s = settings.current()
        settings.set(SettingKeys.presetCategories, s.presetCategories.filter { it != name }.joinToString("|"))
    }

    suspend fun moveTo(ids: List<Long>, category: String) = withContext(Dispatchers.IO) {
        dao.setGroup(ids, category.trim().ifEmpty { DEFAULT_CATEGORY })
        ids.forEach { id -> dao.get(id)?.let { runCatching { writeToFolder(it.toPreset()) } } }
    }

    /** Inserts or updates, and mirrors the preset to the presets folder. Returns the id. */
    suspend fun save(preset: Preset): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = PresetEntity(
            id = preset.id,
            name = preset.name.trim().ifBlank { "Preset" },
            group = preset.group.trim().ifBlank { DEFAULT_CATEGORY },
            settings = preset.settings.encode(),
            source = preset.source,
            favorite = preset.favorite,
            lastUsed = preset.lastUsed,
            createdAt = dao.get(preset.id)?.createdAt ?: now,
            updatedAt = now,
        )
        val id = if (preset.id == 0L) dao.insert(entity) else entity.id.also { dao.update(entity) }
        runCatching { writeToFolder(entity.toPreset().copy(id = id)) }
        id
    }

    suspend fun delete(preset: Preset) = withContext(Dispatchers.IO) {
        dao.detachFromPhotos(preset.id)
        dao.delete(preset.id)
        runCatching { folder()?.findFile(XmpPresetWriter.fileName(preset))?.delete() }
    }

    suspend fun duplicate(preset: Preset): Long = save(preset.copy(id = 0, name = "${preset.name} (cópia)", source = "criado"))

    /** Imports a picked file (DNG, XMP, LRTemplate or ZIP). Returns what was added. */
    suspend fun import(fileName: String, bytes: ByteArray): List<Preset> = withContext(Dispatchers.IO) {
        PresetImporter.import(fileName, bytes).map { p -> p.copy(id = save(p)) }
    }

    /** Picks up preset files copied into the presets folder by hand or from the computer. */
    suspend fun syncFolder(): Int = withContext(Dispatchers.IO) {
        val dir = folder() ?: return@withContext 0
        val known = dao.all().map { XmpPresetWriter.fileName(it.toPreset()).lowercase() }.toSet()
        var added = 0
        dir.listFiles().filter { it.isFile }.forEach { file ->
            val name = file.name ?: return@forEach
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in setOf("xmp", "dng", "lrtemplate") || name.lowercase() in known) return@forEach
            val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: return@forEach
            runCatching { PresetImporter.import(name, bytes) }.getOrNull()?.forEach { p ->
                if (XmpPresetWriter.fileName(p).lowercase() !in known) {
                    dao.insert(p.toEntity())
                    added++
                }
            }
        }
        added
    }

    /** After a presets folder is picked: copies every preset into it and imports the ones already there. */
    suspend fun attachFolder(): Int = withContext(Dispatchers.IO) {
        val imported = syncFolder()
        dao.all().forEach { runCatching { writeToFolder(it.toPreset()) } }
        imported
    }

    /** An .xmp in the app cache, ready to share (Lightroom, WhatsApp, e-mail). */
    suspend fun exportFile(preset: Preset): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        File(dir, XmpPresetWriter.fileName(preset)).apply { writeText(XmpPresetWriter.write(preset)) }
    }

    private suspend fun folder(): DocumentFile? {
        val uri = settings.current().presetsFolder ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(uri))?.takeIf { it.canWrite() }
    }

    private suspend fun writeToFolder(preset: Preset) {
        val dir = folder() ?: return
        val name = XmpPresetWriter.fileName(preset)
        val file = dir.findFile(name) ?: dir.createFile("application/octet-stream", name) ?: return
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(XmpPresetWriter.write(preset).toByteArray()) }
    }

    private fun Preset.toEntity() = PresetEntity(
        id = id, name = name, group = group, settings = settings.encode(), source = source, favorite = favorite, lastUsed = lastUsed,
        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
    )
}

const val DEFAULT_CATEGORY = "Meus presets"

fun PresetEntity.toPreset() = Preset(
    id = id, name = name, group = group, settings = PresetSettings.decode(settings), source = source, favorite = favorite, lastUsed = lastUsed,
)
