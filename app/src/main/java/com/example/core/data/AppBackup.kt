package com.example.core.data

import com.example.core.preset.Preset
import com.example.core.preset.PresetRepository
import com.example.core.preset.PresetSettings
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** What a restore brought in, for the message shown afterwards. */
data class RestoreReport(val settings: Int, val profiles: Int, val presets: Int)

/**
 * The app's whole setup in one .json file: options, saved profiles and presets (with their
 * categories and favourites). Photos are not in it; the session backup takes care of those.
 * Folders are left out because their permission only works on the phone that picked them.
 */
class AppBackup(private val settings: SettingsRepository, private val presets: PresetRepository) {

    suspend fun export(version: String): String {
        val json = JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("version", version)
            .put("created", System.currentTimeMillis())
            .put("settings", SettingsProfiles.encodeValues(settings.snapshot(SettingsProfiles.deviceOnly)))
            .put("profiles", JSONArray(settings.profiles.first().map(SettingsProfiles::encodeProfile)))
            .put("presets", JSONArray(presets.all().map(::encodePreset)))
        return json.toString(2)
    }

    /**
     * Restores a file made by [export]. Options are replaced (folders stay as they are on this
     * phone), profiles with the same name are replaced, and presets that already exist with the
     * same name and category are kept as they are.
     */
    suspend fun restore(text: String): RestoreReport {
        val json = runCatching { JSONObject(text) }.getOrNull()
        require(json != null && json.optString("app") == APP) { "Este arquivo não é um backup do JR C50D Sync" }

        val values = SettingsProfiles.decodeValues(json.optJSONObject("settings") ?: JSONObject())
            .filterKeys { it !in SettingsProfiles.deviceOnly }
        settings.apply(values, replace = true, keep = SettingsProfiles.deviceOnly)

        val incoming = json.optJSONArray("profiles")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(SettingsProfiles::decodeProfile) } }.orEmpty()
        if (incoming.isNotEmpty()) {
            val names = incoming.map { it.name }.toSet()
            settings.saveProfiles(incoming + settings.profiles.first().filter { it.name !in names })
        }

        val existing = presets.all().map { it.group to it.name }.toSet()
        var added = 0
        json.optJSONArray("presets")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.optJSONObject(i)?.let(::decodePreset) ?: continue
                if ((p.group to p.name) in existing) continue
                presets.save(p)
                added++
            }
        }
        return RestoreReport(values.size, incoming.size, added)
    }

    private fun encodePreset(p: Preset) = JSONObject()
        .put("name", p.name)
        .put("group", p.group)
        .put("source", p.source)
        .put("favorite", p.favorite)
        .put("settings", p.settings.encode())

    private fun decodePreset(o: JSONObject): Preset? {
        val name = o.optString("name").trim().ifEmpty { return null }
        return Preset(
            name = name,
            group = o.optString("group").ifBlank { com.example.core.preset.DEFAULT_CATEGORY },
            settings = PresetSettings.decode(o.optString("settings")),
            source = o.optString("source", "criado"),
            favorite = o.optBoolean("favorite"),
        )
    }

    companion object {
        const val APP = "JR C50D Sync"
        const val FORMAT = 1
    }
}
