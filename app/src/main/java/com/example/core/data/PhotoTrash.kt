package com.example.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Deleting with a way back. A deleted photo leaves the screens at once but its files stay for
 * [undoMs]; Desfazer in that time brings it back untouched, after it [erase] removes the files.
 * If the app is closed inside that time nothing is lost: the photo is simply still there next time.
 */
class PhotoTrash(
    private val scope: CoroutineScope,
    private val undoMs: Long = UNDO_MS,
    private val erase: suspend (List<Long>) -> Unit,
) {
    /** One delete the photographer can still take back. */
    data class Batch(val serial: Long, val ids: List<Long>)

    private val _pending = MutableStateFlow<Set<Long>>(emptySet())

    /** Photos deleted but not erased yet: screens leave these out. */
    val pending: StateFlow<Set<Long>> = _pending.asStateFlow()

    private val _deleted = MutableSharedFlow<Batch>(extraBufferCapacity = 8)

    /** Each delete, for the "Desfazer" message. */
    val deleted: SharedFlow<Batch> = _deleted.asSharedFlow()

    private val jobs = mutableMapOf<Long, Job>()
    private var serial = 0L

    fun delete(ids: List<Long>): Batch? {
        val fresh = ids.filter { it !in _pending.value }.distinct()
        if (fresh.isEmpty()) return null
        val batch = synchronized(jobs) { Batch(++serial, fresh) }
        _pending.update { it + fresh }
        val job = scope.launch {
            delay(undoMs)
            synchronized(jobs) { jobs.remove(batch.serial) } ?: return@launch
            try {
                erase(batch.ids)
            } finally {
                _pending.update { it - batch.ids.toSet() }
            }
        }
        synchronized(jobs) { jobs[batch.serial] = job }
        _deleted.tryEmit(batch)
        return batch
    }

    /** Brings [batch] back. False when it is too late (the files are already gone). */
    fun undo(batch: Batch): Boolean {
        val job = synchronized(jobs) { jobs.remove(batch.serial) } ?: return false
        job.cancel()
        _pending.update { it - batch.ids.toSet() }
        return true
    }

    companion object {
        /** A little longer than the message stays on screen (SnackbarDuration.Long, 10 s). */
        const val UNDO_MS = 12_000L
    }
}
