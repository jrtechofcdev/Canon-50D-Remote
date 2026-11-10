package com.example.core.delivery

import com.example.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the portal shows besides the photos. [express] = Fotografia Express is on (its gallery opens
 * without a code); [expressPreview] = its unreleased photos are shown only as marked previews.
 */
data class PortalInfo(
    val photographer: String,
    val picks: Boolean,
    val express: Boolean = false,
    val expressPreview: Boolean = true,
    /** Price of one photo, already formatted ("R$ 15,00"); null = not shown. */
    val price: String? = null,
    /** Pix key for payment; null = not shown. */
    val pix: String? = null,
)

/** What the Express page shows when the client taps Pagar. */
data class PaymentInfo(
    val total: String?,
    val detail: String?,
    val pix: String?,
    val methods: List<String>,
    val note: String,
    val table: String?,
)

/** A prepared photo's marked preview (Fotografia Express), next to the file itself. */
fun previewFileOf(path: String): File = File(path.removeSuffix(".jpg") + ".preview.jpg")

/**
 * The small web server of the local delivery. It runs only while the delivery is on and answers just:
 * the portal files, a client's own list of photos (by token), each prepared file (with Range, so a
 * download cut in the middle continues) and the client's hearts. Nothing else on the phone is reachable:
 * no folder listing, no path from the request ever touches the disk.
 */
