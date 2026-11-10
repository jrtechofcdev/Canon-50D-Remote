package com.example.ui.session

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.TetherApp
import com.example.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Which session new photos go to, always in sight on the Session screen. Tapping it opens
 * [SessionNameSheet]. Without a name the photos go to "Geral", shown in grey as a reminder.
 */
@Composable
fun SessionChip(name: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val named = name.isNotBlank()
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, Tone.Line, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "Trocar a sessão", onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Badge, contentDescription = null, tint = if (named) Tone.Text else Tone.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Sessão", style = MaterialTheme.typography.labelSmall, color = Tone.TextMuted)
            Text(
                if (named) name.trim() else "Sem nome (Geral)",
                style = MaterialTheme.typography.titleSmall,
                color = if (named) Tone.Text else Tone.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // A negative count hides it (Ajustes shows the name only).
        if (count >= 0) Text(
            if (count == 1) "1 foto" else "$count fotos",
            style = MaterialTheme.typography.labelMedium,
            color = Tone.TextMuted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = Tone.TextMuted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Names the session new photos go to. The name is saved only on confirm, so a photo that arrives
 * while typing never lands in half a name. [startNew] opens with an empty field, for the next client.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionNameSheet(app: TetherApp, current: String, startNew: Boolean, onStarted: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(if (startNew) "" else current) }
    val recent by produceState(emptyList<String>()) {
        value = app.database.photoDao().recentSessions(9).filter { it != "Geral" && it != current.trim() }.take(8)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (startNew) runCatching { focus.requestFocus() } }

    val typed = name.trim()
    val same = typed == current.trim()
    val confirm = {
        scope.launch {
            if (!same || startNew) app.startSession(typed)
            onStarted(typed)
        }
        Unit
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (startNew) "Nova sessão" else "Sessão / cliente", style = MaterialTheme.typography.titleMedium)
            Text(
                "As próximas fotos vão para esta sessão: subpasta, nome dos arquivos, resumo e entrega. As fotos já recebidas ficam onde estão.",
                style = MaterialTheme.typography.bodySmall,
                color = Tone.TextMuted,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do cliente ou do evento") },
                placeholder = { Text("Ex.: Casamento Ana e Leo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Tone.Accent, unfocusedBorderColor = Tone.Line),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (recent.isNotEmpty()) {
                Text("Usadas há pouco", style = MaterialTheme.typography.labelMedium, color = Tone.TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { r ->
                        val picked = typed == r
                        Text(
                            r,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (picked) Tone.Accent else Tone.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .border(1.dp, if (picked) Tone.Accent else Tone.Line, MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { name = r }
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = confirm,
                    enabled = !same || startNew,
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        when {
                            typed.isEmpty() -> "Seguir sem nome"
                            typed in recent -> "Voltar para esta sessão"
                            else -> "Começar sessão"
                        },
                    )
                }
            }
        }
    }
}

/**
 * Asked after the session changes while the Entrega sends photos by itself to another client:
 * without it, the next client's photos would still go to the previous client's gallery.
 */
@Composable
fun FollowSessionDialog(session: String, previousClient: String, onFollow: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = Tone.Surface,
        title = { Text("Mandar as próximas fotos para $session?") },
        text = {
            Text(
                "A Entrega está mandando as fotos novas para $previousClient. Trocar cria (ou reabre) o cliente $session na Entrega.",
                color = Tone.TextMuted,
            )
        },
        confirmButton = {
            Button(
                onClick = onFollow,
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
            ) { Text("Mandar para $session") }
        },
        dismissButton = { TextButton(onClick = onKeep) { Text("Manter $previousClient") } },
    )
}
