package com.example.core.delivery

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import com.example.core.delivery.network.DeliveryNetwork
import com.example.core.delivery.network.NetState
import com.example.core.delivery.network.SameWifiNetwork
import com.example.core.delivery.network.SystemHotspotNetwork
import com.example.core.delivery.network.WifiDirectNetwork
import com.example.core.export.ExportService
import com.example.core.log.AppLog
import com.example.core.render.BitmapLoader
import com.example.core.render.Looks
import com.example.core.storage.MirrorCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A photo auto mode did not send, and why. */
data class SkippedPhoto(val photoId: Long, val name: String, val reason: String)

/** Where the local delivery is. */
sealed interface DeliveryStatus {
    data object Off : DeliveryStatus
    data object Starting : DeliveryStatus

    data class Running(
        val host: String,
        val port: Int,
        val ssid: String?,
        val password: String?,
        val network: String,
        /** Developer test: a test page instead of the clients' photos. */
        val test: Boolean = false,
    ) : DeliveryStatus {
        fun url(token: String? = null) = DeliveryRules.portalUrl(host, port, token)
        val wifiQr: String? get() = if (ssid != null && password != null) DeliveryRules.wifiQr(ssid, password) else null
    }

    data class Failed(val reason: String) : DeliveryStatus
}

/**
 * One delivery tool, [mode] Normal (Entrega local: clients with a code) or Express (Fotografia Express:
 * one client at a time, no code, preview until paid). The phone makes (or joins) a Wi-Fi, serves the
 * photos through its own page, and the client saves them on their own phone. No internet, nothing to install.
 *
 * The app holds one instance per tool. They share no state: separate settings, port, page, files and
 * clients. Only the Wi-Fi is one at a time, so starting one stops the other ([peer]).
 * It only listens to [events]: captured, edited. It knows nothing of the camera or USB.
 */
