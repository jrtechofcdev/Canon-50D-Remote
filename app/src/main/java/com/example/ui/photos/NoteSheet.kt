package com.example.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.TetherApp
import com.example.core.data.Photo
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/** Text and voice note for one photo ("mãe da noiva", "refazer com flash"). */
@Composable
fun NoteSheet(app: TetherApp, photo: Photo, onDismiss: () -> Unit) = NoteSheet(app, listOf(photo), onDismiss)

/**
 * The same note for [photos] (one, or several picked in the gallery). With a notes folder chosen,
 * each photo also gets its folder there: nota.txt and audio_N.m4a.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(app: TetherApp, photos: List<Photo>, onDismiss: () -> Unit) {
    val photo = photos.first()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = remember { com.example.core.notes.NotesExport(context) }
    val sameText = photos.map { it.note.orEmpty() }.distinct().singleOrNull().orEmpty()
    var text by remember { mutableStateOf(sameText) }
    var audio by remember { mutableStateOf(photos.map { it.audioNote }.distinct().singleOrNull()) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hint = if (ok) "Segure o microfone para gravar." else "Sem permissão do microfone, só dá para anotar por texto."
    }

    DisposableEffect(Unit) { onDispose { app.notes.stop(); app.notes.stopPlayback() } }

    fun save() = scope.launch {
        val note = text.trim().ifEmpty { null }
        val folder = app.settings.current().notesFolder
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            photos.forEach { p ->
                app.database.photoDao().setNote(p.id, note)
                if (folder != null && note != p.note) export.writeText(folder, p, note)
            }
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = { save() }, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (photos.size == 1) "Nota de ${photo.savedBase}" else "Nota para ${photos.size} fotos",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                placeholder = { Text("Ex.: mãe da noiva, refazer com flash") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (recording) Tone.Error else Tone.SurfaceHigh)
                        .semantics { contentDescription = "Segure para gravar" }
                        .pointerInput(photos.map { it.id }) {
                            detectTapGestures(onPress = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                recording = app.notes.start(photo.savedBase)
                                if (!recording) hint = "Não foi possível usar o microfone."
                                tryAwaitRelease()
                                if (recording) {
                                    recording = false
                                    val file = app.notes.stop()
                                    if (file == null) {
                                        hint = "Segure o botão enquanto fala."
                                    } else {
                                        // One photo keeps one voice note in the app; the folder keeps every take.
                                        if (photos.size == 1) audio?.let(app.notes::delete)
                                        audio = file.absolutePath
                                        hint = null
                                        val folder = app.settings.current().notesFolder
                                        photos.forEach { p ->
                                            app.database.photoDao().setAudioNote(p.id, file.absolutePath)
                                            if (folder != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { export.addAudio(folder, p, file) }
                                        }
                                    }
                                }
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Mic, contentDescription = null, tint = Tone.Text) }
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            recording -> "Gravando… solte para salvar"
                            audio != null -> "Nota de voz gravada"
                            else -> "Segure para gravar uma nota de voz"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (recording) Tone.Error else Tone.Text,
                    )
                    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted) }
                }
                audio?.let { path ->
                    IconButton(onClick = {
                        if (playing) { app.notes.stopPlayback(); playing = false }
                        else { playing = true; app.notes.play(path) { playing = false } }
                    }) { Icon(if (playing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, contentDescription = if (playing) "Parar" else "Ouvir") }
                    IconButton(onClick = {
                        app.notes.stopPlayback(); playing = false
                        app.notes.delete(path); audio = null
                        scope.launch { photos.forEach { app.database.photoDao().setAudioNote(it.id, null) } }
                    }) { Icon(Icons.Outlined.Delete, contentDescription = "Apagar nota de voz", tint = Tone.TextMuted) }
                }
            }
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Salvar nota") }
        }
    }
}
