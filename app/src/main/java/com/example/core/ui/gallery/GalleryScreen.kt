package com.example.core.ui.gallery

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.core.camera.CameraManager
import com.example.core.models.DownloadedPhoto
import com.example.core.repository.PhotoRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    photoRepository: PhotoRepository,
    cameraManager: CameraManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val photos by photoRepository.allPhotos.collectAsState(initial = emptyList())

    var selectedPhotoForExif by remember { mutableStateOf<DownloadedPhoto?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<DownloadedPhoto?>(null) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HISTÓRICO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Downloads & Tethering",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Multi select toggler
            if (photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isMultiSelectMode) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    selectedPhotoIds.forEach { id ->
                                        photoRepository.deletePhoto(id)
                                    }
                                    selectedPhotoIds.clear()
                                    isMultiSelectMode = false
                                    Toast.makeText(context, "Fotos removidas", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Deletar Selecionados")
                        }
                        IconButton(onClick = {
                            selectedPhotoIds.clear()
                            isMultiSelectMode = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar Seleção")
                        }
                    } else {
                        IconButton(onClick = { isMultiSelectMode = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Seleção Múltipla")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (photos.isEmpty()) {
            // Empty state view
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Sem fotos",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma foto importada ainda",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "As fotos tiradas na sua Canon EOS 50D aparecerão aqui instantaneamente se o Auto-Download estiver ligado, ou você pode tirá-las no painel de Tethering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Photos Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .testTag("gallery_grid"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val isSelected = selectedPhotoIds.contains(photo.id)
                    val imageFile = File(photo.filePath)

                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        if (isSelected) selectedPhotoIds.remove(photo.id)
                                        else selectedPhotoIds.add(photo.id)
                                    } else {
                                        fullScreenPhoto = photo
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        isMultiSelectMode = true
                                        selectedPhotoIds.add(photo.id)
                                    }
                                }
                            )
                            .testTag("photo_card_${photo.handle}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Coil loading image from absolute path
                            if (imageFile.exists()) {
                                AsyncImage(
                                    model = imageFile,
                                    contentDescription = photo.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // Fallback loading error
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = "Erro de arquivo", tint = Color.LightGray)
                                }
                            }

                            // Format tag overlay (RAW or JPG)
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .background(
                                        if (photo.format == "RAW") Color(0xFFE91E63) else Color(0xFF2196F3),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = photo.format,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Selection checkbox overlay
                            if (isMultiSelectMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            else Color.Transparent
                                        )
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked == true) selectedPhotoIds.add(photo.id)
                                            else selectedPhotoIds.remove(photo.id)
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp)) // padding for bottom bar
    }

    // Full Screen inspect zoom overlay
    fullScreenPhoto?.let { photo ->
        Dialog(onDismissRequest = { fullScreenPhoto = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = photo.filename,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // EXIF parameters quick layout top overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = photo.filename,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.6f)
                            )
                            Text(
                                text = "${photo.aperture ?: "f/N/A"} | ${photo.shutterSpeed ?: "N/A"}s | ISO ${photo.iso ?: "N/A"}",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Info / EXIF button
                            IconButton(onClick = { selectedPhotoForExif = photo }) {
                                Icon(Icons.Default.Info, contentDescription = "EXIF Completo", tint = Color.White)
                            }
                            // Share button
                            IconButton(onClick = { sharePhoto(context, File(photo.filePath)) }) {
                                Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = Color.White)
                            }
                            // Close dialog
                            IconButton(onClick = { fullScreenPhoto = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // EXIF Information Detail BottomSheet Dialog
    selectedPhotoForExif?.let { photo ->
        AlertDialog(
            onDismissRequest = { selectedPhotoForExif = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Metadados EXIF Canon 50D")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExifInfoRow(label = "Nome do Arquivo", value = photo.filename)
                    ExifInfoRow(label = "Modelo da Câmera", value = photo.cameraModel)
                    ExifInfoRow(label = "Abertura", value = photo.aperture ?: "Desconhecido")
                    ExifInfoRow(label = "Tempo de Exposição", value = "${photo.shutterSpeed ?: "N/A"} s")
                    ExifInfoRow(label = "ISO", value = photo.iso ?: "Desconhecido")
                    ExifInfoRow(label = "Formato de Arquivo", value = photo.format)
                    ExifInfoRow(label = "Tamanho", value = "%.2f MB".format(photo.fileSize / (1024.0 * 1024.0)))
                    ExifInfoRow(label = "Pasta de Gravação", value = photo.folderName)
                    
                    val dateFormatted = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(photo.timestamp))
                    ExifInfoRow(label = "Data do Download", value = dateFormatted)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option to delete from camera as requested by spec
                    Button(
                        onClick = {
                            cameraManager.deletePhotoFromCamera(photo.handle)
                            coroutineScope.launch {
                                photoRepository.deletePhoto(photo.id)
                            }
                            selectedPhotoForExif = null
                            fullScreenPhoto = null
                            Toast.makeText(context, "Foto excluída da câmera e app", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excluir Foto da Câmera (PTP)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPhotoForExif = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ExifInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// System sharing provider intent
private fun sharePhoto(context: android.content.Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar Foto Canon 50D"))
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao compartilhar arquivo", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}
