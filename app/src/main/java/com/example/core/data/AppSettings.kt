package com.example.core.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

/**
 * Every user-facing option in one place. Each feature has its own switch so it can be turned off.
 * Defaults: passive helpers on, anything that changes files or camera behaviour off.
 */
data class Settings(
    // Salvamento
    val downloadMode: DownloadMode = DownloadMode.RAW_AND_JPG,
    /** SAF tree URIs picked by the user. Null means the app's own folder is used. */
    val jpgFolder: String? = null,
    val rawFolder: String? = null,
    val sessionName: String = "",
    val sessionSubfolder: Boolean = true,
    val renameWithSession: Boolean = false,

    // Revisão
    val autoSlideToNew: Boolean = true,
    val autoSlideOnlyAtLatest: Boolean = false,
    val autoOpenViewer: Boolean = false,
    val ratingEnabled: Boolean = true,
    val highlightAlert: Boolean = true,
    val highlightThreshold: Int = 250,
    val histogramEnabled: Boolean = true,
    val histogramRgb: Boolean = true,

    // Câmera
    val keepCameraAwake: Boolean = true,
    val showBattery: Boolean = true,
    val cardBrowserEnabled: Boolean = true,
    val presetsEnabled: Boolean = true,
    val presets: String = "",
    val aebEnabled: Boolean = false,
    val aebShots: Int = 3,
    val aebStepThirds: Int = 3,

    // Alertas
    val vibrateOnReceive: Boolean = true,
    val alertOnDisconnect: Boolean = true,
    val cardLowWarning: Boolean = true,
    val cardLowShots: Int = 50,
    val phoneLowWarning: Boolean = true,
    val phoneLowGb: Int = 2,
    val autoResend: Boolean = true,
    val resendAsk: Boolean = false,
    val sessionSummary: Boolean = true,

    // Envio & cliente
    val lightShareEnabled: Boolean = true,
    val lightShareSize: Int = 2048,
    val lightShareQuality: Int = 85,
    val watermarkEnabled: Boolean = true,
    /** One of [WatermarkSource] ids; "custom" uses [watermarkPath]. */
    val watermarkSource: String = WatermarkSource.HorizontalWhite.id,
    val watermarkPath: String? = null,
    val watermarkPosition: Int = 8,
    val watermarkSizePct: Int = 20,
    val watermarkOpacity: Int = 80,
    val watermarkMarginPct: Int = 3,
    val clientModeEnabled: Boolean = true,
    val clientShowInfo: Boolean = false,
    val clientShowBrand: Boolean = true,
    val clientSlideSeconds: Int = 0,
    val clientOnlyFavorites: Boolean = false,
    val clientPin: String = "",

    // Presets
    val presetsFolder: String? = null,
    val editedFolder: String? = null,
    /** Preset applied to new photos; -1 = none. */
    val sessionPresetId: Long = -1,
    val applyPresetToNew: Boolean = true,
    /** A calibration carried to the next photos ("Aplicar às próximas"); empty = use the preset as is. */
    val sessionAdjust: String = "",
    val saveEditedCopy: Boolean = true,
    val editedQuality: Int = 92,
    val clientPresetsEnabled: Boolean = true,
    val clientCalibratePin: Boolean = true,

    // Tela
    val nightMode: Boolean = false,
    val nightBrightness: Int = 15,
    val keepScreenOn: Boolean = true,
) {
    /** Session label used for grouping in the database, even when no name was typed. */
    val sessionKey: String get() = sessionName.trim().ifEmpty { "Geral" }

    val exposurePresets: List<ExposurePreset> get() = ExposurePreset.decode(presets)
}

/** Jr_Tech_OFC logos bundled in assets/watermarks, plus the user's own PNG. */
enum class WatermarkSource(val id: String, val label: String, val asset: String?) {
    HorizontalWhite("horizontal_branco", "Logo horizontal branco", "watermarks/horizontal_branco.png"),
    HorizontalBrand("horizontal_cores", "Logo horizontal nas cores", "watermarks/horizontal_cores.png"),
    Stacked("empilhado", "Logo completo", "watermarks/empilhado.png"),
    Symbol("simbolo", "Só o símbolo", "watermarks/simbolo.png"),
    Custom("custom", "Meu PNG", null);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: HorizontalWhite
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

/** DataStore keys. Screens write through [SettingsRepository.set] with one of these. */
object SettingKeys {
    val downloadMode = stringPreferencesKey("download_mode")
    val jpgFolder = stringPreferencesKey("jpg_folder")
    val rawFolder = stringPreferencesKey("raw_folder")
    val sessionName = stringPreferencesKey("session_name")
    val sessionSubfolder = booleanPreferencesKey("session_subfolder")
    val renameWithSession = booleanPreferencesKey("rename_with_session")

