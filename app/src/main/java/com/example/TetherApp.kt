package com.example

import android.app.Application
import com.example.core.alerts.Alerts
import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionState
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Each warning fires once per connection, not on every event.
    private var warnedCard = false
    private var warnedBattery = false
    private var warnedPhone = false

    override fun onCreate() {
        super.onCreate()
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
                val s = settings.current()
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
}
