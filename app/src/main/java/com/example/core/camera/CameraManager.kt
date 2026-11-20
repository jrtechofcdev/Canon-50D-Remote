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
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel

sealed interface ConnectionState {
    data object NoCamera : ConnectionState
    data object WaitingPermission : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val model: String, val serial: String) : ConnectionState
    data class Failed(val message: String) : ConnectionState

    /**
     * The link dropped with the cable still in (a USB hiccup, the camera's auto power off): the app is
     * trying again on its own, [attempt] of [Reconnect.TRIES].
     */
    data class Reconnecting(val attempt: Int, val reason: String) : ConnectionState
}

/** How long to wait before each new try after the link drops: 1, 2, 4, 8, 16 and then 30 s. */
object Reconnect {
    const val TRIES = 10

    fun delayMs(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
}

/** Live view: off, starting, streaming (with measured frame rate and camera zoom), or failed with a hint. */
sealed interface LiveState {
    data object Off : LiveState
    data object Starting : LiveState
    data class Running(val fps: Float, val zoom: Int) : LiveState
    data class Failed(val message: String) : LiveState
}

/** Exposure values as last reported by the camera, keyed by EOS property code. */
/** Modo desenvolvedor, to compare on the camera: SetRemoteMode's value and whether the capture destination is written. */
data class ConnectionPrefs(
    val remoteMode: Int = 1,
    val writeDestination: Boolean = true,
    val restoreLiveOutput: Boolean = false,
    val resetRemoteAfterLive: Boolean = true,
)

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
    /** Modo desenvolvedor: which release to use and RemoteReleaseOn's second parameter. */
    private val releasePrefs: suspend () -> ReleasePrefs = { ReleasePrefs() },
    /** Modo desenvolvedor: remote mode value and whether the capture destination is written. */
    private val connectionPrefs: suspend () -> ConnectionPrefs = { ConnectionPrefs() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler("CAMERA"))
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.NoCamera)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    // Room for a long burst (RAW + JPG of every frame) while the download side is busy.
    private val _newObjects = MutableSharedFlow<EosEventRecord.ObjectAdded>(extraBufferCapacity = 1024)
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
    /** The "turn live view off" still on its way to the camera; a new start waits for it. */
    private var liveStopJob: Job? = null
    @Volatile private var liveZoom = 1
    /** Set while the shutter works, so live view stops asking for frames and the camera can focus and fire. */
    @Volatile private var liveHold = false
    /** Live view AF runs on the sensor ("Modo Live"): slower, so every wait around it is longer. */
    @Volatile private var liveSilentAf = false
    private var afJob: kotlinx.coroutines.Job? = null
    @Volatile private var liveFrameMs = 50L

    private val _shutter = MutableStateFlow<ShutterState>(ShutterState.Idle)
    val shutter: StateFlow<ShutterState> = _shutter.asStateFlow()
    /** One shot at a time: the intervalometer and the button never send RemoteRelease together. */
    private val shotLock = kotlinx.coroutines.sync.Mutex()
    /** The finger on the app's shutter for the shot in progress; null for a tap, the intervalometer and bracketing. */
    @Volatile private var gestures: Channel<Gesture>? = null
    /** Set when the camera answers "unknown command" to the two-step press: the rest of the session uses 0x910F. */
    @Volatile private var pressRejected = false

    /** What the camera announces for the shutter and what the app uses, for the Desenvolvedor screen. */
    private val _releaseInfo = MutableStateFlow<String?>(null)
    val releaseInfo: StateFlow<String?> = _releaseInfo.asStateFlow()
    /** How the app's button presses the connected camera's shutter; null without a camera. */
    private val _releaseMethod = MutableStateFlow<ReleaseMethod?>(null)
    val releaseMethod: StateFlow<ReleaseMethod?> = _releaseMethod.asStateFlow()

    /** The session [disconnect] is saying goodbye on: its USB must stay open until the camera got it. */
    @Volatile private var closingPolitely: PtpSession? = null

    /** The camera's values when live view opened, to log what live view left changed. */
    @Volatile private var valuesBeforeLive: Map<Int, Int> = emptyMap()
    /** Modo desenvolvedor: how much of the USB link is reset before a session opens. */
    @Volatile var usbReset: com.example.core.ptp.UsbReset = com.example.core.ptp.UsbReset.FULL

    /** Live view ran in this session: a camera stuck "busy" afterwards gets live view turned off again. */
    @Volatile private var liveUsed = false

    /** What the connected camera lists as supported; everything true without a camera. */
    private val _capabilities = MutableStateFlow(CameraCapabilities())
    val capabilities: StateFlow<CameraCapabilities> = _capabilities.asStateFlow()

    /** False once the camera turns down the depth of field preview (the 50D does over USB): the button goes away. */
    private val _dofSupported = MutableStateFlow(true)
    val dofSupported: StateFlow<Boolean> = _dofSupported.asStateFlow()

    private var session: PtpSession? = null
    private var device: UsbDevice? = null
    private var deviceInfo: DeviceInfo? = null
    private var sessionJob: Job? = null
    private var intervalJob: Job? = null
    private var retryJob: Job? = null
    /** How each try during a reconnect ended: true = connected, false = failed. */
    private val attemptEnded = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)

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
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (dev != null && (dev.deviceName == device?.deviceName || _state.value is ConnectionState.Reconnecting)) {
                    log("Cabo desconectado")
                    retryJob?.cancel()
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
        // Tapping Conectar while the app is already retrying: the tap takes over right now.
        retryJob?.cancel()
        val camera = attachedCamera()
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

    private fun attachedCamera(): UsbDevice? = usbManager.deviceList.values
        .filter { stillImageInterface(it) != null }
        .sortedByDescending { it.vendorId == CANON_VENDOR_ID }
        .firstOrNull()

    /**
     * The link dropped while the cable is still in: try again on our own, waiting a little longer each
     * time, until the camera answers, the cable comes out or [Reconnect.TRIES] are used up.
     */
    private fun reconnect(reason: String) {
        retryJob?.cancel()
        retryJob = scope.launch {
            for (attempt in 1..Reconnect.TRIES) {
                _state.value = ConnectionState.Reconnecting(attempt, reason)
                kotlinx.coroutines.delay(Reconnect.delayMs(attempt))
                val dev = attachedCamera()
                if (dev == null) {
                    _state.value = ConnectionState.NoCamera
                    return@launch
                }
                if (!usbManager.hasPermission(dev)) break
                log("Reconectando (tentativa $attempt de ${Reconnect.TRIES})")
                // The session opens on its own coroutine: listen before opening, then wait for how it ended.
                val ended = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(20_000) { attemptEnded.first() }
                }
                open(dev, retrying = true)
                if (ended.await() == true) {
                    log("Câmera reconectada")
                    _messages.tryEmit("Câmera reconectada")
                    return@launch
                }
                if (session != null) closeSession()
                _state.value = ConnectionState.Reconnecting(attempt, reason)
            }
            _state.value = ConnectionState.Failed("$reason. Confira a câmera e toque em Conectar.")
        }
    }

    private fun open(dev: UsbDevice, retrying: Boolean = false) {
        // Two "permission granted" answers (two taps on Conectar) must not open the camera twice: the
        // second connection would replace the first without ever closing it.
        if (_state.value is ConnectionState.Connected || session != null) return
        if (!retrying) _state.value = ConnectionState.Connecting
        val iface = stillImageInterface(dev)
        val endpoints = iface?.let { UsbPtpTransport.endpointsOf(it) }
        val connection = usbManager.openDevice(dev)
        if (iface == null || endpoints == null || connection == null) {
            connection?.close()
            if (retrying) attemptEnded.tryEmit(false) else _state.value = ConnectionState.Failed("Não foi possível abrir a câmera pela USB.")
            return
        }
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            if (retrying) attemptEnded.tryEmit(false) else _state.value = ConnectionState.Failed("Outro app está usando a câmera. Feche-o e tente de novo.")
            return
        }
        val transport = UsbPtpTransport(connection, iface, endpoints.first, endpoints.second, usbReset)
        if (usbReset != com.example.core.ptp.UsbReset.FULL) log("Reset da USB antes da sessão: $usbReset (modo desenvolvedor)")
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
            liveUsed = false
            pressRejected = false
            val method = ShutterPress.methodFor(info, releasePrefs().method)
            _releaseInfo.value = ShutterPress.describe(info, method)
            _releaseMethod.value = method
            AppLog.i("DISPARO", "Disparo pelo cabo: ${ShutterPress.describe(info, method)}")
            AppLog.i("DISPARO", "Operações Canon anunciadas: " +
                info.operations.filter { it in 0x9100..0x91FF }.sorted().joinToString(" ") { "%04X".format(it) })
            val caps = CameraCapabilities.of(info)
            _capabilities.value = caps
            _dofSupported.value = caps.dof
            log("Câmera: ${info.manufacturer} ${info.model} (firmware ${info.version})")
            AppLog.i("CAMERA", "Capacidades: ${caps.describe()}")
            val prefs = connectionPrefs()

            // Remote mode + event mode is what EOS Utility uses for tethering. No UI lock is sent,
            // so the shutter button, dials and menus on the camera stay usable.
            if (info.supports(PtpOp.EOS_SET_REMOTE_MODE)) {
                ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, prefs.remoteMode)
                if (prefs.remoteMode != 1) AppLog.i("CAMERA", "Modo remoto ${prefs.remoteMode} (modo desenvolvedor)")
            }
            if (info.supports(PtpOp.EOS_SET_EVENT_MODE)) ptp.transact(PtpOp.EOS_SET_EVENT_MODE, 1)
            // A cable pulled while the app's button was held leaves the camera "pressed": let go first.
            if (info.supports(PtpOp.EOS_REMOTE_RELEASE_OFF)) ShutterPress.releaseAll(ptp)
            if (prefs.writeDestination) {
                runCatching { setPropertyNow(ptp, EosProp.CAPTURE_DESTINATION, EosProp.DESTINATION_CARD) }
                    .onFailure { log("Destino de captura mantido pela câmera: ${it.message}") }
            } else {
                AppLog.i("CAMERA", "Destino de captura não gravado (modo desenvolvedor)")
            }

            // The first GetEvent calls describe the current state (and may list files already on the card).
            // Read them until the queue is empty and only then start treating new objects as new shots.
            for (i in 0 until 20) {
                val records = EosEventParser.parse(ptp.transactForData(PtpOp.EOS_GET_EVENT))
                applyRecords(records, announceObjects = false)
                if (records.isEmpty()) break
            }

            // Values the camera left out of its first events (the drive mode, often) are asked for one by one.
            requestMissingValues(ptp, "conexão")
            _status.value.value(EosProp.CAPTURE_DESTINATION)?.let { AppLog.i("CAMERA", "Destino de captura na câmera: $it (2 = cartão)") }
            AppLog.i("CAMERA", "Live view na conexão: saída D1B0=${_status.value.value(EosProp.EVF_OUTPUT_DEVICE) ?: "-"}, D1B8=${_status.value.value(0xD1B8) ?: "-"}")

            if (syncClock()) setCameraClock(ptp)

            _state.value = ConnectionState.Connected(info.model.ifBlank { "Canon EOS" }, info.serial)
            attemptEnded.tryEmit(true)
            log("Pronto: captura vinculada ativa")
            eventLoop(ptp, info)
        } catch (e: Exception) {
            if (session === ptp) {
                log("Erro: ${e.message}")
                val wasConnected = _state.value is ConnectionState.Connected
                closeSession()
                val reason = e.message ?: "Falha na comunicação com a câmera"
                when {
                    // A try inside a reconnect: the screen keeps saying "Reconectando", the loop decides.
                    retryJob?.isActive == true -> attemptEnded.tryEmit(false)
                    // A link that was working drops with the cable still in: try again on our own.
                    wasConnected && attachedCamera() != null -> {
                        _lostConnection.tryEmit("Conexão com a câmera perdida")
                        reconnect("Conexão perdida")
                    }
                    else -> {
                        // The 50D answers nothing to a new session on a link it already closed: only replugging helps.
                        _state.value = ConnectionState.Failed(
                            if (!wasConnected && reason.startsWith("Sem resposta da câmera")) "A câmera não respondeu. Tire e ponha o cabo USB de novo." else reason,
                        )
                        if (wasConnected) _lostConnection.tryEmit("Conexão com a câmera perdida")
                    }
                }
            } else if (closingPolitely !== ptp) {
                // A session that was already replaced still holds the USB interface: let it go.
                // Not while [disconnect] is still saying goodbye on it: closing the USB first (as up to 5.3.1)
                // left the camera with its old session open, and it then answered nothing until the cable came out.
                runCatching { ptp.close() }
            }
        }
    }

    private suspend fun eventLoop(ptp: PtpSession, info: DeviceInfo) {
        var lastKeepAlive = System.currentTimeMillis()
        while (scope.isActive && session === ptp) {
            val raw = try {
                ptp.transactForData(PtpOp.EOS_GET_EVENT)
            } catch (e: PtpResponseException) {
                // Busy while the camera writes to the card: just ask again shortly.
                delay(300)
                continue
            }
            val records = EosEventParser.parse(raw)
            if (raw.size > 8) eventTap?.let { tap -> runCatching { tap(raw) } }
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
                log("${if (record.isVideo) "Novo vídeo" else "Nova foto"}: ${record.filename} (${record.size / 1024} KB)")
                // Never silent: a frame that couldn't be announced is found again by the search for missed frames.
                if (!_newObjects.tryEmit(record)) AppLog.w("CAMERA", "Fila de fotos novas cheia: ${record.filename} fica para a busca de fotos perdidas")
            }
        }
    }

    /**
     * Streams one file from the camera, from byte [startAt] (a download resumed midway). In 1 MB blocks
     * when the camera lists GetPartialObject, so live view and the shutter keep their turns, and paused
     * while the shutter works; in one GetObject otherwise (that one always starts from the beginning).
     */
    suspend fun download(handle: Int, size: Long, sink: PtpDataSink, startAt: Long = 0L) {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        if (!_capabilities.value.partialObject || size <= 0) {
            require(startAt == 0L) { "GetObject não retoma do meio" }
            ptp.transact(PtpOp.GET_OBJECT, handle, dataIn = sink, timeoutMs = DOWNLOAD_TIMEOUT_MS)
            return
        }
        PartialDownload.run(ptp, handle, size, sink, startAt, beforeChunk = {
            // The shutter goes first: the next block waits until the shot is done.
            while (liveHold && session === ptp) delay(20)
            if (session !== ptp) throw PtpException("Câmera desconectada")
        })
    }

    /** True when [download] can continue a file from the middle. */
    val canResumeDownloads: Boolean get() = session != null && _capabilities.value.partialObject

    /**
     * Fires from the app with a tap. The camera gets up to [waitSeconds] to focus and fire.
     * See [shutterDown] for the button held down.
     */
    fun capture(waitSeconds: Int = 5, focusFirst: Boolean = false) {
        startShot(waitSeconds, focusFirst, null)
    }

    /**
     * Raw GetEvent data, for the video module's monitor. Called on the event loop with every
     * non-empty answer; a failure in it is ignored so the loop never stops.
     */
    @Volatile var eventTap: ((ByteArray) -> Unit)? = null

    /** The camera's DeviceInfo of the current session, null when disconnected. */
    val currentDeviceInfo: DeviceInfo? get() = deviceInfo

    /** What a raw command got back: the response code, its parameters and how many data bytes came. */
    data class CommandResult(val code: Int, val params: IntArray, val dataBytes: Int, val millis: Long, val data: ByteArray = ByteArray(0)) {
        val ok get() = code == PtpResponse.OK
        fun describe() = "resposta 0x%04X (%s)%s%s em %d ms".format(
            code, PtpResponse.name(code),
            if (params.isNotEmpty()) " · parâmetros " + params.joinToString(",") { "0x%X".format(it) } else "",
            if (dataBytes > 0) " · $dataBytes bytes de dados" else "",
            millis,
        )
    }

    /** Sends any operation and reports the answer instead of throwing: the video lab's test commands. */
    suspend fun rawCommand(op: Int, vararg params: Int, readData: Boolean = false): CommandResult {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        val started = System.currentTimeMillis()
        return try {
            if (readData) {
                val sink = com.example.core.ptp.ByteArraySink()
                val answer = ptp.transact(op, *params, dataIn = sink)
                val bytes = sink.bytes()
                CommandResult(PtpResponse.OK, answer, bytes.size, System.currentTimeMillis() - started, bytes)
            } else {
                CommandResult(PtpResponse.OK, ptp.transact(op, *params), 0, System.currentTimeMillis() - started)
            }
        } catch (e: PtpResponseException) {
            CommandResult(e.code, IntArray(0), 0, System.currentTimeMillis() - started)
        }
    }

    /** SetDevicePropValueEx with the answer reported, for the video lab. */
    suspend fun rawSetProperty(prop: Int, value: Int): CommandResult {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        val started = System.currentTimeMillis()
        return try {
            ptp.transact(PtpOp.EOS_SET_DEVICE_PROP_VALUE_EX, dataOut = PtpContainer.eosPropValue(prop, value))
            CommandResult(PtpResponse.OK, IntArray(0), 0, System.currentTimeMillis() - started)
        } catch (e: PtpResponseException) {
            CommandResult(e.code, IntArray(0), 0, System.currentTimeMillis() - started)
        }
    }

    /**
     * Stops sending live view to the phone and gives the picture back to the camera's screen, keeping
     * the session open. Magic Lantern's movie mode on the 50D needs live view on the camera itself.
     */
    suspend fun handLiveViewToCamera(): CommandResult {
        val job = liveJob
        liveJob = null
        job?.cancel()
        job?.join()
        liveStopJob?.join()
        _live.value = LiveState.Off
        _liveFrames.resetReplayCache()
        val ptp = session ?: throw PtpException("Câmera desconectada")
        runCatching { if (liveZoom != 1) ptp.transact(PtpOp.EOS_ZOOM, 1) }
        liveZoom = 1
        cancelAf(ptp, "ao passar o live view para a câmera")
        // Buttons first (remote mode 0 → on), then the camera's live view: in the other order the reset
        // switched the camera's live view off again (event 0xD1B0 = 0 in the 5.3.4 log) and SET did nothing.
        resetRemoteMode(ptp, "ao passar o live view para a câmera")
        log("Live view devolvido à tela da câmera")
        return rawSetProperty(EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_CAMERA)
    }

    /**
     * Leaves the camera to the photographer, session still open: the app's live view stops and the
     * body's buttons come back. The camera's own live view button and SET then work as without the cable.
     */
    suspend fun freeCameraBody() {
        stopLiveView()
        liveStopJob?.join()
        val ptp = session ?: throw PtpException("Câmera desconectada")
        cancelAf(ptp, "ao soltar a câmera")
        resetRemoteMode(ptp, "ao soltar a câmera")
    }

    /**
     * Downloads a video in 1 MB pieces ([PartialDownload], the same code as the photos): live view,
     * events and the shutter get the USB between pieces. [keepGoing] false stops it with an exception.
     */
    suspend fun downloadInChunks(
        handle: Int,
        size: Long,
        sink: PtpDataSink,
        chunk: Int = PartialDownload.CHUNK,
        keepGoing: () -> Boolean = { true },
    ) {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        // GetPartialObject takes a 32-bit offset; bigger files (and cameras without it) go in one GetObject.
        if (!_capabilities.value.partialObject || size <= 0 || size > 0xFFFF_FFFFL) {
            ptp.transact(PtpOp.GET_OBJECT, handle, dataIn = sink, timeoutMs = DOWNLOAD_TIMEOUT_MS)
            return
        }
        PartialDownload.run(ptp, handle, size, sink, chunk = chunk, beforeChunk = {
            if (!keepGoing()) throw PtpException("Download cancelado")
            while (liveHold && session === ptp) delay(20)
            if (session !== ptp) throw PtpException("Câmera desconectada")
        })
    }

    /**
     * The finger touched the app's shutter. With the two-step press the camera half-presses now (it
     * focuses, like a finger resting on the button); [shutterUp] takes the photo, [shutterHold] starts a
     * burst in a continuous drive mode, and [shutterCancel] (finger slid off) lets go without a photo.
     * With the one-step release nothing reaches the camera until the finger lifts.
     */
    fun shutterDown(waitSeconds: Int = 5, focusFirst: Boolean = false) {
        startShot(waitSeconds, focusFirst, Channel(Channel.UNLIMITED))
    }

    fun shutterUp() { gestures?.trySend(Gesture.UP) }
    fun shutterHold() { gestures?.trySend(Gesture.HOLD) }
    fun shutterCancel() { gestures?.trySend(Gesture.CANCEL) }

    private fun startShot(waitSeconds: Int, focusFirst: Boolean, finger: Channel<Gesture>?) {
        val ptp = session ?: return
        val info = deviceInfo ?: return
        // A log showed the button and the intervalometer firing together: both got "busy" and neither fired.
        if (_interval.value.running) {
            _messages.tryEmit("O intervalômetro está disparando. Pare o intervalômetro para usar o botão.")
            return
        }
        if (_bracketing.value != null) {
            _messages.tryEmit("O bracketing está disparando. Espere a série terminar.")
            return
        }
        // Marked busy before anything starts, so a double tap fires once.
        val before = _shutter.value
        if (before is ShutterState.Focusing || !_shutter.compareAndSet(before, ShutterState.Focusing(0))) return
        gestures = finger
        scope.launch {
            val inLive = liveJob?.isActive == true
            liveHold = true
            var fired = false
            try {
                shotLock.withLock {
                    val twoStep = methodNow(info) == ReleaseMethod.PRESS_RELEASE
                    if (inLive) {
                        // A tap-AF still running (or never closed) makes the next release come back busy or do
                        // nothing: finish it first, the way the camera expects.
                        afJob?.cancel()
                        cancelAf(ptp, "antes do disparo")
                        // The half press focuses by itself; a separate AF before it only made the camera busy.
                        if (focusFirst && !twoStep) {
                            runAf(ptp, "antes do disparo")
                            cancelAf(ptp, "depois do foco")
                        }
                    }
                    // Contrast AF ("Modo Live") can take a few seconds per try; give the release more room.
                    val wait = (waitSeconds.coerceIn(1, 15) + if (inLive && liveSilentAf) 4 else 0) * 1000L
                    AppLog.i("DISPARO", "Disparando${if (inLive) " no live view (AF ${if (liveSilentAf) "Live" else "rápido"})" else ""}, espera até ${wait / 1000} s${evfNote()}")
                    val next: (suspend () -> Gesture)? = finger?.let { ch ->
                        { withTimeoutOrNull(ShutterPress.MAX_HALF_HOLD_MS) { ch.receive() } ?: Gesture.CANCEL }
                    }
                    fired = shootOnce(ptp, info, wait, inLive, next) { _shutter.value = ShutterState.Focusing(it) }
                }
                if (fired) {
                    _shutter.value = ShutterState.Fired
                    log("Disparo feito")
                } else {
                    _shutter.value = ShutterState.Idle
                    AppLog.i("DISPARO", "Disparo cancelado: o dedo saiu do botão")
                }
            } catch (e: PtpException) {
                _shutter.value = ShutterState.Failed(e.message ?: "A câmera não disparou")
                _messages.tryEmit("Não disparou: ${e.message}")
                AppLog.e("DISPARO", "Disparo falhou: ${e.message}")
            } finally {
                if (gestures === finger) gestures = null
                // In live view the mirror cycles after the shot; give it a moment before asking for frames.
                if (inLive) delay(LIVE_AFTER_SHOT_MS)
                liveHold = false
            }
            if (!fired && _shutter.value is ShutterState.Idle) return@launch
            delay(1_200)
            if (_shutter.value !is ShutterState.Focusing) _shutter.value = ShutterState.Idle
        }
    }

    /** The release this shot gets: the developer choice, else what the camera announces (0x910F after a refusal). */
    private suspend fun methodNow(info: DeviceInfo): ReleaseMethod {
        val prefs = releasePrefs()
        val method = ShutterPress.methodFor(info, prefs.method)
        return if (method == ReleaseMethod.PRESS_RELEASE && pressRejected && prefs.method == ReleasePrefs.AUTO) ReleaseMethod.LEGACY else method
    }

    /**
     * One photo, by the method this camera gets. Returns false when the finger slid off (no photo);
     * throws [PtpException] with the reason when the camera did not make the photo. With the two-step
     * press the photo counts only when the camera announces the new file, not on its "OK".
     */
    private suspend fun shootOnce(
        ptp: PtpSession,
        info: DeviceInfo,
        waitMs: Long,
        inLive: Boolean,
        nextGesture: (suspend () -> Gesture)? = null,
        onWaiting: (Long) -> Unit = {},
    ): Boolean {
        val prefs = releasePrefs()
        // Long exposures: the file comes after the shutter closes.
        val exposureMs = _status.value.value(EosProp.SHUTTER_SPEED)
            ?.takeIf { EosValues.isKnownShutter(it) }?.let { (EosValues.shutterSeconds(it) * 1000).toLong() } ?: 0L

        suspend fun legacy(): Boolean {
            // The one-step release can't half-press: it waits for the finger, as the button always did.
            if (nextGesture != null) {
                while (true) when (nextGesture()) {
                    Gesture.CANCEL -> return false
                    Gesture.UP -> break
                    Gesture.HOLD -> Unit
                }
            }
            fireLogged(ptp, waitMs + exposureMs, inLive, onWaiting)
            return true
        }

        when (methodNow(info)) {
            ReleaseMethod.NONE -> throw PtpException("Esta câmera não aceita disparo pelo cabo.")
            ReleaseMethod.LEGACY -> return legacy()
            ReleaseMethod.PRESS_RELEASE -> Unit
        }
        val drive = _status.value.value(EosProp.DRIVE_MODE)
        val wait = waitMs + exposureMs + ShutterPress.timerExtraMs(drive)
        // Listening before the press, so the new-file event can't slip past.
        val photo = scope.async(start = CoroutineStart.UNDISPATCHED) { newObjects.first() }
        try {
            val outcome = ShutterPress.shoot(
                ptp, prefs.flag, ShutterPress.isContinuous(drive), wait,
                photoArrived = { ms -> withTimeoutOrNull(ms) { photo.await() } != null },
                nextGesture = nextGesture,
                report = { AppLog.i("DISPARO", "Respostas da câmera (flag ${prefs.flag}${drive?.let { d -> ", drive 0x%02X".format(d) } ?: ""}): $it") },
            )
            return when (outcome) {
                PressOutcome.FIRED -> true
                PressOutcome.CANCELLED -> false
                PressOutcome.NO_PHOTO -> throw PtpException(ShutterPress.noPhotoMessage(wait))
            }
        } catch (e: PtpResponseException) {
            if (ShutterPress.unsupported(e)) {
                if (prefs.method != ReleasePrefs.AUTO) throw PtpException(ShutterPress.UNSUPPORTED_MESSAGE)
                pressRejected = true
                _releaseInfo.value = ShutterPress.describe(info, ReleaseMethod.LEGACY) + " (0x9128 recusado)"
                _releaseMethod.value = ReleaseMethod.LEGACY
                AppLog.w("DISPARO", "A câmera recusou 0x9128: o resto da sessão usa 0x910F")
                return legacy()
            }
            throw PtpException(
                if (e.code == PtpResponse.DEVICE_BUSY || e.code == PtpResponse.NOT_READY) "A câmera ficou ocupada e não aceitou o botão. Tente de novo."
                else "A câmera recusou o disparo (${PtpResponse.name(e.code)})."
            )
        } finally {
            photo.cancel()
        }
    }

    /**
     * RemoteRelease with every answer of the camera written to the log, so the next log says whether
     * it was "busy", "not ready" or "no focus". When the camera stays busy, the AF is closed and, after
     * live view was used, live view is turned off once more before the next tries.
     */
    private suspend fun fireLogged(ptp: PtpSession, waitMs: Long, inLive: Boolean, onWaiting: (Long) -> Unit = {}) =
        ShutterRelease.fire(
            ptp,
            waitMs,
            onStuck = {
                val closeLive = !inLive && liveUsed
                AppLog.w("DISPARO", "Câmera ocupada há ${ShutterRelease.STUCK_MS / 1000.0} s: fechando o AF${if (closeLive) " e o live view" else ""}")
                cancelAf(ptp, "câmera ocupada")
                if (closeLive) {
                    runCatching { setPropertyNow(ptp, EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_OFF) }
                        .onFailure { AppLog.w("DISPARO", "A câmera não aceitou desligar o live view: ${it.message}") }
                }
            },
            report = { AppLog.i("DISPARO", "Respostas da câmera: $it") },
            onWaiting = onWaiting,
        )

    /**
     * Asks the camera for the values it hasn't reported yet (RequestDevicePropValue, 0x9127). They come
     * back through the next GetEvent, like any change made on the camera.
     */
    private suspend fun requestMissingValues(ptp: PtpSession, why: String) {
        if (!_capabilities.value.requestProp) return
        val missing = REQUESTED_PROPS.filter { _status.value.value(it) == null }
        if (missing.isEmpty()) return
        missing.forEach { prop -> runCatching { ptp.transact(PtpOp.EOS_REQUEST_DEVICE_PROP_VALUE, prop, timeoutMs = 2_000) } }
        AppLog.i("CAMERA", "Valores pedidos à câmera ($why): ${missing.joinToString(" ") { "%04X".format(it) }}")
        // Read the answers now, so the screen (and the drive mode for the shutter) has them right away.
        for (i in 0 until 3) {
            val records = runCatching { EosEventParser.parse(ptp.transactForData(PtpOp.EOS_GET_EVENT)) }.getOrDefault(emptyList())
            applyRecords(records, announceObjects = why != "conexão")
            if (records.isEmpty()) break
        }
    }

    /** Where the camera says live view is going, for the log: tells a camera left in live view apart. */
    private fun evfNote(): String = _status.value.value(EosProp.EVF_OUTPUT_DEVICE)?.let { " · saída do live view na câmera: $it" } ?: ""

    /** Starts AF and waits the time the chosen live view AF needs; the answer code goes to the log. */
    private suspend fun runAf(ptp: PtpSession, why: String): Boolean = if (!_capabilities.value.af) false else try {
        ptp.transact(PtpOp.EOS_DO_AF)
        AppLog.i("FOCO", "AF iniciado ($why)")
        delay(if (liveSilentAf) AF_WAIT_LIVE_MS else AF_WAIT_QUICK_MS)
        true
    } catch (e: PtpResponseException) {
        AppLog.w("FOCO", "AF recusado ($why): código 0x%04X".format(e.code))
        false
    } catch (e: PtpException) {
        AppLog.w("FOCO", "AF falhou ($why): ${e.message}")
        false
    }

    /** Ends the AF operation (the lens keeps its focus). Without it the 50D may refuse what comes next. */
    private suspend fun cancelAf(ptp: PtpSession, why: String) {
        if (!_capabilities.value.afCancel) return
        runCatching { ptp.transact(PtpOp.EOS_AF_CANCEL) }
            .onFailure { AppLog.w("FOCO", "Fim do AF ($why) não aceito: ${it.message}") }
    }

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
        if (!_capabilities.value.liveView) {
            _live.value = LiveState.Failed("Esta câmera não manda a imagem ao vivo pelo cabo (0x9153 não anunciada).")
            return
        }
        liveFrameMs = 1000L / fps.coerceIn(5, 30)
        // No picture from an earlier session: the screen waits for this camera's first frame.
        _liveFrames.resetReplayCache()
        val pendingStop = liveStopJob
        liveJob = scope.launch {
            // A stop sent just before (reopening the screen, a changed option) must reach the camera
            // first; otherwise its "off" lands after this "on" and no frame ever comes.
            pendingStop?.join()
            runLiveView(ptp, alsoOnCamera, silentAf)
        }
    }

    fun stopLiveView() {
        val job = liveJob ?: return
        liveJob = null
        job.cancel()
        _live.value = LiveState.Off
        _liveFrames.resetReplayCache()
        val ptp = session ?: return
        liveStopJob = scope.launch {
            job.join()
            // Everything that belongs to live view is closed while live view is still on: an AF cancel sent
            // after it (as up to 5.3) reached a camera no longer in live view, and the 50D body stayed "BUSY".
            cancelAf(ptp, "ao sair do live view")
            runCatching { if (liveZoom != 1) ptp.transact(PtpOp.EOS_ZOOM, 1) }
            // Modo desenvolvedor can put back the output the camera had before (its own screen, for example)
            // instead of switching it off, to compare which one leaves the body usable.
            val before = valuesBeforeLive[EosProp.EVF_OUTPUT_DEVICE]
            val output = if (connectionPrefs().restoreLiveOutput && before != null && before in 0..1) before else EosProp.EVF_OFF
            runCatching { setPropertyNow(ptp, EosProp.EVF_OUTPUT_DEVICE, output) }
                .onSuccess { if (output != EosProp.EVF_OFF) log("Saída do live view devolvida para $output (como estava antes)") }
                .onFailure { log("A câmera não aceitou desligar o live view: ${it.message}") }
            liveZoom = 1
            log("Live view desligado")
            if (connectionPrefs().resetRemoteAfterLive) resetRemoteMode(ptp, "ao sair do live view")
            // Apart from the stop itself, so reopening live view doesn't wait for it.
            scope.launch {
                delay(800)
                logLiveChanges()
            }
        }
    }

    /**
     * Gives the camera body back after live view, inside the same session. Up to 5.3.3 the 50D stayed
     * "BUSY" (buttons and dials dead) until the cable came out. libgphoto2 ends an EOS capture the same
     * way: read the pending events, remote mode 0 ("resets the camera state") and remote mode on again
     * ("enables camera display"). The session, the event mode and the downloads stay as they are.
     */
    private suspend fun resetRemoteMode(ptp: PtpSession, why: String) {
        if (deviceInfo?.supports(PtpOp.EOS_SET_REMOTE_MODE) != true) return
        // Events the camera queued while leaving live view (a new photo among them) are taken first.
        for (i in 0 until 5) {
            val records = runCatching { EosEventParser.parse(ptp.transactForData(PtpOp.EOS_GET_EVENT)) }.getOrDefault(emptyList())
            applyRecords(records, announceObjects = true)
            if (records.isEmpty()) break
        }
        val mode = connectionPrefs().remoteMode
        val off = runCatching { ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, 0, timeoutMs = 2_000) }
        delay(150)
        val on = runCatching { ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, mode, timeoutMs = 2_000) }
        log("Modo remoto reiniciado $why (0 → $mode): ${if (off.isSuccess) "ok" else "0 recusado"}, ${if (on.isSuccess) "ok" else "$mode recusado"}")
    }

    /**
     * Modo desenvolvedor → Liberar a câmera: the same reset by hand, for a body that stays BUSY.
     * Live view closes first when it is open.
     */
    fun releaseCameraBody() {
        val ptp = session ?: return
        scope.launch {
            liveStopJob?.join()
            if (liveJob?.isActive == true) return@launch
            cancelAf(ptp, "liberar a câmera")
            resetRemoteMode(ptp, "pelo botão")
            _messages.tryEmit("Câmera liberada. Teste os botões da 50D.")
        }
    }

    /** What changed on the camera between opening and closing live view: the evidence when the body stays BUSY. */
    private fun logLiveChanges() {
        val before = valuesBeforeLive
        val now = _status.value.values
        val changed = (before.keys + now.keys).filter { before[it] != now[it] }.sorted()
        log("Saída do live view na câmera depois de desligar: ${now[EosProp.EVF_OUTPUT_DEVICE]}")
        AppLog.i("CAMERA", "Propriedades que mudaram com o live view: " +
            if (changed.isEmpty()) "nenhuma" else changed.joinToString(", ") { "%04X: %s → %s".format(it, before[it]?.toString() ?: "-", now[it]?.toString() ?: "-") })
    }

    /**
     * Closes the session politely and opens it again, without the cable. Also used by the Desenvolvedor
     * screen. A camera slow to answer after closing gets two more tries, each after a longer pause.
     */
    fun reconnectQuietly() {
        scope.launch {
            disconnect()
            withTimeoutOrNull(4_000) { _state.first { it is ConnectionState.NoCamera } }
            for (attempt in 1..3) {
                // The same pause as pulling and plugging the cable: the camera leaves PC mode before the new session.
                delay(1_000L * attempt)
                findAndConnect()
                val end = withTimeoutOrNull(20_000) {
                    _state.first { it is ConnectionState.Connected || it is ConnectionState.Failed || it == ConnectionState.NoCamera }
                }
                if (end is ConnectionState.Connected || end == ConnectionState.NoCamera) return@launch
                log("Reconexão rápida: tentativa $attempt sem resposta")
            }
        }
    }

    private suspend fun runLiveView(ptp: PtpSession, alsoOnCamera: Boolean, silentAf: Boolean) {
        _live.value = LiveState.Starting
        valuesBeforeLive = _status.value.values
        liveUsed = true
        liveSilentAf = silentAf
        if (!silentAf) {
            // Quick mode (the mirror drops for AF): the most reliable on the 50D, and the one that also
            // undoes a "Modo Live" left set by an earlier version.
            runCatching { setPropertyNow(ptp, EosProp.LV_AF_SYSTEM, EosProp.LV_AF_QUICK) }
                .onSuccess { log("AF do live view: Modo rápido") }
                .onFailure { log("A câmera não aceitou o AF rápido pelo cabo: ${it.message}") }
        }
        if (silentAf) {
            // "Modo Live" (contrast AF on the sensor) focuses without dropping the mirror; "Modo rápido"
            // drops it for every AF, which is the clack-clack. Not every firmware takes it over USB.
            runCatching { setPropertyNow(ptp, EosProp.LV_AF_SYSTEM, EosProp.LV_AF_LIVE) }
                .onSuccess { log("AF do live view: Modo Live (silencioso)") }
                .onFailure {
                    liveSilentAf = false
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
        log("Live view ligado (saída ${if (alsoOnCamera) "celular e câmera" else "celular"}), esperando a primeira imagem")
        requestMissingValues(ptp, "live view")
        var lastFrame = System.currentTimeMillis()
        var windowStart = lastFrame
        var windowFrames = 0
        var fps = 0f
        var firstFrame = false
        while (kotlinx.coroutines.currentCoroutineContext().isActive && session === ptp) {
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
            val jpeg = LiveViewParser.jpeg(data)
            if (jpeg == null) {
                // A frame without a picture (the camera still starting): count it as silence, not as life.
                if (started - lastFrame > LIVE_GIVE_UP_MS) {
                    log("Live view: ${data.size} bytes sem imagem por ${LIVE_GIVE_UP_MS / 1000} s")
                    _live.value = LiveState.Failed("A 50D não mandou imagem. $LIVE_HINT")
                    runCatching { setPropertyNow(ptp, EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_OFF) }
                    return
                }
                delay(60)
                continue
            }
            if (!firstFrame) {
                firstFrame = true
                log("Live view: primeira imagem em ${System.currentTimeMillis() - windowStart} ms")
            }
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
    fun setLiveZoom(level: Int) = liveCommand("A câmera não aceitou o zoom", _capabilities.value.zoom) {
        it.transact(PtpOp.EOS_ZOOM, level)
        liveZoom = level
    }

    /** Moves the zoomed area / AF frame. [x], [y] are 0…1 across the picture. */
    fun setLiveFocusPoint(x: Float, y: Float) = liveCommand("A câmera não aceitou a posição", _capabilities.value.zoomPosition) {
        it.transact(PtpOp.EOS_ZOOM_POSITION, (x.coerceIn(0f, 1f) * SENSOR_W).toInt(), (y.coerceIn(0f, 1f) * SENSOR_H).toInt())
    }

    /**
     * Autofocus now (like AF-ON), with the camera's live view AF mode. Always closed with AF cancel,
     * so the next focus or shot finds the camera ready; a second tap while focusing is ignored.
     */
    fun liveAutofocus() {
        val ptp = session ?: return
        if (!_capabilities.value.af) return
        if (afJob?.isActive == true || _shutter.value is ShutterState.Focusing) return
        afJob = scope.launch {
            val ok = runAf(ptp, "toque")
            cancelAf(ptp, "toque")
            if (!ok) _messages.tryEmit("Não focou. Confira a chave da lente em AF e, na 50D, Menu → Live View → Modo AF.")
        }
    }

    /** Nudges focus with the lens in AF: [step] 1…3 towards near, −1…−3 towards far. */
    fun driveLens(step: Int) = liveCommand("A lente não se moveu: deixe a chave da lente em AF", _capabilities.value.driveLens) {
        val size = kotlin.math.abs(step).coerceIn(1, 3)
        it.transact(PtpOp.EOS_DRIVE_LENS, if (step < 0) 0x8000 or size else size)
    }

    /**
     * Stops the lens down to the chosen aperture on the live picture (depth of field preview). The 50D
     * answers "operation not supported" over USB: the button then goes away, with one message.
     */
    fun depthOfFieldPreview(on: Boolean) {
        val ptp = session ?: return
        if (!_dofSupported.value) return
        scope.launch {
            try {
                ptp.transact(PtpOp.EOS_DOF_PREVIEW, if (on) 1 else 0)
            } catch (e: PtpResponseException) {
                if (e.code == PtpResponse.OPERATION_NOT_SUPPORTED) {
                    if (_dofSupported.compareAndSet(true, false)) {
                        log("A câmera não faz a prévia de profundidade pelo cabo (0x9156 não suportada)")
                        _messages.tryEmit("A 50D não faz a prévia de profundidade pelo cabo. Use o botão de profundidade na câmera.")
                    }
                } else {
                    _messages.tryEmit("A câmera não fez a prévia de profundidade")
                    log("A câmera não fez a prévia de profundidade: ${e.message}")
                }
            } catch (e: PtpException) {
                _messages.tryEmit("A câmera não fez a prévia de profundidade")
                log("A câmera não fez a prévia de profundidade: ${e.message}")
            }
        }
    }

    /** Runs a live view command when the camera lists it ([supported]); nothing is sent otherwise. */
    private fun liveCommand(failure: String, supported: Boolean, block: suspend (PtpSession) -> Unit) {
        val ptp = session ?: return
        if (!supported) return
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
        // Taken before anything starts, so a double tap runs one bracket.
        if (!_bracketing.compareAndSet(null, BracketState(0, shots))) return
        scope.launch {
            val mode = _status.value.value(EosProp.SHOOTING_MODE)
            val useShutter = mode == MODE_M
            val prop = if (useShutter) EosProp.SHUTTER_SPEED else EosProp.EXPOSURE_COMPENSATION
            val base = _status.value.value(prop)
            if (base == null || (useShutter && !EosValues.isKnownShutter(base))) {
                _messages.tryEmit("Bracketing indisponível: a câmera ainda não informou a exposição")
                _bracketing.value = null
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
                    shotLock.withLock { shootOnce(ptp, info, INTERVAL_WAIT_MS + (seconds * 1000).toLong(), liveJob?.isActive == true) }
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
    /**
     * [newestFirst] reads from the last handle back (the camera numbers them in the order it wrote the
     * files) and, with [isOld], stops after [stopAfterOld] files in a row it says are old.
     */
    suspend fun listCardFiles(
        newestFirst: Boolean = false,
        stopAfterOld: Int = 0,
        isOld: (ObjectInfo) -> Boolean = { false },
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<ObjectInfo> {
        val ptp = session ?: throw PtpException("Câmera desconectada")
        val info = deviceInfo ?: throw PtpException("Câmera desconectada")
        if (!info.supports(PtpOp.GET_OBJECT_HANDLES) || !info.supports(PtpOp.GET_OBJECT_INFO)) {
            throw PtpException("Esta câmera não permite listar o cartão pela USB")
        }
        val storages = PtpDataReader(ptp.transactForData(PtpOp.GET_STORAGE_IDS)).u32Array()
        val files = mutableListOf<ObjectInfo>()
        for (storage in storages) {
            // Parent 0 = every object in the storage, flat, whatever folder it is in.
            val all = PtpDataReader(ptp.transactForData(PtpOp.GET_OBJECT_HANDLES, storage, 0, 0)).u32Array()
            val handles = if (newestFirst) all.reversed() else all.toList()
            var oldInARow = 0
            for ((i, handle) in handles.withIndex()) {
                val obj = ObjectInfo.parse(handle, ptp.transactForData(PtpOp.GET_OBJECT_INFO, handle))
                onProgress(i + 1, handles.size)
                if (obj.isFolder) continue
                files += obj
                if (stopAfterOld > 0) {
                    oldInARow = if (isOld(obj)) oldInARow + 1 else 0
                    if (oldInARow >= stopAfterOld) break
                }
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
            AppLog.i("INTERVALO", "Início: $shots foto(s), uma a cada $seconds s")
            var failures = 0
            for (i in 1..shots) {
                // The camera went away mid-series: say so, instead of the counter just stopping.
                val ptp = session
                val info = deviceInfo
                if (ptp == null || info == null) {
                    _messages.tryEmit("Intervalômetro parou na foto ${i - 1} de $shots: a câmera desconectou")
                    break
                }
                val result = shotLock.withLock {
                    // Same care as the button: live view pauses, and an AF left open is closed first.
                    val inLive = liveJob?.isActive == true
                    liveHold = true
                    try {
                        if (inLive) cancelAf(ptp, "antes do intervalo")
                        AppLog.i("DISPARO", "Intervalômetro: foto $i de $shots${evfNote()}")
                        runCatching { shootOnce(ptp, info, INTERVAL_WAIT_MS, inLive) }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    } finally {
                        if (inLive) delay(LIVE_AFTER_SHOT_MS)
                        liveHold = false
                    }
                }
                _interval.update { it.copy(done = i) }
                failures = if (result.isSuccess) 0 else failures + 1
                result.exceptionOrNull()?.let { e ->
                    AppLog.e("INTERVALO", "Foto $i de $shots não disparou: ${e.message}")
                    // A camera that refuses three in a row won't fire the fourth: stop and say why, once.
                    if (failures >= INTERVAL_MAX_FAILURES) {
                        _messages.tryEmit("Intervalômetro parado: $failures disparos seguidos falharam. ${e.message}")
                        break
                    }
                    _messages.tryEmit("Intervalômetro: ${e.message}")
                }
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
        retryJob?.cancel()
        if (_state.value is ConnectionState.Reconnecting) _state.value = ConnectionState.NoCamera
        val ptp = session ?: return
        // Detach first so the event loop stops and its error handler ignores this session; the USB stays
        // open until the camera has been told to leave remote mode and close its session.
        closingPolitely = ptp
        closeSession(closeTransport = false)
        scope.launch {
            // What the camera answered to each goodbye, for the log: a camera that missed them keeps its old session.
            val answers = mutableListOf<String>()
            suspend fun bye(name: String, call: suspend () -> Unit) {
                answers += name + if (runCatching { call() }.isSuccess) " ok" else " falhou"
            }
            // The order libgphoto2 uses to end an EOS capture: AF cancel, live view off, remote mode 0 (resets
            // the camera) and back on (gives the display back), event mode off, then close. Up to 5.3.3 the app
            // ended on remote mode 0, and the 50D then answered no new session until the cable came out.
            withTimeoutOrNull(5_000) {
                bye("fim do AF") { ptp.transact(PtpOp.EOS_AF_CANCEL, timeoutMs = 1_000) }
                bye("live view") { ptp.transact(PtpOp.EOS_SET_DEVICE_PROP_VALUE_EX, dataOut = PtpContainer.eosPropValue(EosProp.EVF_OUTPUT_DEVICE, EosProp.EVF_OFF), timeoutMs = 1_000) }
                bye("modo remoto 0") { ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, 0, timeoutMs = 1_000) }
                bye("modo remoto 1") { ptp.transact(PtpOp.EOS_SET_REMOTE_MODE, 1, timeoutMs = 1_000) }
                bye("eventos") { ptp.transact(PtpOp.EOS_SET_EVENT_MODE, 0, timeoutMs = 1_000) }
                bye("sessão") { ptp.transact(PtpOp.CLOSE_SESSION, timeoutMs = 1_000) }
            }
            log("Despedida da câmera: ${answers.joinToString(", ").ifEmpty { "sem resposta" }}")
            ptp.close()
            if (closingPolitely === ptp) closingPolitely = null
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
        _releaseInfo.value = null
        _releaseMethod.value = null
        _capabilities.value = CameraCapabilities()
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
        /** Quick mode: the mirror drops, the AF sensor focuses, the mirror comes back. */
        private const val AF_WAIT_QUICK_MS = 1_200L
        /** "Modo Live": contrast AF on the sensor, slower in low light. */
        private const val AF_WAIT_LIVE_MS = 2_800L
        private const val LIVE_AFTER_SHOT_MS = 600L
        private const val INTERVAL_WAIT_MS = 5_000L
        /** What the screens and the shutter read from the camera; asked for when an event left one out. */
        private val REQUESTED_PROPS = listOf(
            EosProp.DRIVE_MODE, EosProp.SHOOTING_MODE, EosProp.ISO, EosProp.SHUTTER_SPEED, EosProp.APERTURE,
            EosProp.EXPOSURE_COMPENSATION, EosProp.WHITE_BALANCE, EosProp.AVAILABLE_SHOTS, EosProp.BATTERY_POWER,
            EosProp.CAPTURE_DESTINATION, EosProp.EVF_OUTPUT_DEVICE,
        )
        private const val INTERVAL_MAX_FAILURES = 3
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
