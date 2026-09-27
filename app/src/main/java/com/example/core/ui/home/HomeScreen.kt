package com.example.core.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.CameraManager
import com.example.core.download.DownloadManager
import com.example.core.models.*

@Composable
fun HomeScreen(
    cameraManager: CameraManager,
    downloadManager: DownloadManager,
    onNavigateToCamera: () -> Unit
) {
    val connectionState by cameraManager.connectionState.collectAsState()
    val properties by cameraManager.cameraProperties.collectAsState()
    val eventConfig by cameraManager.eventConfig.collectAsState()
    val downloadTask by downloadManager.currentTask.collectAsState()
    val intervalometer by cameraManager.intervalometer.collectAsState()
    val bulb by cameraManager.bulb.collectAsState()

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showBulbDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // Header inspired by Bold Typography
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONNECTED DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "EOS 50D",
                        style = MaterialTheme.typography.displayMedium, // Black weight tracking-tight
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Decorative Camera Icon in circular terracotta container
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "EOS 50D Remote",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Connection badges with exact Design HTML specifications
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "USB 2.0 PTP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "FW ${properties.firmwareVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection Card with rounded-[32px] and a thick 4dp primaryContainer border like the design HTML
            ConnectionStateCard(
                connectionState = connectionState,
                onConnectRealUsb = { cameraManager.tryConnectUsb() },
                onResetSimulator = { cameraManager.resetToSimulator() },
                onDisconnect = { cameraManager.disconnect() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Camera status indicators (Battery, CF Card)
            if (connectionState != CameraManager.ConnectionState.DISCONNECTED) {
                StatusMetricsRow(properties = properties)

                Spacer(modifier = Modifier.height(16.dp))

                // Active Download Progress Bar
                AnimatedVisibility(
                    visible = downloadTask != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    downloadTask?.let { task ->
                        DownloadProgressCard(task = task)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Tethering Quick Trigger & Modes Row
                TetheringActionsCard(
                    properties = properties,
                    eventConfig = eventConfig,
                    intervalometer = intervalometer,
                    bulb = bulb,
                    onTriggerCapture = { cameraManager.capturePhoto() },
                    onToggleLiveView = { cameraManager.toggleLiveView() },
                    onOpenIntervalometer = { showIntervalDialog = true },
                    onOpenBulb = { showBulbDialog = true },
                    onStopIntervalometer = { cameraManager.stopIntervalometer() },
                    onStopBulb = { cameraManager.stopBulb() },
                    onNavigateToCamera = onNavigateToCamera
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Event Mode Info
                EventSessionCard(eventConfig = eventConfig)
            } else {
                // Disconnected Placeholder State (chunky styled)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                        ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.UsbOff,
                            contentDescription = "USB Desconectado",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nenhuma câmera detectada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Por favor, conecte a sua Canon EOS 50D utilizando um cabo USB OTG ou utilize o Modo Simulador clicando em 'Ativar Simulador' acima para testar as automações.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Padding for navigation bar
        }
    }

    // Intervalometer Dialog Setup
    if (showIntervalDialog) {
        IntervalometerDialog(
            onDismiss = { showIntervalDialog = false },
            onConfirm = { interval, shots ->
                cameraManager.startIntervalometer(interval, shots)
                showIntervalDialog = false
            }
        )
    }

    // Bulb Dialog Setup
    if (showBulbDialog) {
        BulbDialog(
            onDismiss = { showBulbDialog = false },
            onConfirm = { seconds ->
                cameraManager.startBulb(seconds)
                showBulbDialog = false
            }
        )
    }
}

@Composable
fun ConnectionStateCard(
    connectionState: CameraManager.ConnectionState,
    onConnectRealUsb: () -> Unit,
    onResetSimulator: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (statusLabel, statusColor, statusDesc) = when (connectionState) {
        CameraManager.ConnectionState.CONNECTED_REAL_USB -> Triple(
            "CONECTADO (USB OTG)",
            Color(0xFF4CAF50),
            "Comunicação direta por hardware de barramento USB estabelecida com a Canon EOS 50D."
        )
        CameraManager.ConnectionState.CONNECTED_SIMULATOR -> Triple(
            "MODO SIMULADOR",
            Color(0xFF8F4C38),
            "Câmera simulada ativa para desenvolvimento, testes offline e simulação de disparos."
        )
        CameraManager.ConnectionState.DISCONNECTED -> Triple(
            "DESCONECTADO",
            Color(0xFFBA1A1A),
            "Sem conexão ativa. Conecte o cabo USB OTG na câmera."
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 4.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
            )
            .testTag("connection_state_card"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                    )
                }

                if (connectionState != CameraManager.ConnectionState.DISCONNECTED) {
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Desconectar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connectionState != CameraManager.ConnectionState.CONNECTED_REAL_USB) {
                    Button(
                        onClick = onConnectRealUsb,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("connect_usb_button"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detectar USB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (connectionState == CameraManager.ConnectionState.DISCONNECTED) {
                    OutlinedButton(
                        onClick = onResetSimulator,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("activate_simulator_button"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ativar Simulador", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusMetricsRow(properties: CameraProperties) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Battery status Card
        Card(
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "BATTERY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Icon(
                        imageVector = if (properties.batteryLevel > 20) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (properties.batteryLevel > 20) Color(0xFF4CAF50) else Color(0xFFBA1A1A),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${properties.batteryLevel}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "FW: ${properties.firmwareVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // CF Card Status Card
        Card(
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "STORAGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Icon(
                        imageVector = Icons.Default.SdCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", properties.freeSpaceGb),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "GB",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Disparos: ${properties.totalPhotosCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DownloadProgressCard(task: DownloadManager.DownloadTask) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AUTO-DOWNLOAD: ON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = task.filename,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "${(task.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.CircleShape),
                color = Color.White,
                trackColor = Color.Transparent
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Transferindo para celular...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", task.speedMbS)} MB/s",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun TetheringActionsCard(
    properties: CameraProperties,
    eventConfig: EventModeConfig,
    intervalometer: IntervalometerConfig,
    bulb: BulbConfig,
    onTriggerCapture: () -> Unit,
    onToggleLiveView: () -> Unit,
    onOpenIntervalometer: () -> Unit,
    onOpenBulb: () -> Unit,
    onStopIntervalometer: () -> Unit,
    onStopBulb: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TETHERING & DISPAROS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Primary shoot button
            Button(
                onClick = onTriggerCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("shoot_photo_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disparar Câmera", fontWeight = FontWeight.Black, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Accessory action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live View toggle
                OutlinedButton(
                    onClick = onToggleLiveView,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = if (properties.isLiveViewActive) Icons.Filled.Tv else Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = if (properties.isLiveViewActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (properties.isLiveViewActive) "LV Ligado" else "Iniciar LV",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Control panel shortcut
                OutlinedButton(
                    onClick = onNavigateToCamera,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Parâmetros",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Parameters Mock Showcase row inspired by the buttons grid at the bottom of the Design HTML!
            // It showcases current ISO, Aperture and Shutter Speed in a premium bold grid of 3 keys.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ISO Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ISO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = properties.iso,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // AV Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "AV",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = properties.aperture,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // TV Badge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TV",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = properties.shutterSpeed,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Divider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "DISPAROS AUTOMÁTICOS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Intervalometer & Bulb controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Intervalometer trigger
                Button(
                    onClick = {
                        if (intervalometer.isRunning) onStopIntervalometer() else onOpenIntervalometer()
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (intervalometer.isRunning) Color(0xFFBA1A1A) else MaterialTheme.colorScheme.secondary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Timelapse, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (intervalometer.isRunning) "${intervalometer.currentShot}/${intervalometer.totalShots}" else "Intervalômetro",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bulb Trigger
                Button(
                    onClick = {
                        if (bulb.isRunning) onStopBulb() else onOpenBulb()
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bulb.isRunning) Color(0xFF9C27B0) else MaterialTheme.colorScheme.tertiary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (bulb.isRunning) "Expondo ${bulb.elapsedSeconds}s" else "Modo Bulb",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EventSessionCard(eventConfig: EventModeConfig) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (eventConfig.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (eventConfig.isActive) Icons.Default.EventAvailable else Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = if (eventConfig.isActive) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (eventConfig.isActive) "SESSÃO DE EVENTO ATIVA" else "SESSÃO DE EVENTO INATIVA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (eventConfig.isActive) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (eventConfig.isActive) {
                Text(
                    text = eventConfig.eventName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Regra de Renomeação: ${eventConfig.autoRenamePattern}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Pasta de Destino: /Pictures/Canon50D/${eventConfig.targetFolder}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Nenhum evento ativo. Todas as fotos tiradas serão salvas na pasta padrão '/Pictures/Canon50D/' utilizando os nomes originais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
fun IntervalometerDialog(
    onDismiss: () -> Unit,
    onConfirm: (intervalSeconds: Int, totalShots: Int) -> Unit
) {
    var interval by remember { mutableStateOf("5") }
    var shots by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Intervalômetro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("Intervalo entre disparos (segundos)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shots,
                    onValueChange = { shots = it },
                    label = { Text("Total de fotos a disparar") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val intervalInt = interval.toIntOrNull() ?: 5
                val shotsInt = shots.toIntOrNull() ?: 100
                onConfirm(intervalInt, shotsInt)
            }) {
                Text("Iniciar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun BulbDialog(
    onDismiss: () -> Unit,
    onConfirm: (exposureSeconds: Int) -> Unit
) {
    var seconds by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Exposição Bulb") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = seconds,
                    onValueChange = { seconds = it },
                    label = { Text("Tempo de exposição (segundos)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val secInt = seconds.toIntOrNull() ?: 30
                onConfirm(secInt)
            }) {
                Text("Iniciar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
