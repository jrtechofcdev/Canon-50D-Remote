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
import com.example.core.preset.forPicking
import com.example.core.render.Looks
import com.example.core.render.RenderParams
import com.example.core.render.RenderParamsJson
import com.example.core.storage.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val presetRepo: com.example.core.preset.PresetRepository,
    private val shop: com.example.core.shop.PresetShop,
    private val insta: com.example.core.insta.InstaDao? = null,
) : ChurchBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + AppLog.handler(ChurchServer.TAG))
    private val lock = Mutex()
    val server = ChurchServer(this)
    private var network: DeliveryNetwork? = null
    private var mdns: MdnsResponder? = null
    /** Prepared files (pronta, previews, thumbnails), one folder per event so they leave with it. */
    private val root get() = File(context.cacheDir, "church").apply { mkdirs() }
    private fun dirOf(eventId: Long) = File(root, eventId.toString()).apply { mkdirs() }

    private val _status = MutableStateFlow<DeliveryStatus>(DeliveryStatus.Off)
    val status: StateFlow<DeliveryStatus> = _status.asStateFlow()

    /** Set by the app: stops the Entrega local and Express before this takes the Wi-Fi. */
    var stopOthers: suspend () -> Unit = {}

    /** Photos waiting for their "pronta" copy, made one at a time in the background. */
    private val readyQueue = Channel<Long>(Channel.UNLIMITED)
    private val renderLock = Mutex()

    init {
        // The event's deadline: checked every minute while the delivery is on the air.
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                if (_status.value is DeliveryStatus.Running && runCatching { repo.closeIfDue() }.getOrDefault(false)) {
                    AppLog.i(ChurchServer.TAG, "Evento encerrado pela validade")
                    stop()
                }
            }
        }
        scope.launch { for (id in readyQueue) runCatching { readyFile(id) }.onFailure { AppLog.w(ChurchServer.TAG, "Pronta da foto $id falhou", it) } }
    }

    // ---- start and stop ----------------------------------------------------------------------

    suspend fun start(): DeliveryStatus = lock.withLock {
        (_status.value as? DeliveryStatus.Running)?.let { return it }
        stopOthers()
        _status.value = DeliveryStatus.Starting
        runCatching { clearLeftovers() }
        if (repo.closeIfDue()) AppLog.i(ChurchServer.TAG, "Evento passou da validade e foi encerrado")
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

    /** A photo's look, crop or Melhorar changed: its "pronta" is made again ahead, when it is in the open event. */
    suspend fun onPhotoEdited(photoId: Long) {
        val e = repo.dao.openEvent() ?: return
        if (repo.dao.photos(e.id).any { it.photoId == photoId }) readyQueue.trySend(photoId)
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

    override suspend fun file(id: Long, kind: ChurchFile, look: ReadyLook?): ServedFile? {
        val p = photos.get(id) ?: return null
        return when (kind) {
            // The camera's own JPG, untouched. A RAW-only frame offers the JPEG the camera embedded in it.
            ChurchFile.ORIGINAL -> p.jpgUri?.let { uri(it, "${p.savedBase}.JPG", p.jpgSize) }
                ?: p.previewPath?.let { fileServed(File(it), "${p.savedBase}.JPG") }
            ChurchFile.RAW -> p.rawUri?.let { uri(it, "${p.savedBase}.${p.rawCameraName?.substringAfterLast('.', "CR2") ?: "CR2"}", p.rawSize) }
            ChurchFile.READY -> readyFile(id, look)?.let { fileServed(it, "${p.savedBase}_pronta.jpg") }
        }
    }

    override suspend fun thumb(id: Long, member: ChurchMember, marked: Boolean): ServedFile? {
        val clean = small(id, THUMB_EDGE, "thumb", member.eventId) ?: return null
        return if (marked) markedCopy(clean, THUMB_EDGE, "mt${member.id}_$id.jpg", member)?.let { fileServed(it, "t.jpg") } else fileServed(clean, "t.jpg")
    }

    override suspend fun preview(id: Long, member: ChurchMember, marked: Boolean, ready: Boolean, look: ReadyLook?): ServedFile? {
        // The "pronta" to look at before downloading; while it can't be made, the photo as it is.
        val clean = (if (ready) readyView(id, member.eventId, look) else null) ?: small(id, PREVIEW_EDGE, "view", member.eventId) ?: return null
        return if (marked) markedCopy(clean, PREVIEW_EDGE, "m${member.id}_${clean.nameWithoutExtension}.jpg", member)?.let { fileServed(it, "p.jpg") } else fileServed(clean, "p.jpg")
    }

    /** The "pronta" at the size of the big view; its name carries the look, so a new look makes a new one. */
    private suspend fun readyView(id: Long, eventId: Long, chosen: ReadyLook? = null): File? {
        val full = readyFile(id, chosen) ?: return null
        val out = File(dirOf(eventId), "${full.nameWithoutExtension}_view.jpg")
        if (out.exists()) return out
        return withContext(Dispatchers.IO) {
            val bitmap = BitmapLoader.load(context, Uri.fromFile(full), PREVIEW_EDGE) ?: return@withContext null
            writeWhole(out) { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            out.takeIf { it.exists() }
        }
    }

    /** A copy of [clean] with this person's name, role and the time: a screenshot of it shows who it came from. */
    private suspend fun markedCopy(clean: File, edge: Int, name: String, member: ChurchMember): File? {
        val out = File(dirOf(member.eventId), name)
        if (!out.exists()) withContext(Dispatchers.IO) {
            val bitmap = BitmapLoader.load(context, Uri.fromFile(clean), edge) ?: return@withContext
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== bitmap) bitmap.recycle() }
            mark(copy, "${member.name} · ${member.role}", SimpleDateFormat("dd/MM HH:mm", Locale.ROOT).format(Date()))
            writeWhole(out) { copy.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            copy.recycle()
        }
        return out.takeIf { it.exists() }
    }

    override suspend fun presets(): List<ChurchPresetInfo> =
        presetRepo.all().forPicking().map { ChurchPresetInfo(it.id, it.name, it.group) }

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
    private suspend fun readyFile(id: Long, chosen: ReadyLook? = null): File? = renderLock.withLock {
        val photo = photos.get(id) ?: return@withLock null
        val e = repo.dao.openEvent() ?: return@withLock null
        val enhance = enhanceFor(photo, e)
        val looked = if (chosen != null) photo.copy(presetId = chosen.presetId, adjust = chosen.adjust, enhance = enhance)
        else photo.copy(presetId = e.presetId ?: photo.presetId, adjust = if (e.presetId != null || e.adjust != null) e.adjust else photo.adjust, enhance = enhance)
        // The look's content, not only its id: a preset edited in the app makes a new "pronta".
        val lookText = looked.adjust ?: looked.presetId?.let { presetRepo.get(it)?.settings?.encode() }
        val key = listOf(lookText, looked.crop, Enhance.resolve(enhance, photo.iso), looked.displayUri).hashCode().toUInt().toString(16)
        val out = File(dirOf(e.id), "${id}_$key.jpg")
        if (out.exists()) return@withLock out
        val started = System.currentTimeMillis()
        val made = looks.exportFile(looked, 0, tag = "igreja_$id", forceEnhance = null) ?: return@withLock null
        withContext(Dispatchers.IO) { if (!made.renameTo(out)) { made.copyTo(out, overwrite = true); made.delete() } }
        AppLog.i(ChurchServer.TAG, "Pronta de ${photo.savedBase} em ${System.currentTimeMillis() - started} ms")
        if (chosen == null) repo.changed()
        out
    }

    override suspend fun igPost(event: ChurchEvent): ChurchIgPost? {
        val picks = insta?.picks(event.id).orEmpty()
        if (picks.isEmpty()) return null
        return ChurchIgPost(
            event.igCaption.orEmpty(),
            picks.map { p ->
                val feed = com.example.core.render.CropSpec.decode(p.feedCrop)
                ChurchIgPhoto(p.photoId, feed?.let(::shape) ?: "4:5", p.storyCrop != null)
            },
        )
    }

    private fun shape(c: com.example.core.render.CropSpec) = when (com.example.core.render.CropRatio.of(c.ratio)) {
        com.example.core.render.CropRatio.IG_SQUARE -> "1:1"
        com.example.core.render.CropRatio.IG_34 -> "3:4"
        else -> "4:5"
    }

    /**
     * A post photo for Instagram: the event's look (as the "pronta"), cut to the feed or Stories frame at
     * the size Instagram wants. Named "01_feed_IMG_1234.jpg" so the carousel keeps its order in the downloads.
     */
    override suspend fun igFile(event: ChurchEvent, photoId: Long, story: Boolean, position: Int): ServedFile? {
        val pick = insta?.picks(event.id)?.firstOrNull { it.photoId == photoId } ?: return null
        val crop = (if (story) pick.storyCrop else pick.feedCrop) ?: return null
        val made = renderLock.withLock {
            val photo = photos.get(photoId) ?: return@withLock null
            val enhance = enhanceFor(photo, event)
            val looked = photo.copy(
                presetId = event.presetId ?: photo.presetId,
                adjust = if (event.presetId != null || event.adjust != null) event.adjust else photo.adjust,
                enhance = enhance, crop = crop,
            )
            val lookText = looked.adjust ?: looked.presetId?.let { presetRepo.get(it)?.settings?.encode() }
            val key = listOf(lookText, crop, Enhance.resolve(enhance, photo.iso), looked.displayUri).hashCode().toUInt().toString(16)
            val out = File(dirOf(event.id), "ig_${photoId}_$key.jpg")
            if (out.exists()) return@withLock out
            val file = looks.exportFile(looked, 0, tag = "ig_$photoId", forceEnhance = null) ?: return@withLock null
            withContext(Dispatchers.IO) { if (!file.renameTo(out)) { file.copyTo(out, overwrite = true); file.delete() } }
            out
        } ?: return null
        val p = photos.get(photoId)
        val name = "%02d_%s_%s.jpg".format(position, if (story) "stories" else "feed", p?.savedBase ?: photoId)
        return fileServed(made, name)
    }

    /** Melhorar on the "pronta": the event's switch or Ajustes, else what the photo itself has. */
    private suspend fun enhanceFor(photo: com.example.core.data.Photo, e: ChurchEvent): Int {
        val s = settings.current()
        return if (e.enhanceReady || s.enhanceOnChurch) s.enhanceLevel else photo.enhance
    }

    override suspend fun store(): List<ChurchStoreItem> {
        shop.loadSaved()
        val saved = presetRepo.all().map { it.source }.toSet()
        return shop.items.value.map { ChurchStoreItem(it.id, it.name, it.categoryLabel, it.source in saved) }
    }

    override suspend fun storePicture(id: Long): ServedFile? {
        val item = shop.items.value.firstOrNull { it.id == id } ?: return null
        val bitmap = shop.preview(item) ?: return null
        val bytes = withContext(Dispatchers.IO) { java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray() }
        return ServedFile("loja.jpg", bytes.size.toLong()) { bytes.inputStream() }
    }

    override suspend fun storeInstall(id: Long): Result<ChurchPresetInfo> = runCatching {
        shop.loadSaved()
        val item = shop.items.value.firstOrNull { it.id == id } ?: error("Preset não está na Loja")
        val presetId = try {
            shop.save(item)
        } catch (e: Exception) {
            throw IllegalStateException(if (shop.hasOffline(item)) "Não deu para instalar" else "Precisa de internet no celular do fotógrafo para baixar este preset", e)
        }
        ChurchPresetInfo(presetId, item.name, com.example.core.preset.DEFAULT_CATEGORY)
    }

    override fun editorSpec(): String = com.example.ui.preset.EditorSpecJson.encode()

    override suspend fun lookText(look: ReadyLook): String =
        look.adjust ?: look.presetId?.let { presetRepo.get(it)?.settings?.encode() } ?: ""

    override suspend fun onEventLook() {
        val e = repo.dao.openEvent() ?: return
        repo.dao.photos(e.id).forEach { readyQueue.trySend(it.photoId) }
    }

    override suspend fun params(presetId: Long): String? =
        presetRepo.get(presetId)?.settings?.let { RenderParamsJson.encode(RenderParams.from(it)) }

    override suspend fun base(id: Long, member: ChurchMember, marked: Boolean): ServedFile? {
        val photo = photos.get(id) ?: return null
        val e = repo.dao.openEvent() ?: return null
        val enhance = enhanceFor(photo, e)
        val key = listOf(photo.crop, Enhance.resolve(enhance, photo.iso), photo.displayUri).hashCode().toUInt().toString(16)
        val out = File(dirOf(member.eventId), "${id}_base_$key.jpg")
        if (!out.exists()) {
            // Without crop or Melhorar there is nothing to apply: the big view is the base.
            val made = looks.renderPhoto(photo, null, PREVIEW_EDGE, enhance)
            if (made == null) small(id, PREVIEW_EDGE, "view", member.eventId)?.let { withContext(Dispatchers.IO) { it.copyTo(out, overwrite = true) } }
            else withContext(Dispatchers.IO) { writeWhole(out) { made.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        }
        if (!out.exists()) return null
        return if (marked) markedCopy(out, PREVIEW_EDGE, "m${member.id}_${out.nameWithoutExtension}.jpg", member)?.let { fileServed(it, "b.jpg") } else fileServed(out, "b.jpg")
    }

    /** A smaller JPG of the photo as it is (grid and big view), made once. */
    private suspend fun small(id: Long, edge: Int, kind: String, eventId: Long): File? {
        val out = File(dirOf(eventId), "${id}_$kind.jpg")
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

    /** Deletes what was prepared for [eventId]; it is made again if the event opens and the media asks. */
    suspend fun clearFiles(eventId: Long) = withContext(Dispatchers.IO) {
        File(root, eventId.toString()).deleteRecursively()
        AppLog.i(ChurchServer.TAG, "Arquivos preparados do evento $eventId apagados")
    }

    /** Bytes prepared for [eventId] (to show how much a cleanup frees). */
    suspend fun filesSize(eventId: Long): Long = withContext(Dispatchers.IO) {
        File(root, eventId.toString()).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Files of events that no longer exist, and the loose files of 5.4 (before the folder per event). */
    internal suspend fun clearLeftovers() = withContext(Dispatchers.IO) {
        val events = repo.dao.observeEvents().first().map { it.id.toString() }.toSet()
        root.listFiles()?.forEach { f -> if (f.isFile || f.name !in events) f.deleteRecursively() }
    }

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
