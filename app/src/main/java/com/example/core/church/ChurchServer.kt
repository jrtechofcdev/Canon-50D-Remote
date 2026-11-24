package com.example.core.church

import com.example.core.delivery.LocalServer
import com.example.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A file the server sends: its name, size and how to read it from the start. */
class ServedFile(val name: String, val length: Long, val open: () -> InputStream?)

/** One photo as the media page lists it. */
data class ChurchPhotoInfo(val id: Long, val name: String, val hasJpg: Boolean, val hasRaw: Boolean)

/** A preset of the shop as the media page lists it; [installed] = already in the photographer's app. */
data class ChurchStoreItem(val id: Long, val name: String, val category: String, val installed: Boolean)

/** A preset as the media page lists it. */
data class ChurchPresetInfo(val id: Long, val name: String, val group: String)

/** What the server needs from the app; a fake one in tests. */
interface ChurchBackend {
    val repo: ChurchRepository
    fun asset(name: String): InputStream?
    suspend fun photo(id: Long): ChurchPhotoInfo?
    /** [look]: the person's look for the "pronta" (null = the event's). */
    suspend fun file(id: Long, kind: ChurchFile, look: ReadyLook? = null): ServedFile?
    /** The grid picture; [marked] = with the person's name, for who cannot download anything. */
    suspend fun thumb(id: Long, member: ChurchMember, marked: Boolean): ServedFile?
    /**
     * The big picture; [marked] carries the person's name, [ready] = the "pronta" instead of the photo as it
     * is, and [look] is the person's look for it (null = the event's).
     */
    suspend fun preview(id: Long, member: ChurchMember, marked: Boolean, ready: Boolean = false, look: ReadyLook? = null): ServedFile?
    suspend fun logo(event: ChurchEvent): ServedFile?
    /** The photographer's presets, in the app's order (favourites and recent first). */
    suspend fun presets(): List<ChurchPresetInfo> = emptyList()
    /** A preset's numbers for the page's live preview ([com.example.core.render.RenderParamsJson]); null when it is gone. */
    suspend fun params(presetId: Long): String? = null
    /** The photo the page's live preview starts from: cropped and "Melhorar" applied, no look. */
    suspend fun base(id: Long, member: ChurchMember, marked: Boolean): ServedFile? = null
    /** The event's look changed: the "pronta" of its photos are made again ahead. */
    suspend fun onEventLook() {}
    /** The shop's catalogue as saved on the phone (no internet needed to list it). */
    suspend fun store(): List<ChurchStoreItem> = emptyList()
    /** A shop item's picture; null when it is not on the phone and there is no internet. */
    suspend fun storePicture(id: Long): ServedFile? = null
    /** Installs a shop item into the photographer's presets: its preset id, or the reason it couldn't. */
    suspend fun storeInstall(id: Long): Result<ChurchPresetInfo> = Result.failure(IllegalStateException("Loja indisponível"))
}

/**
 * The media team's mini server for church events: its own port and page, separate from the Entrega
 * local. Every request but the page itself carries the person's token ("m"), checked against the open
 * event, their status and their permissions; nothing is served to someone who may not see it.
 *
 * Same safety as the Entrega local: a fixed set of workers, a short waiting line, a few connections per
 * device, slow senders cut off, Range for resumed downloads, no path from the request touches the disk.
 */
