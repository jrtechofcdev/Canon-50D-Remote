package com.example.core.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import com.example.core.data.SettingsRepository
import com.example.core.log.AppLog
import com.example.core.preset.DngPresetParser
import com.example.core.preset.Preset
import com.example.core.preset.PresetRepository
import com.example.core.preset.XmpPresetParser
import com.example.core.preset.XmpPresetWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Bytes fetched from the web, starting at [start] of the file (0 unless a range was asked). */
class Fetched(val start: Long, val bytes: ByteArray)

/** The network, kept apart so tests can serve files without it. */
fun interface ShopFetcher {
    /** Downloads [url], or only [range] of it when given (the server may send the whole file instead). */
    fun get(url: String, range: LongRange?): Fetched
}

/** Where the catalogue stands, for the Loja header. */
sealed interface ShopStatus {
    data object Idle : ShopStatus
    data object Loading : ShopStatus
    /** Showing the catalogue saved on the phone because the API couldn't be reached. */
    data class Offline(val message: String, val savedAt: Long) : ShopStatus
    data class Failed(val message: String) : ShopStatus
}

/** What a download to the presets folder wrote. */
data class ShopDownload(val folder: String, val dng: String?, val xmp: String)

/**
 * The preset shop: the catalogue from the photographer's API, the look of each preset (the preview
 * JPEG inside its DNG), and saving it into Meus presets, after which it works offline everywhere.
 *
 * Only what is needed travels: the catalogue once, then per preset the first 64 KB of its DNG (to find
 * the preview and the settings) and the preview itself. The full DNG is fetched only for Baixar.
 */
