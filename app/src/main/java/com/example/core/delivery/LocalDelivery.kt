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
 * Entrega local: the phone makes (or joins) a Wi-Fi, serves each client's photos through a small
 * portal, and the client saves them on their own phone. No internet, nothing to install.
 *
 * It only listens to [events]: captured, edited, approved. It knows nothing of the camera or USB, and
 * with the option off in Ajustes nothing here starts.
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
) {
    private val root = File(context.cacheDir, "delivery")
    private val lock = Mutex()
    private var network: DeliveryNetwork? = null
    private var devicesJob: Job? = null

    @Volatile private var portalInfo = PortalInfo("Jr_Tech_OFC", true)

    val queue = DeliveryQueue(dao, photos, DeliveryPreparer { photo, session, target -> prepare(photo, session, target) }, root, events)

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
    )

    private val _status = MutableStateFlow<DeliveryStatus>(DeliveryStatus.Off)
    val status: StateFlow<DeliveryStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<Int?>(null)
    /** Phones in the Wi-Fi Direct group (null when the network can't tell). */
    val devices: StateFlow<Int?> = _devices.asStateFlow()

    /** Phones that opened the portal in the last 2 minutes. */
    val online: StateFlow<Int> = server.online

    val sessions: Flow<List<DeliverySession>> = dao.observeSessions()
    val items: Flow<List<DeliveryItem>> = dao.observeItems()

    /** Starts the queue and the listeners; called once by the app. */
    fun init() {
        queue.start(scope)
        scope.launch {
            settings.settings.collect { portalInfo = PortalInfo(it.deliveryPhotographer, it.deliveryPicks) }
        }
        scope.launch {
            events.events.collect { event ->
                when (event) {
                    is PhotoEvent.Captured -> runCatching { autoSend(event.photoId) }.onFailure { AppLog.w("ENTREGA", "Envio automático falhou", it) }
                    is PhotoEvent.Edited -> queue.refresh(event.photoId)
                    else -> Unit
                }
            }
        }
    }

    // ---- start and stop ----------------------------------------------------------------------

    /** Brings the Wi-Fi and the server up. [test] = developer test page with that photo. */
    suspend fun start(test: File? = null): DeliveryStatus = lock.withLock {
        (_status.value as? DeliveryStatus.Running)?.let { if (it.test == (test != null)) return it }
        stopLocked()
        _status.value = DeliveryStatus.Starting
        val s = settings.current()
        val ssid = if (test != null) TEST_SSID else DeliveryRules.ssid(s.deliverySsid)
        var password = if (test != null) TEST_PASSWORD else s.deliveryPassword
        if (!DeliveryRules.passwordOk(password)) {
            password = DeliveryRules.newPassword()
            settings.set(SettingKeys.deliveryPassword, password)
        }
        val net = when (s.deliveryNetwork) {
            "hotspot" -> SystemHotspotNetwork()
            "wifi" -> SameWifiNetwork()
            else -> WifiDirectNetwork(context)
        }
        val state = runCatching { net.start(ssid, password) }.getOrElse { NetState.Failed(it.message ?: "Erro na rede") }
        if (state !is NetState.Up) {
            runCatching { net.stop() }
            val reason = (state as? NetState.Failed)?.reason ?: "A rede não ligou"
            AppLog.w("ENTREGA", "Rede não ligou: $reason")
            return DeliveryStatus.Failed(reason).also { _status.value = it }
        }
        val port = runCatching { withContext(Dispatchers.IO) { server.start(DeliveryRules.PORT) } }.getOrElse {
            runCatching { net.stop() }
            return DeliveryStatus.Failed("O servidor não ligou: ${it.message}").also { s2 -> _status.value = s2 }
        }
        server.testFile = test
        network = net
        dao.closeExpired(System.currentTimeMillis())
        queue.poke()
        val running = DeliveryStatus.Running(state.host, port, state.ssid, state.password, state.label, test != null)
        _status.value = running
        devicesJob = scope.launch {
            while (isActive) {
                _devices.value = runCatching { net.devices() }.getOrNull()
                delay(5_000)
            }
        }
        AppLog.i("ENTREGA", "Entrega ligada: ${state.label}, ${running.url()}")
        DeliveryService.start(context)
        running
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
        if (was is DeliveryStatus.Running && !was.test && settings.current().deliveryClearOnStop) queue.clearFiles()
        _status.value = DeliveryStatus.Off
    }

    // ---- clients -----------------------------------------------------------------------------

    suspend fun newClient(name: String, phone: String? = null, event: Boolean = false): DeliverySession {
        val s = settings.current()
        val now = System.currentTimeMillis()
        val session = DeliverySession(
            client = name.trim().ifEmpty { if (event) "Galeria do evento" else "Cliente" }.take(40),
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            code = DeliveryRules.newCode(dao.openCodes()),
            token = DeliveryRules.newToken(),
            event = event,
            createdAt = now,
            expiresAt = DeliveryRules.expiresAt(now, s.deliveryValidityHours),
        )
        val id = dao.insertSession(session)
        // The newest client becomes the one auto mode sends to.
        if (!event) settings.set(SettingKeys.deliveryActiveSession, id)
        AppLog.i("ENTREGA", "Cliente novo: ${session.client}")
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
        dao.retryAll()
        queue.poke()
    }

    suspend fun removeItem(item: DeliveryItem) {
        dao.deleteItem(item.id)
        item.path?.let { withContext(Dispatchers.IO) { File(it).delete(); File(it.removeSuffix(".jpg") + ".thumb.jpg").delete() } }
    }

    private suspend fun autoSend(photoId: Long) {
        val s = settings.current()
        if (!s.deliveryEnabled || s.deliveryMode != "auto") return
        val now = System.currentTimeMillis()
        val active = s.deliveryActiveSession.takeIf { it > 0 }?.let { dao.session(it) }
        val event = if (s.deliveryEventGallery) {
            dao.openSessions().firstOrNull { it.event && it.isValid(now) } ?: newClient("Galeria do evento", event = true)
        } else {
            null
        }
        val target = DeliveryRules.autoTarget(s, active, event, now)
        if (target == null) {
            AppLog.i("ENTREGA", "Modo automático sem cliente ativo: foto não enviada")
            return
        }
        queue.enqueue(target, listOf(photoId))
    }

    // ---- files -------------------------------------------------------------------------------

    /**
     * The JPG a client gets: "light" (resized, with the look, optional watermark), "edited" (full size
     * with the look) or "original" (the camera's JPG as it is). With the 07 Entregas folder set, a copy
     * also goes to <folder>/<client>/.
     */
    private suspend fun prepare(photo: Photo, session: DeliverySession, target: File): Boolean = withContext(Dispatchers.IO) {
        val s = settings.current()
        val source = photo.displayUri ?: return@withContext false
        if (s.deliverySize == "original") {
            val uri = Uri.parse(photo.jpgUri ?: source)
            val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
            input?.use { i -> target.outputStream().use { i.copyTo(it) } } ?: return@withContext false
        } else {
            val edge = if (s.deliverySize == "edited") 0 else s.lightShareSize.takeIf { it > 0 } ?: 2048
            val looked = looks.exportFile(photo, edge)
            val from = looked?.let { Uri.fromFile(it).toString() } ?: source
            val made = export.exportLight(from, "entrega_${photo.id}", s.copy(lightShareSize = edge, watermarkEnabled = s.deliveryWatermark), photo.lat, photo.lon)
            looked?.delete()
            made ?: return@withContext false
            if (!made.renameTo(target)) {
                made.copyTo(target, overwrite = true)
                made.delete()
            }
        }
        s.deliveryFolder?.let { folder ->
            runCatching { MirrorCopy(context).copy(Uri.fromFile(target).toString(), folder, listOf(session.client), DeliveryRules.itemName(session.client, photo.savedBase)) }
                .onFailure { AppLog.w("ENTREGA", "Cópia em 07 Entregas falhou", it) }
        }
        true
    }

    /**
     * The portal's files: the page from assets/portal, the brand's font and logos from the app itself.
     * A font resource is stored as the plain .ttf, so it opens as raw without a second copy in the APK.
     */
    @android.annotation.SuppressLint("ResourceType")
    private fun portalFile(name: String): java.io.InputStream? = runCatching {
        when (name) {
            "exo2.ttf" -> context.resources.openRawResource(com.example.R.font.exo2_semibold)
            "logo.png" -> context.assets.open("watermarks/horizontal_branco.png")
            "icon.png" -> context.assets.open("watermarks/simbolo.png")
            else -> context.assets.open("portal/$name")
        }
    }.getOrNull()

    /** A small JPG for the portal grid, made once next to the file. */
    private fun thumbFor(item: DeliveryItem): File? {
        val path = item.path ?: return null
        val file = File(path)
        val thumb = File(file.parentFile, "${item.id}.thumb.jpg")
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
