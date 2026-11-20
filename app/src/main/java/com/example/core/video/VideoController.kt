package com.example.core.video

import android.content.Context
import android.graphics.BitmapFactory
import com.example.core.camera.CameraManager
import com.example.core.camera.ConnectionState
import com.example.core.camera.LiveState
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import com.example.core.log.AppLog
import com.example.core.ptp.EosEventParser
import com.example.core.ptp.EosProp
import com.example.core.ptp.ObjectInfo
import com.example.core.ptp.PtpOp
import com.example.core.ptp.StreamSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Recording by the phone from the live view (method B). */
sealed interface PhoneRecording {
    data object Idle : PhoneRecording
    data class Recording(val name: String, val startedAt: Long, val frames: Int, val dropped: Int, val size: String) : PhoneRecording
    data object Saving : PhoneRecording
}

/** How the camera's own SET button gets live view while the app stays connected (method A). */
enum class SetVariant(val label: String, val detail: String) {
    FreeCamera(
        "Câmera livre (recomendado)",
        "O app desliga o live view dele e solta os botões da câmera. Na câmera, ligue o live view no botão dela e aperte SET para filmar.",
    ),
    CameraOnly(
        "O app liga o live view na câmera",
        "O app solta os botões e liga o live view na tela da câmera. Aperte SET para filmar.",
    ),
}

sealed interface CameraFilming {
    data object Off : CameraFilming
    data class Waiting(val variant: SetVariant, val since: Long) : CameraFilming
}

