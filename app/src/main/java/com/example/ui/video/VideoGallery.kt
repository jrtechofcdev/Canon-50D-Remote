package com.example.ui.video

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.TetherApp
import com.example.core.video.VideoController
import com.example.core.video.VideoItem
import com.example.core.video.VideoLog
import com.example.ui.components.EmptyState
import com.example.ui.components.ScreenHeader
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Videos in the gallery: the ones filmed by the camera and the live view recorded by the phone.
 * Opens from the Fotos tab. Videos have no sound (the 50D has no microphone).
 */
@Composable
fun VideoGallery(app: TetherApp, onBack: () -> Unit) {
    val library = app.video.library
    val items by library.items.collectAsState()
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var deleting by remember { mutableStateOf<VideoItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { library.refresh() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Vídeos", count = items.size, onBack = onBack)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    "Nenhum vídeo ainda",
                    text = "Grave o live view pelo botão de vídeo no Live view, ou filme com o SET da câmera e busque o arquivo em Ajustes → Vídeo.",
                )
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.uri }) { item ->
                VideoTile(app, item, onPlay = { open(app, item, context, onPlayInside = { playing = item }) }, onDelete = { deleting = item })
            }
        }
    }

    playing?.let { item -> VideoPlayer(app, item, onClose = { playing = null }) }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Tone.Surface,
            title = { Text("Apagar ${item.name}?") },
            text = { Text("O arquivo sai do celular. Se veio da câmera, a cópia no cartão continua lá.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { if (!library.delete(item)) Toast.makeText(context, "Não deu para apagar", Toast.LENGTH_SHORT).show() }
                }) { Text("Apagar", color = Tone.Error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

/** Plays inside the app when Android can; Magic Lantern RAW (.MLV) goes to another app. */
private fun open(app: TetherApp, item: VideoItem, context: android.content.Context, onPlayInside: () -> Unit) {
    if (item.playable) {
        VideoLog.i("GALERIA", "Abrindo ${item.name} no player do app")
        onPlayInside()
        return
    }
    VideoLog.i("GALERIA", "${item.name} (${item.extension}) não toca no Android; oferecendo outro app")
    openElsewhere(app, item, context)
}

private fun openElsewhere(app: TetherApp, item: VideoItem, context: android.content.Context) {
    val library = app.video.library
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(library.shareableUri(item), library.mime(item)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Abrir ${item.name}"))
    } catch (e: ActivityNotFoundException) {
        VideoLog.w("GALERIA", "Nenhum app abre ${item.name}")
        Toast.makeText(context, "Nenhum app do celular abre este arquivo. Copie para o computador.", Toast.LENGTH_LONG).show()
    }
}

private fun share(app: TetherApp, item: VideoItem, context: android.content.Context) {
    val library = app.video.library
    val send = Intent(Intent.ACTION_SEND).setType(library.mime(item)).putExtra(Intent.EXTRA_STREAM, library.shareableUri(item)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    VideoLog.i("GALERIA", "Compartilhando ${item.name}")
    context.startActivity(Intent.createChooser(send, "Enviar ${item.name}"))
}

@Composable
private fun VideoTile(app: TetherApp, item: VideoItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var duration by remember(item.uri) { mutableStateOf<Long?>(null) }
    LaunchedEffect(item.uri) {
        val (bitmap, ms) = app.video.library.preview(item)
        thumb = bitmap
        duration = ms
    }
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(Tone.Surface)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 2f).background(Color.Black).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
            thumb?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Icon(
                if (item.playable) Icons.Outlined.PlayCircle else Icons.Outlined.OpenInNew,
                contentDescription = if (item.playable) "Tocar ${item.name}" else "Abrir ${item.name} em outro app",
                tint = Tone.OnPhoto,
                modifier = Modifier.size(40.dp),
            )
            Row(Modifier.align(Alignment.BottomStart).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.fromPhone) Icon(Icons.Outlined.PhoneAndroid, contentDescription = "Gravado pelo celular", tint = Tone.OnPhoto, modifier = Modifier.size(16.dp))
                Icon(Icons.Outlined.VideocamOff, contentDescription = "Sem áudio", tint = Tone.OnPhoto, modifier = Modifier.size(16.dp))
                duration?.let { Text(" " + clock(it), style = MaterialTheme.typography.labelSmall, color = Tone.OnPhoto) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.labelMedium, color = Tone.Text, maxLines = 1)
                Text("${item.extension} · ${VideoController.sizeLabel(item.size)}", style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
            }
            IconButton(onClick = { share(app, item, context) }) { Icon(Icons.Outlined.Share, contentDescription = "Enviar ${item.name}", tint = Tone.TextMuted) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Apagar ${item.name}", tint = Tone.TextMuted) }
        }
    }
}

/** Full screen player with Android's own controls. Errors go to the video log instead of closing the app. */
@Composable
private fun VideoPlayer(app: TetherApp, item: VideoItem, onClose: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        val uri = Uri.parse(item.uri)
                        if (uri.scheme == "file") setVideoPath(uri.path) else setVideoURI(uri)
                        setOnPreparedListener { player ->
                            VideoLog.i("PLAYER", "${item.name}: ${player.videoWidth}x${player.videoHeight}, ${player.duration / 1000} s")
                            start()
                        }
                        setOnErrorListener { _, what, extra ->
                            VideoLog.e("PLAYER", "${item.name} não tocou (erro $what/$extra)")
                            Toast.makeText(ctx, "O player do app não abriu este vídeo. Tente em outro app.", Toast.LENGTH_LONG).show()
                            onClose()
                            openElsewhere(app, item, context)
                            true
                        }
                    }
                },
                onRelease = { it.stopPlayback() },
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Tone.Scrim, RoundedCornerShape(50))) {
                Icon(Icons.Outlined.Close, contentDescription = "Fechar o vídeo", tint = Tone.OnPhoto)
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
