package com.example.ui.delivery

import com.example.TetherApp
import com.example.core.render.Enhance

/**
 * "Melhorar" on a set of photos, as in the gallery: on for all when any is still without it, off when all
 * have it. The deliveries make their copies again by themselves (the photo's "edited" event).
 * Returns the message for the photographer.
 */
internal suspend fun toggleEnhance(app: TetherApp, ids: List<Long>): String {
    val level = app.settings.current().enhanceLevel
    val list = ids.distinct().mapNotNull { app.database.photoDao().get(it) }
    if (list.isEmpty()) return "Nenhuma foto"
    val on = list.any { it.enhance == Enhance.OFF }
    list.forEach { app.looks.setEnhance(it, if (on) level else Enhance.OFF) }
    val n = list.size
    return if (on) "$n ${if (n == 1) "foto melhorada" else "fotos melhoradas"}" else "Melhorar tirado de $n ${if (n == 1) "foto" else "fotos"}"
}
