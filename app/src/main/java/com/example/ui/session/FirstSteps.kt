package com.example.ui.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.data.Settings
import com.example.ui.theme.Tone

/**
 * What to set once before the first event, on the Session until it is closed (the folders have their
 * own notice right above it). These steps used to be only in the README, and notifications were asked
 * for at launch, during the opening, with no reason given.
 */
@Composable
fun FirstStepsCard(settings: Settings, onDone: () -> Unit) {
    if (settings.firstStepsDone) return
    val context = LocalContext.current
    fun notificationsOn() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notified by remember { mutableStateOf(notificationsOn()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notified = it }

    Spacer(Modifier.height(12.dp))
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Tone.Surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Primeiros passos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fechar") }
        }
        // Short lines with the action on the same line, so the card doesn't push the last photo off screen.
        Step(false, "50D: Menu → Desligamento automático → Desativado")
        Step(false, "No aviso da USB, marque \"Usar por padrão\"")
        Step(notified, "Avisos de cabo solto, cartão cheio e celular sem espaço") {
            if (!notified) TextButton(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Permitir") }
        }
    }
}

@Composable
private fun Step(done: Boolean, text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (done) "Feito" else null,
            tint = if (done) Tone.Accent else Tone.TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (done) Tone.TextMuted else Tone.Text, modifier = Modifier.weight(1f))
        if (!done) action?.invoke()
    }
}
