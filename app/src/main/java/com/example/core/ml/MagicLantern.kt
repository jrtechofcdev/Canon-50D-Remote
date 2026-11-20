package com.example.core.ml

import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionState
import com.example.core.data.SettingsRepository
import com.example.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Which firmware the photographer runs on the 50D: the app asks the camera, or is told. */
enum class CameraFirmware(val key: String, val label: String) {
    AUTO("auto", "Automático"),
    CANON("canon", "Canon original"),
    ML("ml", "Magic Lantern");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** The ML build that answered over USB (its own PTP operation, see [MlPtp]). */
data class MlBuild(val protocol: String, val version: String?, val id: String?, val date: String?, val user: String?) {
    fun describe() = listOfNotNull(version, date?.let { "compilado em $it" }).joinToString(" · ").ifBlank { "versão não informada" }
}

sealed interface MlState {
    data object NoCamera : MlState
    data object Checking : MlState
    /** The camera turned the ML operation down: Canon's own firmware is running (or ML without its USB part). */
    data object Canon : MlState
    data class Loaded(val build: MlBuild) : MlState
    /** Firmware set by hand to Canon: the camera is not asked. */
    data object NotAsked : MlState
}

/**
 * Magic Lantern's USB operation, from its source (src/ptp-ml.c): opcode 0xA1E8 with the subcommand in the
 * first parameter. Only the read-only subcommands are used. Its menu lookup is disabled in the current ML
 * source, so changing menus (SetMenu) could touch a random address on the camera: never sent.
 */
object MlPtp {
    const val CODE = 0xA1E8
    const val USB_VERSION = 0
    const val VERSION = 1
    const val GET_MENUS = 4
    /** Selectors of [VERSION]: build version, build id, build date, build user. */
    val VERSION_FIELDS = 0..3

    /** GetMenus data: per menu, 4 unused bytes, a 32-bit name length and the name (no terminator). */
    fun parseMenus(data: ByteArray): List<String> {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val names = mutableListOf<String>()
        while (b.remaining() >= 8) {
            b.position(b.position() + 4)
            val length = b.int
            if (length < 0 || length > b.remaining()) break
            val bytes = ByteArray(length)
            b.get(bytes)
            names += String(bytes, Charsets.ISO_8859_1).trim()
        }
        return names
    }
}

/**
 * Knows whether the 50D is running Magic Lantern, so the app can offer what only ML does (filming with
 * SET) and keep the plain Canon behaviour otherwise. In Automático it asks the camera at each connection.
 */
class MagicLanternController(
    private val camera: CameraManager,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler(TAG))

    private val _state = MutableStateFlow<MlState>(MlState.NoCamera)
    val state: StateFlow<MlState> = _state.asStateFlow()

    private val _menus = MutableStateFlow<List<String>>(emptyList())
    val menus: StateFlow<List<String>> = _menus.asStateFlow()

    /** The firmware chosen in Ajustes → Magic Lantern. */
    val firmware: StateFlow<CameraFirmware> = settings.settings.map { CameraFirmware.of(it.cameraFirmware) }
        .stateIn(scope, SharingStarted.Eagerly, CameraFirmware.AUTO)

    /** True when the app should offer ML's functions: set by hand, or ML answered in Automático. */
    val active: StateFlow<Boolean> = combine(firmware, _state) { f, s ->
        f == CameraFirmware.ML || (f == CameraFirmware.AUTO && s is MlState.Loaded)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            camera.state.distinctUntilChangedBy { it is ConnectionState.Connected }.collect { state ->
                if (state is ConnectionState.Connected) detect("conexão") else {
                    _state.value = MlState.NoCamera
                    _menus.value = emptyList()
                }
            }
        }
    }

    /** Asks the camera whether ML is running; skipped when the firmware is set to Canon. */
    fun detect(why: String = "botão") = scope.launch {
        if (camera.state.value !is ConnectionState.Connected) return@launch
        if (firmware.value == CameraFirmware.CANON) {
            _state.value = MlState.NotAsked
            AppLog.i(TAG, "Firmware definido como Canon original: a câmera não é consultada")
            return@launch
        }
        _state.value = MlState.Checking
        val answer = runCatching { camera.rawCommand(MlPtp.CODE, MlPtp.USB_VERSION) }.getOrElse {
            AppLog.w(TAG, "Consulta do Magic Lantern falhou ($why): ${it.message}")
            _state.value = MlState.Canon
            return@launch
        }
        if (!answer.ok) {
            AppLog.i(TAG, "Magic Lantern não respondeu ($why): ${answer.describe()} → firmware Canon original")
            _state.value = MlState.Canon
            return@launch
        }
        val protocol = answer.params.take(2).joinToString(".")
        val fields = MlPtp.VERSION_FIELDS.map { field ->
            runCatching { camera.rawCommand(MlPtp.CODE, MlPtp.VERSION, field, readData = true) }.getOrNull()
                ?.takeIf { it.ok && it.data.isNotEmpty() }?.let { String(it.data, Charsets.ISO_8859_1).trim() }
        }
        val build = MlBuild(protocol, fields[0], fields[1], fields[2], fields[3])
        AppLog.i(TAG, "Magic Lantern carregado ($why): protocolo USB $protocol · versão ${build.version ?: "-"} · id ${build.id ?: "-"} · data ${build.date ?: "-"} · por ${build.user ?: "-"}")
        _state.value = MlState.Loaded(build)
    }

    /** ML's root menus (Audio, Expo, Overlay, Movie…), read-only, to see which features this build has. */
    fun readMenus() = scope.launch {
        if (_state.value !is MlState.Loaded) return@launch
        val answer = runCatching { camera.rawCommand(MlPtp.CODE, MlPtp.GET_MENUS, readData = true) }.getOrElse {
            AppLog.w(TAG, "Leitura dos menus do ML falhou: ${it.message}")
            return@launch
        }
        if (!answer.ok) {
            AppLog.w(TAG, "Menus do ML: ${answer.describe()}")
            return@launch
        }
        val names = MlPtp.parseMenus(answer.data)
        _menus.value = names
        AppLog.i(TAG, "Menus do ML (${names.size}): ${names.joinToString(", ")}")
    }

    fun setFirmware(value: CameraFirmware) = scope.launch {
        settings.set(com.example.core.data.SettingKeys.cameraFirmware, value.key)
        AppLog.i(TAG, "Firmware escolhido: ${value.label}")
        detect("firmware trocado")
    }

    companion object {
        const val TAG = "ML"
    }
}
