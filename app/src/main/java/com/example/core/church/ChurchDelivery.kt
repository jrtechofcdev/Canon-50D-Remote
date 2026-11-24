package com.example.core.church

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.data.SettingKeys
import com.example.core.data.SettingsRepository
import com.example.core.delivery.DeliveryRules
import com.example.core.delivery.DeliveryStatus
import com.example.core.delivery.network.DeliveryNetwork
import com.example.core.delivery.network.MdnsResponder
import com.example.core.delivery.network.NetState
import com.example.core.delivery.network.SameWifiNetwork
import com.example.core.delivery.network.SystemHotspotNetwork
import com.example.core.delivery.network.WifiDirectNetwork
import com.example.core.log.AppLog
import com.example.core.render.BitmapLoader
import com.example.core.render.Enhance
import com.example.core.render.Looks
import com.example.core.storage.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * The church delivery on the phone: brings the network up, runs [ChurchServer], takes each new photo into
 * the open event and gets the files ready (original and RAW as they are, the "pronta" with the event's look).
 * Only one delivery holds the Wi-Fi: starting this stops the Entrega local and Express ([stopOthers]).
 */
class ChurchDelivery(
    private val context: Context,
    override val repo: ChurchRepository,
    private val photos: PhotoDao,
    private val looks: Looks,
    private val store: PhotoStore,
    private val settings: SettingsRepository,
) : ChurchBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler(ChurchServer.TAG))
    private val lock = Mutex()
    val server = ChurchServer(this)
    private var network: DeliveryNetwork? = null
    private var mdns: MdnsResponder? = null
    private val dir get() = File(context.cacheDir, "church").apply { mkdirs() }

    private val _status = MutableStateFlow<DeliveryStatus>(DeliveryStatus.Off)
    val status: StateFlow<DeliveryStatus> = _status.asStateFlow()

    /** Set by the app: stops the Entrega local and Express before this takes the Wi-Fi. */
    var stopOthers: suspend () -> Unit = {}

    /** Photos waiting for their "pronta" copy, made one at a time in the background. */
    private val readyQueue = Channel<Long>(Channel.UNLIMITED)
    private val renderLock = Mutex()

    init {
        scope.launch { for (id in readyQueue) runCatching { readyFile(id) }.onFailure { AppLog.w(ChurchServer.TAG, "Pronta da foto $id falhou", it) } }
    }

    // ---- start and stop ----------------------------------------------------------------------

    suspend fun start(): DeliveryStatus = lock.withLock {
        (_status.value as? DeliveryStatus.Running)?.let { return it }
        stopOthers()
        _status.value = DeliveryStatus.Starting
        val s = settings.current()
        val ssid = DeliveryRules.ssid(s.churchSsid)
        var password = s.churchPassword
        if (!DeliveryRules.passwordOk(password)) {
            password = DeliveryRules.newPassword()
            settings.set(SettingKeys.churchPassword, password)
        }
        val net = when (s.churchNetwork) {
            "hotspot" -> SystemHotspotNetwork()
            "direct" -> WifiDirectNetwork(context)
            else -> SameWifiNetwork()
        }
        val state = runCatching { net.start(ssid, password) }.getOrElse { NetState.Failed(it.message ?: "Erro na rede") }
        if (state !is NetState.Up) {
            runCatching { net.stop() }
            val reason = (state as? NetState.Failed)?.reason ?: "A rede não ligou"
            AppLog.w(ChurchServer.TAG, "Rede não ligou: $reason")
            return DeliveryStatus.Failed(reason).also { _status.value = it }
        }
        val port = runCatching { withContext(Dispatchers.IO) { server.start(ChurchServer.PORT, state.host) } }.getOrElse {
            runCatching { net.stop() }
            return DeliveryStatus.Failed("O servidor não ligou: ${it.message}").also { f -> _status.value = f }
        }
        network = net
        mdns = s.churchDomain.takeIf { it.isNotBlank() }
            ?.let { MdnsResponder(context, MdnsResponder.hostName(it, "igreja"), state.host) }
            ?.takeIf { withContext(Dispatchers.IO) { it.start() } }
        val running = DeliveryStatus.Running(state.host, port, state.ssid, state.password, state.label, false, mdns?.fullName)
        _status.value = running
        AppLog.i(ChurchServer.TAG, "Entrega da igreja ligada: ${state.label}, ${running.url()}")
        com.example.core.delivery.DeliveryService.start(context)
        // Photos of the open event without their "pronta" yet (the app was closed meanwhile).
        repo.dao.openEvent()?.let { e -> repo.dao.photos(e.id).forEach { readyQueue.trySend(it.photoId) } }
        running
    }

    /** Screenshots only: shows a status without bringing a network up. */
    @androidx.annotation.VisibleForTesting
    internal fun showStatusForPreview(status: DeliveryStatus) {
        _status.value = status
    }

    suspend fun stop() = lock.withLock {
        withContext(Dispatchers.IO) { server.stop() }
        mdns?.stop()
        mdns = null
        network?.let { runCatching { it.stop() } }
        network = null
        _status.value = DeliveryStatus.Off
    }

    /** The link of the open event (in the QR when the QR is on). */
    fun eventUrl(event: ChurchEvent, via: String): String? {
        val running = _status.value as? DeliveryStatus.Running ?: return null
        return "http://${running.host}:${running.port}/?e=${event.token}&via=$via"
    }

    // ---- photos coming in --------------------------------------------------------------------

    /** A file of [photo] was saved (JPG or RAW): it joins the open event and its "pronta" is made. */
    suspend fun onPhotoSaved(photo: Photo) {
        val e = repo.dao.openEvent() ?: return
        repo.addPhoto(e.id, photo.id)
        // The second file of a RAW+JPG pair adds nothing new to the list, but the page must offer it now.
        repo.changed()
        readyQueue.trySend(photo.id)
    }

    // ---- what the server reads ---------------------------------------------------------------

    @android.annotation.SuppressLint("ResourceType")
    override fun asset(name: String): InputStream? = runCatching {
        when (name) {
            "exo2-400.ttf" -> context.resources.openRawResource(com.example.R.font.exo2_regular)
            "exo2-500.ttf" -> context.resources.openRawResource(com.example.R.font.exo2_medium)
            "exo2-600.ttf" -> context.resources.openRawResource(com.example.R.font.exo2_semibold)
            "brand.css", "brand.js" -> context.assets.open("shared/$name")
            "logo.png" -> context.assets.open("watermarks/horizontal_branco.png")
            "icon.png" -> context.assets.open("watermarks/simbolo.png")
            else -> context.assets.open("church/$name")
        }
    }.getOrNull()

    override suspend fun photo(id: Long): ChurchPhotoInfo? = photos.get(id)?.let {
        ChurchPhotoInfo(it.id, it.savedBase, hasJpg = it.jpgUri != null || it.previewPath != null, hasRaw = it.rawUri != null)
    }

    override suspend fun file(id: Long, kind: ChurchFile): ServedFile? {
        val p = photos.get(id) ?: return null
        return when (kind) {
            // The camera's own JPG, untouched. A RAW-only frame offers the JPEG the camera embedded in it.
            ChurchFile.ORIGINAL -> p.jpgUri?.let { uri(it, "${p.savedBase}.JPG", p.jpgSize) }
                ?: p.previewPath?.let { fileServed(File(it), "${p.savedBase}.JPG") }
            ChurchFile.RAW -> p.rawUri?.let { uri(it, "${p.savedBase}.${p.rawCameraName?.substringAfterLast('.', "CR2") ?: "CR2"}", p.rawSize) }
            ChurchFile.READY -> readyFile(id)?.let { fileServed(it, "${p.savedBase}_pronta.jpg") }
        }
    }

    override suspend fun thumb(id: Long): ServedFile? = small(id, THUMB_EDGE, "thumb")?.let { fileServed(it, "t.jpg") }

    override suspend fun preview(id: Long, member: ChurchMember, marked: Boolean): ServedFile? {
        val clean = small(id, PREVIEW_EDGE, "view") ?: return null
        if (!marked) return fileServed(clean, "p.jpg")
        // A copy with this person's name, role and the time: a screenshot of it shows who it came from.
        val out = File(dir, "m${member.id}_$id.jpg")
        if (!out.exists()) withContext(Dispatchers.IO) {
            val bitmap = BitmapLoader.load(context, Uri.fromFile(clean), PREVIEW_EDGE) ?: return@withContext
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bitmap) bitmap.recycle() }
            mark(copy, "${member.name} · ${member.role}", SimpleDateFormat("dd/MM HH:mm", Locale.ROOT).format(Date()))
            writeWhole(out) { copy.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            copy.recycle()
        }
        return out.takeIf { it.exists() }?.let { fileServed(it, "p.jpg") }
    }

    override suspend fun logo(event: ChurchEvent): ServedFile? {
        val uri = event.logoUri ?: return null
        val bytes = withContext(Dispatchers.IO) { runCatching { store.openInput(uri)?.use { it.readBytes() } }.getOrNull() } ?: return null
        return ServedFile("logo.png", bytes.size.toLong()) { bytes.inputStream() }
    }

    // ---- files -------------------------------------------------------------------------------

    /**
     * The "pronta": the photo with the event's look (preset, calibration) and "Melhorar" when the event or
     * Ajustes ask for it, at full size. Made once per look and kept; with no look at all it is the original.
     */
    private suspend fun readyFile(id: Long): File? = renderLock.withLock {
        val photo = photos.get(id) ?: return@withLock null
        val e = repo.dao.openEvent() ?: return@withLock null
        val s = settings.current()
        val enhance = if (e.enhanceReady || s.enhanceOnChurch) s.enhanceLevel else photo.enhance
        val looked = photo.copy(presetId = e.presetId ?: photo.presetId, adjust = if (e.presetId != null || e.adjust != null) e.adjust else photo.adjust, enhance = enhance)
        val key = listOf(looked.presetId, looked.adjust, looked.crop, Enhance.resolve(enhance, photo.iso), looked.displayUri).hashCode().toUInt().toString(16)
        val out = File(dir, "${e.id}_${id}_$key.jpg")
        if (out.exists()) return@withLock out
        val started = System.currentTimeMillis()
        val made = looks.exportFile(looked, 0, tag = "igreja_$id", forceEnhance = null) ?: return@withLock null
        withContext(Dispatchers.IO) { if (!made.renameTo(out)) { made.copyTo(out, overwrite = true); made.delete() } }
        AppLog.i(ChurchServer.TAG, "Pronta de ${photo.savedBase} em ${System.currentTimeMillis() - started} ms")
        repo.changed()
        out
    }

    /** A smaller JPG of the photo as it is (grid and big view), made once. */
    private suspend fun small(id: Long, edge: Int, kind: String): File? {
        val out = File(dir, "${id}_$kind.jpg")
        if (out.exists()) return out
        val p = photos.get(id) ?: return null
        val source = p.displayUri ?: return null
        return withContext(Dispatchers.IO) {
            val bitmap = BitmapLoader.load(context, Uri.parse(source), edge) ?: return@withContext null
            writeWhole(out) { bitmap.compress(Bitmap.CompressFormat.JPEG, if (kind == "thumb") 78 else 85, it) }
            bitmap.recycle()
            out.takeIf { it.exists() }
        }
    }

    private fun fileServed(f: File, name: String) = ServedFile(name, f.length()) { runCatching { f.inputStream() }.getOrNull() }

    /** A file in the folders the photographer picked (or the app's own), sent as it is. */
    private fun uri(uri: String, name: String, knownSize: Long): ServedFile? {
        val parsed = Uri.parse(uri)
        val length = when {
            parsed.scheme == "file" -> File(parsed.path!!).length()
            knownSize > 0 -> knownSize
            else -> DocumentFile.fromSingleUri(context, parsed)?.length() ?: 0L
        }
        if (length <= 0) return null
        return ServedFile(name, length) { runCatching { store.openInput(uri) }.getOrNull() }
    }

    private fun writeWhole(target: File, write: (java.io.OutputStream) -> Unit) {
        val temp = File(target.parentFile, target.name + ".part")
        temp.outputStream().use(write)
        if (!temp.renameTo(target)) temp.delete()
    }

    /** Clears the prepared files (after an event, or to free space). */
    suspend fun clearFiles() = withContext(Dispatchers.IO) { dir.deleteRecursively() }

    companion object {
        const val THUMB_EDGE = 480
        const val PREVIEW_EDGE = 1600

        /** "Name · Role" and the time repeated across the picture, light enough to still see the photo. */
        fun mark(bitmap: Bitmap, who: String, time: String) {
            val canvas = Canvas(bitmap)
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                alpha = 70
                textSize = minOf(w, h) / 18f
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(3f, 0f, 0f, 0x55000000)
            }
            val text = "$who · $time"
            val step = paint.measureText(text) + paint.textSize * 2
            val gap = paint.textSize * 3.5f
            val diag = sqrt(w * w + h * h)
            canvas.save()
            canvas.rotate(-28f, w / 2, h / 2)
            var y = (h - diag) / 2
            var row = 0
            while (y < (h + diag) / 2) {
                var x = (w - diag) / 2 - (row % 2) * step / 2
                while (x < (w + diag) / 2) {
                    canvas.drawText(text, x, y, paint)
                    x += step
                }
                y += gap
                row++
            }
            canvas.restore()
            // A clear line at the bottom too, where a crop would keep it.
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                alpha = 150
                textSize = minOf(w, h) / 26f
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 0f, 0f, 0x99000000.toInt())
            }
            canvas.drawText(text, band.textSize, h - band.textSize, band)
        }
    }
}
