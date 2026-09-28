package com.example.ui.preset

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.TetherApp
import com.example.core.data.SettingKeys
import com.example.core.data.Settings
import com.example.core.preset.Preset
import com.example.ui.components.SectionLabel
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of one import, shown in a dialog: what came in and which files could not be read. */
private data class ImportReport(val added: List<String>, val failed: List<Pair<String, String>>)

/**
 * The Presets tab: every preset grouped as in Lightroom, each with a thumbnail of the latest photo
 * (or a sample image) under its look. Import from DNG/XMP/LRTemplate/ZIP, create from scratch,
 * edit, duplicate, export as XMP and pick the one the session applies to new frames.
 */
@Composable
fun PresetsScreen(app: TetherApp, onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    val settings by app.settings.settings.collectAsState(initial = Settings())
    var report by remember { mutableStateOf<ImportReport?>(null) }
    var importing by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Preset?>(null) }

    // Thumbnails use the newest photo so the list shows how each look lands on today's light.
    val sample by produceState<String?>(null) {
        value = app.database.photoDao().latestDisplayUri() ?: SampleImage.uri(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val added = mutableListOf<String>()
            val failed = mutableListOf<Pair<String, String>>()
            uris.forEach { uri ->
                val name = displayName(context, uri)
                runCatching {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: error("não foi possível abrir o arquivo")
                    app.presets.import(name, bytes)
                }.onSuccess { list -> added += list.map { it.name } }
                    .onFailure { failed += name to (it.message ?: "formato não reconhecido") }
            }
            importing = false
            report = ImportReport(added, failed)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Presets", style = MaterialTheme.typography.titleLarge, color = Tone.Text)
            val current = presets.firstOrNull { it.id == settings.sessionPresetId }
            Text(
                if (current != null && settings.applyPresetToNew) "Na sessão: ${current.name}" else "Nenhum preset aplicado nas fotos novas",
                style = MaterialTheme.typography.bodySmall,
                color = if (current != null && settings.applyPresetToNew) Tone.Accent else Tone.TextMuted,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (importing) "  Lendo…" else "  Importar", maxLines = 1)
                }
                OutlinedButton(onClick = { onEdit(0L) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Tone.Text)
                    Text("  Criar do zero", color = Tone.Text, maxLines = 1)
                }
            }
            Text(
                "Aceita .dng, .xmp, .lrtemplate e .zip com vários presets.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (presets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Nenhum preset ainda. Importe um arquivo ou crie o seu.", color = Tone.TextMuted)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
            presets.groupBy { it.group }.forEach { (group, list) ->
                item(key = "g:$group") { SectionLabel(group, Modifier.padding(top = 16.dp)) }
                items(list, key = { it.id }) { preset ->
                    PresetRow(
                        app = app,
                        preset = preset,
                        sample = sample,
                        inSession = preset.id == settings.sessionPresetId,
                        onOpen = { onEdit(preset.id) },
                        onToggleSession = {
                            scope.launch {
                                if (preset.id == settings.sessionPresetId) {
                                    app.settings.set(SettingKeys.sessionPresetId, -1L)
                                } else {
                                    app.settings.set(SettingKeys.sessionPresetId, preset.id)
                                    app.settings.set(SettingKeys.applyPresetToNew, true)
                                }
                                app.settings.set(SettingKeys.sessionAdjust, "")
                            }
                        },
                        onDuplicate = { scope.launch { app.presets.duplicate(preset) } },
                        onExport = {
                            scope.launch {
                                val file = app.presets.exportFile(preset)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/octet-stream")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, "Exportar ${file.name}"))
                            }
                        },
                        onDelete = { toDelete = preset },
                    )
                }
            }
        }
    }

    report?.let { r ->
        AlertDialog(
            onDismissRequest = { report = null },
            containerColor = Tone.Surface,
            title = { Text(if (r.added.isNotEmpty()) "Preset importado" else "Não deu para importar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.added.isNotEmpty()) {
                        Text("${r.added.size} adicionado(s) em Importados:", color = Tone.Text)
                        r.added.take(8).forEach { Text("• $it", color = Tone.TextMuted) }
                        if (r.added.size > 8) Text("… e mais ${r.added.size - 8}", color = Tone.TextMuted)
                    }
                    r.failed.forEach { (file, why) -> Text("$file: $why", color = Tone.Error) }
                }
            },
            confirmButton = { TextButton(onClick = { report = null }) { Text("OK") } },
        )
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Tone.Surface,
            title = { Text("Apagar \"${p.name}\"?") },
            text = { Text("As fotos que usam este preset voltam ao original. Os arquivos das fotos não são alterados.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (p.id == settings.sessionPresetId) app.settings.set(SettingKeys.sessionPresetId, -1L)
                        app.presets.delete(p)
                    }
                    toDelete = null
                }) { Text("Apagar", color = Tone.Error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PresetRow(
    app: TetherApp,
    preset: Preset,
    sample: String?,
    inSession: Boolean,
    onOpen: () -> Unit,
    onToggleSession: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onOpen).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetThumb(app, preset, sample, Modifier.size(64.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(preset.name, color = Tone.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (inSession) "Na sessão" else sourceLabel(preset.source),
                style = MaterialTheme.typography.bodySmall,
                color = if (inSession) Tone.Accent else Tone.TextMuted,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Opções", tint = Tone.TextMuted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tone.SurfaceHigh) {
                DropdownMenuItem(
                    text = { Text(if (inSession) "Parar de usar na sessão" else "Usar na sessão") },
                    onClick = { menu = false; onToggleSession() },
                )
                DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Exportar XMP") }, onClick = { menu = false; onExport() })
                DropdownMenuItem(text = { Text("Apagar", color = Tone.Error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** A small render of [sample] with the preset's look. */
@Composable
fun PresetThumb(app: TetherApp, preset: Preset, sample: String?, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(sample, preset.settings) {
        bitmap = sample?.let { runCatching { app.looks.thumb(it, preset.settings) }.getOrNull() }
    }
    Box(modifier.clip(MaterialTheme.shapes.extraSmall).background(Tone.SurfaceHigh)) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun sourceLabel(source: String) = when (source) {
    "dng" -> "Importado de DNG"
    "xmp" -> "Importado de XMP"
    "lrtemplate" -> "Importado de LRTemplate"
    "jr_link" -> "Pack Jr_Link"
    else -> "Criado no app"
}

private fun displayName(context: android.content.Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "preset"