    val autoSlideToNew = booleanPreferencesKey("auto_slide_to_new")
    val autoSlideOnlyAtLatest = booleanPreferencesKey("auto_slide_only_at_latest")
    val autoOpenViewer = booleanPreferencesKey("auto_open_viewer")
    val ratingEnabled = booleanPreferencesKey("rating_enabled")
    val highlightAlert = booleanPreferencesKey("highlight_alert")
    val highlightThreshold = intPreferencesKey("highlight_threshold")
    val histogramEnabled = booleanPreferencesKey("histogram_enabled")
    val histogramRgb = booleanPreferencesKey("histogram_rgb")

    val keepCameraAwake = booleanPreferencesKey("keep_camera_awake")
    val showBattery = booleanPreferencesKey("show_battery")
    val cardBrowserEnabled = booleanPreferencesKey("card_browser_enabled")
    val presetsEnabled = booleanPreferencesKey("presets_enabled")
    val presets = stringPreferencesKey("presets")
    val aebEnabled = booleanPreferencesKey("aeb_enabled")
    val aebShots = intPreferencesKey("aeb_shots")
    val aebStepThirds = intPreferencesKey("aeb_step_thirds")

    val vibrateOnReceive = booleanPreferencesKey("vibrate_on_receive")
    val alertOnDisconnect = booleanPreferencesKey("alert_on_disconnect")
    val cardLowWarning = booleanPreferencesKey("card_low_warning")
    val cardLowShots = intPreferencesKey("card_low_shots")
    val phoneLowWarning = booleanPreferencesKey("phone_low_warning")
    val phoneLowGb = intPreferencesKey("phone_low_gb")
    val autoResend = booleanPreferencesKey("auto_resend")
    val resendAsk = booleanPreferencesKey("resend_ask")
    val sessionSummary = booleanPreferencesKey("session_summary")

    val lightShareEnabled = booleanPreferencesKey("light_share_enabled")
    val lightShareSize = intPreferencesKey("light_share_size")
    val lightShareQuality = intPreferencesKey("light_share_quality")
    val watermarkEnabled = booleanPreferencesKey("watermark_enabled")
    val watermarkSource = stringPreferencesKey("watermark_source")
    val watermarkPath = stringPreferencesKey("watermark_path")
    val watermarkPosition = intPreferencesKey("watermark_position")
    val watermarkSizePct = intPreferencesKey("watermark_size_pct")
    val watermarkOpacity = intPreferencesKey("watermark_opacity")
    val watermarkMarginPct = intPreferencesKey("watermark_margin_pct")
    val clientModeEnabled = booleanPreferencesKey("client_mode_enabled")
    val clientShowInfo = booleanPreferencesKey("client_show_info")
    val clientShowBrand = booleanPreferencesKey("client_show_brand")
    val clientSlideSeconds = intPreferencesKey("client_slide_seconds")
    val clientOnlyFavorites = booleanPreferencesKey("client_only_favorites")
    val clientPin = stringPreferencesKey("client_pin")

    val presetsFolder = stringPreferencesKey("presets_folder")
    val editedFolder = stringPreferencesKey("edited_folder")
    val sessionPresetId = longPreferencesKey("session_preset_id")
    val applyPresetToNew = booleanPreferencesKey("apply_preset_to_new")
    val sessionAdjust = stringPreferencesKey("session_adjust")
    val saveEditedCopy = booleanPreferencesKey("save_edited_copy")
    val editedQuality = intPreferencesKey("edited_quality")
    val clientPresetsEnabled = booleanPreferencesKey("client_presets_enabled")
    val clientCalibratePin = booleanPreferencesKey("client_calibrate_pin")

    val nightMode = booleanPreferencesKey("night_mode")
    val nightBrightness = intPreferencesKey("night_brightness")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    /** Stores a nullable string; null removes the key so the default applies again. */
    suspend fun setOrClear(key: Preferences.Key<String>, value: String?) {
        context.dataStore.edit { prefs: MutablePreferences -> if (value == null) prefs.remove(key) else prefs[key] = value }
    }

    suspend fun setDownloadMode(mode: DownloadMode) = set(SettingKeys.downloadMode, mode.name)