data class VideoDownload(val name: String, val received: Long, val total: Long) {
    val progress get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Experimental video module for a 50D that films (the body has no movie mode from Canon; the SET
 * button + red LED behaviour matches Magic Lantern's movie mode). Two ways to record:
 *  - A: the camera films by itself (SET button), the app gets out of the way and then fetches the file;
 *  - B: the phone records the live view it receives into an MP4 (live view resolution, no audio).
 * Everything it does goes to the log as VIDEO-<step> lines.
 */
class VideoController(
    private val context: Context,
    private val camera: CameraManager,
    private val settings: SettingsRepository,
    val library: VideoLibrary,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler("VIDEO"))
    private val recorderThread = Executors.newSingleThreadExecutor { Thread(it, "video-recorder") }.asCoroutineDispatcher()
    private val prefs = context.getSharedPreferences("video_lab", Context.MODE_PRIVATE)

    private val _phone = MutableStateFlow<PhoneRecording>(PhoneRecording.Idle)
    val phone: StateFlow<PhoneRecording> = _phone.asStateFlow()

    private val _filming = MutableStateFlow<CameraFilming>(CameraFilming.Off)
    val filming: StateFlow<CameraFilming> = _filming.asStateFlow()

    private val _cardVideos = MutableStateFlow<List<ObjectInfo>>(emptyList())
    val cardVideos: StateFlow<List<ObjectInfo>> = _cardVideos.asStateFlow()

    private val _download = MutableStateFlow<VideoDownload?>(null)
    val download: StateFlow<VideoDownload?> = _download.asStateFlow()

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** A lab action in progress ("Procurando vídeos no cartão…"), for the buttons to wait. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    var bitRateKbps: Int
        get() = prefs.getInt("bitrate_kbps", 8_000)
        set(value) { prefs.edit().putInt("bitrate_kbps", value).apply() }

    /** Videos the camera announces (or that show up on the card) are downloaded on their own. */
    var autoDownload: Boolean
        get() = prefs.getBoolean("auto_download", true)
        set(value) { prefs.edit().putBoolean("auto_download", value).apply() }

    private var recordJob: Job? = null
    private var watchJob: Job? = null
    private var recordings = 0
    @Volatile private var lastStats: LiveRecorder.Stats? = null
    @Volatile private var cancelDownload = false
    private var monitorByFilming = false
    private var knownOnCard: Set<String> = emptySet()

    init {
        AppLog.crashNote(AppLog.VIDEO_TAG) { stateLine() }
        scope.launch {
            camera.newObjects.collect { obj ->
                if (!obj.isVideo) return@collect
                VideoLog.i("CAMERA", "A câmera anunciou um vídeo: ${obj.filename} (${obj.size / 1024} KB, formato 0x%04X)".format(obj.format))
                if (autoDownload) downloadVideo(ObjectInfo(obj.handle, obj.storageId, obj.format, obj.size, obj.parent, obj.filename, ""))
            }
        }
        scope.launch {
            camera.state.collect { state ->
                if (state !is ConnectionState.Connected) {
                    if (_phone.value is PhoneRecording.Recording) stopPhoneRecording("câmera desconectada (${state::class.simpleName})")
                    if (_filming.value != CameraFilming.Off) {
                        VideoLog.w("SET", "Câmera desconectou durante o modo SET (${state::class.simpleName})")
                        _filming.value = CameraFilming.Off
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) { runCatching { library.refresh() } }
    }

    /** One line with what the module is doing, written next to a crash. Null when it is idle. */
    fun stateLine(): String? {
        val parts = buildList {
            (_phone.value as? PhoneRecording.Recording)?.let { r ->
                val s = lastStats
                add("gravando no celular ${r.name} há ${(System.currentTimeMillis() - r.startedAt) / 1000} s, ${r.frames} quadros, ${r.dropped} pulados" +
                    (s?.let { ", ${it.width}x${it.height}, encoder ${it.codecName}, ${"%.1f".format(it.encodeMsAvg)} ms/quadro" } ?: ""))
            }
            if (_phone.value == PhoneRecording.Saving) add("fechando o arquivo do celular")
            (_filming.value as? CameraFilming.Waiting)?.let { add("modo SET (${it.variant.label}) há ${(System.currentTimeMillis() - it.since) / 1000} s") }
            _download.value?.let { add("baixando ${it.name} ${(it.progress * 100).toInt()}%") }
            if (_monitoring.value) add("monitor de eventos ligado")
        }
        return if (parts.isEmpty()) null else "Módulo de vídeo: " + parts.joinToString(" · ")
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    // Method B: the phone records the live view.

    fun startPhoneRecording() {
        if (_phone.value != PhoneRecording.Idle) return
        val live = camera.live.value
        if (live !is LiveState.Running) {
            VideoLog.w("CELULAR", "Pedido de gravação sem live view rodando (estado: ${live::class.simpleName})")
            say("Abra o live view e espere a imagem aparecer para gravar.")
            return
        }
        val free = library.freeBytes()
        if (free != null && free < MIN_FREE_BYTES) {
            VideoLog.w("CELULAR", "Gravação recusada: pouco espaço (${free / 1_048_576} MB)")
            say("Pouco espaço no celular para gravar vídeo.")
            return
        }
        val name = VideoItem.PHONE_PREFIX + stamp() + ".mp4"
        val file = File(library.workDir(), name)
        val recorder = LiveRecorder(file, bitRateKbps * 1000)
        recordings++
        VideoLog.banner("gravação no celular #$recordings iniciada · $name")
        VideoLog.i("CELULAR", "Live view a ${"%.1f".format(live.fps)} fps, zoom ${live.zoom}x · ${VideoLog.resources(free)}")
        val startedAt = System.currentTimeMillis()
        _phone.value = PhoneRecording.Recording(name, startedAt, 0, 0, "")
        lastStats = null
        recordJob = scope.launch(recorderThread) {
            var lastReport = System.currentTimeMillis()
            var failure: Throwable? = null
            try {
                camera.liveFrames.conflate().collect { jpeg ->
                    val arrived = System.nanoTime()
                    if (!recorder.started) {
                        val (w, h) = jpegSize(jpeg) ?: run {
                            VideoLog.w("CELULAR", "Primeiro quadro sem tamanho legível (${jpeg.size} bytes), esperando o próximo")
                            return@collect
                        }
                        recorder.start(w, h)
                    }
                    recorder.encodeJpeg(jpeg, arrived)
                    val stats = recorder.stats()
                    lastStats = stats
                    _phone.value = PhoneRecording.Recording(name, startedAt, stats.frames, stats.dropped, sizeLabel(stats.bytes))
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= REPORT_MS) {
                        val seconds = (now - startedAt) / 1000f
                        VideoLog.i("CELULAR", "%.0f s · %d quadros (%.1f fps) · %d pulados · %.1f ms/quadro · %s · %s".format(
                            seconds, stats.frames, stats.frames / seconds.coerceAtLeast(1f), stats.dropped, stats.encodeMsAvg,
                            sizeLabel(stats.bytes), VideoLog.resources(library.freeBytes())))
                        lastReport = now
                        val left = library.freeBytes()
                        if (left != null && left < MIN_FREE_BYTES) {
                            VideoLog.w("CELULAR", "Espaço acabando (${left / 1_048_576} MB): parando a gravação")
                            say("Espaço do celular acabando: gravação parada.")
                            throw StopRecording()
                        }
                    }
                }
            } catch (e: StopRecording) {
                // Normal end requested from inside the loop.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failure = e
                VideoLog.e("CELULAR", "A gravação parou por erro", e)
            } finally {
                withContext(NonCancellable) { finishPhoneRecording(recorder, file, name, startedAt, failure) }
            }
        }
        watchJob = scope.launch {
            val ended = camera.live.first { it !is LiveState.Running && it !is LiveState.Starting }
            stopPhoneRecording("o live view parou (${ended::class.simpleName}${(ended as? LiveState.Failed)?.message?.let { ": $it" } ?: ""})")
        }
    }

    fun stopPhoneRecording(reason: String = "parada pelo botão") {
        val job = recordJob ?: return
        recordJob = null
        watchJob?.cancel()
        watchJob = null
        VideoLog.i("CELULAR", "Parando: $reason")
        job.cancel()
    }

    private suspend fun finishPhoneRecording(recorder: LiveRecorder, file: File, name: String, startedAt: Long, failure: Throwable?) {
        _phone.value = PhoneRecording.Saving
        val stats = recorder.stats()
        val seconds = (System.currentTimeMillis() - startedAt) / 1000f
        val ok = recorder.finish()
        VideoLog.i("CELULAR", "Arquivo fechado: %s · %.1f s · %d quadros (%.1f fps médios) · %d pulados · %dx%d · %.1f ms/quadro · %s · encoder %s".format(
            if (ok) "ok" else "vazio/descartado", seconds, stats.frames, stats.frames / seconds.coerceAtLeast(1f), stats.dropped,
            stats.width, stats.height, stats.encodeMsAvg, sizeLabel(file.length()), stats.codecName))
        if (ok) {
            runCatching { library.publish(file, name) }
                .onSuccess { say("Vídeo salvo: ${it.name}") }
                .onFailure {
                    VideoLog.e("CELULAR", "Não deu para guardar $name na galeria; o arquivo ficou em ${file.path}", it)
                    say("O vídeo ficou na pasta do app, mas não foi para a galeria: ${it.message}")
                }
        } else {
            say(if (failure != null) "A gravação falhou: ${failure.message}" else "Nenhum quadro chegou: nada foi gravado.")
        }
        VideoLog.banner("gravação no celular #$recordings encerrada")
        lastStats = null
        _phone.value = PhoneRecording.Idle
    }

    // Method A: the camera films with its SET button.

    /**
     * Gets the app out of the way so the camera's SET button can start filming, keeping the USB session.
     * The event monitor turns on meanwhile, to see if the camera reports anything while it films.
     */
    fun enterSetMode(variant: SetVariant) = scope.launch {
        if (_filming.value != CameraFilming.Off) return@launch
        if (camera.state.value !is ConnectionState.Connected) {
            say("Conecte a câmera primeiro.")
            return@launch
        }
        VideoLog.banner("modo botão SET · ${variant.label}")
        knownOnCard = runCatching { scanCardQuiet() }.getOrDefault(emptyList()).map { it.filename }.toSet()
        VideoLog.i("SET", "Vídeos já no cartão antes de filmar: ${knownOnCard.size}")
        when (variant) {
            SetVariant.FreeCamera -> {
                val result = runCatching { camera.freeCameraBody() }
                result.onSuccess { VideoLog.i("SET", "Live view do app desligado e botões da câmera soltos; ligue o live view na câmera") }
                    .onFailure { VideoLog.e("SET", "Não deu para soltar a câmera", it) }
            }
            SetVariant.CameraOnly -> {
                val result = runCatching { camera.handLiveViewToCamera() }
                result.onSuccess { VideoLog.i("SET", "Live view devolvido à câmera (0xD1B0 = 1): ${it.describe()}") }
                    .onFailure { VideoLog.e("SET", "Não deu para devolver o live view à câmera", it) }
            }
        }
        if (!_monitoring.value) {
            setMonitor(true)
            monitorByFilming = true
        }
        _filming.value = CameraFilming.Waiting(variant, System.currentTimeMillis())
        say(if (variant == SetVariant.FreeCamera) "Na câmera, ligue o live view no botão dela e aperte SET para filmar (SET de novo para parar)."
            else "Agora aperte SET na câmera para filmar e SET de novo para parar.")
    }

    /** Back to normal. With [searchCard], looks for the new video files and downloads them. */
    fun exitSetMode(searchCard: Boolean, restartLive: Boolean) = scope.launch {
        val waiting = _filming.value as? CameraFilming.Waiting ?: return@launch
        val seconds = (System.currentTimeMillis() - waiting.since) / 1000
        VideoLog.i("SET", "Saindo do modo SET depois de $seconds s")
        if (monitorByFilming) {
            setMonitor(false)
            monitorByFilming = false
        }
        _filming.value = CameraFilming.Off
        if (restartLive && camera.state.value is ConnectionState.Connected) {
            val s = settings.current()
            camera.startLiveView(alsoOnCamera = s.liveOnCamera, silentAf = s.liveSilentAf, fps = s.liveFps)
            VideoLog.i("SET", "Live view do celular religado")
        }
        if (searchCard) {
            val found = runCatching { scanCard() }.getOrElse {
                VideoLog.e("SET", "Busca no cartão falhou", it)
                emptyList()
            }
            val fresh = found.filter { it.filename !in knownOnCard }
            if (fresh.isEmpty()) {
                VideoLog.w("SET", "Nenhum vídeo novo no cartão pela USB. Arquivos gravados fora do firmware da Canon podem só aparecer depois de reconectar o cabo")
                say("Nenhum vídeo novo apareceu. Use \"Reconectar e procurar\" em Ajustes → Vídeo.")
            } else {
                VideoLog.i("SET", "Vídeos novos: ${fresh.joinToString { "${it.filename} (${sizeLabel(it.size)})" }}")
                if (autoDownload) fresh.forEach { downloadVideo(it) } else say("${fresh.size} vídeo(s) novo(s) no cartão.")
            }
        }
    }

    // Card and download.

    /** Lists the newest files on the card and keeps the videos among them. */
    suspend fun scanCard(limit: Int = SCAN_LIMIT): List<ObjectInfo> {
        _busy.value = "Procurando vídeos no cartão…"
        try {
            val videos = scanCardQuiet(limit)
            VideoLog.i("CARTAO", "Busca: ${videos.size} vídeo(s) entre os $limit arquivos mais novos" +
                if (videos.isNotEmpty()) ": " + videos.joinToString { "${it.filename} ${sizeLabel(it.size)} ${it.captureDate} formato 0x%04X".format(it.format) } else "")
            return videos
        } finally {
            _busy.value = null
        }
    }

    private suspend fun scanCardQuiet(limit: Int = SCAN_LIMIT): List<ObjectInfo> {
        if (!camera.supportsCardListing) throw IllegalStateException("A câmera não permite listar o cartão pela USB")
        var seen = 0
        val started = System.currentTimeMillis()
        val files = camera.listCardFiles(newestFirst = true, stopAfterOld = 1, isOld = { seen++ >= limit })
        val videos = files.filter { it.isVideo }.sortedByDescending { it.filename }
        VideoLog.i("CARTAO", "${files.size} arquivo(s) lidos em ${System.currentTimeMillis() - started} ms; extensões: " +
            files.groupBy { it.filename.substringAfterLast('.', "?").uppercase() }.entries.joinToString { "${it.key} ${it.value.size}" })
        _cardVideos.value = videos
        return videos
    }

    /** Reconnects the USB (a camera only lists files written by other firmware after a new session) and searches. */
    fun reconnectAndScan() = scope.launch {
        VideoLog.i("CARTAO", "Reconectando para a câmera reler o cartão")
        _busy.value = "Reconectando…"
        camera.disconnect()
        kotlinx.coroutines.delay(1_500)
        camera.findAndConnect()
        val connected = kotlinx.coroutines.withTimeoutOrNull(20_000) { camera.state.first { it is ConnectionState.Connected } }
        _busy.value = null
        if (connected == null) {
            VideoLog.w("CARTAO", "A câmera não voltou em 20 s")
            say("A câmera não reconectou. Confira o cabo.")
            return@launch
        }
        runCatching { scanCard() }.onFailure { VideoLog.e("CARTAO", "Busca depois de reconectar falhou", it) }
    }

    fun downloadVideo(obj: ObjectInfo) = scope.launch(Dispatchers.IO) {
        if (_download.value != null) {
            say("Já há um vídeo baixando. Espere terminar.")
            return@launch
        }
        cancelDownload = false
        val temp = File(library.workDir(), obj.filename)
        val started = System.currentTimeMillis()
        VideoLog.banner("download ${obj.filename}")
        VideoLog.i("DOWNLOAD", "${obj.filename}: ${sizeLabel(obj.size)}, formato 0x%04X, em pedaços de 1 MB · %s".format(obj.format, VideoLog.resources(library.freeBytes())))
        _download.value = VideoDownload(obj.filename, 0, obj.size)
        try {
            var lastPercent = -1
            BufferedOutputStream(temp.outputStream(), 1 shl 20).use { out ->
                camera.downloadInChunks(obj.handle, obj.size, StreamSink(out) { received, total ->
                    val size = if (total > 0) total else obj.size
                    val percent = if (size > 0) (received * 100 / size).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _download.value = VideoDownload(obj.filename, received, size)
                        if (percent % 25 == 0) VideoLog.i("DOWNLOAD", "${obj.filename} $percent%")
                    }
                }, keepGoing = { !cancelDownload })
            }
            val ms = (System.currentTimeMillis() - started).coerceAtLeast(1)
            VideoLog.i("DOWNLOAD", "%s recebido em %.1f s (%.1f MB/s)".format(obj.filename, ms / 1000f, temp.length() / 1_048_576f / (ms / 1000f)))
            library.publish(temp, obj.filename)
            say("${obj.filename} baixado")
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Throwable) {
            temp.delete()
            VideoLog.e("DOWNLOAD", "${obj.filename} não foi baixado", e)
            say("${obj.filename} não foi baixado: ${e.message}")
        } finally {
            _download.value = null
        }
    }

    fun cancelDownload() {
        cancelDownload = true
        VideoLog.i("DOWNLOAD", "Cancelamento pedido")
    }

    // Lab: commands sent to the camera to see how it answers.

    /** Writes everything the camera announces (operations, properties, events) and what it implies for video. */
    fun dumpCapabilities() = scope.launch {
        val info = camera.currentDeviceInfo ?: run { say("Conecte a câmera primeiro."); return@launch }
        fun hex(set: Set<Int>) = set.sorted().joinToString(" ") { "%04X".format(it) }
        VideoLog.banner("o que a câmera anuncia")
        VideoLog.i("LAB", "${info.manufacturer} ${info.model} · firmware ${info.version} · série ${info.serial}")
        VideoLog.i("LAB", "Operações (${info.operations.size}): ${hex(info.operations)}")
        VideoLog.i("LAB", "Propriedades (${info.properties.size}): ${hex(info.properties)}")
        VideoLog.i("LAB", "Eventos (${info.events.size}): ${hex(info.events)}")
        val checks = listOf(
            "Gravação de filme pela USB (0xD1B8)" to (EosProp.EVF_RECORD_STATUS in info.properties),
            "Chave de filme (0x9133/0x9134)" to (info.supports(PtpOp.EOS_MOVIE_SELECT_SW_ON) && info.supports(PtpOp.EOS_MOVIE_SELECT_SW_OFF)),
            "Download em pedaços (0x101B)" to info.supports(PtpOp.GET_PARTIAL_OBJECT),
            "Live view (0x9153)" to info.supports(PtpOp.EOS_GET_VIEWFINDER_DATA),
            "Listar o cartão (0x1007/0x1008)" to camera.supportsCardListing,
        )
        checks.forEach { (label, yes) -> VideoLog.i("LAB", "$label: ${if (yes) "anunciado" else "não anunciado"}") }
        say("Lista da câmera gravada no log (linhas VIDEO-LAB).")
    }

    /** Runs one test command and writes the answer. */
    fun labCommand(label: String, block: suspend (CameraManager) -> CameraManager.CommandResult) = scope.launch {
        if (camera.state.value !is ConnectionState.Connected) { say("Conecte a câmera primeiro."); return@launch }
        _busy.value = label
        try {
            val result = block(camera)
            VideoLog.i("LAB", "$label → ${result.describe()}")
            say("$label: ${if (result.ok) "a câmera aceitou" else "a câmera recusou (0x%04X)".format(result.code)}")
        } catch (e: Throwable) {
            VideoLog.e("LAB", "$label falhou", e)
            say("$label falhou: ${e.message}")
        } finally {
            _busy.value = null
        }
    }

    /** Writes every GetEvent answer while on: shows if the camera reports anything when it films. */
    fun setMonitor(on: Boolean) {
        if (on == _monitoring.value) return
        _monitoring.value = on
        if (on) {
            var count = 0
            camera.eventTap = { raw ->
                count++
                val records = EosEventParser.describe(raw)
                VideoLog.i("EVENTOS", "#$count ${raw.size} bytes: ${records.joinToString(" | ")}")
            }
            VideoLog.i("EVENTOS", "Monitor de eventos ligado")
        } else {
            camera.eventTap = null
            VideoLog.i("EVENTOS", "Monitor de eventos desligado")
        }
    }

    private fun jpegSize(jpeg: ByteArray): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())

    private class StopRecording : RuntimeException()

    companion object {
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024
        private const val REPORT_MS = 5_000L
        private const val SCAN_LIMIT = 80

        fun sizeLabel(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            else -> "${bytes / 1024} KB"
        }
    }
}