class ChurchServer(private val backend: ChurchBackend, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var socket: ServerSocket? = null
    private var pool: ThreadPoolExecutor? = null
    private val open = ConcurrentHashMap.newKeySet<Socket>()
    private val perDevice = ConcurrentHashMap<String, AtomicInteger>()
    private val seen = ConcurrentHashMap<String, Long>()
    private val _online = MutableStateFlow(0)

    /** Devices that asked for something in the last 2 minutes. */
    val online: StateFlow<Int> = _online.asStateFlow()
    /** Person and photo already logged as seen (kept while the server runs). */
    private val viewed: MutableSet<Pair<Long, Long>> = ConcurrentHashMap.newKeySet()
    val port: Int get() = socket?.localPort ?: 0
    val running: Boolean get() = socket?.isClosed == false

    private val repo get() = backend.repo

    @Synchronized
    fun start(port: Int = PORT, host: String? = null): Int {
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
        val server = host?.let { bindOn(it) } ?: bindOn(null) ?: throw java.io.IOException("Nenhuma porta livre a partir de $port")
        socket = server
        val workers = ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE),
            { r -> Thread(r, "igreja-http").apply { isDaemon = true } },
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
                    AppLog.w(TAG, "Falha ao aceitar conexão", e)
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
        }, "igreja-accept").apply { isDaemon = true }.start()
        AppLog.i(TAG, "Servidor da igreja ligado na porta ${server.localPort}" + (host?.let { " ($it)" } ?: ""))
        return server.localPort
    }

    private fun done(client: Socket, ip: String) {
        runCatching { client.close() }
        open -= client
        perDevice[ip]?.decrementAndGet()
    }

    @Synchronized
    fun stop() {
        socket?.let { runCatching { it.close() }; AppLog.i(TAG, "Servidor da igreja desligado") }
        socket = null
        pool?.shutdownNow()
        pool = null
        open.forEach { runCatching { it.close() } }
        open.clear()
        perDevice.clear()
        seen.clear()
        _online.value = 0
    }

    // ---- one request -------------------------------------------------------------------------

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val ip: String)

    private fun handle(client: Socket) = client.use { s ->
        s.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        val ip = s.inetAddress?.hostAddress.orEmpty()
        val deadline = System.nanoTime() + HEAD_DEADLINE_MS * 1_000_000L
        val request = runCatching { read(input, ip, deadline) }.getOrNull()
        if (request == null) {
            runCatching { text(out, 400, "Pedido inválido") }
            return@use
        }
        markSeen(ip)
        runCatching { runBlocking { route(request, out) } }.onFailure { e ->
            if (e !is SocketException) {
                AppLog.w(TAG, "Erro em ${request.method} ${request.path.replace(TOKEN, "…")}", e)
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

    private suspend fun route(r: Request, out: OutputStream) {
        val head = r.method == "HEAD"
        if (r.method != "GET" && r.method != "HEAD" && r.method != "POST") return text(out, 405, "Método não aceito")
        val seg = r.path.trim('/').split('/').filter { it.isNotEmpty() }
        when {
            seg.isEmpty() || seg == listOf("index.html") -> asset(out, "index.html", head)
            seg.size == 1 && seg[0] in ASSETS -> asset(out, seg[0], head)
            seg == listOf("api", "c", "hello") -> hello(r, out)
            seg == listOf("api", "c", "join") && r.method == "POST" -> join(r, out)
            seg == listOf("api", "c", "state") -> state(r, out)
            seg == listOf("api", "c", "presets") -> presets(r, out)
            seg == listOf("api", "c", "shader") -> shader(r, out)
            seg == listOf("api", "c", "look") && r.method == "POST" -> eventLook(r, out)
            seg == listOf("api", "c", "mylook") && r.method == "POST" -> ownLook(r, out)
            seg == listOf("api", "c", "independent") && r.method == "POST" -> independent(r, out)
            seg == listOf("api", "c", "revert") && r.method == "POST" -> revert(r, out)
            seg == listOf("api", "c", "share") && r.method == "POST" -> share(r, out)
            seg == listOf("api", "c", "store") -> store(r, out)
            seg == listOf("api", "c", "store", "install") && r.method == "POST" -> storeInstall(r, out)
            seg.size == 3 && seg[0] == "s" -> storePicture(seg[1], seg[2], r, out, head)
            seg == listOf("api", "c", "share", "accept") && r.method == "POST" -> closeShare(r, out, apply = true)
            seg == listOf("api", "c", "share", "dismiss") && r.method == "POST" -> closeShare(r, out, apply = false)
            seg == listOf("api", "c", "params") -> params(r, out)
            seg.size == 3 && seg[0] == "b" -> base(seg[1], seg[2], r, out, head)
            seg == listOf("evento-logo") -> logo(out, head)
            seg.size == 3 && seg[0] == "t" -> thumb(seg[1], seg[2], r, out, head)
            seg.size == 3 && seg[0] == "p" -> preview(seg[1], seg[2], r, out, head)
            seg.size == 3 && seg[0] == "f" -> download(seg[1], seg[2], r, out, head)
            else -> text(out, 404, "Não encontrado")
        }
    }

    // ---- the event and the door --------------------------------------------------------------

    private suspend fun hello(r: Request, out: OutputStream) {
        val e = repo.dao.openEvent() ?: return json(out, 200, """{"open":false}""")
        val token = r.query["e"]
        // The page learns only what the door needs: the event's look and whether it asks for the code.
        val viaLink = token != null && token == e.token
        val linkClosed = token != null && token == e.token && !e.linkOn && !e.qrOn
        json(out, 200, buildString {
            append("""{"open":true""")
            append(""","name":${q(e.name)},"congregation":${q(e.congregation)},"date":${e.date}""")
            append(""","motto":${q(e.motto.orEmpty())},"accent":${q(ChurchTheme.color(e.accent) ?: ChurchTheme.ACCENT)}""")
            append(""","secondary":${q(ChurchTheme.color(e.secondary) ?: ChurchTheme.SECONDARY)},"logo":${e.logoUri != null}""")
            append(""","code":${e.codeOn},"link":$viaLink,"closed":$linkClosed}""")
        })
    }

    private suspend fun join(r: Request, out: OutputStream) {
        val via = when (r.query["via"]) {
            ChurchVia.QR -> ChurchVia.QR
            ChurchVia.LINK -> ChurchVia.LINK
            else -> ChurchVia.ADDRESS
        }
        val result = repo.join(
            via = via,
            eventToken = r.query["e"],
            code = r.query["code"],
            name = r.query["name"]?.let(LocalServer::cleanName),
            role = r.query["role"]?.let(LocalServer::cleanName),
            ip = r.ip,
            device = r.headers["user-agent"],
            existingToken = r.query["m"]?.takeIf { TOKEN.matches(it) },
        )
        when (result) {
            is JoinResult.Ok -> json(out, 200, """{"token":${q(result.member.token)},"name":${q(result.member.name)},"role":${q(result.member.role)}}""")
            is JoinResult.Denied -> json(out, 403, """{"error":${q(result.message)}}""")
        }
    }

    /** The person behind "m", or an answer saying why not (the page goes back to the door). */
    private suspend fun who(r: Request, out: OutputStream, token: String?): Pair<ChurchMember, ChurchEvent>? {
        val found = token?.takeIf { TOKEN.matches(it) }?.let { repo.memberFor(it, r.ip) }
        if (found != null) return found
        val m = token?.takeIf { TOKEN.matches(it) }?.let { repo.dao.memberByToken(it) }
        val message = when {
            m?.status == MemberStatus.BLOCKED -> ChurchRepository.BLOCKED
            repo.dao.openEvent() == null -> "O evento foi encerrado pelo fotógrafo."
            else -> "Entre de novo para continuar."
        }
        json(out, 401, """{"gone":${q(message)}}""")
        return null
    }

    private suspend fun state(r: Request, out: OutputStream) {
        val (m, e) = who(r, out, r.query["m"]) ?: return
        val v = repo.version
        if (r.query["v"] == v.toString()) return json(out, 200, """{"same":true,"v":$v}""")
        val ids = repo.visiblePhotos(m)
        val photos = ids.mapNotNull { backend.photo(it) }
        val perms = m.perms
        json(out, 200, buildString {
            append("""{"v":$v""")
            append(""","event":{"name":${q(e.name)},"congregation":${q(e.congregation)},"date":${e.date},"motto":${q(e.motto.orEmpty())}""")
            append(""","accent":${q(ChurchTheme.color(e.accent) ?: ChurchTheme.ACCENT)},"secondary":${q(ChurchTheme.color(e.secondary) ?: ChurchTheme.SECONDARY)},"logo":${e.logoUri != null}""")
            // The event's look and who last changed it, for the notice on every page.
            val lookName = e.presetId?.let { p -> backend.presets().firstOrNull { it.id == p }?.name } ?: if (e.adjust != null) "ajuste próprio" else "o da foto"
            append(""","look":{"preset":${e.presetId ?: "null"},"name":${q(lookName)},"by":${e.lookBy?.let(::q) ?: "null"},"at":${e.lookAt ?: "null"}}}""")
            append(""","me":{"name":${q(m.name)},"role":${q(m.role)}""")
            append(""","view":${ChurchPerm.has(perms, ChurchPerm.VIEW)}""")
            append(""","original":${ChurchPerm.has(perms, ChurchPerm.DOWNLOAD_ORIGINAL)}""")
            append(""","pronta":${ChurchPerm.has(perms, ChurchPerm.DOWNLOAD_READY)}""")
            append(""","raw":${ChurchPerm.has(perms, ChurchPerm.DOWNLOAD_RAW)}""")
            append(""","rank":${m.rank},"rankLabel":${q(ChurchRank.label(m.rank))}""")
            append(""","presets":${ChurchPerm.has(perms, ChurchPerm.PRESETS)},"store":${ChurchPerm.has(perms, ChurchPerm.STORE)}""")
            // An admin's own look, whether it leads their "pronta", and whether there is one to go back to.
            val ownName = m.lookPresetId?.let { p -> backend.presets().firstOrNull { it.id == p }?.name } ?: m.lookAdjust?.let { "ajuste próprio" }
            append(""","independent":${m.independent},"own":${ownName?.let { """{"preset":${m.lookPresetId ?: "null"},"name":${q(it)}}""" } ?: "null"}""")
            append(""","canRevert":${m.prevPresetId != null || m.prevAdjust != null}}""")
            // Admins: who else they can send a look to, and the looks sent to them still waiting.
            if (m.rank >= ChurchRank.ADMIN) {
                val presets = backend.presets()
                val admins = repo.dao.members(e.id).filter { it.id != m.id && it.rank >= ChurchRank.ADMIN && it.isActive }
                append(""","admins":[""")
                admins.forEachIndexed { i, a -> if (i > 0) append(','); append("""{"id":${a.id},"label":${q(a.label)}}""") }
                append("""],"shares":[""")
                repo.dao.sharesTo(e.id, m.id).forEachIndexed { i, s ->
                    if (i > 0) append(',')
                    val name = s.presetId?.let { p -> presets.firstOrNull { it.id == p }?.name } ?: "ajuste próprio"
                    append("""{"id":${s.id},"from":${q(s.fromLabel)},"name":${q(name)},"preset":${s.presetId ?: "null"}}""")
                }
                append("]")
            }
            append(""","photos":[""")
            photos.forEachIndexed { i, p ->
                if (i > 0) append(',')
                append("""{"id":${p.id},"name":${q(p.name)},"jpg":${p.hasJpg},"raw":${p.hasRaw}}""")
            }
            append("]}")
        })
    }

    // ---- presets ------------------------------------------------------------------------------

    /** The presets released for this event, for who may use them on the page. */
    private suspend fun presets(r: Request, out: OutputStream) {
        val (m, e) = who(r, out, r.query["m"]) ?: return
        if (!ChurchPerm.has(m.perms, ChurchPerm.PRESETS)) return json(out, 403, """{"error":"O fotógrafo não liberou os presets para você."}""")
        val released = ChurchRules.released(e)
        val list = backend.presets().filter { released == null || it.id in released }
        json(out, 200, buildString {
            append("""{"event":${e.presetId ?: "null"},"presets":[""")
            list.forEachIndexed { i, p ->
                if (i > 0) append(',')
                append("""{"id":${p.id},"name":${q(p.name)},"group":${q(p.group)}}""")
            }
            append("]}")
        })
    }

    /** May [m] use [presetId] on the page: the Presets switch, and the preset released for the event. */
    private fun presetAllowed(m: ChurchMember, e: ChurchEvent, presetId: Long): Boolean {
        if (!ChurchPerm.has(m.perms, ChurchPerm.PRESETS)) return false
        val released = ChurchRules.released(e)
        return released == null || presetId in released
    }

    /** The main admin sets the event's look: a released preset, or "none" for the photo's own look. */
    private suspend fun eventLook(r: Request, out: OutputStream) {
        val (m, e) = who(r, out, r.query["m"]) ?: return
        if (m.rank != ChurchRank.MAIN) return json(out, 403, """{"error":"Só o ADM principal muda o look do evento."}""")
        val raw = r.query["preset"] ?: return json(out, 400, """{"error":"Preset?"}""")
        val id = if (raw == "none") null else raw.toLongOrNull() ?: return json(out, 400, """{"error":"Preset?"}""")
        if (id != null && !presetAllowed(m, e, id)) return json(out, 403, """{"error":"Este preset não está liberado."}""")
        val name = id?.let { p -> backend.presets().firstOrNull { it.id == p }?.name } ?: if (id == null) "o da foto" else return json(out, 404, """{"error":"Preset não encontrado"}""")
        repo.setEventLook(e.id, id, null, m, name)
        backend.onEventLook()
        json(out, 200, """{"ok":true}""")
    }

    /** The admin behind [r], or an error answer: only admins keep a look of their own. */
    private suspend fun admin(r: Request, out: OutputStream): Pair<ChurchMember, ChurchEvent>? {
        val (m, e) = who(r, out, r.query["m"]) ?: return null
        if (m.rank != ChurchRank.ADMIN) {
            json(out, 403, """{"error":"Só ADM guarda um look próprio."}""")
            return null
        }
        return m to e
    }

    private suspend fun ownLook(r: Request, out: OutputStream) {
        val (m, e) = admin(r, out) ?: return
        val raw = r.query["preset"] ?: return json(out, 400, """{"error":"Preset?"}""")
        val id = if (raw == "none") null else raw.toLongOrNull() ?: return json(out, 400, """{"error":"Preset?"}""")
        if (id != null && !presetAllowed(m, e, id)) return json(out, 403, """{"error":"Este preset não está liberado."}""")
        val name = id?.let { p -> backend.presets().firstOrNull { it.id == p }?.name } ?: "o do evento"
        repo.setOwnLook(m.id, id, null, name)
        json(out, 200, """{"ok":true}""")
    }

    private suspend fun independent(r: Request, out: OutputStream) {
        val (m, _) = admin(r, out) ?: return
        repo.setIndependent(m.id, r.query["on"] == "1")
        json(out, 200, """{"ok":true}""")
    }

    private suspend fun revert(r: Request, out: OutputStream) {
        val (m, _) = admin(r, out) ?: return
        if (!repo.revertOwnLook(m.id)) return json(out, 409, """{"error":"Não há um look anterior."}""")
        json(out, 200, """{"ok":true}""")
    }

    /** An admin (or the main admin) sends a released preset to another admin or to the photographer. */
    private suspend fun share(r: Request, out: OutputStream) {
        val (m, e) = who(r, out, r.query["m"]) ?: return
        if (m.rank < ChurchRank.ADMIN) return json(out, 403, """{"error":"Só ADM compartilha looks."}""")
        val id = r.query["preset"]?.toLongOrNull() ?: return json(out, 400, """{"error":"Preset?"}""")
        if (!presetAllowed(m, e, id)) return json(out, 403, """{"error":"Este preset não está liberado."}""")
        val to = when (val t = r.query["to"]) {
            "fotografo" -> null
            else -> t?.toLongOrNull()?.let { repo.dao.member(it) }
                ?.takeIf { it.eventId == e.id && it.id != m.id && it.rank >= ChurchRank.ADMIN && it.isActive }
                ?: return json(out, 400, """{"error":"Escolha um ADM ou o fotógrafo."}""")
        }
        val name = backend.presets().firstOrNull { it.id == id }?.name ?: return json(out, 404, """{"error":"Preset não encontrado"}""")
        repo.shareLook(m, to?.id, id, null, name)
        json(out, 200, """{"ok":true}""")
    }

    /** Apply (it becomes the admin's own look, or the event's for the main admin) or dismiss a look received. */
    private suspend fun closeShare(r: Request, out: OutputStream, apply: Boolean) {
        val (m, e) = who(r, out, r.query["m"]) ?: return
        val s = r.query["id"]?.toLongOrNull()?.let { repo.dao.share(it) }
            ?.takeIf { it.toMemberId == m.id && it.doneAt == null } ?: return json(out, 404, """{"error":"Look não encontrado"}""")
        if (apply) {
            val name = s.presetId?.let { p -> backend.presets().firstOrNull { it.id == p }?.name } ?: "ajuste de ${s.fromLabel}"
            if (m.rank == ChurchRank.MAIN) {
                repo.setEventLook(e.id, s.presetId, s.adjust, m, name)
                backend.onEventLook()
            } else repo.setOwnLook(m.id, s.presetId, s.adjust, name)
        }
        repo.closeShare(s.id, apply, m)
        json(out, 200, """{"ok":true}""")
    }

    // ---- the preset shop, for who has the Loja switch ----------------------------------------

    private suspend fun storeMember(token: String?, r: Request, out: OutputStream): Pair<ChurchMember, ChurchEvent>? {
        val (m, e) = who(r, out, token) ?: return null
        if (!ChurchPerm.has(m.perms, ChurchPerm.STORE)) {
            json(out, 403, """{"error":"O fotógrafo não liberou a Loja para você."}""")
            return null
        }
        return m to e
    }

    private suspend fun store(r: Request, out: OutputStream) {
        storeMember(r.query["m"], r, out) ?: return
        json(out, 200, buildString {
            append("""{"items":[""")
            backend.store().forEachIndexed { i, it ->
                if (i > 0) append(',')
                append("""{"id":${it.id},"name":${q(it.name)},"category":${q(it.category)},"installed":${it.installed}}""")
            }
            append("]}")
        })
    }

    private suspend fun storePicture(token: String, id: String, r: Request, out: OutputStream, head: Boolean) {
        storeMember(token, r, out) ?: return
        val f = id.toLongOrNull()?.let { backend.storePicture(it) } ?: return text(out, 404, "Sem prévia")
        serve(out, f, null, head, download = false, private = false) {}
    }

    /** Installs into the photographer's app; when the event releases only some presets, the new one joins them. */
    private suspend fun storeInstall(r: Request, out: OutputStream) {
        val (m, e) = storeMember(r.query["m"], r, out) ?: return
        val id = r.query["id"]?.toLongOrNull() ?: return json(out, 400, """{"error":"Preset?"}""")
        val result = backend.storeInstall(id)
        val p = result.getOrElse { return json(out, 409, """{"error":${q(it.message ?: "Não deu para instalar")}}""") }
        ChurchRules.released(e)?.let { if (p.id !in it) repo.setReleasedPresets(e.id, it + p.id) }
        repo.log(e.id, m, "Instalou da Loja", p.name, r.ip)
        repo.changed()
        json(out, 200, """{"ok":true,"preset":${p.id},"name":${q(p.name)}}""")
    }

    /** The app's own shader, so the page's preview is made by the same maths. */
    private suspend fun shader(r: Request, out: OutputStream) {
        val (m, _) = who(r, out, r.query["m"]) ?: return
        if (!ChurchPerm.has(m.perms, ChurchPerm.PRESETS)) return json(out, 403, """{"error":"Sem presets"}""")
        json(out, 200, """{"vertex":${q(com.example.core.render.PresetShader.VERTEX)},"fragment":${q(com.example.core.render.PresetShader.FRAGMENT)}}""")
    }

    private suspend fun params(r: Request, out: OutputStream) {
        val (m, e) = who(r, out, r.query["m"]) ?: return
        val id = r.query["preset"]?.toLongOrNull() ?: return json(out, 400, """{"error":"Preset?"}""")
        if (!presetAllowed(m, e, id)) return json(out, 403, """{"error":"Este preset não está liberado para você."}""")
        val p = backend.params(id) ?: return json(out, 404, """{"error":"Preset não encontrado"}""")
        json(out, 200, p)
    }

    private suspend fun base(token: String, id: String, r: Request, out: OutputStream, head: Boolean) {
        val (m, _, photoId) = visible(token, id, r, out) ?: return
        if (!ChurchPerm.has(m.perms, ChurchPerm.PRESETS)) return text(out, 403, "Sem presets")
        val f = backend.base(photoId, m, marked(m)) ?: return text(out, 404, "Foto indisponível")
        serve(out, f, null, head, download = false, private = true) {}
    }

    // ---- pictures and files ------------------------------------------------------------------

    /** The person and the photo, when they may see it; otherwise an error answer is sent. */
    private suspend fun visible(token: String, id: String, r: Request, out: OutputStream): Triple<ChurchMember, ChurchEvent, Long>? {
        val (m, e) = who(r, out, token) ?: return null
        val photoId = id.toLongOrNull()
        if (photoId == null || photoId !in repo.visiblePhotos(m)) {
            text(out, 404, "Foto não encontrada")
            return null
        }
        return Triple(m, e, photoId)
    }

    private suspend fun thumb(token: String, id: String, r: Request, out: OutputStream, head: Boolean) {
        val (m, _, photoId) = visible(token, id, r, out) ?: return
        val f = backend.thumb(photoId, m, marked(m)) ?: return text(out, 404, "Miniatura indisponível")
        serve(out, f, null, head, download = false, private = true) {}
    }

    private suspend fun preview(token: String, id: String, r: Request, out: OutputStream, head: Boolean) {
        val (m, e, photoId) = visible(token, id, r, out) ?: return
        val ready = r.query["v"] == ChurchFile.READY.key
        val preset = r.query["preset"]?.toLongOrNull()
        if (preset != null && !presetAllowed(m, e, preset)) return text(out, 403, "Este preset não está liberado para você.")
        val f = backend.preview(photoId, m, marked(m), ready || preset != null, ChurchRules.lookFor(e, m, preset)) ?: return text(out, 404, "Foto indisponível")
        serve(out, f, null, head, download = false, private = true) {}
        // Opening the big picture is "seeing" it: one line per person and photo, however often they come back.
        if (!head && viewed.add(m.id to photoId)) {
            repo.log(m.eventId, m, VIEWED, backend.photo(photoId)?.name ?: "foto $photoId", r.ip)
        }
    }

    /** Who cannot download at all only ever gets pictures with their own name on them. */
    private fun marked(m: ChurchMember) = m.perms and ChurchPerm.DOWNLOAD_ANY == 0

    private suspend fun download(token: String, id: String, r: Request, out: OutputStream, head: Boolean) {
        val (m, e, photoId) = visible(token, id, r, out) ?: return
        val kind = ChurchFile.of(r.query["v"]) ?: ChurchFile.ORIGINAL
        if (!repo.canDownload(m, kind)) {
            repo.log(e.id, m, "Tentou baixar sem permissão", "foto $photoId (${kind.label})", r.ip)
            return text(out, 403, "Você não tem permissão para baixar esta versão.")
        }
        val preset = r.query["preset"]?.toLongOrNull()?.takeIf { kind == ChurchFile.READY }
        if (preset != null && !presetAllowed(m, e, preset)) return text(out, 403, "Este preset não está liberado para você.")
        val look = if (kind == ChurchFile.READY) ChurchRules.lookFor(e, m, preset) else null
        val f = backend.file(photoId, kind, look) ?: return text(out, 404, "Este arquivo ainda não chegou da câmera.")
        val range = r.headers["range"]
        // The log says which look the "pronta" went with, when it was not the event's.
        val lookName = look?.presetId?.let { id -> backend.presets().firstOrNull { it.id == id }?.name } ?: look?.let { "look próprio" }
        serve(out, f, range, head, download = r.query["dl"] == "1", private = true) {
            runBlocking { repo.log(e.id, m, "Baixou", "${f.name} (${kind.label}${lookName?.let { ", $it" } ?: ""})", r.ip) }
        }
    }

    private suspend fun logo(out: OutputStream, head: Boolean) {
        val e = repo.dao.openEvent() ?: return text(out, 404, "Sem evento")
        val f = backend.logo(e) ?: return text(out, 404, "Sem logo")
        serve(out, f, null, head, download = false, private = false) {}
    }

    // ---- responses ---------------------------------------------------------------------------

    /** Sends [f] or the asked part of it; [whole] runs once a download reached its last byte. */
    private fun serve(out: OutputStream, f: ServedFile, range: String?, head: Boolean, download: Boolean, private: Boolean, whole: () -> Unit) {
        val length = f.length
        var start = 0L
        var end = length - 1
        var partial = false
        if (range != null) {
            val m = Regex("""bytes=(\d*)-(\d*)""").matchEntire(range.trim())
            if (m == null || length <= 0L) return rangeError(out, length)
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
            add("Content-Type" to contentType(f.name))
            add("Content-Length" to count.toString())
            add("Accept-Ranges" to "bytes")
            // Pictures with a person's token in the address stay out of shared caches.
            add("Cache-Control" to if (private) "private, max-age=600" else "max-age=600")
            if (partial) add("Content-Range" to "bytes $start-$end/$length")
            if (download) add("Content-Disposition" to "attachment; filename=\"${f.name.replace("\"", "")}\"")
        }
        if (head) {
            writeHead(out, if (partial) 206 else 200, headers)
            return out.flush()
        }
        val input = f.open() ?: return text(out, 404, "Arquivo indisponível")
        writeHead(out, if (partial) 206 else 200, headers)
        input.use { s ->
            var skip = start
            while (skip > 0) {
                val n = s.skip(skip)
                if (n <= 0) break
                skip -= n
            }
            val buf = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = s.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
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
        val bytes = backend.asset(name)?.use { it.readBytes() } ?: return text(out, 404, "Página não encontrada")
        writeHead(out, 200, listOf("Content-Type" to contentType(name), "Content-Length" to bytes.size.toString(), "Cache-Control" to "no-cache"))
        if (!head) out.write(bytes)
        out.flush()
    }

    private fun json(out: OutputStream, status: Int, body: String) = bytes(out, status, "application/json; charset=utf-8", body)

    private fun text(out: OutputStream, status: Int, body: String) = bytes(out, status, "text/plain; charset=utf-8", body)

    private fun bytes(out: OutputStream, status: Int, type: String, body: String) {
        val data = body.toByteArray()
        writeHead(out, status, listOf("Content-Type" to type, "Content-Length" to data.size.toString(), "Cache-Control" to "no-store"))
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
        seen.entries.removeIf { now - it.value > ONLINE_MS }
        _online.value = seen.size
    }

    private fun q(s: String) = LocalServer.quote(s)

    companion object {
        const val PORT = 8100
        const val TAG = "IGREJA"
        private const val WORKERS = 8
        private const val QUEUE = 64
        private const val PER_DEVICE = 8
        private const val READ_TIMEOUT_MS = 10_000
        private const val HEAD_DEADLINE_MS = 10_000L
        private const val ONLINE_MS = 2 * 60_000L
        private val TOKEN = Regex("[0-9a-f]{32}")
        /** The log line of a photo opened in the big view (the panel can hide these). */
        const val VIEWED = "Viu a foto"

        val ASSETS = setOf(
            "index.html", "app.js", "look.js", "style.css", "brand.css", "brand.js",
            "exo2-400.ttf", "exo2-500.ttf", "exo2-600.ttf", "logo.png", "icon.png",
        )
        private val COMMON = listOf(
            "Connection" to "close",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
            // The page can't be framed by another site (a fake page around the real one).
            "X-Frame-Options" to "DENY",
        )
        private val REASONS = mapOf(
            200 to "OK", 206 to "Partial Content", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden",
            404 to "Not Found", 405 to "Method Not Allowed", 416 to "Range Not Satisfiable", 500 to "Internal Server Error",
        )

        fun contentType(name: String): String = when (name.substringAfterLast('.').lowercase()) {
            "cr2" -> "image/x-canon-cr2"
            "png" -> "image/png"
            else -> LocalServer.contentType(name)
        }
    }
}