class LocalServer(
    private val dao: DeliveryDao,
    private val portal: (String) -> InputStream?,
    /** Small JPG for the grid; with locked = true it must be made from the marked preview. */
    private val thumb: (DeliveryItem, Boolean) -> File?,
    private val info: () -> PortalInfo,
    private val onDownloaded: suspend (DeliveryItem) -> Unit = {},
    private val onPick: suspend (DeliveryItem, Boolean, DeliverySession) -> Unit = { _, _, _ -> },
    /**
     * Which tool this server belongs to. An Express server only knows Express clients (no code, no
     * login) and a Entrega local server only its own: a token from one is unknown to the other.
     */
    private val expressServer: Boolean = false,
    /** Payment for [count] hearted photos out of [available] (Express); null = nothing to show. */
    private val payment: (count: Int, available: Int) -> PaymentInfo? = { _, _ -> null },
    /** A PNG QR code of a text (the Pix code). */
    private val qrPng: (String) -> ByteArray? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var socket: ServerSocket? = null
    private var pool: ThreadPoolExecutor? = null
    private val limiter = AttemptLimiter(clock = clock)
    /** Connections being answered, so Parar can cut them, and how many each device has open. */
    private val open = ConcurrentHashMap.newKeySet<Socket>()
    private val perDevice = ConcurrentHashMap<String, AtomicInteger>()
    /**
     * Fotografia Express: the devices that joined each client's page (a key kept in a cookie). Only
     * they can heart photos or say the name, so a phone left on the Wi-Fi can't change someone else's order.
     */
    private val expressKeys = ConcurrentHashMap<Long, MutableSet<String>>()
    private val seen = ConcurrentHashMap<String, Long>()
    private val _online = MutableStateFlow(0)

    /** Devices that asked the portal for something in the last 2 minutes. */
    val online: StateFlow<Int> = _online.asStateFlow()

    /** Developer test: a page with this photo at /teste. */
    @Volatile var testFile: File? = null

    val port: Int get() = socket?.localPort ?: 0
    val running: Boolean get() = socket?.isClosed == false

    /**
     * Starts on [port] (0 = any free one, for tests), trying the next few if it is taken. Returns the port.
     * With [host] it listens only on the delivery network's address, so the phone's other networks (the
     * venue's Wi-Fi, mobile data) never reach it; if that address can't be used it falls back to all.
     */
    @Synchronized
    fun start(port: Int = DeliveryRules.PORT, host: String? = null): Int {
        socket?.takeIf { !it.isClosed }?.let { return it.localPort }
        fun bindOn(address: String?) = (0..5).firstNotNullOfOrNull { offset ->
            val p = if (port == 0) 0 else port + offset
            runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(if (address == null) InetSocketAddress(p) else InetSocketAddress(address, p), 32)
                }
            }.getOrNull()
        }
        val server = host?.let { h -> bindOn(h) ?: null.also { AppLog.w("ENTREGA", "Não deu para ouvir só em $h; ouvindo em todas as redes") } }
            ?: bindOn(null)
            ?: throw java.io.IOException("Nenhuma porta livre a partir de $port")
        socket = server
        // A fixed team with a short line: a flood of connections is turned away instead of piling up.
        val workers = ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE),
            { r -> Thread(r, "entrega-http").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
        pool = workers
        Thread({
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    // Out of file handles or similar: wait a moment instead of spinning.
                    AppLog.w("ENTREGA", "Falha ao aceitar conexão", e)
                    runCatching { Thread.sleep(200) }
                    continue
                }
                val ip = client.inetAddress?.hostAddress.orEmpty()
                val count = perDevice.getOrPut(ip) { AtomicInteger() }
                if (count.incrementAndGet() > PER_DEVICE) {
                    count.decrementAndGet()
                    runCatching { client.close() }
                    continue
                }
                open += client
                runCatching { workers.execute { try { handle(client) } finally { done(client, ip) } } }
                    .onFailure { done(client, ip) }
            }
        }, "entrega-accept").apply { isDaemon = true }.start()
        AppLog.i("ENTREGA", "Servidor ligado na porta ${server.localPort}" + (host?.let { " ($it)" } ?: ""))
        return server.localPort
    }

    private fun done(client: Socket, ip: String) {
        runCatching { client.close() }
        open -= client
        perDevice[ip]?.decrementAndGet()
    }

    @Synchronized
    fun stop() {
        socket?.let { runCatching { it.close() }; AppLog.i("ENTREGA", "Servidor desligado") }
        socket = null
        pool?.shutdownNow()
        pool = null
        // Downloads in the middle stop too: Parar means nothing leaves the phone any more.
        open.forEach { runCatching { it.close() } }
        open.clear()
        perDevice.clear()
        expressKeys.clear()
        seen.clear()
        _online.value = 0
    }

    // ---- one request -------------------------------------------------------------------------

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val ip: String) {
        fun cookie(name: String): String? = headers["cookie"]?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
    }

    private fun handle(client: Socket) = client.use { s ->
        s.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        val ip = s.inetAddress?.hostAddress.orEmpty()
        // The whole request head has to arrive in time: a device sending one byte at a time can't hold a worker.
        val deadline = System.nanoTime() + HEAD_DEADLINE_MS * 1_000_000L
        val request = runCatching { read(input, ip, deadline) }.getOrNull()
        if (request == null) {
            runCatching { text(out, 400, "Pedido inválido") }
            return@use
        }
        markSeen(ip)
        runCatching { route(request, out) }.onFailure { e ->
            if (e !is SocketException) {
                AppLog.w("ENTREGA", "Erro em ${request.method} ${safe(request.path)}", e)
                runCatching { text(out, 500, "Erro no celular do fotógrafo") }
            }
        }
    }

    private fun read(input: InputStream, ip: String, deadline: Long): Request? {
        val line = readLine(input, deadline) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val h = readLine(input, deadline) ?: break
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
            if (headers.size > 60) return null
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val query = if (q < 0) emptyMap() else target.substring(q + 1).split('&').filter { it.isNotEmpty() }.associate {
            val eq = it.indexOf('=')
            if (eq < 0) decode(it) to "" else decode(it.substring(0, eq)) to decode(it.substring(eq + 1))
        }
        return Request(parts[0].uppercase(), path, query, headers, ip)
    }

    private fun readLine(input: InputStream, deadline: Long): String? {
        val sb = StringBuilder()
        while (true) {
            if (System.nanoTime() > deadline) return null
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun route(r: Request, out: OutputStream) {
        val head = r.method == "HEAD"
        if (r.method != "GET" && r.method != "HEAD" && r.method != "POST") return text(out, 405, "Método não aceito")
        val seg = r.path.trim('/').split('/').filter { it.isNotEmpty() }
        when {
            seg.isEmpty() || seg == listOf("index.html") -> asset(out, "index.html", head)
            seg.size == 1 && seg[0] in ASSETS -> asset(out, seg[0], head)
            seg == listOf("api", "ping") -> json(out, 200, """{"ok":true}""")
            seg == listOf("api", "login") && !expressServer -> login(r, out)
            seg == listOf("api", "express") && expressServer -> express(r, out)
            seg.size == 3 && seg[0] == "api" && seg[1] == "s" -> sessionJson(seg[2], out)
            seg.size == 5 && seg[0] == "api" && seg[1] == "s" && seg[3] == "pick" && r.method == "POST" -> pick(seg[2], seg[4], r, out)
            seg.size == 4 && seg[0] == "api" && seg[1] == "s" && seg[3] == "name" && r.method == "POST" -> name(seg[2], r, out)
            seg.size == 4 && seg[0] == "api" && seg[1] == "s" && seg[3] == "pay" && expressServer -> pay(seg[2], out)
            seg.size == 4 && seg[0] == "api" && seg[1] == "s" && seg[3] == "pix.png" && expressServer -> pixPng(seg[2], out, head)
            seg.size == 3 && seg[0] == "f" -> file(seg[1], seg[2], r, out, head)
            seg.size == 3 && seg[0] == "t" -> thumbnail(seg[1], seg[2], out, head)
            seg.size == 3 && seg[0] == "p" -> view(seg[1], seg[2], out, head)
            seg == listOf("teste") && testFile != null -> html(out, TEST_PAGE)
            seg == listOf("teste.jpg") && testFile != null -> serve(out, testFile!!, r.headers["range"], head, "teste.jpg", r.query["dl"] == "1") {}
            else -> text(out, 404, "Não encontrado")
        }
    }

    private fun login(r: Request, out: OutputStream) {
        if (limiter.blocked(r.ip)) return json(out, 429, """{"error":"Muitas tentativas. Espere 1 minuto."}""")
        val code = r.query["code"].orEmpty().filter(Char::isDigit)
        val session = // 6 digits too: codes handed out by 4.0 keep working until they expire.
        code.takeIf { it.length == DeliveryRules.CODE_LENGTH || it.length == 6 }?.let { runBlocking { dao.sessionByCode(it) } }?.takeIf { it.isValid(clock()) }
        if (session == null) {
            limiter.failed(r.ip)
            AppLog.i("ENTREGA", "Código errado de ${r.ip}")
            return json(out, 401, """{"error":"Código não encontrado. Confira com o fotógrafo."}""")
        }
        json(out, 200, """{"token":"${session.token}"}""")
    }

    /**
     * Fotografia Express: the open gallery, no code. Only while the module is on. The device gets a key
     * (cookie) for this client's page; a device that already has one keeps it.
     */
    private fun express(r: Request, out: OutputStream) {
        if (!info().express) return json(out, 404, """{"error":"Sem entrega express agora."}""")
        val session = runBlocking { dao.openExpress() }?.takeIf { it.isValid(clock()) }
            ?: return json(out, 404, """{"error":"Sem entrega express agora."}""")
        val keys = expressKeys.getOrPut(session.id) { ConcurrentHashMap.newKeySet() }
        val key = r.cookie(DEVICE_COOKIE)?.takeIf { it in keys } ?: DeliveryRules.newToken().also { new ->
            // Old keys go first if a crowd joined, so the list stays small.
            if (keys.size >= 30) keys.remove(keys.first())
            keys += new
        }
        bytes(
            out, 200, "application/json; charset=utf-8", """{"token":"${session.token}"}""",
            listOf("Set-Cookie" to "$DEVICE_COOKIE=$key; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400"),
        )
    }

    /** An Express client's page changes only from a device that joined it; the Entrega local has its own code. */
    private fun joined(session: DeliverySession, r: Request): Boolean =
        !session.express || r.cookie(DEVICE_COOKIE)?.let { it in expressKeys[session.id].orEmpty() } == true

    /** An Express photo not released yet while the preview mode is on: only its marked preview leaves the phone. */
    private fun locked(session: DeliverySession, item: DeliveryItem): Boolean =
        session.express && info().expressPreview && !item.released

    private fun validSession(token: String): DeliverySession? {
        if (!TOKEN.matches(token)) return null
        return runBlocking { dao.sessionByToken(token, expressServer) }?.takeIf { it.isValid(clock()) }
    }

    private fun sessionJson(token: String, out: OutputStream) {
        val session = validSession(token) ?: return json(out, 404, """{"error":"Acesso encerrado ou inválido."}""")
        val items = runBlocking {
            dao.touch(session.id, clock())
            dao.items(session.id)
        }
        val ready = items.filter { it.state in DeliveryState.served && it.path?.let { p -> File(p).exists() } == true }
        val waiting = items.count { it.state == DeliveryState.WAITING || it.state == DeliveryState.PREPARING }
        val portalInfo = info()
        val list = ready.joinToString(",") {
            """{"id":${it.id},"name":${quote(it.name)},"size":${it.size},"picked":${it.picked},"locked":${locked(session, it)},"version":${if (it.variant > 0) it.variant + 1 else 0},"label":${it.label?.let(::quote) ?: "null"}}"""
        }
        json(
            out, 200,
            """{"client":${quote(session.client)},"event":${session.event},"express":${session.express},""" +
                """"preview":${session.express && portalInfo.expressPreview},"photographer":${quote(portalInfo.photographer)},""" +
                """"picks":${portalInfo.picks},"waiting":$waiting,""" +
                """"price":${portalInfo.price?.let(::quote) ?: "null"},"pix":${portalInfo.pix?.let(::quote) ?: "null"},"items":[$list]}""",
        )
    }

    private fun itemOf(token: String, id: String): Pair<DeliverySession, DeliveryItem>? {
        val session = validSession(token) ?: return null
        val item = id.toLongOrNull()?.let { runBlocking { dao.item(it) } } ?: return null
        return item.takeIf { it.sessionId == session.id && it.state in DeliveryState.served && it.path != null }?.let { session to it }
    }

    private fun file(token: String, id: String, r: Request, out: OutputStream, head: Boolean) {
        val (session, item) = itemOf(token, id) ?: return text(out, 404, "Foto não encontrada")
        if (locked(session, item)) return json(out, 403, """{"error":"Esta foto é uma prévia. O fotógrafo libera depois do pagamento."}""")
        val f = File(item.path!!)
        if (!f.exists()) return text(out, 404, "Foto não encontrada")
        serve(out, f, r.headers["range"], head, item.name, r.query["dl"] == "1") {
            runBlocking { dao.countDownload(item.id); onDownloaded(item) }
            AppLog.i("ENTREGA", "${item.name} baixada por ${r.ip}")
        }
    }

    private fun thumbnail(token: String, id: String, out: OutputStream, head: Boolean) {
        val (session, item) = itemOf(token, id) ?: return text(out, 404, "Foto não encontrada")
        val lock = locked(session, item)
        // A locked photo never falls back to the clean file: no preview yet means no picture.
        val f = runCatching { thumb(item, lock) }.getOrNull() ?: if (lock) previewFileOf(item.path!!) else File(item.path!!)
        if (!f.exists()) return text(out, 404, "Prévia ainda não está pronta")
        serve(out, f, null, head, item.name, false) {}
    }

    /** The big picture in the portal: the clean file, or only the marked preview while the photo is locked. */
    private fun view(token: String, id: String, out: OutputStream, head: Boolean) {
        val (session, item) = itemOf(token, id) ?: return text(out, 404, "Foto não encontrada")
        val f = if (locked(session, item)) previewFileOf(item.path!!) else File(item.path!!)
        if (!f.exists()) return text(out, 404, "Prévia ainda não está pronta")
        serve(out, f, null, head, item.name, false) {}
    }

    private fun pick(token: String, id: String, r: Request, out: OutputStream) {
        if (!info().picks) return json(out, 403, """{"error":"Favoritas desligadas pelo fotógrafo."}""")
        val (session, item) = itemOf(token, id) ?: return json(out, 404, """{"error":"Foto não encontrada"}""")
        if (!joined(session, r)) return json(out, 403, """{"error":"Abra a página de novo pelo QR.","rekey":true}""")
        val on = r.query["on"] != "0"
        runBlocking {
            dao.setPicked(item.id, on)
            onPick(item, on, session)
        }
        json(out, 200, """{"picked":$on}""")
    }

    private fun quoteFor(token: String): PaymentInfo? {
        val session = validSession(token) ?: return null
        val items = runBlocking { dao.items(session.id) }.filter { it.state in DeliveryState.served }
        return payment(items.count { it.picked && !it.released }, items.size)
    }

    /** What to pay for the hearted photos not released yet, and how. */
    private fun pay(token: String, out: OutputStream) {
        validSession(token) ?: return json(out, 404, """{"error":"Acesso encerrado"}""")
        val p = quoteFor(token) ?: return json(out, 200, """{"methods":[]}""")
        json(
            out, 200,
            """{"total":${p.total?.let(::quote) ?: "null"},"detail":${p.detail?.let(::quote) ?: "null"},""" +
                """"pix":${p.pix?.let(::quote) ?: "null"},"methods":[${p.methods.joinToString(",") { quote(it) }}],""" +
                """"note":${quote(p.note)},"table":${p.table?.let(::quote) ?: "null"}}""",
        )
    }

    private fun pixPng(token: String, out: OutputStream, head: Boolean) {
        val pix = quoteFor(token)?.pix ?: return text(out, 404, "Sem Pix")
        val png = qrPng(pix) ?: return text(out, 404, "Sem Pix")
        writeHead(out, 200, listOf("Content-Type" to "image/png", "Content-Length" to png.size.toString(), "Cache-Control" to "no-store"))
        if (!head) out.write(png)
        out.flush()
    }

    /**
     * Fotografia Express: the client says their name when they arrive. Only for Express (a Entrega local
     * client was named by the photographer) and only while it is still the default "Express".
     */
    private fun name(token: String, r: Request, out: OutputStream) {
        val session = validSession(token) ?: return json(out, 404, """{"error":"Acesso encerrado"}""")
        if (!session.express) return json(out, 403, """{"error":"O nome já foi cadastrado pelo fotógrafo"}""")
        if (!joined(session, r)) return json(out, 403, """{"error":"Abra a página de novo pelo QR.","rekey":true}""")
        val typed = cleanName(r.query["n"].orEmpty()) ?: return json(out, 400, """{"error":"Digite seu nome"}""")
        if (session.client == EXPRESS_DEFAULT || session.client.isBlank()) {
            runBlocking { dao.updateSession(session.copy(client = typed)) }
            AppLog.i("EXPRESS", "Cliente se apresentou: $typed")
        }
        json(out, 200, """{"client":${quote(if (session.client == EXPRESS_DEFAULT) typed else session.client)}}""")
    }

    // ---- responses ---------------------------------------------------------------------------

    /**
     * Sends [file], or the part asked in [range] ("bytes=a-b", "bytes=a-", "bytes=-n"). [whole] runs when
     * a download (dl=1) reached the last byte, so a photo counts as delivered once it really arrived.
     */
    private fun serve(out: OutputStream, file: File, range: String?, head: Boolean, name: String, download: Boolean, whole: () -> Unit) {
        val length = file.length()
        var start = 0L
        var end = length - 1
        var partial = false
        if (range != null) {
            val m = Regex("""bytes=(\d*)-(\d*)""").matchEntire(range.trim())
            if (m == null || length == 0L) return rangeError(out, length)
            val (a, b) = m.destructured
            when {
                a.isEmpty() && b.isEmpty() -> return rangeError(out, length)
                a.isEmpty() -> start = (length - b.toLong()).coerceAtLeast(0)
                else -> {
                    start = a.toLong()
                    if (b.isNotEmpty()) end = minOf(b.toLong(), length - 1)
                }
            }
            if (start >= length || start > end) return rangeError(out, length)
            partial = true
        }
        val count = end - start + 1
        val headers = buildList {
            add("Content-Type" to contentType(name))
            add("Content-Length" to count.toString())
            add("Accept-Ranges" to "bytes")
            add("Cache-Control" to "private, max-age=3600")
            if (partial) add("Content-Range" to "bytes $start-$end/$length")
            if (download) add("Content-Disposition" to "attachment; filename=\"${name.replace("\"", "")}\"")
        }
        writeHead(out, if (partial) 206 else 200, headers)
        if (head) return out.flush()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
        out.flush()
        if (download && end == length - 1) whole()
    }

    private fun rangeError(out: OutputStream, length: Long) {
        writeHead(out, 416, listOf("Content-Range" to "bytes */$length", "Content-Length" to "0"))
        out.flush()
    }

    private fun asset(out: OutputStream, name: String, head: Boolean) {
        val bytes = portal(name)?.use { it.readBytes() } ?: return text(out, 404, "Portal não encontrado")
        writeHead(out, 200, listOf("Content-Type" to contentType(name), "Content-Length" to bytes.size.toString(), "Cache-Control" to "no-cache"))
        if (!head) out.write(bytes)
        out.flush()
    }

    private fun json(out: OutputStream, status: Int, body: String) = bytes(out, status, "application/json; charset=utf-8", body)

    private fun html(out: OutputStream, body: String) = bytes(out, 200, "text/html; charset=utf-8", body)

    private fun text(out: OutputStream, status: Int, body: String) = bytes(out, status, "text/plain; charset=utf-8", body)

    private fun bytes(out: OutputStream, status: Int, type: String, body: String, extra: List<Pair<String, String>> = emptyList()) {
        val data = body.toByteArray()
        writeHead(out, status, listOf("Content-Type" to type, "Content-Length" to data.size.toString(), "Cache-Control" to "no-store") + extra)
        out.write(data)
        out.flush()
    }

    private fun writeHead(out: OutputStream, status: Int, headers: List<Pair<String, String>>) {
        val sb = StringBuilder("HTTP/1.1 $status ${REASONS[status] ?: "OK"}\r\n")
        (headers + COMMON).forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
    }

    private fun markSeen(ip: String) {
        val now = clock()
        seen[ip] = now
        seen.entries.removeIf { now - it.value > DeliveryRules.ONLINE_MS }
        _online.value = seen.size
    }

    /** Tokens never go to the log. */
    private fun safe(path: String) = path.replace(Regex("[0-9a-f]{32}"), "…")

    companion object {
        private const val WORKERS = 6
        /** Connections waiting for a worker; more than this are turned away. */
        private const val QUEUE = 48
        /** Open connections per device: a browser uses up to 6 at once, so 8 never gets in a client's way. */
        private const val PER_DEVICE = 8
        private const val READ_TIMEOUT_MS = 10_000
        private const val HEAD_DEADLINE_MS = 10_000L
        private const val DEVICE_COOKIE = "jrx"
        private val TOKEN = Regex("[0-9a-f]{32}")
        const val EXPRESS_DEFAULT = "Express"

        /** A typed name, tidy: no control characters, single spaces, 2 to 40 characters. */
        fun cleanName(typed: String): String? =
            typed.filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim().take(40).takeIf { it.length >= 2 }
        val ASSETS = setOf(
            "index.html", "app.js", "style.css", "brand.css", "brand.js",
            "exo2.ttf", "exo2-400.ttf", "exo2-500.ttf", "exo2-600.ttf", "logo.png", "icon.png",
        )
        private val COMMON = listOf(
            "Connection" to "close",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
        )
        private val REASONS = mapOf(
            200 to "OK", 206 to "Partial Content", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden",
            404 to "Not Found", 405 to "Method Not Allowed", 416 to "Range Not Satisfiable", 429 to "Too Many Requests",
            500 to "Internal Server Error",
        )

        fun contentType(name: String): String = when (name.substringAfterLast('.').lowercase()) {
            "html" -> "text/html; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "ttf" -> "font/ttf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }

        fun quote(s: String): String {
            val sb = StringBuilder("\"")
            s.forEach { c ->
                when {
                    c == '"' -> sb.append("\\\"")
                    c == '\\' -> sb.append("\\\\")
                    c == '<' -> sb.append("\\u003c")
                    c < ' ' -> sb.append("\\u%04x".format(c.code))
                    else -> sb.append(c)
                }
            }
            return sb.append('"').toString()
        }

        private const val TEST_PAGE = """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Teste de entrega</title>
<style>body{margin:0;background:#101413;color:#e8efec;font-family:system-ui,sans-serif;padding:16px}
img{width:100%;border-radius:12px}a{display:block;margin-top:16px;padding:14px;border-radius:12px;background:#00fca8;color:#0b1f17;text-align:center;font-weight:700;text-decoration:none}</style>
</head><body><h1>JR C50D Sync</h1><p>Se esta página abriu, o Wi-Fi e o servidor funcionam.</p>
<img src="/teste.jpg" alt="Foto de teste"><a href="/teste.jpg?dl=1" download>Baixar a foto</a></body></html>"""
    }
}
