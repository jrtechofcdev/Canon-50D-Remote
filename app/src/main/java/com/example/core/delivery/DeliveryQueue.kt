package com.example.core.delivery

import com.example.core.data.Photo
import com.example.core.data.PhotoDao
import com.example.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    /** Adds [photoIds] to a client's queue; the ones already there are skipped. Returns how many were added. */
    suspend fun enqueue(sessionId: Long, photoIds: List<Long>): Int {
        val session = dao.session(sessionId)?.takeIf { it.isOpen } ?: return 0
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
            wake.trySend(Unit)
        }
        return added
    }

    /** A photo's look changed: its prepared copies are made again with the new look. */
    suspend fun refresh(photoId: Long) {
        var any = false
        dao.itemsOfPhoto(photoId).filter { it.state != DeliveryState.WAITING && it.state != DeliveryState.PREPARING }.forEach {
            dao.retry(it.id)
            any = true
        }
        if (any) wake.trySend(Unit)
    }

    fun poke() {
        wake.trySend(Unit)
    }

    /** Runs the queue in [scope] until it is cancelled. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            dao.resetPreparing()
            while (isActive) {
                if (!processNext()) wake.receive()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Prepares the oldest waiting item. False when there was nothing to do. */
    suspend fun processNext(): Boolean {
        val item = dao.nextWaiting() ?: return false
        dao.setState(item.id, DeliveryState.PREPARING, null, 0, null, null)
        val session = dao.session(item.sessionId)
        val photo = photos.get(item.photoId)
        if (session == null || photo == null) {
            dao.setState(item.id, DeliveryState.ERROR, null, 0, "A foto ou o cliente foi apagado", null)
            return true
        }
        val dir = File(root, session.token).apply { mkdirs() }
        val target = File(dir, "${item.id}.jpg")
        val ok = runCatching { preparer.prepare(photo, session, target) }
            .onFailure { AppLog.w("ENTREGA", "Não deu para preparar ${item.name}", it) }
            .getOrDefault(false)
        if (ok && target.exists() && target.length() > 0) {
            File(dir, "${item.id}.thumb.jpg").delete()
            File(dir, "${item.id}.pthumb.jpg").delete()
            dao.setState(item.id, DeliveryState.READY, target.path, target.length(), null, clock())
        } else {
            target.delete()
            dao.setState(item.id, DeliveryState.ERROR, null, 0, "Não deu para preparar a foto", null)
        }
        return true
    }

    /** Deletes every prepared file (the history stays; items go back to waiting). */
    suspend fun clearFiles() {
        dao.forgetFiles()
        root.deleteRecursively()
    }

    /** Deletes the files of one client. */
    fun clearFiles(session: DeliverySession) {
        File(root, session.token).deleteRecursively()
    }
}
