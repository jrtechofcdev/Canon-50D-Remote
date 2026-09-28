package com.example.core.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * A named set of options applied in one tap, so the app is ready for the job before it starts.
 * [values] maps a setting's key name to its value; options not in it stay as they are.
 * [icon] is one of [ProfileIcon] ids.
 */
data class SettingsProfile(
    val name: String,
    val icon: String,
    val values: Map<String, Any>,
    val builtIn: Boolean = false,
)

/** Icons a profile can have, so each one is recognised at a glance. */
enum class ProfileIcon(val id: String, val label: String) {
    Church("church", "Igreja"),
    Rings("rings", "Casamento"),
    Outdoor("outdoor", "Externo"),
    Party("party", "Festa"),
    Studio("studio", "Estúdio"),
    Camera("camera", "Câmera"),
    Star("star", "Favorito");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Camera
    }
}

object SettingsProfiles {

    /** Kept in the data store next to the options, but never part of a profile. */
    val PROFILES = stringPreferencesKey("settings_profiles")
    /** Name of the last profile applied, shown in Sessão. */
    val ACTIVE = stringPreferencesKey("active_profile")

    /**
     * What belongs to this phone or this job and never goes into a profile: folders (their
     * permission only works on this install), the PIN, names, history and preset choices.
     */
    val personal: Set<String> = setOf(
        SettingKeys.jpgFolder, SettingKeys.rawFolder, SettingKeys.presetsFolder, SettingKeys.editedFolder,
        SettingKeys.backupFolder, SettingKeys.watermarkPath, SettingKeys.sessionName, SettingKeys.currentMoment,
        SettingKeys.recentContacts, SettingKeys.clientPin, SettingKeys.sessionAdjust, SettingKeys.presetCategories,
        SettingKeys.sessionPresetId, SettingKeys.livePresetId, SettingKeys.presets, SettingKeys.logFolder, PROFILES, ACTIVE,
    ).map { it.name }.toSet()

    /** Left out of a backup file: folder permissions and preset ids don't carry over to another phone. */
    val deviceOnly: Set<String> = setOf(
        SettingKeys.jpgFolder, SettingKeys.rawFolder, SettingKeys.presetsFolder, SettingKeys.editedFolder,
        SettingKeys.backupFolder, SettingKeys.watermarkPath, SettingKeys.sessionPresetId, SettingKeys.livePresetId,
        SettingKeys.logFolder, PROFILES, ACTIVE,
    ).map { it.name }.toSet()

    private class Builder {
        val map = linkedMapOf<String, Any>()
        operator fun <T : Any> Preferences.Key<T>.invoke(value: T) { map[name] = value }
    }

    private fun profile(name: String, icon: ProfileIcon, block: Builder.() -> Unit) =
        SettingsProfile(name, icon.id, Builder().apply(block).map, builtIn = true)

    /** Ready-made sets for the usual jobs. They can be applied as they are, or tuned and saved as new. */
    val starters: List<SettingsProfile> = listOf(
        profile("Casamento / igreja", ProfileIcon.Church) {
            SettingKeys.downloadMode(DownloadMode.RAW_AND_JPG.name)
            SettingKeys.nightMode(true)
            SettingKeys.nightBrightness(15)
            SettingKeys.keepScreenOn(true)
            SettingKeys.autoSlideToNew(true)
            SettingKeys.autoOpenViewer(false)
            SettingKeys.momentsEnabled(true)
            SettingKeys.momentSubfolder(true)
            SettingKeys.blurAlert(true)
            SettingKeys.eyesCheck(true)
            SettingKeys.blurVibrate(true)
            SettingKeys.cullSuggestions(true)
            SettingKeys.clientModeEnabled(true)
            SettingKeys.liveOnCamera(false)
            SettingKeys.alertOnDisconnect(true)
            SettingKeys.backupOffer(true)
        },
        profile("Ensaio externo", ProfileIcon.Outdoor) {
            SettingKeys.downloadMode(DownloadMode.RAW_AND_JPG.name)
            SettingKeys.nightMode(false)
            SettingKeys.autoOpenViewer(true)
            SettingKeys.highlightAlert(true)
            SettingKeys.highlightThreshold(245)
            SettingKeys.histogramEnabled(true)
            SettingKeys.momentsEnabled(false)
            SettingKeys.clientModeEnabled(true)
            SettingKeys.clientPresetsEnabled(true)
            SettingKeys.clientWhatsApp(true)
            SettingKeys.clientPicksEnabled(true)
            SettingKeys.whatsAutoMessage(true)
            SettingKeys.liveZebra(true)
            SettingKeys.eyesCheck(true)
        },
        profile("Evento / festa", ProfileIcon.Party) {
            SettingKeys.downloadMode(DownloadMode.JPG_ONLY.name)
            SettingKeys.nightMode(false)
            SettingKeys.autoSlideToNew(true)
            SettingKeys.autoSlideOnlyAtLatest(true)
            SettingKeys.autoOpenViewer(false)
            SettingKeys.momentsEnabled(true)
            SettingKeys.blurAlert(true)
            SettingKeys.eyesCheck(true)
            SettingKeys.cullSuggestions(true)
            SettingKeys.hideRejected(true)
            SettingKeys.clientModeEnabled(true)
            SettingKeys.clientSlideSeconds(5)
            SettingKeys.lightShareSize(1600)
            SettingKeys.vibrateOnReceive(false)
        },
        profile("Estúdio / produto", ProfileIcon.Studio) {
            SettingKeys.downloadMode(DownloadMode.RAW_AND_JPG.name)
            SettingKeys.nightMode(false)
            SettingKeys.autoOpenViewer(true)
            SettingKeys.histogramEnabled(true)
            SettingKeys.histogramRgb(true)
            SettingKeys.highlightAlert(true)
            SettingKeys.highlightThreshold(250)
            SettingKeys.eyesCheck(false)
            SettingKeys.momentsEnabled(false)
            SettingKeys.livePeaking(true)
            SettingKeys.liveGrid("thirds")
            SettingKeys.liveHistogram(true)
        },
    )

