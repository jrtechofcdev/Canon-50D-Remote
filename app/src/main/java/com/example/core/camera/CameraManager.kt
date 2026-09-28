package com.example.core.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.core.data.ExposurePreset
import com.example.core.log.AppLog
import com.example.core.ptp.DeviceInfo
import com.example.core.ptp.EosValues
import com.example.core.ptp.ObjectInfo
import com.example.core.ptp.PtpDataReader
import com.example.core.ptp.EosEventParser
import com.example.core.ptp.EosEventRecord
import com.example.core.ptp.EosProp
import com.example.core.ptp.PtpContainer
import com.example.core.ptp.PtpDataSink
import com.example.core.ptp.PtpException
import com.example.core.ptp.PtpOp
import com.example.core.ptp.PtpResponse
import com.example.core.ptp.PtpResponseException
import com.example.core.ptp.PtpSession
import com.example.core.ptp.UsbPtpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import com.example.core.live.LiveViewParser
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ConnectionState {
    data object NoCamera : ConnectionState
    data object WaitingPermission : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val model: String, val serial: String) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

/** Live view: off, starting, streaming (with measured frame rate and camera zoom), or failed with a hint. */
sealed interface LiveState {
    data object Off : LiveState
    data object Starting : LiveState
    data class Running(val fps: Float, val zoom: Int) : LiveState
    data class Failed(val message: String) : LiveState
}

/** Exposure values as last reported by the camera, keyed by EOS property code. */
data class CameraStatus(
    val values: Map<Int, Int> = emptyMap(),
    val options: Map<Int, List<Int>> = emptyMap(),
) {
    fun value(prop: Int): Int? = values[prop]
    fun options(prop: Int): List<Int> = options[prop].orEmpty()
    val shotsLeft: Int? get() = values[EosProp.AVAILABLE_SHOTS]
}

data class BracketState(val shot: Int, val total: Int)

data class IntervalState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val seconds: Int = 0)

/**
 * Owns the USB link to the 50D.
 *
 * The session is opened in "event mode" only: no UI lock and no live view, so the camera body keeps
 * working normally. Every frame written to the card (from the camera's own shutter button or from the
 * app) is announced through GetEvent and handed to [newObjects] for download.
 */
