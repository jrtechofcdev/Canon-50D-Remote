package com.example.core.church

import com.example.core.delivery.AttemptLimiter
import com.example.core.delivery.DeliveryRules
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** How someone reached the page: the link, the QR code or the plain address (no event token). */
/** A look picked for one "pronta": a preset, or the full settings ([adjust]) as the editor saves them. */
data class ReadyLook(val presetId: Long?, val adjust: String?)

object ChurchVia {
    const val LINK = "link"
    const val QR = "qr"
    const val ADDRESS = "address"
}

/** The three files a photo can be downloaded as. */
enum class ChurchFile(val key: String, val label: String, val perm: Int) {
    ORIGINAL("original", "original", ChurchPerm.DOWNLOAD_ORIGINAL),
    READY("pronta", "pronta", ChurchPerm.DOWNLOAD_READY),
    RAW("raw", "RAW", ChurchPerm.DOWNLOAD_RAW);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

sealed interface JoinResult {
    data class Ok(val member: ChurchMember, val event: ChurchEvent) : JoinResult
    data class Denied(val message: String) : JoinResult
}

/** Rules with no database, kept apart so they are tested on their own. */
object ChurchRules {
    const val NAME_MIN = 2
    const val NAME_MAX = 40

    fun cleanName(text: String?): String? = text?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.length in NAME_MIN..NAME_MAX }

    fun validCode(code: String?) = code != null && Regex("\\d{4}").matches(code)

    fun newCode(avoid: String? = null): String {
        var code: String
        do code = (0..9999).random().toString().padStart(4, '0') while (code == avoid)
        return code
    }

    /**
     * The photos one person sees, newest first: the event's photos minus those hidden for everyone or for
     * them, plus the extras given only to them. Nothing without the "ver" permission.
     */
    fun visible(eventPhotos: List<EventPhoto>, rules: List<MemberPhotoRule>, perms: Int): List<Long> {
        if (!ChurchPerm.has(perms, ChurchPerm.VIEW)) return emptyList()
        val hidden = rules.filter { it.rule == PhotoRule.HIDDEN }.map { it.photoId }.toSet()
        val base = eventPhotos.sortedByDescending { it.addedAt }.filter { !it.hidden && it.photoId !in hidden }.map { it.photoId }
        val extras = rules.filter { it.rule == PhotoRule.EXTRA && it.photoId !in base }.map { it.photoId }
        return extras + base
    }

    /**
     * The look of one person's "pronta" (null = the event's): the preset they picked on the page when they
     * may and it is released; else their own look when they are an independent admin; else the event's.
     */
    fun lookFor(event: ChurchEvent, member: ChurchMember, picked: Long?): ReadyLook? {
        if (picked != null && ChurchPerm.has(member.perms, ChurchPerm.PRESETS)) {
            val released = released(event)
            if (released == null || picked in released) return ReadyLook(picked, null)
        }
        if (member.rank == ChurchRank.ADMIN && member.independent && (member.lookPresetId != null || member.lookAdjust != null)) {
            return ReadyLook(member.lookPresetId, member.lookAdjust)
        }
        return null
    }

    /** The presets released to the media (null = all of them). */
    fun released(event: ChurchEvent): Set<Long>? =
        event.presets?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet()

    /** Whether [via] may be used to come in, and the message when not. */
    fun viaProblem(event: ChurchEvent, via: String, token: String?): String? = when (via) {
        ChurchVia.LINK, ChurchVia.QR -> when {
            token != event.token -> "Este link é de outro evento. Peça o QR novo ao fotógrafo."
            via == ChurchVia.LINK && !event.linkOn -> "O fotógrafo desligou a entrada pelo link."
            via == ChurchVia.QR && !event.qrOn -> "O fotógrafo desligou a entrada pelo QR."
            else -> null
        }
        else -> null
    }
}

/**
 * The church delivery's rules over the database: events and congregations (photographer), and how the media
 * team comes in and what each person sees and downloads. Every action goes to the event's log with the
 * person's name and role.
 */
