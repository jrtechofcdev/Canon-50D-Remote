package com.example.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.TetherApp
import com.example.core.data.ProfileIcon
import com.example.core.data.SettingsProfile
import com.example.core.data.SettingsProfiles
import com.example.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun ProfileIcon.vector(): ImageVector = when (this) {
    ProfileIcon.Church -> Icons.Outlined.Church
    ProfileIcon.Rings -> Icons.Outlined.Diamond
    ProfileIcon.Outdoor -> Icons.Outlined.Park
    ProfileIcon.Party -> Icons.Outlined.Celebration
    ProfileIcon.Studio -> Icons.Outlined.Lightbulb
    ProfileIcon.Camera -> Icons.Outlined.PhotoCamera
    ProfileIcon.Star -> Icons.Outlined.Star
}

/** Ajustes → Perfis e backup: ready-made sets, the photographer's own, and the backup file. */
@Composable
fun ProfilesSettings(app: TetherApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by app.settings.profiles.collectAsState(initial = emptyList())
    val active by app.settings.activeProfile.collectAsState(initial = "")
    var naming by remember { mutableStateOf<SettingsProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var restoreAsk by remember { mutableStateOf<String?>(null) }

    fun apply(p: SettingsProfile) = scope.launch {
        app.settings.applyProfile(p)
        Toast.makeText(context, "Perfil \"${p.name}\" aplicado", Toast.LENGTH_SHORT).show()
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = app.appBackup.export(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup dos ajustes salvo" else "Não deu para salvar o backup", Toast.LENGTH_LONG).show()
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            restoreAsk = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (restoreAsk == null) Toast.makeText(context, "Não deu para abrir o arquivo", Toast.LENGTH_LONG).show()
        }
    }

    InfoText("Um perfil guarda os ajustes de um tipo de trabalho. Na correria, aplique pelo chip Perfil na Sessão e o app fica pronto em um toque.")

    SettingsGroup("Perfis prontos", Icons.Outlined.Star) {
        SettingsProfiles.starters.forEach { p -> ProfileRow(p, p.name == active, onApply = { apply(p) }) }
    }

    SettingsGroup("Meus perfis", Icons.Outlined.Save) {
        if (saved.isEmpty()) InfoText("Deixe os ajustes do jeito que você gosta para um evento e salve aqui.")
        saved.forEach { p ->
            ProfileRow(
                p, p.name == active,
                onApply = { apply(p) },
                onUpdate = {
                    scope.launch {
                        app.settings.saveCurrentAs(p.name, p.icon)
                        Toast.makeText(context, "\"${p.name}\" atualizado com os ajustes atuais", Toast.LENGTH_SHORT).show()
                    }
                },
                onRename = { naming = p },
                onDelete = { scope.launch { app.settings.saveProfiles(saved.filter { it.name != p.name }) } },
            )
        }
        Button(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
        ) {
            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Salvar os ajustes atuais como perfil")
        }
        InfoText("Pastas, PIN, nome da sessão e contatos não entram no perfil; ficam como estão.")
    }

    SettingsGroup("Backup dos ajustes", Icons.Outlined.Download) {
        InfoText("Um arquivo .json com todos os ajustes, seus perfis e seus presets (com categorias e favoritos). Guarde no Drive, no pendrive ou mande para outro celular.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportFile.launch("JR_C50D_Sync_ajustes_$date.json")
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salvar")
            }
            OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restaurar")
            }
        }
    }

    if (creating) {
        ProfileNameDialog("Novo perfil", "", ProfileIcon.Camera.id, onDismiss = { creating = false }) { name, icon ->
            scope.launch {
                app.settings.saveCurrentAs(name, icon)
                Toast.makeText(context, "Perfil \"$name\" salvo", Toast.LENGTH_SHORT).show()
            }
            creating = false
        }
    }
    naming?.let { p ->
        ProfileNameDialog("Renomear perfil", p.name, p.icon, onDismiss = { naming = null }) { name, icon ->
            scope.launch {
                val renamed = p.copy(name = name.trim(), icon = icon)
                app.settings.saveProfiles(saved.map { if (it.name == p.name) renamed else it }.distinctBy { it.name })
            }
            naming = null
        }
    }
    restoreAsk?.let { text ->
        AlertDialog(
            onDismissRequest = { restoreAsk = null },
            containerColor = Tone.Surface,
            icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
            title = { Text("Restaurar este backup?") },
            text = { Text("Os ajustes atuais são trocados pelos do arquivo. Perfis e presets do arquivo são somados aos que você já tem. As pastas escolhidas neste celular continuam as mesmas.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreAsk = null
                    scope.launch {
                        val message = runCatching { app.appBackup.restore(text) }.fold(
                            onSuccess = { "Restaurado: ${it.settings} ajustes, ${it.profiles} perfis, ${it.presets} presets novos" },
                            onFailure = { it.message ?: "Arquivo inválido" },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { restoreAsk = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProfileRow(
    p: SettingsProfile,
    active: Boolean,
    onApply: () -> Unit,
    onUpdate: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onApply).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (active) Tone.Accent else Tone.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ProfileIcon.of(p.icon).vector(), contentDescription = null, tint = if (active) Tone.OnAccent else Tone.Text, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (active) "Em uso · ${p.values.size} ajustes" else "${p.values.size} ajustes",
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Tone.Accent else Tone.TextMuted,
            )
        }
        if (active) Icon(Icons.Outlined.CheckCircle, contentDescription = "Em uso", tint = Tone.Accent)
        else TextButton(onClick = onApply) { Text("Aplicar", color = Tone.Accent) }
        if (onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Opções", tint = Tone.TextMuted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tone.SurfaceHigh) {
                    onUpdate?.let {
                        DropdownMenuItem(text = { Text("Atualizar com os ajustes atuais") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; it() })
                    }
                    onRename?.let {
                        DropdownMenuItem(text = { Text("Renomear e trocar ícone") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menu = false; it() })
                    }
                    DropdownMenuItem(
                        text = { Text("Apagar", color = Tone.Error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Tone.Error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(title: String, initialName: String, initialIcon: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tone.Surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nome") },
                    placeholder = { Text("Ex.: Casamento na igreja") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileIcon.entries.forEach { option ->
                        val selected = option.id == icon
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(if (selected) Tone.Accent else Tone.SurfaceHigh).clickable { icon = option.id },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(option.vector(), contentDescription = option.label, tint = if (selected) Tone.OnAccent else Tone.Text)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), icon) }, enabled = name.isNotBlank()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Quick switch from the Session: every profile, one tap applies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(app: TetherApp, onManage: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by app.settings.profiles.collectAsState(initial = emptyList())
    val active by app.settings.activeProfile.collectAsState(initial = "")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tone.Surface) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("Perfil de ajustes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            (saved + SettingsProfiles.starters).forEach { p ->
                ProfileRow(p, p.name == active, onApply = {
                    scope.launch {
                        app.settings.applyProfile(p)
                        Toast.makeText(context, "Perfil \"${p.name}\" aplicado", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                })
            }
            TextButton(onClick = { onDismiss(); onManage() }) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Criar e gerenciar perfis")
            }
        }
    }
}
