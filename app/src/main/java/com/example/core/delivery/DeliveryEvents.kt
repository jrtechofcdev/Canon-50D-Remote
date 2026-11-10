package com.example.core.delivery

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What happens to a photo, as the delivery hears it. The delivery never talks to the camera or USB. */
sealed interface PhotoEvent {
    val photoId: Long

    /** Saved on the phone after the camera fired. */
    data class Captured(override val photoId: Long) : PhotoEvent

    /** Hearted by the client (portal or client mode). */
    data class Approved(override val photoId: Long) : PhotoEvent

    /** Its look or crop changed, so a prepared copy is out of date. */
    data class Edited(override val photoId: Long) : PhotoEvent

    data class Queued(override val photoId: Long, val sessionId: Long) : PhotoEvent

    /** The client downloaded the whole file. */
    data class Delivered(override val photoId: Long, val sessionId: Long) : PhotoEvent
}

/**
 * The bus between capture, editing and delivery. Emitting never suspends. The buffer is large, so a
 * burst never loses a "Captured" (the automatic send would skip that photo); in the extreme it drops the oldest.
 */
class DeliveryEvents {
    private val flow = MutableSharedFlow<PhotoEvent>(extraBufferCapacity = 2048, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<PhotoEvent> = flow.asSharedFlow()

    fun emit(event: PhotoEvent) {
        flow.tryEmit(event)
    }
}
