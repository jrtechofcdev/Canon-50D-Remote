package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Pinch and double-tap zoom (up to 8×) for a photo. At normal size it only takes pinches, so a pager
 * around it still swipes; once zoomed it pans. [onZoomChanged] lets the pager lock while zoomed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Zoomable(
    modifier: Modifier = Modifier,
    key: Any? = null,
    onZoomChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        // Keep the picture covering the frame: no panning past its edges.
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val state = rememberTransformableState { zoom, pan, _ ->
        val newScale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (newScale == 1f) Offset.Zero else clamp(offset + pan, newScale)
        if ((newScale > 1f) != (scale > 1f)) onZoomChanged(newScale > 1f)
        scale = newScale
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(key) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    } else {
                        // Zoom into the spot that was tapped, like the phone's gallery.
                        val target = 3f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        scale = target
                        offset = clamp((center - tap) * (target - 1f), target)
                        onZoomChanged(true)
                    }
                })
            }
            .transformable(state, lockRotationOnZoomPan = true, canPan = { scale > 1f }),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            content = content,
        )
    }
}