class PresetShop(
    private val context: Context,
    private val settings: SettingsRepository,
    private val presets: PresetRepository,
    /** Replaceable so screenshots and tests run without the internet. */
    internal var fetcher: ShopFetcher = HttpShopFetcher(),
) {
    private val keep = File(context.filesDir, "shop")
    private val cache = File(context.cacheDir, "shop")
    private val catalogFile = File(keep, "catalog.json")
    private val network = Semaphore(6)
    // Fetching ahead of the scroll uses two of those at most, so what is on screen always comes first.
    private val ahead = Semaphore(2)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO + AppLog.handler("LOJA"))
    private var prefetchJob: Job? = null

    private val _items = MutableStateFlow<List<ShopItem>>(emptyList())
    val items: StateFlow<List<ShopItem>> = _items.asStateFlow()

    private val _status = MutableStateFlow<ShopStatus>(ShopStatus.Idle)
    val status: StateFlow<ShopStatus> = _status.asStateFlow()

    /** Sources ("loja:1085") of the presets already in Meus presets. */
    val saved: Flow<Map<String, Long>> = presets.presets.map { list ->
        list.filter { it.source.startsWith(ShopItem.SOURCE_PREFIX) }.associate { it.source to it.id }
    }

    // Decoded pictures: an eighth of the app's memory (up to 64 MB), so scrolling back is instant.
    // Grid thumbnails are RGB_565, half the memory of full colour, with no visible loss at that size.
    private val bitmaps = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun trim() = bitmaps.evictAll()

    /** The grid's picture of [item], if it is in memory: the full screen shows it at once while the big one loads. */
    fun cachedPreview(item: ShopItem): Bitmap? = bitmaps.get("${item.id}@$GRID_EDGE")

    /** The catalogue saved on the phone, shown at once and whenever the API is out of reach. */
    suspend fun loadSaved() = withContext(Dispatchers.IO) {
        if (_items.value.isNotEmpty() || !catalogFile.exists()) return@withContext
        runCatching { _items.value = ShopCatalog.parse(catalogFile.readText()) }
    }

    private var fetchedAt = 0L

    /** Refreshes when the list is empty or older than half an hour, so opening the tab stays instant. */
    suspend fun refreshIfStale() {
        if (_items.value.isEmpty() || System.currentTimeMillis() - fetchedAt > 30 * 60_000L) refresh()
    }

    /** Asks the API for the current list. Offline, the saved list stays on screen. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        loadSaved()
        val url = settings.current().shopUrl
        _status.value = ShopStatus.Loading
        runCatching {
            val body = network.withPermit { fetcher.get(url, null) }.bytes.toString(Charsets.UTF_8)
            val list = ShopCatalog.parse(body)
            if (list.isEmpty()) error("A API respondeu sem nenhum preset DNG ou XMP")
            keep.mkdirs()
            catalogFile.writeText(body)
            list
        }.onSuccess {
            _items.value = it
            _status.value = ShopStatus.Idle
            fetchedAt = System.currentTimeMillis()
            AppLog.i("LOJA", "Catálogo: ${it.size} presets de $url")
        }.onFailure { e ->
            AppLog.w("LOJA", "Catálogo não carregou: ${e.message}")
            _status.value = if (_items.value.isNotEmpty()) {
                ShopStatus.Offline(reason(e), catalogFile.lastModified())
            } else {
                ShopStatus.Failed(reason(e))
            }
        }
    }

    /** The finished look of [item] (the DNG's preview), sized for [maxEdge]. Null when it can't be read. */
    suspend fun preview(item: ShopItem, maxEdge: Int = GRID_EDGE): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${item.id}@$maxEdge"
        bitmaps.get(key)?.let { return@withContext it }
        val jpeg = runCatching { previewJpeg(item) }.getOrNull() ?: return@withContext null
        decode(jpeg, maxEdge, small = maxEdge <= GRID_EDGE)?.also { bitmaps.put(key, it) }
    }

    /**
     * Downloads the previews of [items] (the ones just below the screen) before they scroll in, so
     * they appear already loaded. A new call replaces the previous one.
     */
    fun prefetch(items: List<ShopItem>) {
        prefetchJob?.cancel()
        if (items.isEmpty()) return
        prefetchJob = background.launch {
            for (item in items) {
                if (!isActive) break
                if (previewFile(item).exists()) continue
                ahead.withPermit { runCatching { previewJpeg(item) } }
            }
        }
    }

    /**
     * The photo inside the DNG before the preset, for the comparison. [full] false gives the small copy
     * the file keeps (about 500 px, a few dozen KB, fast); true the largest one worth fetching.
     */
    suspend fun original(item: ShopItem, full: Boolean, maxEdge: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        if (!item.isDng) return@withContext null
        val key = "${item.id}@orig${if (full) "F" else "Q"}"
        bitmaps.get(key)?.let { return@withContext it }
        val file = File(cache, "orig/${item.id}_${if (full) "f" else "q"}.jpg")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { bitmaps.put(key, it); return@withContext it }
        }
        val bmp = runCatching {
            val head = head(item).bytes
            val layout = DngLayout.read(head)
            val choices = layout.originals.filter { it.span in 1..MAX_ORIGINAL_BYTES }
            val image = if (full) choices.lastOrNull() else (choices.firstOrNull { it.maxEdge >= 400 } ?: choices.lastOrNull())
            image ?: return@runCatching null
            val got = network.withPermit { fetcher.get(item.url, image.start until image.end) }
            val bytes = if (got.start == image.start) got.bytes else got.bytes.copyOfRange((image.start - got.start).toInt(), (image.end - got.start).toInt())
            DngOriginal.render(image, layout, bytes, maxEdge)
        }.onFailure { AppLog.w("LOJA", "Original de ${item.name} não abriu: ${it.message}") }.getOrNull() ?: return@withContext null
        file.parentFile?.mkdirs()
        runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
        bitmaps.put(key, bmp)
        bmp
    }

    /** Saves several at once; [onProgress] gets how many are done. Returns how many were saved. */
    suspend fun saveAll(items: List<ShopItem>, onProgress: (Int) -> Unit = {}): Int {
        var ok = 0
        items.forEachIndexed { i, item ->
            if (runCatching { save(item) }.isSuccess) ok++
            onProgress(i + 1)
        }
        return ok
    }

    /** Downloads several at once into the presets folder. Returns how many made it. */
    suspend fun downloadAll(items: List<ShopItem>, onProgress: (Int) -> Unit = {}): Int {
        var ok = 0
        items.forEachIndexed { i, item ->
            if (runCatching { download(item) }.isSuccess) ok++
            onProgress(i + 1)
        }
        return ok
    }

    /**
     * The DNG in the app's share folder, for Lightroom: opening it there brings the photo with the
     * preset, and in Lightroom "Criar predefinição" saves the preset in its own list.
     */
    suspend fun fileForLightroom(item: ShopItem): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        if (item.isDng) {
            File(dir, "${item.baseName}.dng").apply {
                if (!exists() || length() == 0L) writeBytes(network.withPermit { fetcher.get(item.url, null) }.bytes)
            }
        } else {
            File(dir, "${item.baseName}.xmp").apply { writeText(XmpPresetWriter.write(preset(item))) }
        }
    }

    /** The preset's settings, read from the XMP inside the DNG (or the .xmp itself). Kept for offline use. */
    suspend fun preset(item: ShopItem): Preset = withContext(Dispatchers.IO) {
        val xmp = xmpText(item)
        XmpPresetParser.parse(xmp, item.name).copy(name = item.name, group = item.categoryLabel, source = item.source)
    }

    /** Puts [item] in Meus presets (once) and returns its id there. */
    suspend fun save(item: ShopItem): Long {
        presets.all().firstOrNull { it.source == item.source }?.let { return it.id }
        val id = presets.save(preset(item))
        AppLog.i("LOJA", "Salvo em Meus presets: ${item.name} (${item.categoryLabel})")
        return id
    }

    /**
     * Writes the DNG and an .xmp of [item] into the presets folder:
     * `JR_LR_Presets/JR_LR_Presets_DNG/…dng` and `JR_LR_Presets/JR_LR_Presets_XMP/…xmp`.
     */
    suspend fun download(item: ShopItem): ShopDownload = withContext(Dispatchers.IO) {
        val treeUri = settings.current().presetsFolder ?: throw ShopException("Escolha a pasta de presets primeiro")
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.takeIf { it.canWrite() }
            ?: throw ShopException("Sem permissão na pasta de presets. Escolha a pasta de novo.")
        val root = tree.dir(ROOT)
        val preset = preset(item)
        var dngName: String? = null
        if (item.isDng) {
            val bytes = network.withPermit { fetcher.get(item.url, null) }.bytes
            dngName = "${item.baseName}.dng"
            root.dir(DNG_DIR).write(dngName, bytes)
        }
        val xmpName = "${item.baseName}.xmp"
        root.dir(XMP_DIR).write(xmpName, XmpPresetWriter.write(preset).toByteArray())
        AppLog.i("LOJA", "Baixado: ${item.fileName} → $ROOT")
        ShopDownload("$ROOT/", dngName?.let { "$DNG_DIR/$it" }, "$XMP_DIR/$xmpName")
    }

    /** Whether the settings of [item] are already on the phone (so it can be used without internet). */
    fun hasOffline(item: ShopItem): Boolean = xmpFile(item).exists()

    // ---- files ---------------------------------------------------------------------------------

    private fun previewFile(item: ShopItem) = File(cache, "prev/${item.id}.jpg")
    private fun headFile(item: ShopItem) = File(cache, "head/${item.id}.bin")
    private fun xmpFile(item: ShopItem) = File(keep, "xmp/${item.id}.xmp")

    /**
     * The start of the DNG. One request of 128 KB already holds the preview of most presets, so the
     * picture comes in a single round trip. Only the first 48 KB (the file's index and settings) stay
     * on the phone; the preview is kept as its own file.
     */
    private suspend fun head(item: ShopItem): Fetched {
        val file = headFile(item)
        if (file.exists()) return Fetched(0, file.readBytes())
        val got = network.withPermit { fetcher.get(item.url, 0L until HEAD_BYTES) }
        // Some servers ignore the range and send the whole file.
        val bytes = if (got.bytes.size > HEAD_BYTES) got.bytes.copyOf(HEAD_BYTES.toInt()) else got.bytes
        file.parentFile?.mkdirs()
        file.writeBytes(if (bytes.size > KEEP_HEAD_BYTES) bytes.copyOf(KEEP_HEAD_BYTES) else bytes)
        return Fetched(0, bytes)
    }

    private suspend fun previewJpeg(item: ShopItem): ByteArray? {
        val file = previewFile(item)
        if (file.exists()) return file.readBytes()
        if (!item.isDng) return null
        val head = head(item).bytes
        val layout = DngLayout.read(head)
        // Largest preview that is a normal JPEG; the 1024 px one is plenty for the screen.
        for (seg in layout.previews) {
            val bytes = if (seg.end <= head.size) {
                head.copyOfRange(seg.offset.toInt(), seg.end.toInt())
            } else {
                val got = network.withPermit { fetcher.get(item.url, seg.offset until seg.end) }
                slice(got, seg) ?: continue
            }
            if (DngLayout.isViewableJpeg(bytes)) {
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                return bytes
            }
        }
        return null
    }

    private suspend fun xmpText(item: ShopItem): String {
        val file = xmpFile(item)
        if (file.exists()) return file.readText()
        val text = if (item.isDng) {
            val head = head(item).bytes
            val seg = DngLayout.read(head).xmp
            when {
                seg != null && seg.end <= head.size -> String(head, seg.offset.toInt(), seg.length, Charsets.UTF_8)
                seg != null -> network.withPermit { fetcher.get(item.url, seg.offset until seg.end) }.let { got ->
                    slice(got, seg)?.toString(Charsets.UTF_8) ?: DngPresetParser.extractXmp(got.bytes)
                }
                // Unusual layout: read the whole file with the regular importer.
                else -> DngPresetParser.extractXmp(network.withPermit { fetcher.get(item.url, null) }.bytes)
            }
        } else {
            network.withPermit { fetcher.get(item.url, null) }.bytes.toString(Charsets.UTF_8)
        }
        if (!text.contains("crs:")) throw ShopException("Este preset não tem ajustes do Lightroom")
        file.parentFile?.mkdirs()
        file.writeText(text)
        return text
    }

    private fun slice(got: Fetched, seg: DngLayout.Segment): ByteArray? {
        val from = seg.offset - got.start
        val to = from + seg.length
        if (from < 0 || to > got.bytes.size) return null
        return got.bytes.copyOfRange(from.toInt(), to.toInt())
    }

    private fun decode(jpeg: ByteArray, maxEdge: Int, small: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            if (small) inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    private fun DocumentFile.dir(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory } ?: createDirectory(name) ?: throw ShopException("Não deu para criar a pasta $name")

    private fun DocumentFile.write(name: String, bytes: ByteArray) {
        val file = findFile(name) ?: createFile("application/octet-stream", name) ?: throw ShopException("Não deu para criar $name")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } ?: throw ShopException("Não deu para gravar $name")
    }

    private fun reason(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Sem internet"
        is java.net.SocketTimeoutException -> "A API demorou para responder"
        is ShopException -> e.message ?: "Erro"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        const val ROOT = "JR_LR_Presets"
        const val DNG_DIR = "JR_LR_Presets_DNG"
        const val XMP_DIR = "JR_LR_Presets_XMP"
        const val HEAD_BYTES = 128L * 1024
        const val KEEP_HEAD_BYTES = 48 * 1024
        const val GRID_EDGE = 480
        /** Bigger originals (a few presets carry 5000 px photos) are skipped for the comparison. */
        const val MAX_ORIGINAL_BYTES = 8L * 1024 * 1024
    }
}

class ShopException(message: String) : Exception(message)

/** Plain HttpURLConnection: no extra library, and Range requests for the parts of a DNG. */
class HttpShopFetcher : ShopFetcher {
    override fun get(url: String, range: LongRange?): Fetched {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "JR-C50D-Sync (Android)")
            conn.setRequestProperty("Accept", "*/*")
            if (range != null) conn.setRequestProperty("Range", "bytes=${range.first}-${range.last}")
            val code = conn.responseCode
            if (code !in 200..299) throw ShopException("A API respondeu erro $code")
            val bytes = conn.inputStream.use { it.readBytes() }
            val start = if (code == 206 && range != null) range.first else 0L
            return Fetched(start, bytes)
        } finally {
            conn.disconnect()
        }
    }
}