class LocalDelivery(
    private val context: Context,
    private val settings: SettingsRepository,
    private val dao: DeliveryDao,
    private val photos: PhotoDao,
    private val looks: Looks,
    private val export: ExportService,
    val events: DeliveryEvents,
    private val scope: CoroutineScope,
    val mode: DeliveryMode = DeliveryMode.Normal,
    /** The photo once its sharpness and eyes were checked (null when that could not be done in time). */
    private val analysis: suspend (Long) -> Photo? = { null },
) {
    private val root = File(context.cacheDir, mode.dir)
    private val lock = Mutex()
    private val expressLock = Mutex()
    private var network: DeliveryNetwork? = null
    private var devicesJob: Job? = null
    private val tag = mode.tag

    /** The other tool: stopped before this one takes the Wi-Fi. */
    var peer: LocalDelivery? = null

    @Volatile private var portalInfo = PortalInfo("Jr_Tech_OFC", true, express = mode.express)

    val queue = DeliveryQueue(dao, photos, DeliveryPreparer { photo, session, target -> prepare(photo, session, target) }, root, events, express = mode.express)

    val server = LocalServer(
        dao = dao,
        portal = ::portalFile,
        thumb = ::thumbFor,
        info = { portalInfo },
        onDownloaded = { item -> events.emit(PhotoEvent.Delivered(item.photoId, item.sessionId)) },
        onPick = { item, on ->
            photos.setClientPick(item.photoId, on)
            if (on) events.emit(PhotoEvent.Approved(item.photoId))
        },
        expressServer = mode.express,
    )

    private val _status = MutableStateFlow<DeliveryStatus>(DeliveryStatus.Off)
    val status: StateFlow<DeliveryStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<Int?>(null)
    /** Phones in the Wi-Fi Direct group (null when the network can't tell). */
    val devices: StateFlow<Int?> = _devices.asStateFlow()

    /** Phones that opened the page in the last 2 minutes. */
    val online: StateFlow<Int> = server.online

    /** Photos auto mode left out (shaken, out of focus, eyes closed), newest first, for the screen to say so. */
    private val _skipped = MutableStateFlow<List<SkippedPhoto>>(emptyList())
    val skipped: StateFlow<List<SkippedPhoto>> = _skipped.asStateFlow()

    fun clearSkipped() {
        _skipped.value = emptyList()
    }

    /** This tool's clients and photos only. */
    val sessions: Flow<List<DeliverySession>> = dao.observeSessions(mode.express)
    val items: Flow<List<DeliveryItem>> = dao.observeItems(mode.express)

    private suspend fun config() = mode.config(settings.current())

    /** Starts the queue and the listeners; called once by the app. */
    fun init() {
        queue.start(scope)
        scope.launch {
            settings.settings.collect {
                val c = mode.config(it)
                portalInfo = PortalInfo(
                    photographer = c.photographer,
                    picks = c.picks,
                    express = mode.express && it.expressEnabled,
                    expressPreview = it.expressPreview,
                    price = if (mode.express) DeliveryRules.price(it.expressPriceCents) else null,
                    pix = if (mode.express && it.expressShowPix) it.pixKey.trim().takeIf { k -> k.isNotEmpty() } else null,
                )
            }
        }
        scope.launch {
            events.events.collect { event ->
                when (event) {
                    // Each photo waits for its own analysis, so a burst never queues behind one slow frame.
                    is PhotoEvent.Captured -> scope.launch {
                        runCatching { autoSend(event.photoId) }.onFailure { AppLog.w(tag, "Envio automático falhou", it) }
                    }
                    is PhotoEvent.Edited -> queue.refresh(event.photoId)
                    else -> Unit
                }
            }
        }
    }

    // ---- start and stop ----------------------------------------------------------------------

    /** Brings the Wi-Fi and the server up. [test] = developer test page with that photo. */
    suspend fun start(test: File? = null): DeliveryStatus {
        // One Wi-Fi at a time: the other tool lets go first.
        peer?.takeIf { it.status.value !is DeliveryStatus.Off }?.stop()
        return lock.withLock { startLocked(test) }
    }

    private suspend fun startLocked(test: File?): DeliveryStatus {
        (_status.value as? DeliveryStatus.Running)?.let { if (it.test == (test != null)) return it }
        stopLocked()
        _status.value = DeliveryStatus.Starting
        val c = config()
        val ssid = if (test != null) TEST_SSID else DeliveryRules.ssid(c.ssid)
        var password = if (test != null) TEST_PASSWORD else c.password
        if (!DeliveryRules.passwordOk(password)) {
            password = DeliveryRules.newPassword()
            settings.set(mode.passwordKey, password)
        }
        val net = when (c.network) {
            "hotspot" -> SystemHotspotNetwork()
            "wifi" -> SameWifiNetwork()
            else -> WifiDirectNetwork(context)
        }
        val state = runCatching { net.start(ssid, password) }.getOrElse { NetState.Failed(it.message ?: "Erro na rede") }
        if (state !is NetState.Up) {
            runCatching { net.stop() }
            val reason = (state as? NetState.Failed)?.reason ?: "A rede não ligou"
            AppLog.w(tag, "Rede não ligou: $reason")
            return DeliveryStatus.Failed(reason).also { _status.value = it }
        }
        val port = runCatching { withContext(Dispatchers.IO) { server.start(mode.port) } }.getOrElse {
            runCatching { net.stop() }
            return DeliveryStatus.Failed("O servidor não ligou: ${it.message}").also { s2 -> _status.value = s2 }
        }
        server.testFile = test
        network = net
        dao.closeExpired(System.currentTimeMillis())
        if (mode.express) expressSession()
        queue.poke()
        val running = DeliveryStatus.Running(state.host, port, state.ssid, state.password, state.label, test != null)
        _status.value = running
        devicesJob = scope.launch {
            while (isActive) {
                _devices.value = runCatching { net.devices() }.getOrNull()
                delay(5_000)
            }
        }
        AppLog.i(tag, "${mode.label} ligada: ${state.label}, ${running.url()}")
        DeliveryService.start(context)
        return running
    }

    suspend fun stop() = lock.withLock { stopLocked() }

    /** Screenshots only: shows a status without bringing a network up. */
    @androidx.annotation.VisibleForTesting
    internal fun showStatusForPreview(status: DeliveryStatus) {
        _status.value = status
    }

    private suspend fun stopLocked() {
        val was = _status.value
        devicesJob?.cancel()
        devicesJob = null
        _devices.value = null
        server.testFile = null
        withContext(Dispatchers.IO) { server.stop() }
        network?.let { runCatching { it.stop() } }
        network = null
        if (was is DeliveryStatus.Running && !was.test && config().clearOnStop) queue.clearFiles()
        _status.value = DeliveryStatus.Off
    }

    // ---- clients (Entrega local) -------------------------------------------------------------

    suspend fun newClient(name: String, phone: String? = null, event: Boolean = false): DeliverySession {
        val c = config()
        val now = System.currentTimeMillis()
        val session = DeliverySession(
            client = name.trim().ifEmpty { if (event) "Galeria do evento" else "Cliente" }.take(40),
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            code = DeliveryRules.newCode(dao.openCodes()),
            token = DeliveryRules.newToken(),
            event = event,
            createdAt = now,
            expiresAt = DeliveryRules.expiresAt(now, c.validityHours),
            express = mode.express,
        )
        val id = dao.insertSession(session)
        // The newest client becomes the one auto mode sends to.
        if (!event && !mode.express) settings.set(SettingKeys.deliveryActiveSession, id)
        AppLog.i(tag, "Cliente novo: ${session.client}")
        return session.copy(id = id)
    }

    suspend fun setActive(id: Long) = settings.set(SettingKeys.deliveryActiveSession, id)

    /** Ends a client's access: the token and code stop working, the files are deleted, the history stays. */
    suspend fun closeClient(id: Long) {
        val session = dao.session(id) ?: return
        dao.closeSession(id, System.currentTimeMillis())
        withContext(Dispatchers.IO) { queue.clearFiles(session) }
        if (settings.current().deliveryActiveSession == id) settings.set(SettingKeys.deliveryActiveSession, 0L)
    }

    suspend fun deleteClient(id: Long) {
        closeClient(id)
        dao.deleteItemsOf(id)
        dao.deleteSession(id)
    }

    /** Sends photos to a client. Returns how many were new for them. */
    suspend fun send(sessionId: Long, photoIds: List<Long>): Int = queue.enqueue(sessionId, photoIds)

    suspend fun retry(itemId: Long) {
        dao.retry(itemId)
        queue.poke()
    }

    suspend fun retryAll() {
        dao.retryAll(mode.express)
        queue.poke()
    }

    suspend fun removeItem(item: DeliveryItem) {
        dao.deleteItem(item.id)
        item.path?.let { path ->
            withContext(Dispatchers.IO) {
                listOf(".jpg", ".thumb.jpg", ".pthumb.jpg", ".preview.jpg").forEach { File(path.removeSuffix(".jpg") + it).delete() }
            }
        }
    }

    // ---- Fotografia Express ------------------------------------------------------------------

    /** The client of the moment in Fotografia Express: the open one, or a new one. */
    suspend fun expressSession(name: String? = null): DeliverySession = expressLock.withLock {
        check(mode.express) { "Só a Fotografia Express tem cliente da vez sem código" }
        dao.openExpress()?.takeIf { it.isValid(System.currentTimeMillis()) } ?: run {
            val now = System.currentTimeMillis()
            val session = DeliverySession(
                client = name?.trim()?.takeIf { it.isNotEmpty() }?.take(40) ?: "Express",
                code = DeliveryRules.newCode(dao.openCodes()),
                token = DeliveryRules.newToken(),
                createdAt = now,
                express = true,
            )
            session.copy(id = dao.insertSession(session)).also { AppLog.i(tag, "Cliente novo") }
        }
    }

    /** "Mandar para o Express": the photos go to the client of the moment. Returns how many were new. */
    suspend fun sendToExpress(photoIds: List<Long>): Int = queue.enqueue(expressSession().id, photoIds)

    /** Photos paid: they download clean from now on. */
    suspend fun release(itemIds: List<Long>, on: Boolean = true) {
        if (itemIds.isNotEmpty()) dao.setReleased(itemIds, on)
        AppLog.i(tag, "${itemIds.size} foto(s) ${if (on) "liberadas" else "bloqueadas de novo"}")
    }

    suspend fun releaseAll() {
        dao.openExpress()?.let { dao.releaseAll(it.id) }
        AppLog.i(tag, "Todas liberadas")
    }

    /**
     * Ends the Express client: their page stops working, the files are deleted and an empty session is
     * ready for the next one. The history (how many photos, how many released) stays. [name] labels it.
     */
    suspend fun finishExpress(name: String? = null) {
        dao.openExpress()?.let { old ->
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { dao.updateSession(old.copy(client = it.take(40))) }
            dao.closeSession(old.id, System.currentTimeMillis())
            withContext(Dispatchers.IO) { queue.clearFiles(old) }
        }
        expressSession()
        AppLog.i(tag, "Entrega limpa: pronta para o próximo cliente")
    }

    private suspend fun autoSend(photoId: Long) {
        val s = settings.current()
        val c = mode.config(s)
        if (!c.enabled || !c.auto) return
        // Only good frames go by themselves; the rest can still be sent from the gallery.
        if (c.skipBlurry || c.skipEyes) {
            val photo = analysis(photoId)
            DeliveryRules.autoQualityProblem(photo, c.skipBlurry, c.skipEyes)?.let { why ->
                AppLog.i(tag, "${photo?.savedBase} ficou de fora do envio automático: $why")
                _skipped.value = (listOf(SkippedPhoto(photoId, photo?.savedBase.orEmpty(), why)) + _skipped.value).take(50)
                return
            }
        }
        if (mode.express) {
            queue.enqueue(expressSession().id, listOf(photoId))
            return
        }
        val now = System.currentTimeMillis()
        val active = s.deliveryActiveSession.takeIf { it > 0 }?.let { dao.session(it) }
        val event = if (s.deliveryEventGallery) {
            dao.openSessions().firstOrNull { it.event && it.isValid(now) } ?: newClient("Galeria do evento", event = true)
        } else {
            null
        }
        val target = DeliveryRules.autoTarget(s, active, event, now)
        if (target == null) {
            AppLog.i(tag, "Modo automático sem cliente ativo: foto não enviada")
            return
        }
        queue.enqueue(target, listOf(photoId))
    }

    // ---- files -------------------------------------------------------------------------------

    /**
     * The JPG a client gets: "light" (resized, with the look, optional watermark), "edited" (full size
     * with the look) or "original" (the camera's JPG as it is). Express also makes the marked preview,
     * and fails the photo when it can't, so an unmarked file is never the only one. With a copies folder
     * set, a copy goes to <folder>/<client>/.
     */
    private suspend fun prepare(photo: Photo, session: DeliverySession, target: File): Boolean = withContext(Dispatchers.IO) {
        val s = settings.current()
        val c = mode.config(s)
        val source = photo.displayUri ?: return@withContext false
        if (c.size == "original") {
            val uri = Uri.parse(photo.jpgUri ?: source)
            val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
            input?.use { i -> target.outputStream().use { i.copyTo(it) } } ?: return@withContext false
        } else {
            val edge = if (c.size == "edited") 0 else s.lightShareSize.takeIf { it > 0 } ?: 2048
            val looked = looks.exportFile(photo, edge, tag = "${mode.dir}${target.nameWithoutExtension}")
            // Encoded once: without a watermark the rendered look (or the camera JPG at full size) is the file.
            val made = when {
                !c.watermark && looked != null -> looked
                !c.watermark && c.size == "edited" && photo.jpgUri != null -> {
                    val uri = Uri.parse(photo.jpgUri)
                    val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
                    input?.use { i -> target.outputStream().use { i.copyTo(it) } }
                    if (target.length() > 0) target else null
                }
                else -> {
                    val from = looked?.let { Uri.fromFile(it).toString() } ?: source
                    export.exportLight(from, "${mode.dir}_${target.nameWithoutExtension}", s.copy(lightShareSize = edge, watermarkEnabled = c.watermark), photo.lat, photo.lon)
                        .also { looked?.delete() }
                }
            } ?: return@withContext false
            if (made != target && !made.renameTo(target)) {
                made.copyTo(target, overwrite = true)
                made.delete()
            }
        }
        if (session.express && !makePreview(target, c.photographer, s.expressMarkOpacity, s)) {
            target.delete()
            return@withContext false
        }
        // The page's grid is ready before the client asks for it.
        warmThumbs(target, session.express)
        c.folder?.let { folder ->
            runCatching { MirrorCopy(context).copy(Uri.fromFile(target).toString(), folder, listOf(session.client), DeliveryRules.itemName(session.client, photo.savedBase)) }
                .onFailure { AppLog.w(tag, "Cópia na pasta de entregas falhou", it) }
        }
        true
    }

    /**
     * The page's files: assets/portal (Entrega local) or assets/express (Fotografia Express), and the
     * brand's font and logos from the app itself. A font resource is stored as the plain .ttf, so it
     * opens as raw without a second copy in the APK.
     */
    @android.annotation.SuppressLint("ResourceType")
    private fun portalFile(name: String): java.io.InputStream? = runCatching {
        when (name) {
            "exo2.ttf" -> context.resources.openRawResource(com.example.R.font.exo2_semibold)
            "logo.png" -> context.assets.open("watermarks/horizontal_branco.png")
            "icon.png" -> context.assets.open("watermarks/simbolo.png")
            else -> context.assets.open("${mode.assets}/$name")
        }
    }.getOrNull()

    /** The marked, smaller copy that is all an unreleased Express photo shows. False when it could not be made. */
    private fun makePreview(target: File, photographer: String, opacity: Int, s: com.example.core.data.Settings): Boolean {
        val bitmap = BitmapLoader.load(context, Uri.fromFile(target), ExpressPreview.EDGE) ?: return false
        val marked = bitmap.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bitmap) bitmap.recycle() }
        val logo = export.loadLogo(s)
        ExpressPreview.mark(marked, logo, photographer, opacity)
        logo?.recycle()
        val out = previewFileOf(target.path)
        out.outputStream().use { marked.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        marked.recycle()
        return out.length() > 0
    }

    private fun warmThumbs(target: File, express: Boolean) {
        val id = target.nameWithoutExtension.toLongOrNull() ?: return
        val item = DeliveryItem(id = id, sessionId = 0, photoId = 0, name = "", queuedAt = 0, path = target.path)
        runCatching {
            thumbFor(item, locked = false)
            if (express) thumbFor(item, locked = true)
        }
    }

    /** A small JPG for the page's grid, made once next to the file (from the marked preview when [locked]). */
    private fun thumbFor(item: DeliveryItem, locked: Boolean): File? {
        val path = item.path ?: return null
        val file = if (locked) previewFileOf(path).takeIf { it.exists() } ?: return null else File(path)
        val thumb = File(file.parentFile, "${item.id}.${if (locked) "pthumb" else "thumb"}.jpg")
        if (thumb.exists()) return thumb
        synchronized(this) {
            if (thumb.exists()) return thumb
            val bitmap = BitmapLoader.load(context, Uri.fromFile(file), THUMB_EDGE) ?: return null
            thumb.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bitmap.recycle()
        }
        return thumb
    }

    companion object {
        const val THUMB_EDGE = 480
        const val TEST_SSID = "DIRECT-JR-Teste"
        const val TEST_PASSWORD = "jrteste2026"
    }
}