    suspend fun savePresets(list: List<ExposurePreset>) = set(SettingKeys.presets, ExposurePreset.encode(list))

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        val k = SettingKeys
        return Settings(
            downloadMode = this[k.downloadMode]?.let { runCatching { DownloadMode.valueOf(it) }.getOrNull() } ?: d.downloadMode,
            jpgFolder = this[k.jpgFolder],
            rawFolder = this[k.rawFolder],
            sessionName = this[k.sessionName] ?: d.sessionName,
            sessionSubfolder = this[k.sessionSubfolder] ?: d.sessionSubfolder,
            renameWithSession = this[k.renameWithSession] ?: d.renameWithSession,

            autoSlideToNew = this[k.autoSlideToNew] ?: d.autoSlideToNew,
            autoSlideOnlyAtLatest = this[k.autoSlideOnlyAtLatest] ?: d.autoSlideOnlyAtLatest,
            autoOpenViewer = this[k.autoOpenViewer] ?: d.autoOpenViewer,
            ratingEnabled = this[k.ratingEnabled] ?: d.ratingEnabled,
            highlightAlert = this[k.highlightAlert] ?: d.highlightAlert,
            highlightThreshold = this[k.highlightThreshold] ?: d.highlightThreshold,
            histogramEnabled = this[k.histogramEnabled] ?: d.histogramEnabled,
            histogramRgb = this[k.histogramRgb] ?: d.histogramRgb,

            keepCameraAwake = this[k.keepCameraAwake] ?: d.keepCameraAwake,
            showBattery = this[k.showBattery] ?: d.showBattery,
            cardBrowserEnabled = this[k.cardBrowserEnabled] ?: d.cardBrowserEnabled,
            presetsEnabled = this[k.presetsEnabled] ?: d.presetsEnabled,
            presets = this[k.presets] ?: d.presets,
            aebEnabled = this[k.aebEnabled] ?: d.aebEnabled,
            aebShots = this[k.aebShots] ?: d.aebShots,
            aebStepThirds = this[k.aebStepThirds] ?: d.aebStepThirds,

            vibrateOnReceive = this[k.vibrateOnReceive] ?: d.vibrateOnReceive,
            alertOnDisconnect = this[k.alertOnDisconnect] ?: d.alertOnDisconnect,
            cardLowWarning = this[k.cardLowWarning] ?: d.cardLowWarning,
            cardLowShots = this[k.cardLowShots] ?: d.cardLowShots,
            phoneLowWarning = this[k.phoneLowWarning] ?: d.phoneLowWarning,
            phoneLowGb = this[k.phoneLowGb] ?: d.phoneLowGb,
            autoResend = this[k.autoResend] ?: d.autoResend,
            resendAsk = this[k.resendAsk] ?: d.resendAsk,
            sessionSummary = this[k.sessionSummary] ?: d.sessionSummary,

            lightShareEnabled = this[k.lightShareEnabled] ?: d.lightShareEnabled,
            lightShareSize = this[k.lightShareSize] ?: d.lightShareSize,
            lightShareQuality = this[k.lightShareQuality] ?: d.lightShareQuality,
            watermarkEnabled = this[k.watermarkEnabled] ?: d.watermarkEnabled,
            watermarkSource = this[k.watermarkSource] ?: d.watermarkSource,
            watermarkPath = this[k.watermarkPath],
            watermarkPosition = this[k.watermarkPosition] ?: d.watermarkPosition,
            watermarkSizePct = this[k.watermarkSizePct] ?: d.watermarkSizePct,
            watermarkOpacity = this[k.watermarkOpacity] ?: d.watermarkOpacity,
            watermarkMarginPct = this[k.watermarkMarginPct] ?: d.watermarkMarginPct,
            clientModeEnabled = this[k.clientModeEnabled] ?: d.clientModeEnabled,
            clientShowInfo = this[k.clientShowInfo] ?: d.clientShowInfo,
            clientShowBrand = this[k.clientShowBrand] ?: d.clientShowBrand,
            clientSlideSeconds = this[k.clientSlideSeconds] ?: d.clientSlideSeconds,
            clientOnlyFavorites = this[k.clientOnlyFavorites] ?: d.clientOnlyFavorites,
            clientPin = this[k.clientPin] ?: d.clientPin,

            presetsFolder = this[k.presetsFolder],
            editedFolder = this[k.editedFolder],
            sessionPresetId = this[k.sessionPresetId] ?: d.sessionPresetId,
            applyPresetToNew = this[k.applyPresetToNew] ?: d.applyPresetToNew,
            sessionAdjust = this[k.sessionAdjust] ?: d.sessionAdjust,
            saveEditedCopy = this[k.saveEditedCopy] ?: d.saveEditedCopy,
            editedQuality = this[k.editedQuality] ?: d.editedQuality,
            clientPresetsEnabled = this[k.clientPresetsEnabled] ?: d.clientPresetsEnabled,
            clientCalibratePin = this[k.clientCalibratePin] ?: d.clientCalibratePin,

            nightMode = this[k.nightMode] ?: d.nightMode,
            nightBrightness = this[k.nightBrightness] ?: d.nightBrightness,
            keepScreenOn = this[k.keepScreenOn] ?: d.keepScreenOn,
        )
    }
}
