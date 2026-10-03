package com.example.core.delivery

import com.example.core.data.Photo
import org.json.JSONObject

/**
 * The look frozen into an extra version of a photo: its preset, its own calibration and its crop,
 * as they were when the version was sent. Later edits to the photo don't change it.
 */
data class DeliveryLook(val presetId: Long? = null, val adjust: String? = null, val crop: String? = null) {

    fun encode(): String = JSONObject().apply {
        presetId?.let { put("p", it) }
        adjust?.let { put("a", it) }
        crop?.let { put("c", it) }
    }.toString()

    /** [photo] wearing this look instead of its own. */
    fun applyTo(photo: Photo): Photo = photo.copy(presetId = presetId, adjust = adjust, crop = crop)

    companion object {
        fun decode(text: String?): DeliveryLook? = text?.let {
            runCatching {
                val o = JSONObject(it)
                DeliveryLook(
                    presetId = if (o.has("p")) o.getLong("p") else null,
                    adjust = o.optString("a").takeIf { a -> o.has("a") && a.isNotEmpty() },
                    crop = o.optString("c").takeIf { c -> o.has("c") && c.isNotEmpty() },
                )
            }.getOrNull()
        }

        /** The photo's look right now (what "Como está agora" sends). */
        fun of(photo: Photo) = DeliveryLook(photo.presetId, photo.adjust, photo.crop)
    }
}
