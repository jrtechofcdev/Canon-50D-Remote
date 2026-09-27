package com.example.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class DownloadMode(val label: String, val raw: Boolean, val jpg: Boolean) {
    JPG_ONLY("Só JPG", raw = false, jpg = true),
    RAW_ONLY("Só RAW", raw = true, jpg = false),
    RAW_AND_JPG("RAW + JPG", raw = true, jpg = true),
}

data class Settings(
    val downloadMode: DownloadMode = DownloadMode.RAW_AND_JPG,
    /** SAF tree URIs picked by the user. Null means the app's own folder is used. */
    val jpgFolder: String? = null,
    val rawFolder: String? = null,
    val sessionName: String = "",
    val sessionSubfolder: Boolean = true,
    val renameWithSession: Boolean = false,
    val keepCameraAwake: Boolean = true,
) {
    /** Session label used for grouping in the database, even when no name was typed. */
    val sessionKey: String get() = sessionName.trim().ifEmpty { "Geral" }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val mode = stringPreferencesKey("download_mode")
        val jpgFolder = stringPreferencesKey("jpg_folder")
        val rawFolder = stringPreferencesKey("raw_folder")
        val session = stringPreferencesKey("session_name")
        val subfolder = booleanPreferencesKey("session_subfolder")
        val rename = booleanPreferencesKey("rename_with_session")
        val keepAwake = booleanPreferencesKey("keep_camera_awake")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setDownloadMode(mode: DownloadMode) = edit { it[Keys.mode] = mode.name }
    suspend fun setJpgFolder(uri: String?) = edit { put(it, Keys.jpgFolder, uri) }
    suspend fun setRawFolder(uri: String?) = edit { put(it, Keys.rawFolder, uri) }
    suspend fun setSessionName(name: String) = edit { it[Keys.session] = name }
    suspend fun setSessionSubfolder(on: Boolean) = edit { it[Keys.subfolder] = on }
    suspend fun setRenameWithSession(on: Boolean) = edit { it[Keys.rename] = on }
    suspend fun setKeepCameraAwake(on: Boolean) = edit { it[Keys.keepAwake] = on }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun put(prefs: androidx.datastore.preferences.core.MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value == null) prefs.remove(key) else prefs[key] = value
    }

    private fun Preferences.toSettings() = Settings(
        downloadMode = this[Keys.mode]?.let { runCatching { DownloadMode.valueOf(it) }.getOrNull() }
            ?: DownloadMode.RAW_AND_JPG,
        jpgFolder = this[Keys.jpgFolder],
        rawFolder = this[Keys.rawFolder],
        sessionName = this[Keys.session] ?: "",
        sessionSubfolder = this[Keys.subfolder] ?: true,
        renameWithSession = this[Keys.rename] ?: false,
        keepCameraAwake = this[Keys.keepAwake] ?: true,
    )
}
