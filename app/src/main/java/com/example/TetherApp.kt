package com.example

import android.app.Application
import com.example.core.alerts.Alerts
import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionPrefs
import com.example.core.camera.ConnectionState
import com.example.core.camera.ReleasePrefs
import com.example.core.data.AppBackup
import com.example.core.log.AppLog
import com.example.core.data.PhotoDatabase
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import com.example.core.export.ExportService
import com.example.core.ptp.EosProp
import com.example.core.ptp.EosValues
import com.example.core.service.CameraForegroundService
import com.example.core.storage.PhotoStore
import com.example.core.tether.TetherController
import com.example.core.preset.PresetRepository
import com.example.core.render.Looks
import com.example.core.render.PresetEngine
import com.example.core.notes.VoiceNotes
import com.example.core.location.LocationTracker
import com.example.core.backup.SessionBackup
import com.example.core.analysis.Culler
import com.example.core.analysis.MlKitFaceAnalyzer
import com.example.core.shop.PresetShop
import com.example.core.delivery.DeliveryEvents
import com.example.core.delivery.LocalDelivery
import com.example.core.delivery.PhotoEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Creates the app's single instances; screens reach them through `application as TetherApp`. */
class TetherApp : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var database: PhotoDatabase
        private set
    lateinit var store: PhotoStore
        private set
    lateinit var camera: CameraManager
        private set
    lateinit var tether: TetherController
        private set
    lateinit var export: ExportService
        private set
    lateinit var alerts: Alerts
        private set
    lateinit var presets: PresetRepository
        private set
    lateinit var engine: PresetEngine
        private set
    lateinit var looks: Looks
        private set
    lateinit var notes: VoiceNotes
        private set
    lateinit var location: LocationTracker
        private set
    lateinit var backup: SessionBackup
        private set
    lateinit var culler: Culler
        private set
    lateinit var appBackup: AppBackup
        private set
    lateinit var shop: PresetShop
        private set
    /** Photo events (captured, edited, approved): the only link between capture and delivery. */
    val events = DeliveryEvents()
    /** Entrega local: clients with a code. */
    lateinit var delivery: LocalDelivery
        private set
    /** Fotografia Express: its own tool, no code, preview until paid. Independent of [delivery]. */
    lateinit var express: LocalDelivery

    /** Entrega da igreja: the media team sees and downloads the event's photos live. */
    lateinit var church: com.example.core.church.ChurchDelivery

    /** Instagram picker (experimental): models, measures and what it learned. */
    lateinit var insta: com.example.core.insta.InstaPicker
        private set
    /** Deleted photos wait here a few seconds so Desfazer can bring them back. */
    lateinit var trash: com.example.core.data.PhotoTrash
        private set
    /** Experimental: films with the 50D (SET button) or records the live view on the phone. */
    lateinit var video: com.example.core.video.VideoController
        private set

    /**
     * Switches the session new photos go to (folder, name, delivery): the name, the photo counter
     * and the last backup result all belong to the session that just ended.
     */
    suspend fun startSession(name: String) {
        settings.set(SettingKeys.sessionName, name.trim())
        tether.resetSessionCounter()
        backup.dismiss()
        _cameraLost.value = false
        AppLog.i("SESSAO", "Sessão: ${name.trim().ifEmpty { "Geral" }}")
    }

    /** Every photo except the ones waiting in [trash]: what the screens list. */
    val photos: kotlinx.coroutines.flow.Flow<List<com.example.core.data.Photo>> by lazy {
        kotlinx.coroutines.flow.combine(database.photoDao().observeAll(), trash.pending) { list, gone ->
            if (gone.isEmpty()) list else list.filter { it.id !in gone }
        }
    }

    // A failure in one of these collectors is written to the log instead of closing the app.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + AppLog.handler("APP"))

    private val _cameraLost = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** The camera dropped during a session (not a deliberate Desconectar), for the red dot on the Sessão tab. */
    val cameraLost: kotlinx.coroutines.flow.StateFlow<Boolean> = _cameraLost

    private val _phoneLowGb = kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)

    /** Free space on the phone when it is under the warning level, else null: a chip on the Session. */
    val phoneLowGb: kotlinx.coroutines.flow.StateFlow<Double?> = _phoneLowGb

    private val _notices = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Messages that say what to do next: shown at the bottom, longer than a Toast and with a close button. */
    val notices: kotlinx.coroutines.flow.SharedFlow<String> = _notices

    /**
     * Once: the messages become the 35 of the Central de Atendimento v1.3. Ready messages never edited take
     * the new text, the new ones join, the photographer's own texts and messages stay as they are.
     */
    private suspend fun upgradeMessages() {
        val s = settings.current()
        if (s.messageLibraryVersion >= 2) return
        val lib = com.example.core.messages.MessageLibrary
        listOf(
            Triple(com.example.core.messages.MessageKind.WhatsApp, SettingKeys.msgWhatsApp, s.msgWhatsApp),
            Triple(com.example.core.messages.MessageKind.Preview, SettingKeys.msgPreview, s.msgPreview),
            Triple(com.example.core.messages.MessageKind.Delivered, SettingKeys.msgDelivered, s.msgDelivered),
            Triple(com.example.core.messages.MessageKind.Payment, SettingKeys.msgPayment, s.msgPayment),
            Triple(com.example.core.messages.MessageKind.Selection, SettingKeys.msgSelection, s.msgSelection),
        ).forEach { (kind, key, current) -> lib.upgradeLegacy(kind, current)?.let { settings.set(key, it) } }
        val saved = lib.decode(s.messageLibrary)
        if (saved.isNotEmpty()) {
            val up = lib.upgrade(saved)
            settings.set(SettingKeys.messageLibrary, lib.encode(up.list))
            if (up.renamed.isNotEmpty()) {
                notice("Mensagens novas com atalho que já era seu: " + up.renamed.joinToString(", ") { (name, code) -> "$name ficou \"$code\"" })
            }
        }
        settings.set(SettingKeys.messageLibraryVersion, 2)
    }

    fun notice(text: String) {
        _notices.tryEmit(text)
    }

    fun forgetLostCamera() {
        _cameraLost.value = false
    }

    // Each warning fires once per connection, not on every event.
    private var warnedCard = false
    private var warnedBattery = false
    private var warnedPhone = false

    override fun onCreate() {
        super.onCreate()
        // First, so everything after it (and any crash) is written down.
        AppLog.init(this)
        settings = SettingsRepository(this)
        database = PhotoDatabase.create(this)
        store = PhotoStore(this)
        trash = com.example.core.data.PhotoTrash(appScope) { ids ->
            withContext(Dispatchers.IO) {
                ids.forEach { id -> database.photoDao().get(id)?.let { com.example.ui.photos.deletePhoto(this@TetherApp, it) } }
            }
            AppLog.i("FOTOS", "${ids.size} foto(s) apagada(s)")
        }
        camera = CameraManager(
            this,
            keepCameraAwake = { settings.current().keepCameraAwake },
            syncClock = { settings.current().syncCameraClock },
            onClockRefused = { settings.set(SettingKeys.syncCameraClock, false) },
            releasePrefs = { settings.current().let { ReleasePrefs(if (it.developerMode) it.devShutterMethod else ReleasePrefs.AUTO, it.devReleaseFlag) } },
            connectionPrefs = { settings.current().let { if (it.developerMode) ConnectionPrefs(it.devRemoteMode, it.devWriteDestination, it.devRestoreLiveOutput, it.devResetRemoteAfterLive) else ConnectionPrefs() } },
        )
        presets = PresetRepository(this, database.presetDao(), settings)
        engine = PresetEngine()
        looks = Looks(this, engine, presets, database.photoDao(), store, settings)
        location = LocationTracker(this)
        tether = TetherController(this, camera, settings, store, database.photoDao(), looks, { id -> presets.get(id)?.name }, location::recent)
        video = com.example.core.video.VideoController(this, camera, com.example.core.video.VideoLibrary(this, settings))
        export = ExportService(this)
        alerts = Alerts(this)
        notes = VoiceNotes(this)
        backup = SessionBackup(this, database.photoDao(), store)
        culler = Culler(this, database.photoDao(), settings, MlKitFaceAnalyzer())
        appBackup = AppBackup(settings, presets)
        shop = PresetShop(this, settings, presets)
        looks.onEdited = { id -> events.emit(PhotoEvent.Edited(id)) }
        delivery = LocalDelivery(this, settings, database.deliveryDao(), database.photoDao(), looks, export, events, appScope, com.example.core.delivery.DeliveryMode.Normal, analysis = { culler.awaitAnalysis(it) })
        express = LocalDelivery(this, settings, database.deliveryDao(), database.photoDao(), looks, export, events, appScope, com.example.core.delivery.DeliveryMode.Express, analysis = { culler.awaitAnalysis(it) })
        // One Wi-Fi at a time: starting either tool stops the other.
        delivery.peer = express
        express.peer = delivery
        val likes = com.example.core.delivery.LikeNotifier(this)
        delivery.onLike = { session, item -> if (settings.current().deliveryNotifyLikes) likes.liked(session, item) }
        express.onLike = { session, item -> if (settings.current().expressNotifyLikes) likes.liked(session, item) }
        delivery.init()
        express.init()
        church = com.example.core.church.ChurchDelivery(this, com.example.core.church.ChurchRepository(database.churchDao()), database.photoDao(), looks, store, settings, presets, shop, database.instaDao())
        insta = com.example.core.insta.InstaPicker(this, database.instaDao(), com.example.core.insta.MlKitVision())
        // The church delivery also takes the Wi-Fi, so it and the other two stop each other.
        church.stopOthers = {
            if (delivery.status.value !is com.example.core.delivery.DeliveryStatus.Off) delivery.stop()
            if (express.status.value !is com.example.core.delivery.DeliveryStatus.Off) express.stop()
        }
        val stopChurch: suspend () -> Unit = { if (church.status.value !is com.example.core.delivery.DeliveryStatus.Off) church.stop() }
        delivery.stopOthers = stopChurch
        express.stopOthers = stopChurch
        appScope.launch {
            events.events.collect { e ->
                if ((e is PhotoEvent.Delivered || e is PhotoEvent.Approved) && settings.current().deliveryVibrate) alerts.photoReceived()
                if (e is PhotoEvent.Edited) runCatching { church.onPhotoEdited(e.photoId) }
            }
        }

        appScope.launch { upgradeMessages() }
        appScope.launch {
            settings.settings.distinctUntilChangedBy { it.logEnabled to it.logFolder }.collect { AppLog.configure(it.logEnabled, it.logFolder) }
        }
        appScope.launch {
            // Modo desenvolvedor: detailed log and the CPU-only render follow their switches.
            settings.settings.collect {
                AppLog.verbose = it.developerMode && it.devVerboseLog
                engine.forceCpu = it.developerMode && it.devRender == "cpu"
                camera.usbReset = when (if (it.developerMode) it.devUsbReset else "full") {
                    "noclear" -> com.example.core.ptp.UsbReset.NO_CLEAR
                    "none" -> com.example.core.ptp.UsbReset.NONE
                    else -> com.example.core.ptp.UsbReset.FULL
                }
            }
        }

        appScope.launch(Dispatchers.IO) {
            export.clearOldExports()
            presets.seedIfEmpty()
            runCatching { presets.syncFolder() }
            runCatching { store.releaseUnused(settings.snapshot().values.joinToString("\n") { it.toString() }) }
        }

        appScope.launch {
            var before: ConnectionState? = null
            camera.state.distinctUntilChangedBy { it::class }.collect { state ->
                // Back after a reconnect it is the same shoot: the count of photos received goes on.
                if (state is ConnectionState.Connected && before !is ConnectionState.Reconnecting) {
                    tether.resetSessionCounter()
                    warnedCard = false
                    warnedBattery = false
                    warnedPhone = false
                }
                if (state is ConnectionState.Connected) _cameraLost.value = false
                if (state is ConnectionState.Connected) CameraForegroundService.start(this@TetherApp)
                before = state
                if (state is ConnectionState.Connected && settings.current().gpsEnabled) location.start() else location.stop()
            }
        }

        appScope.launch {
            // The backup runs under the foreground service too: the screen going off used to be able to stop it.
            backup.state.collect { if (it is com.example.core.backup.BackupState.Running) CameraForegroundService.start(this@TetherApp) }
        }

        appScope.launch {
            // The video module's messages show at the bottom on any screen (live view, Ajustes, galeria).
            video.messages.collect { notice(it) }
        }

        appScope.launch {
            camera.lostConnection.collect { reason ->
                _cameraLost.value = true
                if (settings.current().alertOnDisconnect) alerts.warn("Câmera desconectada", "$reason. Confira o cabo e a câmera.")
            }
        }

        appScope.launch {
            tether.saved.collect {
                AppLog.i("FOTO", "${it.savedBase} salva · ${AppLog.memory()}")
                events.emit(PhotoEvent.Captured(it.id))
                val photo = it
                appScope.launch { runCatching { church.onPhotoSaved(photo) }.onFailure { e -> AppLog.w("IGREJA", "Foto não entrou no evento", e) } }
                val s = settings.current()
                if (s.blurAlert || s.eyesCheck || s.cullSuggestions) culler.enqueue(it.id)
                if (s.vibrateOnReceive) alerts.photoReceived()
                if (s.phoneLowWarning) {
                    val free = withContext(Dispatchers.IO) { alerts.phoneFreeGb() }
                    _phoneLowGb.value = free.takeIf { it < s.phoneLowGb }
                    if (free < s.phoneLowGb && !warnedPhone) {
                        warnedPhone = true
                        alerts.warn("Memória do celular acabando", "Restam %.1f GB livres.".format(free))
                    }
                } else {
                    _phoneLowGb.value = null
                }
            }
        }

        appScope.launch {
            culler.warnings.collect { if (settings.current().blurVibrate) alerts.photoProblem() }
        }

        appScope.launch {
            camera.status.collect { status ->
                val s = settings.current()
                val shots = status.shotsLeft
                if (s.cardLowWarning && !warnedCard && shots != null && shots < s.cardLowShots) {
                    warnedCard = true
                    alerts.warn("Cartão quase cheio", "Restam $shots disparos no cartão da câmera.")
                }
                val battery = status.value(EosProp.BATTERY_POWER)
                // The alert doesn't depend on showing the battery in the Session: a low battery always warns.
                if (!warnedBattery && battery != null && EosValues.batteryIsLow(battery)) {
                    warnedBattery = true
                    alerts.warn("Bateria da câmera baixa", "Bateria em ${EosValues.batteryLabel(battery)}.")
                }
            }
        }
    }

    /** Android asks for memory back: drop the preview caches (they are rebuilt when needed). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            AppLog.w("MEM", "Android pediu memória (nível $level) · ${AppLog.memory()}")
            if (::looks.isInitialized) looks.trim()
            if (::shop.isInitialized) shop.trim()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLog.w("MEM", "Memória baixa · ${AppLog.memory()}")
        if (::looks.isInitialized) looks.trim()
    }
}