class CameraManager(
    private val context: Context,
    private val keepCameraAwake: suspend () -> Boolean,
    private val syncClock: suspend () -> Boolean = { false },
    private val onClockRefused: suspend () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler("CAMERA"))
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.NoCamera)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _newObjects = MutableSharedFlow<EosEventRecord.ObjectAdded>(extraBufferCapacity = 64)
    val newObjects: SharedFlow<EosEventRecord.ObjectAdded> = _newObjects.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _interval = MutableStateFlow(IntervalState())
    val interval: StateFlow<IntervalState> = _interval.asStateFlow()

    /** Fires when the link drops without the user asking (cable pulled, camera switched off, USB error). */
    private val _lostConnection = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val lostConnection: SharedFlow<String> = _lostConnection.asSharedFlow()

    private val _bracketing = MutableStateFlow<BracketState?>(null)
    val bracketing: StateFlow<BracketState?> = _bracketing.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _live = MutableStateFlow<LiveState>(LiveState.Off)
    val live: StateFlow<LiveState> = _live.asStateFlow()

    /** Latest live view JPEG. Slow consumers only ever get the newest frame. */
    private val _liveFrames = MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val liveFrames: SharedFlow<ByteArray> = _liveFrames.asSharedFlow()
    private var liveJob: Job? = null
    @Volatile private var liveZoom = 1
    /** Set while the shutter works, so live view stops asking for frames and the camera can focus and fire. */
    @Volatile private var liveHold = false
    @Volatile private var liveFrameMs = 50L

    private val _shutter = MutableStateFlow<ShutterState>(ShutterState.Idle)
    val shutter: StateFlow<ShutterState> = _shutter.asStateFlow()

    private var session: PtpSession? = null
    private var device: UsbDevice? = null
    private var deviceInfo: DeviceInfo? = null
    private var sessionJob: Job? = null
    private var intervalJob: Job? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val dev = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && dev != null) {
                        open(dev)
                    } else {
                        _state.value = ConnectionState.Failed("Permissão USB negada. Toque em Conectar para tentar de novo.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> findAndConnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (dev != null && dev.deviceName == device?.deviceName) {
                    log("Cabo desconectado")
                    val wasConnected = _state.value is ConnectionState.Connected
                    closeSession()
                    _state.value = ConnectionState.NoCamera
                    if (wasConnected) _lostConnection.tryEmit("Cabo USB desconectado")
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Looks for a Canon still-image device and connects, asking for USB permission if needed. */
    fun findAndConnect() {
        if (_state.value is ConnectionState.Connected || _state.value == ConnectionState.Connecting) return
        val camera = usbManager.deviceList.values
            .filter { stillImageInterface(it) != null }
            .sortedByDescending { it.vendorId == CANON_VENDOR_ID }
            .firstOrNull()
        if (camera == null) {
            _state.value = ConnectionState.NoCamera
            return
        }
        if (usbManager.hasPermission(camera)) {
            open(camera)
        } else {
            _state.value = ConnectionState.WaitingPermission
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            // The system fills in the device and the grant flag, so the intent has to be mutable.
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            usbManager.requestPermission(camera, PendingIntent.getBroadcast(context, 0, intent, flags))
        }
    }

    private fun open(dev: UsbDevice) {
        if (_state.value is ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        val iface = stillImageInterface(dev)
        val endpoints = iface?.let { UsbPtpTransport.endpointsOf(it) }
        val connection = usbManager.openDevice(dev)
        if (iface == null || endpoints == null || connection == null) {
            connection?.close()
            _state.value = ConnectionState.Failed("Não foi possível abrir a câmera pela USB.")
            return
        }
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            _state.value = ConnectionState.Failed("Outro app está usando a câmera. Feche-o e tente de novo.")
            return
        }
        val transport = UsbPtpTransport(connection, iface, endpoints.first, endpoints.second)
        val ptp = PtpSession(transport, ::log)
        session = ptp
        device = dev
        sessionJob = scope.launch { runSession(ptp) }
    }

    private suspend fun runSession(ptp: PtpSession) {
        try {
            log("Abrindo sessão PTP")
            ptp.openSession()
            val info = DeviceInfo.parse(ptp.transactForData(PtpOp.GET_DEVICE_INFO))
            deviceInfo = info
            log("Câmera: ${info.manufacturer} ${info.model} (firmware ${info.version})")

            // Remote mode + event mode is what EOS Utility uses for tethering. No UI lock is sent,
            // so the shutter button, dials and menus on the camera stay usable.
            if (info.supports(PtpOp.EOS_SET_REMOTE_MODE)) ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, 1)
            if (info.supports(PtpOp.EOS_SET_EVENT_MODE)) ptp.transact(PtpOp.EOS_SET_EVENT_MODE, 1)
            runCatching { setPropertyNow(ptp, EosProp.CAPTURE_DESTINATION, EosProp.DESTINATION_CARD) }
                .onFailure { log("Destino de captura mantido pela câmera: ${it.message}") }

            // The first GetEvent calls describe the current state (and may list files already on the card).
            // Read them until the queue is empty and only then start treating new objects as new shots.
            for (i in 0 until 20) {
                val records = EosEventParser.parse(ptp.transactForData(PtpOp.EOS_GET_EVENT))
                applyRecords(records, announceObjects = false)
                if (records.isEmpty()) break
            }

            if (syncClock()) setCameraClock(ptp)

            _state.value = ConnectionState.Connected(info.model.ifBlank { "Canon EOS" }, info.serial)
            log("Pronto: captura vinculada ativa")
            eventLoop(ptp, info)
        } catch (e: Exception) {
            if (session === ptp) {
                log("Erro: ${e.message}")
                val wasConnected = _state.value is ConnectionState.Connected
                closeSession()
                _state.value = ConnectionState.Failed(e.message ?: "Falha na comunicação com a câmera")
                if (wasConnected) _lostConnection.tryEmit("Conexão com a câmera perdida")
            }
        }
    }

    private suspend fun eventLoop(ptp: PtpSession, info: DeviceInfo) {
        var lastKeepAlive = System.currentTimeMillis()
        while (scope.isActive && session === ptp) {
            val records = try {
                EosEventParser.parse(ptp.transactForData(PtpOp.EOS_GET_EVENT))
            } catch (e: PtpResponseException) {
                // Busy while the camera writes to the card: just ask again shortly.
                delay(300)
                continue
            }
            applyRecords(records, announceObjects = true)

            val now = System.currentTimeMillis()
            if (now - lastKeepAlive > KEEP_ALIVE_MS && info.supports(PtpOp.EOS_KEEP_DEVICE_ON) && keepCameraAwake()) {
                runCatching { ptp.transact(PtpOp.EOS_KEEP_DEVICE_ON) }
                lastKeepAlive = now
            }
            delay(if (records.isEmpty()) POLL_IDLE_MS else POLL_BUSY_MS)
        }
    }

    private fun applyRecords(records: List<EosEventRecord>, announceObjects: Boolean) {
        for (record in records) when (record) {
            is EosEventRecord.PropertyChanged ->
                _status.update { it.copy(values = it.values + (record.prop to record.value)) }
            is EosEventRecord.AvailableValues ->
                _status.update { it.copy(options = it.options + (record.prop to record.values)) }
            is EosEventRecord.ObjectAdded -> if (announceObjects && !record.isFolder) {
                log("Nova foto: ${record.filename} (${record.size / 1024} KB)")
                _newObjects.tryEmit(record)
            }
        }
    }

    /** Streams one file from the camera. Runs inside the session lock, so polling pauses meanwhile. */
    suspend fun download(handle: Int, sink: PtpDataSink) {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        ptp.transact(PtpOp.GET_OBJECT, handle, dataIn = sink, timeoutMs = DOWNLOAD_TIMEOUT_MS)
    }

    /**
     * Fires from the app. The camera gets up to [waitSeconds] to focus: while it answers "not focused"
     * or "busy" the app asks again. With [focusFirst] and live view on, AF runs first and the shot
     * follows once it settles. Live view pauses meanwhile so the camera isn't interrupted.
     */
    fun capture(waitSeconds: Int = 5, focusFirst: Boolean = false) {
        val ptp = session ?: return
        val info = deviceInfo ?: return
        if (_shutter.value is ShutterState.Focusing) return
        scope.launch {
            _shutter.value = ShutterState.Focusing(0)
            val inLive = liveJob?.isActive == true
            liveHold = true
            try {
                if (focusFirst && inLive) {
                    runCatching { ptp.transact(PtpOp.EOS_DO_AF) }
                    delay(AF_SETTLE_MS)
                }
                ShutterRelease.fire(ptp, info.supports(PtpOp.EOS_REMOTE_RELEASE), waitSeconds.coerceIn(1, 15) * 1000L) {
                    _shutter.value = ShutterState.Focusing(it)
                }
                _shutter.value = ShutterState.Fired
                log("Disparo feito")
            } catch (e: PtpException) {
                _shutter.value = ShutterState.Failed(e.message ?: "A câmera não disparou")
                _messages.tryEmit("Não disparou: ${e.message}")
                AppLog.e("DISPARO", "Disparo falhou: ${e.message}")
            } finally {
                // In live view the mirror cycles after the shot; give it a moment before asking for frames.
                if (inLive) delay(LIVE_AFTER_SHOT_MS)
                liveHold = false
            }
            delay(1_200)
            if (_shutter.value !is ShutterState.Focusing) _shutter.value = ShutterState.Idle
        }
    }

    private suspend fun releaseShutter(ptp: PtpSession, info: DeviceInfo) =
        ShutterRelease.fire(ptp, info.supports(PtpOp.EOS_REMOTE_RELEASE), 5_000)

    fun setProperty(prop: Int, value: Int) {
        val ptp = session ?: return
        scope.launch {
            try {
                setPropertyNow(ptp, prop, value)
                _status.update { it.copy(values = it.values + (prop to value)) }
            } catch (e: PtpException) {
                _messages.tryEmit("A câmera recusou o ajuste. Confira o modo no dial (P/Tv/Av/M).")
            }
        }
    }

    private suspend fun setPropertyNow(ptp: PtpSession, prop: Int, value: Int) {
        repeat(4) { attempt ->
            try {
                ptp.transact(PtpOp.EOS_SET_DEVICE_PROP_VALUE_EX, dataOut = PtpContainer.eosPropValue(prop, value))
                return
            } catch (e: PtpResponseException) {
                if (e.code != PtpResponse.DEVICE_BUSY || attempt == 3) throw e
                delay(200)
            }
        }
    }

    /** Sets the camera clock to the phone's, so two bodies (or the phone's notes) line up in time. */
    private suspend fun setCameraClock(ptp: PtpSession) {
        val now = EosValues.cameraClock(System.currentTimeMillis())
        _status.value.value(EosProp.CAMERA_TIME)?.let { cam -> log("Relógio da câmera diferia ${now - cam} s do celular") }
        runCatching { setPropertyNow(ptp, EosProp.CAMERA_TIME, now) }
            .onSuccess { log("Relógio da câmera acertado pelo celular") }
            .onFailure {
                log("A câmera recusou o acerto do relógio: ${it.message}")
                _messages.tryEmit("A 50D não aceitou acertar o relógio pelo cabo. A opção foi desligada.")
                onClockRefused()
            }
    }

    /**
     * Streams live view to the phone. [alsoOnCamera] keeps the picture on the camera's screen too.
     * The event loop keeps running alongside (the session lock takes turns), so settings changed on
     * the camera still show up and new photos still download.
     */
    fun startLiveView(alsoOnCamera: Boolean = false, silentAf: Boolean = true, fps: Int = 20) {
        val ptp = session ?: return
        if (liveJob?.isActive == true) return
        liveFrameMs = 1000L / fps.coerceIn(5, 30)
        liveJob = scope.launch { runLiveView(ptp, alsoOnCamera, silentAf) }
    }

    fun stopLiveView() {
        val job = liveJob ?: return
        liveJob = null
        job.cancel()
        _live.value = LiveState.Off
        val ptp = session ?: return
        scope.launch {
            runCatching { if (liveZoom != 1) ptp.transact(PtpOp.EOS_ZOOM, 1) }
            runCatching { setPropertyNow(ptp, EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_OFF) }
            liveZoom = 1
        }
    }

    private suspend fun runLiveView(ptp: PtpSession, alsoOnCamera: Boolean, silentAf: Boolean) {
        _live.value = LiveState.Starting
        if (silentAf) {
            // "Modo Live" (contrast AF on the sensor) focuses without dropping the mirror; "Modo rápido"
            // drops it for every AF, which is the clack-clack. Not every firmware takes it over USB.
            runCatching { setPropertyNow(ptp, EosProp.LV_AF_SYSTEM, EosProp.LV_AF_LIVE) }
                .onSuccess { log("AF do live view: Modo Live (silencioso)") }
                .onFailure {
                    log("A câmera não aceitou trocar o AF do live view: ${it.message}")
                    _messages.tryEmit("Para focar sem o barulho do espelho, na 50D escolha Menu → Live View → Modo AF: Modo Live.")
                }
        }
        val output = if (alsoOnCamera) EosProp.EVF_PHONE or EosProp.EVF_CAMERA else EosProp.EVF_PHONE
        try {
            setPropertyNow(ptp, EosProp.EVF_OUTPUT_DEVICE, output)
        } catch (e: PtpException) {
            _live.value = LiveState.Failed("A câmera recusou o live view (${e.message}). $LIVE_HINT")
            return
        }
        log("Live view ligado")
        var lastFrame = System.currentTimeMillis()
        var windowStart = lastFrame
        var windowFrames = 0
        var fps = 0f
        while (scope.isActive && session === ptp && liveJob?.isActive == true) {
            if (liveHold) {
                // The shutter has the camera: no frames, and the pause doesn't count as silence.
                delay(50)
                lastFrame = System.currentTimeMillis()
                continue
            }
            val started = System.currentTimeMillis()
            val data = try {
                ptp.transactForData(PtpOp.EOS_GET_VIEWFINDER_DATA, LIVE_VIEW_PARAM, timeoutMs = LIVE_TIMEOUT_MS)
            } catch (e: PtpResponseException) {
                // "Not ready" while the mirror goes up or the camera focuses; only a long silence is a problem.
                if (started - lastFrame > LIVE_GIVE_UP_MS) {
                    _live.value = LiveState.Failed("A 50D não mandou imagem. $LIVE_HINT")
                    runCatching { setPropertyNow(ptp, EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_OFF) }
                    return
                }
                delay(60)
                continue
            }
            val jpeg = LiveViewParser.jpeg(data) ?: continue
            _liveFrames.tryEmit(jpeg)
            lastFrame = System.currentTimeMillis()
            windowFrames++
            if (lastFrame - windowStart >= 1000) {
                fps = windowFrames * 1000f / (lastFrame - windowStart)
                windowFrames = 0
                windowStart = lastFrame
            }
            _live.value = LiveState.Running(fps, liveZoom)
            // Leave the cable some room for events and downloads, and the phone some rest.
            val spent = lastFrame - started
            if (spent < liveFrameMs) delay(liveFrameMs - spent)
        }
    }

    /** Camera zoom on the live picture: 1, 5 or 10 times. */
    fun setLiveZoom(level: Int) = liveCommand("A câmera não aceitou o zoom") {
        it.transact(PtpOp.EOS_ZOOM, level)
        liveZoom = level
    }

    /** Moves the zoomed area / AF frame. [x], [y] are 0…1 across the picture. */
    fun setLiveFocusPoint(x: Float, y: Float) = liveCommand("A câmera não aceitou a posição") {
        it.transact(PtpOp.EOS_ZOOM_POSITION, (x.coerceIn(0f, 1f) * SENSOR_W).toInt(), (y.coerceIn(0f, 1f) * SENSOR_H).toInt())
    }

    /** Autofocus now (like half-pressing with AF-ON), using the camera's live view AF mode. */
    fun liveAutofocus() = liveCommand("Não focou: confira se a lente está em AF") {
        it.transact(PtpOp.EOS_DO_AF)
    }

    /** Nudges focus with the lens in AF: [step] 1…3 towards near, −1…−3 towards far. */
    fun driveLens(step: Int) = liveCommand("A lente não se moveu: deixe a chave da lente em AF") {
        val size = kotlin.math.abs(step).coerceIn(1, 3)
        it.transact(PtpOp.EOS_DRIVE_LENS, if (step < 0) 0x8000 or size else size)
    }

    /** Stops the lens down to the chosen aperture on the live picture (depth of field preview). */
    fun depthOfFieldPreview(on: Boolean) = liveCommand("A câmera não fez a prévia de profundidade") {
        it.transact(PtpOp.EOS_DOF_PREVIEW, if (on) 1 else 0)
    }

    private fun liveCommand(failure: String, block: suspend (PtpSession) -> Unit) {
        val ptp = session ?: return
        scope.launch {
            try {
                block(ptp)
            } catch (e: PtpException) {
                _messages.tryEmit(failure)
                log("$failure: ${e.message}")
            }
        }
    }

    /** Applies a saved preset, skipping values the current dial mode doesn't let the camera change. */
    fun applyPreset(preset: ExposurePreset) {
        val ptp = session ?: return
        scope.launch {
            val mode = _status.value.value(EosProp.SHOOTING_MODE)
            val steps = listOfNotNull(
                preset.iso?.let { EosProp.ISO to it },
                preset.shutter?.takeIf { mode in TV_MODES }?.let { EosProp.SHUTTER_SPEED to it },
                preset.aperture?.takeIf { mode in AV_MODES }?.let { EosProp.APERTURE to it },
                preset.exposureCompensation?.takeIf { mode != MODE_M }?.let { EosProp.EXPOSURE_COMPENSATION to it },
                preset.whiteBalance?.let { EosProp.WHITE_BALANCE to it },
            )
            var failed = 0
            for ((prop, value) in steps) {
                try {
                    setPropertyNow(ptp, prop, value)
                    _status.update { it.copy(values = it.values + (prop to value)) }
                } catch (e: PtpException) {
                    failed++
                }
            }
            _messages.tryEmit(if (failed == 0) "Preset \"${preset.name}\" aplicado" else "Preset aplicado em parte ($failed ajuste(s) recusado(s))")
        }
    }

    /**
     * Bracketing done by the app: shifts ±EV (or the shutter speed in M) per frame, fires, and restores
     * the starting value at the end, so it works whatever the camera's own AEB setting is.
     */
    fun startBracket(shots: Int, stepThirds: Int) {
        val ptp = session ?: return
        val info = deviceInfo ?: return
        if (_bracketing.value != null) return
        scope.launch {
            val mode = _status.value.value(EosProp.SHOOTING_MODE)
            val useShutter = mode == MODE_M
            val prop = if (useShutter) EosProp.SHUTTER_SPEED else EosProp.EXPOSURE_COMPENSATION
            val base = _status.value.value(prop)
            if (base == null || (useShutter && !EosValues.isKnownShutter(base))) {
                _messages.tryEmit("Bracketing indisponível: a câmera ainda não informou a exposição")
                return@launch
            }
            val offsets = Bracket.offsets(shots, stepThirds)
            try {
                offsets.forEachIndexed { i, offset ->
                    _bracketing.value = BracketState(i + 1, offsets.size)
                    val value = if (useShutter) Bracket.shutter(base, offset) else Bracket.compensation(base, offset)
                    setPropertyNow(ptp, prop, value)
                    val seconds = if (useShutter) EosValues.shutterSeconds(value) else 0.0
                    // Subscribe before firing so the new-file event can't slip past between release and collect.
                    val arrived = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull((seconds * 1000).toLong() + 6_000) { newObjects.first() }
                    }
                    releaseShutter(ptp, info)
                    arrived.join()
                }
            } catch (e: PtpException) {
                _messages.tryEmit("Bracketing interrompido: ${e.message}")
            } finally {
                runCatching { setPropertyNow(ptp, prop, base) }
                _status.update { it.copy(values = it.values + (prop to base)) }
                _bracketing.value = null
            }
        }
    }

    /** All files on the card (folders excluded), newest name last. Uses standard PTP listing. */
    suspend fun listCardFiles(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<ObjectInfo> {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        val info = deviceInfo ?: throw PtpException("Câmera desconectada")
        if (!info.supports(PtpOp.GET_OBJECT_HANDLES) || !info.supports(PtpOp.GET_OBJECT_INFO)) {
            throw PtpException("Esta câmera não permite listar o cartão pela USB")
        }
        val storages = PtpDataReader(ptp.transactForData(PtpOp.GET_STORAGE_IDS)).u32Array()
        val files = mutableListOf<ObjectInfo>()
        for (storage in storages) {
            // Parent 0 = every object in the storage, flat, whatever folder it is in.
            val handles = PtpDataReader(ptp.transactForData(PtpOp.GET_OBJECT_HANDLES, storage, 0, 0)).u32Array()
            handles.forEachIndexed { i, handle ->
                val obj = ObjectInfo.parse(handle, ptp.transactForData(PtpOp.GET_OBJECT_INFO, handle))
                if (!obj.isFolder) files += obj
                onProgress(i + 1, handles.size)
            }
        }
        return files.sortedBy { it.filename }
    }

    /** Small JPEG the camera keeps for each file (about 160×120). */
    suspend fun thumbnail(handle: Int): ByteArray? {
        val ptp = session ?: return null
        return runCatching { ptp.transactForData(PtpOp.GET_THUMB, handle) }.getOrNull()
    }

    val supportsCardListing: Boolean
        get() = deviceInfo?.let { it.supports(PtpOp.GET_OBJECT_HANDLES) && it.supports(PtpOp.GET_OBJECT_INFO) } == true

    fun startInterval(seconds: Int, shots: Int) {
        intervalJob?.cancel()
        intervalJob = scope.launch {
            _interval.value = IntervalState(running = true, done = 0, total = shots, seconds = seconds)
            for (i in 1..shots) {
                val ptp = session ?: break
                val info = deviceInfo ?: break
                runCatching { releaseShutter(ptp, info) }.onFailure { _messages.tryEmit("Intervalômetro: ${it.message}") }
                _interval.update { it.copy(done = i) }
                if (i < shots) delay(seconds * 1000L)
            }
            _interval.update { it.copy(running = false) }
        }
    }

    fun stopInterval() {
        intervalJob?.cancel()
        _interval.update { it.copy(running = false) }
    }

    /** Ends the session politely so the camera leaves PC mode and goes back to normal. */
    fun disconnect() {
        val ptp = session ?: return
        // Detach first so the event loop stops and its error handler ignores this session.
        closeSession(closeTransport = false)
        scope.launch {
            withTimeoutOrNull(2_000) {
                // Hand the picture back to the camera's screen before leaving remote mode.
                runCatching { ptp.transact(PtpOp.EOS_SET_DEVICE_PROP_VALUE_EX, dataOut = PtpContainer.eosPropValue(EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_OFF), timeoutMs = 1_000) }
                runCatching { ptp.transact(PtpOp.EOS_SET_EVENT_MODE, 0, timeoutMs = 1_000) }
                runCatching { ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, 0, timeoutMs = 1_000) }
                runCatching { ptp.transact(PtpOp.CLOSE_SESSION, timeoutMs = 1_000) }
            }
            ptp.close()
            _state.value = ConnectionState.NoCamera
            log("Sessão encerrada")
        }
    }

    private fun closeSession(closeTransport: Boolean = true) {
        stopInterval()
        liveJob?.cancel()
        liveJob = null
        _live.value = LiveState.Off
        liveZoom = 1
        val ptp = session
        session = null
        device = null
        deviceInfo = null
        sessionJob?.cancel()
        sessionJob = null
        if (closeTransport) ptp?.close()
        _status.value = CameraStatus()
    }

    private fun stillImageInterface(dev: UsbDevice) =
        (0 until dev.interfaceCount).map { dev.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }

    private fun log(message: String) {
        AppLog.i("USB", message)
        val line = "${timeFormat.format(Date())}  $message"
        _log.update { (it + line).takeLast(300) }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    companion object {
        private const val TAG = "CameraManager"
        private const val MODE_M = 3
        private val TV_MODES = setOf(1, 3, 4)
        private val AV_MODES = setOf(2, 3, 4)
        private const val ACTION_USB_PERMISSION = "com.jrtechofc.tether50d.USB_PERMISSION"
        const val CANON_VENDOR_ID = 0x04A9
        private const val LIVE_VIEW_PARAM = 0x00100000
        private const val LIVE_TIMEOUT_MS = 3_000
        private const val LIVE_GIVE_UP_MS = 6_000L
        private const val AF_SETTLE_MS = 700L
        private const val LIVE_AFTER_SHOT_MS = 600L
        /** Zoom position coordinates use the sensor's pixel grid (50D: 4752 × 3168). */
        private const val SENSOR_W = 4752
        private const val SENSOR_H = 3168
        const val LIVE_HINT = "Na 50D, ative em Menu → Configurações de Live View → Disparo com Live View: Ativar, e deixe o dial em P, Tv, Av ou M."
        private const val POLL_IDLE_MS = 200L
        private const val POLL_BUSY_MS = 40L
        private const val KEEP_ALIVE_MS = 60_000L
        private const val DOWNLOAD_TIMEOUT_MS = 20_000
    }
}
