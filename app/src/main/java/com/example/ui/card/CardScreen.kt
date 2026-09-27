package com.example.ui.card

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.camera.ConnectionState
import com.example.core.ptp.ObjectInfo
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Camera thumbnails are ~160×120 JPEGs; 300 of them decoded is well under 30 MB.
private val thumbCache = LruCache<Int, ImageBitmap>(300)

private sealed interface CardLoad {
    data class Loading(val done: Int, val total: Int) : CardLoad
    data class Ready(val files: List<ObjectInfo>) : CardLoad
    data class Error(val message: String) : CardLoad
}

/** Browse what is already on the CF card and pull only the chosen frames to the phone. */
@Composable
fun CardScreen(app: TetherApp, onBack: () -> Unit) {
    val state by app.camera.state.collectAsState()
    var load by remember { mutableStateOf<CardLoad>(CardLoad.Loading(0, 0)) }
    var known by remember { mutableStateOf(emptySet<String>()) }
    var selected by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(Unit) {
        known = app.database.photoDao().knownCameraNames().toSet()
        load = try {
            // Newest first: that's what the photographer usually looks for.
            CardLoad.Ready(app.camera.listCardFiles { done, total -> load = CardLoad.Loading(done, total) }.reversed())
        } catch (e: Exception) {
            CardLoad.Error(e.message ?: "Não foi possível ler o cartão")
        }
    }
    LaunchedEffect(state) { if (state !is ConnectionState.Connected) onBack() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            Column(Modifier.weight(1f)) {
                Text("Cartão da câmera", style = MaterialTheme.typography.titleLarge)
                (load as? CardLoad.Ready)?.let {
                    Text("${it.files.size} arquivos · ${selected.size} selecionados", style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted)
                }
            }
            (load as? CardLoad.Ready)?.let { ready ->
                val missing = ready.files.filter { it.filename !in known }.map { it.handle }.toSet()
                TextButton(onClick = { selected = if (selected == missing) emptySet() else missing }) {
                    Text(if (selected == missing && missing.isNotEmpty()) "Limpar" else "Selecionar novas")
                }
            }
        }

        when (val l = load) {
            is CardLoad.Loading -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    if (l.total == 0) "Lendo o cartão…" else "Lendo o cartão… ${l.done} de ${l.total}",
                    color = Tone.TextMuted,
                )
                LinearProgressIndicator(
                    progress = { if (l.total > 0) l.done.toFloat() / l.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Tone.Accent, trackColor = Tone.SurfaceHigh,
                )
            }
            is CardLoad.Error -> Text(l.message, color = Tone.Error, modifier = Modifier.padding(24.dp))
            is CardLoad.Ready -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(l.files, key = { it.handle }) { file ->
                        CardCell(
                            app = app,
                            file = file,
                            onPhone = file.filename in known,
                            selected = file.handle in selected,
                            onToggle = {
                                selected = if (file.handle in selected) selected - file.handle else selected + file.handle
                            },
                        )
                    }
                }
                Button(
                    onClick = {
                        l.files.filter { it.handle in selected }.forEach { app.tether.enqueue(it.asObjectAdded(), force = true) }
                        known = known + l.files.filter { it.handle in selected }.map { it.filename }
                        selected = emptySet()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                ) { Text(if (selected.isEmpty()) "Selecione as fotos" else "Baixar ${selected.size} para o celular") }
            }
        }
    }
}

@Composable
private fun CardCell(app: TetherApp, file: ObjectInfo, onPhone: Boolean, selected: Boolean, onToggle: () -> Unit) {
    val thumb by produceState(thumbCache.get(file.handle), file.handle) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                app.camera.thumbnail(file.handle)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
            }?.also { thumbCache.put(file.handle, it) }
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Tone.Surface)
            .border(if (selected) 3.dp else 0.dp, if (selected) Tone.Accent else Color.Transparent, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onToggle),
    ) {
        thumb?.let { Image(it, contentDescription = file.filename, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Text(
            file.filename + if (onPhone) " · no celular" else "",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Tone.Text,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Selecionada", tint = Tone.Accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
        }
    }
}
