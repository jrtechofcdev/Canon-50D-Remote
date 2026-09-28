package com.example.ui.preset

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.core.preset.Preset
import com.example.core.preset.PresetSettings
import com.example.core.render.Looks

/** The settings that give [photo] its look: its own calibration, else its preset, else none. */
fun lookSettings(photo: Photo, presets: List<Preset>): PresetSettings? =
    photo.adjust?.let { PresetSettings.decode(it) } ?: photo.presetId?.let { id -> presets.firstOrNull { it.id == id }?.settings }

/**
 * The photo as the client should see it: rendered with its look, like a colour profile over the file.
 * The plain file shows first and stays underneath, so switching presets never flashes an empty frame.
 */
@Composable
fun LookImage(
    app: TetherApp,
    photo: Photo,
    presets: List<Preset>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxEdge: Int = Looks.PREVIEW_EDGE,
) {
    val look = remember(photo.adjust, photo.presetId, presets) { lookSettings(photo, presets) }
    val crop = photo.crop
    val source = photo.displayUri
    val rendered by produceState<Bitmap?>(null, source, look, crop, maxEdge) {
        value = if (source != null && (look != null || crop != null)) {
            runCatching { app.looks.renderPhoto(photo, look, maxEdge) }.getOrNull()
        } else null
    }
    Box(modifier) {
        // A cropped photo waits for its render: the uncropped file underneath would show the wrong framing.
        if (crop == null) {
            AsyncImage(model = source, contentDescription = photo.savedBase, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
        val bmp = rendered
        if ((look != null || crop != null) && bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = photo.savedBase, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}
