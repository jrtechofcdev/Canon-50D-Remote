package com.example

import android.app.Application
import com.example.core.alerts.Alerts
import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionState
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
        private set

    // A failure in one of these collectors is written to the log instead of closing the app.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + AppLog.handler("APP"))

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
        camera = CameraManager(
            this,
            keepCameraAwake = { settings.current().keepCameraAwake },
            syncClock = { settings.current().syncCameraClock },
            onClockRefused = { settings.set(SettingKeys.syncCameraClock, false) },
        )
        presets = PresetRepository(this, database.presetDao(), settings)
        engine = PresetEngine()
        looks = Looks(this, engine, presets, database.photoDao(), store, settings)
        location = LocationTracker(this)
        tether = TetherController(this, camera, settings, store, database.photoDao(), looks, { id -> presets.get(id)?.name }, location::recent)
        export = ExportService(this)
        alerts = Alerts(this)
        notes = VoiceNotes(this)
        backup = SessionBackup(this, database.photoDao(), store)
        culler = Culler(this, database.photoDao(), settings, MlKitFaceAnalyzer())
        appBackup = AppBackup(settings, presets)
        shop = PresetShop(this, settings, presets)
        looks.onEdited = { id -> events.emit(PhotoEvent.Edited(id)) }
        delivery = LocalDelivery(this, settings, database.deliveryDao(), database.photoDao(), looks, export, events, appScope, com.example.core.delivery.DeliveryMode.Normal)
        express = LocalDelivery(this, settings, database.deliveryDao(), database.photoDao(), looks, export, events, appScope, com.example.core.delivery.DeliveryMode.Express)
        // One Wi-Fi at a time: starting either tool stops the other.
        delivery.peer = express
        express.peer = delivery
        delivery.init()
        express.init()
        appScope.launch {
            events.events.collect { e ->
                if ((e is PhotoEvent.Delivered || e is PhotoEvent.Approved) && settings.current().deliveryVibrate) alerts.photoReceived()
            }
        }

        appScope.launch {
            settings.settings.distinctUntilChangedBy { it.logEnabled to it.logFolder }.collect { AppLog.configure(it.logEnabled, it.logFolder) }
        }
        appScope.launch {
            // Modo desenvolvedor: detailed log and the CPU-only render follow their switches.
            settings.settings.collect {
                AppLog.verbose = it.developerMode && it.devVerboseLog
                engine.forceCpu = it.developerMode && it.devRender == "cpu"
            }
        }

        appScope.launch(Dispatchers.IO) {
            export.clearOldExports()
            presets.seedIfEmpty()
            runCatching { presets.syncFolder() }
        }

        appScope.launch {
            camera.state.distinctUntilChangedBy { it::class }.collect { state ->
                if (state is ConnectionState.Connected) {
                    tether.resetSessionCounter()
                    warnedCard = false
                    warnedBattery = false
                    warnedPhone = false
                    CameraForegroundService.start(this@TetherApp)
                }
                if (state is ConnectionState.Connected && settings.current().gpsEnabled) location.start() else location.stop()
            }
        }

        appScope.launch {
            camera.lostConnection.collect { reason ->
                if (settings.current().alertOnDisconnect) alerts.warn("Câmera desconectada", "$reason. Confira o cabo e a câmera.")
            }
        }

        appScope.launch {
            tether.saved.collect {
                AppLog.i("FOTO", "${it.savedBase} salva · ${AppLog.memory()}")
                events.emit(PhotoEvent.Captured(it.id))
                val s = settings.current()
                if (s.blurAlert || s.eyesCheck || s.cullSuggestions) culler.enqueue(it.id)
                if (s.vibrateOnReceive) alerts.photoReceived()
                if (s.phoneLowWarning && !warnedPhone) {
                    val free = withContext(Dispatchers.IO) { alerts.phoneFreeGb() }
                    if (free < s.phoneLowGb) {
                        warnedPhone = true
                        alerts.warn("Memória do celular acabando", "Restam %.1f GB livres.".format(free))
                    }
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
                if (s.showBattery && !warnedBattery && battery != null && EosValues.batteryIsLow(battery)) {
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
