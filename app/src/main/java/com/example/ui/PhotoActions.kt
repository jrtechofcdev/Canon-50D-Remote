package com.example.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.core.data.Photo
import com.example.core.data.Settings
import com.example.core.export.ExportService
import com.example.core.storage.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ShareSet(val label: String) {
    LIGHT("JPG leve (WhatsApp)"),
    EDITED("JPG editado (com preset)"),
    JPG("JPG original"),
    RAW("RAW"),
    BOTH("RAW + JPG"),
}

/** Share / open intents for saved photos. Other apps get temporary read access to exactly these files. */
class PhotoActions(
    private val context: Context,
    private val store: PhotoStore,
    private val export: ExportService,
) {

    fun availableSets(photo: Photo, settings: Settings, includeLight: Boolean = true): List<ShareSet> = buildList {
        if (includeLight && settings.lightShareEnabled && photo.displayUri != null) add(ShareSet.LIGHT)
        if (photo.editedUri != null) add(ShareSet.EDITED)
        if (photo.jpgUri != null) add(ShareSet.JPG)
        if (photo.rawUri != null) add(ShareSet.RAW)
        if (photo.jpgUri != null && photo.rawUri != null) add(ShareSet.BOTH)
    }

    suspend fun share(photos: List<Photo>, set: ShareSet, settings: Settings) {
        val intent = sendIntent(photos, set, settings) ?: return
        context.startActivity(Intent.createChooser(intent, "Enviar foto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands the files to Lightroom Mobile ("Adicionar ao Lightroom"). Falls back to the share sheet. */
    suspend fun openInLightroom(photos: List<Photo>, set: ShareSet, settings: Settings) {
        val intent = sendIntent(photos, set, settings)?.setPackage(LIGHTROOM) ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Lightroom não está instalado. Escolha outro app.", Toast.LENGTH_LONG).show()
            share(photos, set, settings)
        }
    }

    /**
     * Opens WhatsApp (or WhatsApp Business, or the share sheet when neither is installed) with [text],
     * plus the photos in [set] when given. Text and photos go in one message where the app allows it.
     */
    suspend fun sendMessage(text: String, photos: List<Photo>, set: ShareSet?, settings: Settings) {
        val intent = (if (set != null && photos.isNotEmpty()) sendIntent(photos, set, settings) else null)
            ?: Intent(Intent.ACTION_SEND).setType("text/plain")
        intent.putExtra(Intent.EXTRA_TEXT, text)
        val whatsapp = listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { installed(it) }
        if (whatsapp != null) {
            runCatching { context.startActivity(Intent(intent).setPackage(whatsapp).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        }
        context.startActivity(Intent.createChooser(intent, "Enviar mensagem").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun openWith(photo: Photo) {
        val (uri, mime) = when {
            photo.jpgUri != null -> photo.jpgUri to JPEG
            photo.rawUri != null -> photo.rawUri to CR2
            else -> return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(store.shareableUri(uri), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(Intent.createChooser(intent, "Abrir com").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private suspend fun sendIntent(photos: List<Photo>, set: ShareSet, settings: Settings): Intent? {
        val files: List<Uri> = if (set == ShareSet.LIGHT) {
            withContext(Dispatchers.IO) {
                photos.mapNotNull { p ->
                    p.lookUri?.let { export.exportLight(it, p.savedBase, settings) }
                        ?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
                }
            }
        } else {
            photos.flatMap { p ->
                when (set) {
                    ShareSet.JPG -> listOfNotNull(p.jpgUri)
                    ShareSet.EDITED -> listOfNotNull(p.editedUri)
                    ShareSet.RAW -> listOfNotNull(p.rawUri)
                    else -> listOfNotNull(p.jpgUri, p.rawUri)
                }
            }.map(store::shareableUri)
        }
        if (files.isEmpty()) {
            Toast.makeText(context, "Nenhum arquivo nesse formato", Toast.LENGTH_SHORT).show()
            return null
        }

        val mime = when (set) {
            ShareSet.LIGHT, ShareSet.JPG, ShareSet.EDITED -> JPEG
            ShareSet.RAW -> CR2
            ShareSet.BOTH -> "image/*"
        }
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, files.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        }
        intent.type = mime
        // ClipData carries the read grant to the receiving app for every file, not just the first.
        intent.clipData = ClipData.newRawUri(null, files.first()).apply { files.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    companion object {
        const val LIGHTROOM = "com.adobe.lrmobile"
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val JPEG = "image/jpeg"
        private const val CR2 = "image/x-canon-cr2"
    }
}