    // ---- JSON -------------------------------------------------------------------------------------
    // Each value carries its type in the name ("b:night_mode"), so it goes back into the right key.

    fun encodeValues(values: Map<String, Any>): JSONObject {
        val json = JSONObject()
        values.forEach { (name, value) ->
            when (value) {
                is Boolean -> json.put("b:$name", value)
                is Int -> json.put("i:$name", value)
                is Long -> json.put("l:$name", value)
                is Float -> json.put("f:$name", value.toDouble())
                is Double -> json.put("d:$name", value)
                is String -> json.put("s:$name", value)
                is Set<*> -> json.put("ss:$name", JSONArray(value.map { it.toString() }))
            }
        }
        return json
    }

    fun decodeValues(json: JSONObject): Map<String, Any> {
        val out = linkedMapOf<String, Any>()
        json.keys().forEach { tagged ->
            val type = tagged.substringBefore(':')
            val name = tagged.substringAfter(':')
            val value: Any? = when (type) {
                "b" -> json.optBoolean(tagged)
                "i" -> json.optInt(tagged)
                "l" -> json.optLong(tagged)
                "f" -> json.optDouble(tagged).toFloat()
                "d" -> json.optDouble(tagged)
                "s" -> json.optString(tagged)
                "ss" -> json.optJSONArray(tagged)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> null
            }
            if (value != null && name.isNotEmpty()) out[name] = value
        }
        return out
    }

    fun encodeProfile(p: SettingsProfile): JSONObject =
        JSONObject().put("name", p.name).put("icon", p.icon).put("values", encodeValues(p.values))

    fun decodeProfile(json: JSONObject): SettingsProfile? {
        val name = json.optString("name").trim().ifEmpty { return null }
        return SettingsProfile(name, json.optString("icon", ProfileIcon.Camera.id), decodeValues(json.optJSONObject("values") ?: JSONObject()))
    }

    fun encodeList(list: List<SettingsProfile>): String = JSONArray(list.map(::encodeProfile)).toString()

    fun decodeList(text: String?): List<SettingsProfile> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::decodeProfile) }
    }

    /** The typed data store key for a value read back from JSON. */
    @Suppress("UNCHECKED_CAST")
    fun keyFor(name: String, value: Any): Preferences.Key<Any>? = when (value) {
        is Boolean -> booleanPreferencesKey(name)
        is Int -> intPreferencesKey(name)
        is Long -> longPreferencesKey(name)
        is Float -> floatPreferencesKey(name)
        is Double -> doublePreferencesKey(name)
        is String -> stringPreferencesKey(name)
        is Set<*> -> stringSetPreferencesKey(name)
        else -> null
    } as Preferences.Key<Any>?

    /** How many of [profile]'s options differ from [current] (to show "muda 5 ajustes"). */
    fun changes(profile: SettingsProfile, current: Map<String, Any>): Int =
        profile.values.count { (k, v) -> current[k] != v }
}
