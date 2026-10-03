package com.example.core.delivery

import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Makes the file a client receives for one photo. Returns false (or throws) when it could not. */
fun interface DeliveryPreparer {
    suspend fun prepare(photo: Photo, session: DeliverySession, target: File): Boolean
}

/**
 * The delivery queue: one photo prepared at a time, in the order they were sent, so a burst of 30
 * shots never piles 30 renders on the phone. A photo goes to a client once; a failed one stays
 * in the list with its error until "tentar de novo". Files live in [root]/<token>/.
 */
class DeliveryQueue(
    private val dao: DeliveryDao,
    private val photos: PhotoDao,
    private val preparer: DeliveryPreparer,
    private val root: File,
    private val events: DeliveryEvents,
    /** Which tool this queue works for: it only ever touches that tool's clients. */
    private val express: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val wake = Channel<Unit>(Channel.UNLIMITED)
    private val claim = kotlinx.coroutines.sync.Mutex()
    private val jobs = mutableListOf<Job>()

    /** Adds [photoIds] to a client's queue; the ones already there are skipped. Returns how many were added. */
    suspend fun enqueue(sessionId: Long, photoIds: List<Long>): Int {
        val session = dao.session(sessionId)?.takeIf { it.isOpen && it.express == express } ?: return 0
        var added = 0
        photos.getAll(photoIds.distinct()).sortedBy { it.capturedAt }.forEach { photo ->
            val id = dao.insertItem(
                DeliveryItem(sessionId = sessionId, photoId = photo.id, name = DeliveryRules.itemName(session.client, photo.savedBase), queuedAt = clock()),
            )
            if (id > 0) {
                added++
                events.emit(PhotoEvent.Queued(photo.id, sessionId))
            }
        }
        if (added > 0) {
            AppLog.i("ENTREGA", "$added foto(s) na fila de ${session.client}")
            repeat(minOf(added, WORKERS)) { wake.trySend(Unit) }
        }
        return added
    }

    /**
     * Adds one more version of [photoId] for a client, with its own [look] (a preset, a calibration, a crop).
     * The photo can already be there: each version is a separate file. Returns the new item's id, or -1.
     */
    suspend fun enqueueVersion(sessionId: Long, photoId: Long, look: DeliveryLook, label: String?): Long {
        val session = dao.session(sessionId)?.takeIf { it.isOpen && it.express == express } ?: return -1
        val photo = photos.get(photoId) ?: return -1
        // The first copy a client gets is version 1 even when it was sent with a chosen look.
        val variant = dao.lastVariant(sessionId, photoId)?.plus(1) ?: 0
        val id = dao.insertItem(
            DeliveryItem(
                sessionId = sessionId, photoId = photoId, variant = variant, look = look.encode(), label = label,
                name = DeliveryRules.versionName(session.client, photo.savedBase, variant, label), queuedAt = clock(),
            ),
        )
        if (id > 0) {
            AppLog.i("ENTREGA", "Versão ${variant + 1} de ${photo.savedBase} na fila de ${session.client}" + (label?.let { " ($it)" } ?: ""))
            events.emit(PhotoEvent.Queued(photoId, sessionId))
            wake.trySend(Unit)
        }
        return id
    }

    /** A photo's look changed: its prepared copies are made again with the new look (extra versions keep theirs). */
    suspend fun refresh(photoId: Long) {
        var any = false
        dao.itemsOfPhoto(photoId).filter { it.look == null && it.state != DeliveryState.WAITING && it.state != DeliveryState.PREPARING }.forEach {
            dao.retry(it.id)
            any = true
        }
        if (any) repeat(WORKERS) { wake.trySend(Unit) }
    }

    fun poke() {
        repeat(WORKERS) { wake.trySend(Unit) }
    }

    /**
     * Runs the queue in [scope] until it is cancelled: [WORKERS] photos are prepared at the same time
     * (a render waits on the GPU while the next one decodes), still in the order they were sent.
     */
    fun start(scope: CoroutineScope) {
        if (jobs.any { it.isActive }) return
        jobs.clear()
        jobs += scope.launch { dao.resetPreparing(express); poke() }
        repeat(WORKERS) {
            jobs += scope.launch {
                while (isActive) {
                    if (!processNext()) wake.receive()
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /** Prepares the oldest waiting item. False when there was nothing to do. */
    suspend fun processNext(): Boolean {
        // Taking an item is one step, so two workers never prepare the same photo.
        val item = claim.withLock {
            dao.nextWaiting(express)?.also { dao.setState(it.id, DeliveryState.PREPARING, null, 0, null, null) }
        } ?: return false
        val session = dao.session(item.sessionId)
        // An extra version wears its own look; the file name keeps it apart from the photo's other copies.
        val photo = photos.get(item.photoId)?.let { p ->
            DeliveryLook.decode(item.look)?.applyTo(p)?.copy(savedBase = DeliveryRules.versionBase(p.savedBase, item.variant, item.label)) ?: p
        }
        if (session == null || photo == null) {
            dao.setState(item.id, DeliveryState.ERROR, null, 0, "A foto ou o cliente foi apagado", null)
            return true
        }
        val dir = File(root, session.token).apply { mkdirs() }
        val target = File(dir, "${item.id}.jpg")
        // Old grid pictures go before the new file is made (the preparer may warm new ones).
        File(dir, "${item.id}.thumb.jpg").delete()
        File(dir, "${item.id}.pthumb.jpg").delete()
        val ok = runCatching { preparer.prepare(photo, session, target) }
            .onFailure { AppLog.w("ENTREGA", "Não deu para preparar ${item.name}", it) }
            .getOrDefault(false)
        if (ok && target.exists() && target.length() > 0) {
            dao.setState(item.id, DeliveryState.READY, target.path, target.length(), null, clock())
        } else {
            target.delete()
            dao.setState(item.id, DeliveryState.ERROR, null, 0, "Não deu para preparar a foto", null)
        }
        return true
    }

    private companion object {
        const val WORKERS = 2
    }

    /** Deletes every prepared file (the history stays; items go back to waiting). */
    suspend fun clearFiles() {
        dao.forgetFiles(express)
        root.deleteRecursively()
    }

    /** Deletes the files of one client. */
    fun clearFiles(session: DeliverySession) {
        File(root, session.token).deleteRecursively()
    }
}
