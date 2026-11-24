package com.example.ui.church

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.core.church.ChurchPerm
import com.example.core.church.ChurchRules
import com.example.core.church.ChurchTheme
import com.example.core.church.Congregation
import com.example.ui.components.JrButton
import com.example.ui.components.JrButtonKind
import com.example.ui.components.JrChip
import com.example.ui.components.SheetHeader
import com.example.ui.theme.Space
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * The congregations: the list, and the editor of one (name, colours, line, logo, look of the "pronta" and
 * what newcomers may do). Every new event copies these, so a change here shows from the next event on.
 * [editing]: open straight on that one (a new blank one with 0). [onSaved] gets the id after saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CongregationsSheet(app: TetherApp, onDismiss: () -> Unit, editing: Long? = null, onSaved: (Long) -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        CongregationsContent(app, onDismiss, editing, onSaved)
    }
}

/** What the sheet shows; on its own so the screenshots can draw it. */
@Composable
internal fun CongregationsContent(app: TetherApp, onDismiss: () -> Unit, editing: Long? = null, onSaved: (Long) -> Unit = {}) {
    val congregations by app.church.repo.dao.observeCongregations().collectAsState(initial = emptyList())
    var open by remember { mutableStateOf(editing) }
    run {
        Column(Modifier.padding(horizontal = Space.l).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            val id = open
            if (id == null) {
                SheetHeader("Congregações", subtitle = "Cada evento novo começa com o tema e as regras da congregação", onClose = onDismiss)
                if (congregations.isEmpty()) Text("Nenhuma ainda.", color = Tone.TextMuted, modifier = Modifier.padding(vertical = Space.m))
                congregations.forEach { c -> CongregationRow(app, c) { open = c.id } }
                JrButton("Nova congregação", onClick = { open = 0L }, icon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth().padding(vertical = Space.m))
            } else {
                val start = congregations.firstOrNull { it.id == id } ?: Congregation(name = "", createdAt = 0)
                // Waits for the list when an existing one is asked before Room answered.
                if (id != 0L && start.id == 0L) return@Column
                CongregationEditor(
                    app, start,
                    onBack = { if (editing != null) onDismiss() else open = null },
                    onSaved = { saved -> onSaved(saved); if (editing != null) onDismiss() else open = null },
                )
            }
        }
    }
}

@Composable
private fun CongregationRow(app: TetherApp, c: Congregation, onClick: () -> Unit) {
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Dot(c.accent)
        Spacer(Modifier.width(4.dp))
        Dot(c.secondary)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    presets.firstOrNull { it.id == c.presetId }?.name ?: "Pronta com o look da foto",
                    if (c.defaultPerms and ChurchPerm.DOWNLOAD_ANY == 0) "quem entrar só vê" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Tone.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("Editar", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
    }
}

@Composable
private fun Dot(hex: String) {
    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(ChurchTheme.color(hex) ?: ChurchTheme.ACCENT))))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CongregationEditor(app: TetherApp, start: Congregation, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by app.presets.presets.collectAsState(initial = emptyList())
    var c by remember(start.id) { mutableStateOf(start) }
    var deleting by remember { mutableStateOf(false) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            c = c.copy(logoUri = uri.toString())
        }
    }
    val name = ChurchRules.cleanName(c.name)

    SheetHeader(if (start.id == 0L) "Nova congregação" else start.name, subtitle = "Vale para os próximos eventos dela", onClose = onBack)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.padding(bottom = Space.l)) {
        OutlinedTextField(
            value = c.name, onValueChange = { c = c.copy(name = it.take(40)) },
            label = { Text("Nome") }, placeholder = { Text("AD Vila Nova") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Cor principal", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        Swatches(c.accent) { c = c.copy(accent = it) }
        Text("Cor de destaque", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        Swatches(c.secondary) { c = c.copy(secondary = it) }
        OutlinedTextField(
            value = c.motto.orEmpty(), onValueChange = { c = c.copy(motto = it.take(80)) },
            label = { Text("Frase (tema, versículo)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            JrButton(if (c.logoUri == null) "Escolher logo" else "Trocar logo", onClick = { pickLogo.launch(arrayOf("image/*")) }, kind = JrButtonKind.Secondary, icon = Icons.Outlined.Image)
            if (c.logoUri != null) JrButton("Tirar logo", onClick = { c = c.copy(logoUri = null) }, kind = JrButtonKind.Ghost)
        }
        Text("Versão pronta", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            JrChip("O da foto", c.presetId == null, onClick = { c = c.copy(presetId = null, adjust = null) })
            presets.forEach { p -> JrChip(p.name, c.presetId == p.id, onClick = { c = c.copy(presetId = p.id, adjust = null) }) }
        }
        Text("Quem entrar pode", style = MaterialTheme.typography.labelLarge, color = Tone.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            PERMS.forEach { (flag, label) ->
                val on = ChurchPerm.has(c.defaultPerms, flag)
                JrChip(label, on, onClick = { c = c.copy(defaultPerms = ChurchPerm.with(c.defaultPerms, flag, !on)) })
            }
        }
        JrButton(
            if (name == null) "Escreva o nome (2 letras ou mais)" else "Salvar",
            enabled = name != null,
            onClick = {
                scope.launch {
                    val id = app.church.repo.saveCongregation(c.copy(name = name!!, motto = c.motto?.trim()?.ifBlank { null }))
                    onSaved(id)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (start.id != 0L) JrButton("Apagar congregação", onClick = { deleting = true }, kind = JrButtonKind.Danger, modifier = Modifier.fillMaxWidth())
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Apagar ${start.name}?") },
        text = { Text("Sai da lista. Os eventos dela continuam, com o nome, as cores e o look que já tinham.") },
        confirmButton = { TextButton(onClick = { deleting = false; scope.launch { app.church.repo.deleteCongregation(start.id); onBack() } }) { Text("Apagar", color = Tone.Error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
    )
}
