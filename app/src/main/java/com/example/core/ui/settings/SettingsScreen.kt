package com.example.core.ui.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.CameraManager
import com.example.core.models.EventModeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(cameraManager: CameraManager) {
    val context = LocalContext.current
    val eventConfig by cameraManager.eventConfig.collectAsState()

    var isEventActive by remember { mutableStateOf(eventConfig.isActive) }
    var eventName by remember { mutableStateOf(eventConfig.eventName) }
    var renamePattern by remember { mutableStateOf(eventConfig.autoRenamePattern) }
    var targetFolder by remember { mutableStateOf(eventConfig.targetFolder) }
    var autoJpg by remember { mutableStateOf(eventConfig.autoDownloadJpg) }
    var autoRaw by remember { mutableStateOf(eventConfig.autoDownloadRaw) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "AUTOMAÇÃO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = "Ajustes de Fluxo",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Event/Studio Mode Toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEventActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isEventActive) Icons.Default.Camera else Icons.Default.CameraEnhance,
                            contentDescription = null,
                            tint = if (isEventActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Modo Evento / Estúdio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "Organize downloads por pasta e renomeie arquivos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isEventActive,
                        onCheckedChange = {
                            isEventActive = it
                            // Save instantly on toggle
                            cameraManager.updateEventConfig(
                                EventModeConfig(
                                    isActive = it,
                                    eventName = eventName,
                                    autoRenamePattern = renamePattern,
                                    targetFolder = targetFolder,
                                    autoDownloadJpg = autoJpg,
                                    autoDownloadRaw = autoRaw
                                )
                            )
                        },
                        modifier = Modifier.testTag("event_mode_switch")
                    )
                }

                AnimatedVisibility(
                    visible = isEventActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Divider()

                        // Event Name
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            label = { Text("Nome do Evento / Sessão") },
                            placeholder = { Text("Ex: Casamento João e Maria") },
                            modifier = Modifier.fillMaxWidth().testTag("event_name_input"),
                            leadingIcon = { Icon(Icons.Default.Celebration, contentDescription = null) }
                        )

                        // Target Subfolder
                        OutlinedTextField(
                            value = targetFolder,
                            onValueChange = { targetFolder = it },
                            label = { Text("Pasta de Destino (/Pictures/Canon50D/)") },
                            placeholder = { Text("Ex: Casamento_Joao") },
                            modifier = Modifier.fillMaxWidth().testTag("target_folder_input"),
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )

                        // File Renaming Pattern
                        OutlinedTextField(
                            value = renamePattern,
                            onValueChange = { renamePattern = it },
                            label = { Text("Máscara de Renomeação") },
                            placeholder = { Text("Ex: CASAMENTO_###") },
                            supportingText = { Text("Use '###' para adicionar uma sequência numérica incremental automática baseada no histórico.") },
                            modifier = Modifier.fillMaxWidth().testTag("rename_pattern_input"),
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Automatic tethering download file types configuration
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Formatos para Download Automático",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Selecione quais arquivos devem ser transferidos automaticamente via cabo USB quando você disparar a câmera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Auto JPG
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Baixar Fotos JPEG", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Imagens prontas para compartilhamento rápido", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Checkbox(
                        checked = autoJpg,
                        onCheckedChange = { autoJpg = it ?: true },
                        modifier = Modifier.testTag("download_jpg_checkbox")
                    )
                }

                // Auto RAW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RawOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Baixar Fotos RAW (.CR2)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Arquivos digitais brutos de alta fidelidade", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Checkbox(
                        checked = autoRaw,
                        onCheckedChange = { autoRaw = it ?: false },
                        modifier = Modifier.testTag("download_raw_checkbox")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Trigger Button: Save
        Button(
            onClick = {
                cameraManager.updateEventConfig(
                    EventModeConfig(
                        isActive = isEventActive,
                        eventName = eventName,
                        autoRenamePattern = renamePattern,
                        targetFolder = targetFolder,
                        autoDownloadJpg = autoJpg,
                        autoDownloadRaw = autoRaw
                    )
                )
                Toast.makeText(context, "Configurações de fluxo salvas com sucesso!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_settings_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Salvar Configurações", fontWeight = FontWeight.Black, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(80.dp)) // padding for bottom bar
    }
}
