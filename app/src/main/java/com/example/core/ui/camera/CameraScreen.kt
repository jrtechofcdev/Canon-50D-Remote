package com.example.core.ui.camera

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.camera.CameraManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(cameraManager: CameraManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val connectionState by cameraManager.connectionState.collectAsState()
    val properties by cameraManager.cameraProperties.collectAsState()
    val liveViewFrame by cameraManager.liveViewFrame.collectAsState()

    // Screen adjustments
    var activeGrid by remember { mutableStateOf(0) } // 0 = None, 1 = 3x3 Grid, 2 = Golden Ratio
    var zoomLevel by remember { mutableStateOf(1f) } // 1f, 5f, 10f
    var rotationAngle by remember { mutableStateOf(0f) } // 0, 90, 180, 270

    // Parameter Dialog States
    var selectedPropertyToEdit by remember { mutableStateOf<String?>(null) } // "ISO", "Aperture", etc.

    val propertiesList = listOf(
        PropertyItem("ISO", properties.iso, listOf("100", "200", "400", "800", "1600", "3200", "6400", "12800 (H)")),
        PropertyItem("Abertura", properties.aperture, listOf("f/1.4", "f/1.8", "f/2.8", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16", "f/22")),
        PropertyItem("Exposição", properties.shutterSpeed, listOf("1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/15", "1/8", "1/4", "1/2", "1\"", "2\"", "5\"", "10\"", "30\"", "Bulb")),
        PropertyItem("B. de Branco", properties.whiteBalance, listOf("Auto", "Luz Diária", "Sombra", "Nublado", "Tungstênio", "Fluorescente", "Flash", "Personalizado", "Kelvin")),
        PropertyItem("Comp. EV", properties.exposureCompensation, listOf("-2.0", "-1.3", "-1.0", "-0.7", "-0.3", "0", "+0.3", "+0.7", "+1.0", "+1.3", "+2.0")),
        PropertyItem("Qualidade", properties.imageQuality, listOf("RAW", "RAW + JPG Fino", "JPG Grande Fino", "JPG Médio")),
        PropertyItem("Disparo", properties.driveMode, listOf("Disparo Único", "Contínuo (L)", "Contínuo (H)", "Timer 10s", "Timer 2s"))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Pure photographic black backdrop
    ) {
        // 1. Live View Screen Port
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (properties.isLiveViewActive && liveViewFrame != null) {
                // Render stream frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .testTag("live_view_viewport")
                ) {
                    Image(
                        bitmap = liveViewFrame!!.asImageBitmap(),
                        contentDescription = "Live View Stream",
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationAngle),
                        contentScale = if (zoomLevel > 1f) ContentScale.Crop else ContentScale.Fit
                    )

                    // Draw Live Grids overlays
                    if (activeGrid == 1) {
                        DrawGrid3x3()
                    } else if (activeGrid == 2) {
                        DrawGoldenRatioGrid()
                    }

                    // Viewport control actions overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Grid",
                                tint = if (activeGrid > 0) Color.Green else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { activeGrid = (activeGrid + 1) % 3 }
                            )
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { rotationAngle = (rotationAngle + 90f) % 360f }
                            )
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom",
                                tint = if (zoomLevel > 1f) Color.Green else Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { zoomLevel = if (zoomLevel == 1f) 5f else if (zoomLevel == 5f) 10f else 1f }
                            )
                        }
                    }

                    // Top Status details (Histogram / Battery)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ZOOM: ${zoomLevel.toInt()}x",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Green,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        // Digital Snapshot/Print Button
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val savedFile = saveLiveViewFrameLocally(liveViewFrame, context)
                                    if (savedFile != null) {
                                        Toast.makeText(context, "Captura salva: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Salvar Frame", tint = Color.White)
                        }
                    }
                }
            } else {
                // Live View Off Placeholder
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TvOff,
                        contentDescription = "LV Desligado",
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Live View Desativado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "A transmissão USB não está ativa. Ative o Live View clicando no botão 'LV' abaixo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // 2. Main Parameters grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Parameter dial titles
                Text(
                    text = "PARÂMETROS FOTOGRÁFICOS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(propertiesList) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .testTag("property_card_${item.name.lowercase()}")
                                .clickable { selectedPropertyToEdit = item.name },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = item.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.currentValue,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Quick capture panel trigger at screen bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LV switch
                    Button(
                        onClick = { cameraManager.toggleLiveView() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (properties.isLiveViewActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.weight(0.9f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (properties.isLiveViewActive) "LV Ligado" else "Iniciar LV", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Shutter Trigger
                    Button(
                        onClick = { cameraManager.capturePhoto() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("dial_capture_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disparar", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }

    // Modal Parameter Selector Dialogs
    selectedPropertyToEdit?.let { propName ->
        val propItem = propertiesList.find { it.name == propName }
        if (propItem != null) {
            AlertDialog(
                onDismissRequest = { selectedPropertyToEdit = null },
                title = { Text("Ajustar $propName") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        val state = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(state),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            propItem.options.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (propName) {
                                                "ISO" -> cameraManager.setIso(option)
                                                "Abertura" -> cameraManager.setAperture(option)
                                                "Exposição" -> cameraManager.setShutterSpeed(option)
                                                "B. de Branco" -> cameraManager.setWhiteBalance(option)
                                                "Comp. EV" -> cameraManager.setExposureCompensation(option)
                                                "Qualidade" -> cameraManager.setImageQuality(option)
                                                "Disparo" -> cameraManager.setDriveMode(option)
                                            }
                                            selectedPropertyToEdit = null
                                        }
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (option == propItem.currentValue) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == propItem.currentValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (option == propItem.currentValue) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedPropertyToEdit = null }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }
}

data class PropertyItem(
    val name: String,
    val currentValue: String,
    val options: List<String>
)

@Composable
fun DrawGrid3x3() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Vertical lines
        Box(modifier = Modifier.fillMaxHeight().fillLineWidthPercent(0.33f).background(Color.White.copy(alpha = 0.3f)))
        Box(modifier = Modifier.fillMaxHeight().fillLineWidthPercent(0.66f).background(Color.White.copy(alpha = 0.3f)))
        // Horizontal lines
        Box(modifier = Modifier.fillMaxWidth().fillLineHeightPercent(0.33f).background(Color.White.copy(alpha = 0.3f)))
        Box(modifier = Modifier.fillMaxWidth().fillLineHeightPercent(0.66f).background(Color.White.copy(alpha = 0.3f)))
    }
}

@Composable
fun DrawGoldenRatioGrid() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Golden ratio grid (0.382 and 0.618)
        Box(modifier = Modifier.fillMaxHeight().fillLineWidthPercent(0.382f).background(Color.White.copy(alpha = 0.35f)))
        Box(modifier = Modifier.fillMaxHeight().fillLineWidthPercent(0.618f).background(Color.White.copy(alpha = 0.35f)))

        Box(modifier = Modifier.fillMaxWidth().fillLineHeightPercent(0.382f).background(Color.White.copy(alpha = 0.35f)))
        Box(modifier = Modifier.fillMaxWidth().fillLineHeightPercent(0.618f).background(Color.White.copy(alpha = 0.35f)))
    }
}

// Helpers for grid overlays in pure Compose
fun Modifier.fillLineWidthPercent(fraction: Float): Modifier = this
    .fillMaxHeight()
    .width(1.dp)
    .padding(start = (640 * fraction).dp) // approximation

fun Modifier.fillLineHeightPercent(fraction: Float): Modifier = this
    .fillMaxWidth()
    .height(1.dp)
    .padding(top = (480 * fraction).dp) // approximation

private suspend fun saveLiveViewFrameLocally(bitmap: Bitmap?, context: android.content.Context): File? = withContext(kotlinx.coroutines.Dispatchers.IO) {
    if (bitmap == null) return@withContext null
    try {
        val picturesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val screenshotFile = File(picturesDir, "Screenshot_50D_${System.currentTimeMillis()}.jpg")
        val out = FileOutputStream(screenshotFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        out.flush()
        out.close()
        screenshotFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
