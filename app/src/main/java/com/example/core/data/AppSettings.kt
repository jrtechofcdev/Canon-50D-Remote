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
import com.example.core.messages.MessageDefaults
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
    /** Preset strip and Calibrar button in the photo viewer. */
    val viewerPresets: Boolean = true,

    // Dia a dia (2.5 / 2.6)
    val momentsEnabled: Boolean = true,
    /** Moments offered in the Session, separated by |. */
    val momentList: String = "Making of|Cerimônia|Fotos oficiais|Família|Recepção|Festa",
    val currentMoment: String = "",
    val momentSubfolder: Boolean = false,
    val notesEnabled: Boolean = true,
    val clientPicksEnabled: Boolean = true,
    val msgPreview: String = MessageDefaults.PREVIEW,
    val msgDelivered: String = MessageDefaults.DELIVERED,
    val msgPayment: String = MessageDefaults.PAYMENT,
    val msgSelection: String = MessageDefaults.SELECTION,
    /** Off by default: it needs the location permission. */
    val gpsEnabled: Boolean = false,
    val backupFolder: String? = null,
    val backupOffer: Boolean = true,
    val syncCameraClock: Boolean = false,
    val toolsEnabled: Boolean = true,
    val blurAlert: Boolean = true,
    /** Sharpness below this (0-100 scale) marks a frame as shaken or out of focus. */
    val blurThreshold: Int = 50,
    val blurVibrate: Boolean = true,
    val eyesCheck: Boolean = true,
    val cullSuggestions: Boolean = true,
    val hideRejected: Boolean = true,
    /** Last clients sent to on WhatsApp, "number|name;…". */
    val recentContacts: String = "",
    /** WhatsApp button in client mode: the client types their own number and gets the photo. */
    val clientWhatsApp: Boolean = true,
    /** Preset categories created by hand, "|" separated (the others come from the presets themselves). */
    val presetCategoryText: String = "",
    /** Visible Sair button in client mode (asks the PIN when one is set). */
    val clientExitButton: Boolean = true,
    /** Also leave client mode by holding one finger still for 3 s. */
    val clientExitHold: Boolean = true,
    val clientCropEnabled: Boolean = true,
    /** The client's name is asked before sending on WhatsApp. */
    val whatsRequireName: Boolean = true,
    /** The WhatsApp chat opens with [msgWhatsApp] already typed. */
    val whatsAutoMessage: Boolean = true,
    val msgWhatsApp: String = MessageDefaults.WHATSAPP,
    /** What goes to WhatsApp by default: LIGHT, EDITED or JPG (a [com.example.ui.ShareSet] name). */
    val whatsDefaultSet: String = "LIGHT",
    /** Gallery thumbnail size in dp. */
    val galleryThumb: Int = 112,
    /** Whole-app screen orientation: one of [ScreenTurn] ids. */
    val screenOrientation: String = "portrait",
    /** In the viewer and client mode the screen turns with each photo: standing for vertical, lying for horizontal. */
    val rotateWithPhoto: Boolean = true,
    /** Live view: a tap also runs AF. Off, a tap only moves the focus point and AF waits for the AF button. */
    val liveTapAf: Boolean = false,
    /** Live view: ask the 50D for its silent contrast AF ("Modo Live") instead of Quick mode (mirror flips). */
    val liveSilentAf: Boolean = false,
    /** Live view frames per second cap, to leave the cable free and keep the phone cool. */
    val liveFps: Int = 20,
    /** How long the shutter keeps trying while the camera focuses, in seconds. */
    val shutterWait: Int = 5,
    /** Focus first (AF) and only then fire, from the app's shutter. */
    val shutterFocusFirst: Boolean = true,
    /** Full diary in a file on the phone; off keeps only errors and crashes. */
    val logEnabled: Boolean = true,
    /** SAF folder the log file is copied to; null = only inside the app (share from Diagnóstico). */
    val logFolder: String? = null,
    /** The "Loja" tab: presets from an online catalogue (the photographer's own API). */
    val shopEnabled: Boolean = true,
    /** Address of that catalogue: a JSON list of presets, each with a download link to its DNG. */
    val shopUrl: String = DEFAULT_SHOP_URL,
    /** Root "JR C50D Sync" folder the app created its structure in (Ajustes → Salvamento → Pastas do app). */
    val appFolder: String? = null,
    /** New photos download as soon as they are taken; off leaves them on the card for later. */
    val autoDownload: Boolean = true,
    /** Second copy of every original, like a camera with two cards. */
    val mirrorEnabled: Boolean = false,
    val mirrorFolder: String? = null,
    /** Notes as files: one folder per photo with nota.txt and the voice notes. */
    val notesFolder: String? = null,
    /** Every WhatsApp template (JSON), each with a short code typed when sending. Empty = the defaults. */
    val messageLibrary: String = "",
    /** Filled into {pix} in the templates. */
    val pixKey: String = "",
    /** The watermark over the photo in client mode, so a phone photo of the screen carries the brand. */
    val clientWatermark: Boolean = true,
    val clientWatermarkOpacity: Int = 35,
    /** Unlocked by tapping the brand at the bottom of Ajustes seven times. */
    val developerMode: Boolean = false,
    /** Every PTP operation and its answer goes to the log. */
    val devVerboseLog: Boolean = false,
    /** "auto" or "cpu": which engine renders the presets (cpu to compare when the GPU looks wrong). */
    val devRender: String = "auto",

    // Live view
    val liveViewEnabled: Boolean = true,
    /** Keep the picture on the camera's screen too (uses more battery). */
    val liveOnCamera: Boolean = false,
    val liveGrid: String = "thirds",
    val liveZebra: Boolean = true,
    val liveZebraLevel: Int = 250,
    val livePeaking: Boolean = false,
    val livePeakingColor: String = "mint",
    val livePeakingSensitivity: Int = 2,
    val liveHistogram: Boolean = true,
    /** Look on the live picture: -1 none, 0 the session's look, else a preset id. */
    val livePresetId: Long = 0L,
    val liveTapToFocus: Boolean = true,
    val liveMirror: Boolean = false,

    // Tela
    val nightMode: Boolean = false,
    val nightBrightness: Int = 15,
    val keepScreenOn: Boolean = true,
) {
    /** Session label used for grouping in the database, even when no name was typed. */
    val sessionKey: String get() = sessionName.trim().ifEmpty { "Geral" }

    val exposurePresets: List<ExposurePreset> get() = ExposurePreset.decode(presets)

    val presetCategories: List<String> get() = presetCategoryText.split('|').map { it.trim() }.filter { it.isNotEmpty() }

    val moments: List<String> get() = momentList.split('|').map { it.trim() }.filter { it.isNotEmpty() }
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
    val viewerPresets = booleanPreferencesKey("viewer_presets")
    val momentsEnabled = booleanPreferencesKey("moments_enabled")
    val momentList = stringPreferencesKey("moment_list")
    val currentMoment = stringPreferencesKey("current_moment")
    val momentSubfolder = booleanPreferencesKey("moment_subfolder")
    val notesEnabled = booleanPreferencesKey("notes_enabled")
    val clientPicksEnabled = booleanPreferencesKey("client_picks_enabled")
    val msgPreview = stringPreferencesKey("msg_preview")
    val msgDelivered = stringPreferencesKey("msg_delivered")
    val msgPayment = stringPreferencesKey("msg_payment")
    val msgSelection = stringPreferencesKey("msg_selection")
    val gpsEnabled = booleanPreferencesKey("gps_enabled")
    val backupFolder = stringPreferencesKey("backup_folder")
    val backupOffer = booleanPreferencesKey("backup_offer")
    val syncCameraClock = booleanPreferencesKey("sync_camera_clock")
    val toolsEnabled = booleanPreferencesKey("tools_enabled")
    val blurAlert = booleanPreferencesKey("blur_alert")
    val blurThreshold = intPreferencesKey("blur_threshold")
    val blurVibrate = booleanPreferencesKey("blur_vibrate")
    val eyesCheck = booleanPreferencesKey("eyes_check")
    val cullSuggestions = booleanPreferencesKey("cull_suggestions")
    val hideRejected = booleanPreferencesKey("hide_rejected")
    val recentContacts = stringPreferencesKey("recent_contacts")
    val clientWhatsApp = booleanPreferencesKey("client_whatsapp")
    val presetCategories = stringPreferencesKey("preset_categories")
    val clientExitButton = booleanPreferencesKey("client_exit_button")
    val clientExitHold = booleanPreferencesKey("client_exit_hold")
    val clientCropEnabled = booleanPreferencesKey("client_crop_enabled")
    val whatsRequireName = booleanPreferencesKey("whats_require_name")
    val whatsAutoMessage = booleanPreferencesKey("whats_auto_message")
    val msgWhatsApp = stringPreferencesKey("msg_whatsapp")
    val whatsDefaultSet = stringPreferencesKey("whats_default_set")
    val galleryThumb = intPreferencesKey("gallery_thumb")
    val screenOrientation = stringPreferencesKey("screen_orientation")
    val rotateWithPhoto = booleanPreferencesKey("rotate_with_photo")
    val liveTapAf = booleanPreferencesKey("live_tap_af")
    val liveSilentAf = booleanPreferencesKey("live_silent_af")
    val liveFps = intPreferencesKey("live_fps")
    val shutterWait = intPreferencesKey("shutter_wait")
    val shutterFocusFirst = booleanPreferencesKey("shutter_focus_first")
    val logEnabled = booleanPreferencesKey("log_enabled")
    val logFolder = stringPreferencesKey("log_folder")
    val shopEnabled = booleanPreferencesKey("shop_enabled")
    val shopUrl = stringPreferencesKey("shop_url")
    val appFolder = stringPreferencesKey("app_folder")
    val autoDownload = booleanPreferencesKey("auto_download")
    val mirrorEnabled = booleanPreferencesKey("mirror_enabled")
    val mirrorFolder = stringPreferencesKey("mirror_folder")
    val notesFolder = stringPreferencesKey("notes_folder")
    val messageLibrary = stringPreferencesKey("message_library")
    val pixKey = stringPreferencesKey("pix_key")
    val clientWatermark = booleanPreferencesKey("client_watermark")
    val clientWatermarkOpacity = intPreferencesKey("client_watermark_opacity")
    val developerMode = booleanPreferencesKey("developer_mode")
    val devVerboseLog = booleanPreferencesKey("dev_verbose_log")
    val devRender = stringPreferencesKey("dev_render")
    val liveViewEnabled = booleanPreferencesKey("live_view_enabled")
    val liveOnCamera = booleanPreferencesKey("live_on_camera")
    val liveGrid = stringPreferencesKey("live_grid")
    val liveZebra = booleanPreferencesKey("live_zebra")
    val liveZebraLevel = intPreferencesKey("live_zebra_level")
    val livePeaking = booleanPreferencesKey("live_peaking")
    val livePeakingColor = stringPreferencesKey("live_peaking_color")
    val livePeakingSensitivity = intPreferencesKey("live_peaking_sensitivity")
    val liveHistogram = booleanPreferencesKey("live_histogram")
    val livePresetId = longPreferencesKey("live_preset_id")
    val liveTapToFocus = booleanPreferencesKey("live_tap_focus")
    val liveMirror = booleanPreferencesKey("live_mirror")

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

    /** Every stored option by key name, without the ones in [exclude]. Defaults never touched are absent. */
    suspend fun snapshot(exclude: Set<String> = emptySet()): Map<String, Any> =
        context.dataStore.data.first().asMap().mapKeys { it.key.name }.filterKeys { it !in exclude }

    /**
     * Writes [values] in one go. With [replace], stored options not in [values] go back to their
     * defaults too, except the ones in [keep].
     */
    suspend fun apply(values: Map<String, Any>, replace: Boolean = false, keep: Set<String> = emptySet()) {
        context.dataStore.edit { prefs ->
            if (replace) prefs.asMap().keys.filter { it.name !in keep && it.name !in values }.forEach { prefs.remove(it) }
            values.forEach { (name, value) -> SettingsProfiles.keyFor(name, value)?.let { prefs[it] = value } }
        }
    }

    /** Profiles the photographer saved (the ready-made ones are in [SettingsProfiles.starters]). */
    val profiles: Flow<List<SettingsProfile>> = context.dataStore.data.map { SettingsProfiles.decodeList(it[SettingsProfiles.PROFILES]) }

    val activeProfile: Flow<String> = context.dataStore.data.map { it[SettingsProfiles.ACTIVE].orEmpty() }

    suspend fun saveProfiles(list: List<SettingsProfile>) = set(SettingsProfiles.PROFILES, SettingsProfiles.encodeList(list))

    /** Saves the current options as a profile, replacing one with the same name. */
    suspend fun saveCurrentAs(name: String, icon: String) {
        val clean = name.trim().take(40).ifEmpty { return }
        val profile = SettingsProfile(clean, icon, snapshot(SettingsProfiles.personal))
        saveProfiles(listOf(profile) + profiles.first().filter { it.name != clean })
    }

    suspend fun applyProfile(profile: SettingsProfile) {
        apply(profile.values.filterKeys { it !in SettingsProfiles.personal })
        set(SettingsProfiles.ACTIVE, profile.name)
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
            viewerPresets = this[k.viewerPresets] ?: d.viewerPresets,
            momentsEnabled = this[k.momentsEnabled] ?: d.momentsEnabled,
            momentList = this[k.momentList] ?: d.momentList,
            currentMoment = this[k.currentMoment] ?: d.currentMoment,
            momentSubfolder = this[k.momentSubfolder] ?: d.momentSubfolder,
            notesEnabled = this[k.notesEnabled] ?: d.notesEnabled,
            clientPicksEnabled = this[k.clientPicksEnabled] ?: d.clientPicksEnabled,
            msgPreview = this[k.msgPreview] ?: d.msgPreview,
            msgDelivered = this[k.msgDelivered] ?: d.msgDelivered,
            msgPayment = this[k.msgPayment] ?: d.msgPayment,
            msgSelection = this[k.msgSelection] ?: d.msgSelection,
            gpsEnabled = this[k.gpsEnabled] ?: d.gpsEnabled,
            backupFolder = this[k.backupFolder],
            backupOffer = this[k.backupOffer] ?: d.backupOffer,
            syncCameraClock = this[k.syncCameraClock] ?: d.syncCameraClock,
            toolsEnabled = this[k.toolsEnabled] ?: d.toolsEnabled,
            blurAlert = this[k.blurAlert] ?: d.blurAlert,
            blurThreshold = this[k.blurThreshold] ?: d.blurThreshold,
            blurVibrate = this[k.blurVibrate] ?: d.blurVibrate,
            eyesCheck = this[k.eyesCheck] ?: d.eyesCheck,
            cullSuggestions = this[k.cullSuggestions] ?: d.cullSuggestions,
            hideRejected = this[k.hideRejected] ?: d.hideRejected,
            recentContacts = this[k.recentContacts] ?: d.recentContacts,
            clientWhatsApp = this[k.clientWhatsApp] ?: d.clientWhatsApp,
            presetCategoryText = this[k.presetCategories] ?: d.presetCategoryText,
            clientExitButton = this[k.clientExitButton] ?: d.clientExitButton,
            clientExitHold = this[k.clientExitHold] ?: d.clientExitHold,
            clientCropEnabled = this[k.clientCropEnabled] ?: d.clientCropEnabled,
            whatsRequireName = this[k.whatsRequireName] ?: d.whatsRequireName,
            whatsAutoMessage = this[k.whatsAutoMessage] ?: d.whatsAutoMessage,
            msgWhatsApp = this[k.msgWhatsApp] ?: d.msgWhatsApp,
            whatsDefaultSet = this[k.whatsDefaultSet] ?: d.whatsDefaultSet,
            galleryThumb = this[k.galleryThumb] ?: d.galleryThumb,
            screenOrientation = this[k.screenOrientation] ?: d.screenOrientation,
            rotateWithPhoto = this[k.rotateWithPhoto] ?: d.rotateWithPhoto,
            liveTapAf = this[k.liveTapAf] ?: d.liveTapAf,
            liveSilentAf = this[k.liveSilentAf] ?: d.liveSilentAf,
            liveFps = this[k.liveFps] ?: d.liveFps,
            shutterWait = this[k.shutterWait] ?: d.shutterWait,
            shutterFocusFirst = this[k.shutterFocusFirst] ?: d.shutterFocusFirst,
            logEnabled = this[k.logEnabled] ?: d.logEnabled,
            logFolder = this[k.logFolder],
            shopEnabled = this[k.shopEnabled] ?: d.shopEnabled,
            shopUrl = this[k.shopUrl]?.takeIf { it.isNotBlank() } ?: d.shopUrl,
            appFolder = this[k.appFolder],
            autoDownload = this[k.autoDownload] ?: d.autoDownload,
            mirrorEnabled = this[k.mirrorEnabled] ?: d.mirrorEnabled,
            mirrorFolder = this[k.mirrorFolder],
            notesFolder = this[k.notesFolder],
            messageLibrary = this[k.messageLibrary] ?: d.messageLibrary,
            pixKey = this[k.pixKey] ?: d.pixKey,
            clientWatermark = this[k.clientWatermark] ?: d.clientWatermark,
            clientWatermarkOpacity = this[k.clientWatermarkOpacity] ?: d.clientWatermarkOpacity,
            developerMode = this[k.developerMode] ?: d.developerMode,
            devVerboseLog = this[k.devVerboseLog] ?: d.devVerboseLog,
            devRender = this[k.devRender] ?: d.devRender,
            liveViewEnabled = this[k.liveViewEnabled] ?: d.liveViewEnabled,
            liveOnCamera = this[k.liveOnCamera] ?: d.liveOnCamera,
            liveGrid = this[k.liveGrid] ?: d.liveGrid,
            liveZebra = this[k.liveZebra] ?: d.liveZebra,
            liveZebraLevel = this[k.liveZebraLevel] ?: d.liveZebraLevel,
            livePeaking = this[k.livePeaking] ?: d.livePeaking,
            livePeakingColor = this[k.livePeakingColor] ?: d.livePeakingColor,
            livePeakingSensitivity = this[k.livePeakingSensitivity] ?: d.livePeakingSensitivity,
            liveHistogram = this[k.liveHistogram] ?: d.liveHistogram,
            livePresetId = this[k.livePresetId] ?: d.livePresetId,
            liveTapToFocus = this[k.liveTapToFocus] ?: d.liveTapToFocus,
            liveMirror = this[k.liveMirror] ?: d.liveMirror,

            nightMode = this[k.nightMode] ?: d.nightMode,
            nightBrightness = this[k.nightBrightness] ?: d.nightBrightness,
            keepScreenOn = this[k.keepScreenOn] ?: d.keepScreenOn,
        )
    }
}

const val DEFAULT_SHOP_URL = "https://lightroompresets.kasunpremarathna.com/api/presets.php"