class ChurchRepository(
    val dao: ChurchDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Grows on every change the media page should see; a page with the same number gets "nothing new". */
    private val _version = AtomicLong(1)
    val version: Long get() = _version.get()
    fun changed() = _version.incrementAndGet()

    private val limiter = AttemptLimiter()
    private val seen = ConcurrentHashMap<Long, Long>()

    // Photographer: congregations and events.

    suspend fun saveCongregation(c: Congregation): Long =
        if (c.id == 0L) dao.insertCongregation(c.copy(createdAt = clock())) else c.id.also { dao.updateCongregation(c) }

    /** Takes the congregation off the list; its events keep the name, colours and look they copied. */
    suspend fun deleteCongregation(id: Long) {
        dao.deleteCongregation(id)
    }

    /** A new event, open right away (others close), with its congregation's look and colours. */
    suspend fun newEvent(name: String, congregationId: Long?, date: Long = clock()): ChurchEvent {
        val c = congregationId?.let { dao.congregation(it) }
        val now = clock()
        val e = ChurchEvent(
            congregationId = c?.id,
            name = name.trim().ifBlank { "Evento" },
            congregation = c?.name.orEmpty(),
            date = date,
            accent = c?.accent ?: ChurchTheme.ACCENT,
            secondary = c?.secondary ?: ChurchTheme.SECONDARY,
            motto = c?.motto,
            logoUri = c?.logoUri,
            presetId = c?.presetId,
            adjust = c?.adjust,
            defaultPerms = c?.defaultPerms ?: ChurchPerm.DEFAULT,
            code = ChurchRules.newCode(),
            token = DeliveryRules.newToken(),
            createdAt = now,
        )
        val id = dao.insertEvent(e)
        dao.closeOthers(id, now)
        log(id, null, "Evento criado", "${e.name}${c?.let { " · ${it.name}" } ?: ""}")
        changed()
        return e.copy(id = id)
    }

    suspend fun updateEvent(e: ChurchEvent) {
        dao.updateEvent(e)
        changed()
    }

    /** Fixes the name or the date after the event was created; the media page shows the new ones on its next look. */
    suspend fun editEvent(eventId: Long, name: String, date: Long) {
        val e = dao.event(eventId) ?: return
        val clean = name.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { return }
        if (clean == e.name && date == e.date) return
        dao.updateEvent(e.copy(name = clean, date = date))
        val what = listOfNotNull(
            "nome: $clean".takeIf { clean != e.name },
            "data: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT).format(java.util.Date(date))}".takeIf { date != e.date },
        )
        log(eventId, null, "Evento editado", what.joinToString(", "))
        changed()
    }

    /** Closes the event by itself [hours] from now; null takes the deadline off. */
    suspend fun setAutoClose(eventId: Long, hours: Int?) {
        val e = dao.event(eventId) ?: return
        val at = hours?.let { clock() + it * 3_600_000L }
        dao.updateEvent(e.copy(autoCloseAt = at))
        log(eventId, null, if (at == null) "Validade tirada" else "Encerra sozinho em $hours h")
        changed()
    }

    /** Closes the open event when its deadline passed. True when it did. */
    suspend fun closeIfDue(): Boolean {
        val e = dao.openEvent() ?: return false
        val at = e.autoCloseAt ?: return false
        if (clock() < at) return false
        dao.updateEvent(e.copy(closedAt = clock()))
        log(e.id, null, "Evento encerrado sozinho (validade)")
        changed()
        return true
    }

    /** Opens [eventId] again (the others close). */
    suspend fun open(eventId: Long) {
        val e = dao.event(eventId) ?: return
        // A deadline that already passed would close it again at once.
        dao.updateEvent(e.copy(closedAt = null, autoCloseAt = null))
        dao.closeOthers(eventId, clock())
        log(eventId, null, "Evento aberto")
        changed()
    }

    suspend fun close(eventId: Long) {
        val e = dao.event(eventId) ?: return
        if (e.closedAt == null) dao.updateEvent(e.copy(closedAt = clock()))
        log(eventId, null, "Evento encerrado")
        changed()
    }

    suspend fun deleteEvent(eventId: Long) {
        dao.deleteRulesOf(eventId)
        dao.deleteMembersOf(eventId)
        dao.deletePhotosOf(eventId)
        dao.deleteLogOf(eventId)
        dao.deleteLooksOf(eventId)
        dao.deleteSharesOf(eventId)
        dao.deleteEvent(eventId)
        changed()
    }

    /** Sets the code ([custom], or a new random one); with [kickAll] everyone inside has to type it again. */
    suspend fun resetCode(eventId: Long, custom: String? = null, kickAll: Boolean = false): String? {
        val e = dao.event(eventId) ?: return null
        val code = custom?.takeIf(ChurchRules::validCode) ?: ChurchRules.newCode(avoid = e.code)
        dao.updateEvent(e.copy(code = code))
        log(eventId, null, if (custom != null) "Código definido" else "Código novo gerado")
        if (kickAll) dao.members(eventId).filter { it.isActive }.forEach { kick(it.id, "código trocado") }
        changed()
        return code
    }

    suspend fun setAccess(eventId: Long, linkOn: Boolean, qrOn: Boolean, codeOn: Boolean) {
        val e = dao.event(eventId) ?: return
        dao.updateEvent(e.copy(linkOn = linkOn, qrOn = qrOn, codeOn = codeOn))
        log(eventId, null, "Acesso", "link ${onOff(linkOn)}, QR ${onOff(qrOn)}, código ${onOff(codeOn)}")
        changed()
    }

    /** What someone who comes in from now on may do; the people already inside keep theirs. */
    suspend fun setDefaultPerm(eventId: Long, flag: Int, on: Boolean) {
        val e = dao.event(eventId) ?: return
        dao.updateEvent(e.copy(defaultPerms = ChurchPerm.with(e.defaultPerms, flag, on)))
        log(eventId, null, "Quem entrar: ${permName(flag)} ${onOff(on)}")
        changed()
    }

    /** Gives everyone already inside what a newcomer gets. Blocked people stay as they are. Returns how many changed. */
    suspend fun applyDefaultToAll(eventId: Long): Int {
        val e = dao.event(eventId) ?: return 0
        val changedOnes = dao.members(eventId).filter { it.status != MemberStatus.BLOCKED && it.perms != e.defaultPerms }
        changedOnes.forEach { dao.updateMember(it.copy(perms = e.defaultPerms)) }
        if (changedOnes.isNotEmpty()) {
            log(eventId, null, "Permissões de quem entra aplicadas a todos", "${changedOnes.size} pessoa(s)")
            changed()
        }
        return changedOnes.size
    }

    /**
     * The event's look (the "pronta" of everyone who follows it): set by the main admin on the page or by the
     * photographer ([by] null). Every change goes to the history, and the pages show who made it and when.
     */
    suspend fun setEventLook(eventId: Long, presetId: Long?, adjust: String?, by: ChurchMember?, lookName: String) {
        val e = dao.event(eventId) ?: return
        val who = by?.label ?: "Fotógrafo"
        val now = clock()
        dao.updateEvent(e.copy(presetId = presetId, adjust = adjust, lookBy = who, lookAt = now))
        dao.insertLook(ChurchLook(eventId = eventId, presetId = presetId, adjust = adjust, byWho = who, at = now))
        log(eventId, by, "Mudou o look do evento", lookName)
        changed()
    }

    /** Which presets the media may use: [ids] null releases all of them, empty releases none. */
    suspend fun setReleasedPresets(eventId: Long, ids: Set<Long>?) {
        val e = dao.event(eventId) ?: return
        dao.updateEvent(e.copy(presets = ids?.sorted()?.joinToString(",")))
        log(eventId, null, "Presets liberados", if (ids == null) "todos" else "${ids.size}")
        changed()
    }

    // Photographer: people.

    suspend fun setPerm(memberId: Long, flag: Int, on: Boolean) {
        val m = dao.member(memberId) ?: return
        dao.updateMember(m.copy(perms = ChurchPerm.with(m.perms, flag, on)))
        log(m.eventId, m, "Permissão ${permName(flag)} ${onOff(on)}", byPhotographer = true)
        changed()
    }

    /**
     * Who leads the looks. There is one main admin per event: naming a new one makes the old one an admin.
     * Admins get the presets on the page; going back to viewer keeps the photographer's other switches.
     */
    suspend fun setRank(memberId: Long, rank: Int) {
        val m = dao.member(memberId) ?: return
        if (m.rank == rank) return
        if (rank == ChurchRank.MAIN) {
            dao.members(m.eventId).filter { it.id != m.id && it.rank == ChurchRank.MAIN }.forEach { old ->
                dao.updateMember(old.copy(rank = ChurchRank.ADMIN))
                log(m.eventId, old, "Virou ADM", "outra pessoa é o ADM principal", byPhotographer = true)
            }
        }
        val perms = if (rank >= ChurchRank.ADMIN) ChurchPerm.with(m.perms, ChurchPerm.PRESETS, true) else m.perms
        dao.updateMember(m.copy(rank = rank, perms = perms, independent = m.independent && rank == ChurchRank.ADMIN))
        log(m.eventId, m, "Virou ${ChurchRank.label(rank)}", byPhotographer = true)
        changed()
    }

    /** Out now; the device's old token stops working. They may come in again with the code. */
    suspend fun kick(memberId: Long, why: String? = null) {
        val m = dao.member(memberId) ?: return
        dao.updateMember(m.copy(status = MemberStatus.KICKED, token = DeliveryRules.newToken()))
        log(m.eventId, m, "Expulso", why, byPhotographer = true)
        changed()
    }

    /** Out and kept out: this device and this name are refused at the door. */
    suspend fun block(memberId: Long) {
        val m = dao.member(memberId) ?: return
        dao.updateMember(m.copy(status = MemberStatus.BLOCKED))
        log(m.eventId, m, "Bloqueado", byPhotographer = true)
        changed()
    }

    suspend fun unblock(memberId: Long) {
        val m = dao.member(memberId) ?: return
        dao.updateMember(m.copy(status = MemberStatus.ACTIVE))
        log(m.eventId, m, "Desbloqueado", byPhotographer = true)
        changed()
    }

    /** Hides [photoId] from one person, gives it only to them ([PhotoRule.EXTRA]), or clears the rule (null). */
    suspend fun setRule(memberId: Long, photoId: Long, rule: String?) {
        val m = dao.member(memberId) ?: return
        if (rule == null) dao.clearRule(memberId, photoId) else dao.setRule(MemberPhotoRule(memberId, photoId, rule))
        val what = when (rule) {
            PhotoRule.HIDDEN -> "Foto oculta"
            PhotoRule.EXTRA -> "Foto extra"
            else -> "Regra da foto removida"
        }
        log(m.eventId, m, what, "foto $photoId", byPhotographer = true)
        changed()
    }

    // Photos of the event.

    /** A photo joins the open event (a new frame from the camera, or picked by hand). */
    suspend fun addPhoto(eventId: Long, photoId: Long) {
        if (dao.addPhoto(EventPhoto(eventId, photoId, clock())) > 0) changed()
    }

    suspend fun removePhoto(eventId: Long, photoId: Long) {
        dao.removePhoto(eventId, photoId)
        changed()
    }

    suspend fun hideForAll(eventId: Long, photoIds: List<Long>, hidden: Boolean) {
        dao.setHidden(eventId, photoIds, hidden)
        log(eventId, null, if (hidden) "Fotos ocultas para todos" else "Fotos mostradas de novo", photoIds.joinToString(" "))
        changed()
    }

    // The media team.

    /** Someone at the door: checks the way in, the code, the name and role, and blocks. */
    suspend fun join(
        via: String,
        eventToken: String?,
        code: String?,
        name: String?,
        role: String?,
        ip: String,
        device: String?,
        existingToken: String?,
    ): JoinResult {
        val event = dao.openEvent() ?: return JoinResult.Denied("Nenhum evento aberto agora. Fale com o fotógrafo.")
        // A device already inside comes back without typing anything again.
        existingToken?.let { token ->
            val m = dao.memberByToken(token)
            if (m != null && m.eventId == event.id) {
                if (m.status == MemberStatus.BLOCKED) return JoinResult.Denied(BLOCKED)
                if (m.isActive) return JoinResult.Ok(m, event)
            }
        }
        ChurchRules.viaProblem(event, via, eventToken)?.let { return JoinResult.Denied(it) }
        if (event.codeOn) {
            if (limiter.blocked(ip)) return JoinResult.Denied("Muitas tentativas. Espere um minuto e tente de novo.")
            if (code != event.code) {
                limiter.failed(ip)
                log(event.id, null, "Código errado", ip = ip)
                return JoinResult.Denied("Código errado.")
            }
        }
        val cleanName = ChurchRules.cleanName(name) ?: return JoinResult.Denied("Digite o seu nome (de 2 a 40 letras).")
        val cleanRole = ChurchRules.cleanName(role) ?: return JoinResult.Denied("Digite o seu cargo ou função (de 2 a 40 letras).")
        if (dao.blockedByName(event.id, cleanName) > 0) return JoinResult.Denied(BLOCKED)
        val now = clock()
        val member = ChurchMember(
            eventId = event.id,
            name = cleanName,
            role = cleanRole,
            token = DeliveryRules.newToken(),
            perms = event.defaultPerms,
            device = device?.take(120),
            ip = ip,
            joinedAt = now,
            lastSeenAt = now,
        )
        val id = dao.insertMember(member)
        val saved = member.copy(id = id)
        log(event.id, saved, "Entrou", "pelo ${viaName(via)}", ip)
        changed()
        return JoinResult.Ok(saved, event)
    }

    /** The person behind [token], when they may still be in (active, in the open event). */
    suspend fun memberFor(token: String?, ip: String?): Pair<ChurchMember, ChurchEvent>? {
        if (token.isNullOrBlank()) return null
        val m = dao.memberByToken(token) ?: return null
        if (!m.isActive) return null
        val e = dao.event(m.eventId)?.takeIf { it.isOpen } ?: return null
        // "Visto por último" without a write on every poll.
        val now = clock()
        if (now - (seen[m.id] ?: 0L) > SEEN_MS) {
            seen[m.id] = now
            dao.touchMember(m.id, now, ip)
        }
        return m to e
    }

    suspend fun visiblePhotos(member: ChurchMember): List<Long> =
        ChurchRules.visible(dao.photos(member.eventId), dao.rules(member.id), member.perms)

    fun canDownload(member: ChurchMember, file: ChurchFile) = ChurchPerm.has(member.perms, file.perm)

    suspend fun log(eventId: Long, member: ChurchMember?, action: String, detail: String? = null, ip: String? = null, byPhotographer: Boolean = false) {
        val who = when {
            byPhotographer -> "Fotógrafo" + (member?.let { " → ${it.label}" } ?: "")
            member != null -> member.label
            else -> "Fotógrafo"
        }
        dao.log(ChurchAudit(eventId = eventId, memberId = member?.id, who = who, action = action, detail = detail, ip = ip ?: member?.ip.takeIf { !byPhotographer }, at = clock()))
    }

    private fun onOff(on: Boolean) = if (on) "ligado" else "desligado"

    private fun viaName(via: String) = when (via) {
        ChurchVia.LINK -> "link"
        ChurchVia.QR -> "QR"
        else -> "endereço"
    }

    private fun permName(flag: Int) = when (flag) {
        ChurchPerm.VIEW -> "ver fotos"
        ChurchPerm.DOWNLOAD_ORIGINAL -> "baixar original"
        ChurchPerm.DOWNLOAD_READY -> "baixar pronta"
        ChurchPerm.DOWNLOAD_RAW -> "baixar RAW"
        ChurchPerm.PRESETS -> "presets"
        ChurchPerm.STORE -> "Loja"
        else -> "0x%X".format(flag)
    }

    companion object {
        const val BLOCKED = "Você foi bloqueado neste evento. Fale com o fotógrafo."
        private const val SEEN_MS = 15_000L
    }
}
