package com.example.ui.delivery

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.TetherApp
import com.example.ui.theme.Tone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "Mandar fotos" inside the Express tab: the latest photos, tap the ones the client should see. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressPickSheet(app: TetherApp, alreadyIn: Set<Long>, onDismiss: () -> Unit, onSend: (List<Long>) -> Unit) {
    val photos by remember { app.database.photoDao().observeAll() }.collectAsState(initial = emptyList())
    val chosen = remember { mutableStateListOf<Long>() }
    val shown = photos.filter { it.displayUri != null }.take(120)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tone.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mandar para o Express", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (chosen.isEmpty()) "Toque nas fotos. As que já estão com o cliente têm o sinal verde." else "${chosen.size} escolhidas",
                        style = MaterialTheme.typography.bodySmall, color = if (chosen.isEmpty()) Tone.TextMuted else Tone.Accent,
                    )
                }
                if (chosen.isNotEmpty()) TextButton(onClick = { chosen.clear() }) { Text("Limpar") }
            }
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) {
                Text("Nenhuma foto no celular ainda.", color = Tone.TextMuted, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                ) {
                    items(shown, key = { it.id }) { p ->
                        val picked = p.id in chosen
                        val sent = p.id in alreadyIn
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Tone.SurfaceHigh)
                                .then(if (picked) Modifier.border(3.dp, Tone.Accent, RoundedCornerShape(6.dp)) else Modifier)
                                .clickable { if (picked) chosen.remove(p.id) else chosen.add(p.id) },
                        ) {
                            AsyncImage(model = p.lookUri, contentDescription = p.savedBase, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (picked || sent) {
                                Icon(
                                    Icons.Filled.CheckCircle, if (sent) "Já está no Express" else "Escolhida",
                                    tint = if (picked) Tone.Accent else Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0x66000000)),
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onSend(chosen.toList()) },
                enabled = chosen.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Tone.Accent, contentColor = Tone.OnAccent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 52.dp),
            ) {
                Icon(Icons.Outlined.Send, null)
                Spacer(Modifier.width(8.dp))
                Text(if (chosen.size <= 1) "Mandar ao cliente" else "Mandar ${chosen.size} ao cliente")
            }
        }
    }
}

/**
 * The "Express" action of the gallery, the viewer and the Sessão: the photos go straight to the Express
 * client. With the module off, says so and opens the Express tab instead.
 */
fun sendToExpress(app: TetherApp, context: Context, scope: CoroutineScope, photoIds: List<Long>, onOpenExpress: () -> Unit, done: () -> Unit = {}) {
    scope.launch {
        if (!app.settings.current().expressEnabled) {
            Toast.makeText(context, "Ligue a Fotografia Express na aba Express", Toast.LENGTH_LONG).show()
            onOpenExpress()
            return@launch
        }
        val added = app.express.sendToExpress(photoIds)
        val n = photoIds.size
        Toast.makeText(
            context,
            when {
                added == 0 -> if (n == 1) "Essa foto já está no Express" else "Essas fotos já estão no Express"
                added == 1 -> "1 foto mandada ao Express"
                else -> "$added fotos mandadas ao Express"
            },
            Toast.LENGTH_SHORT,
        ).show()
        done()
    }
}
