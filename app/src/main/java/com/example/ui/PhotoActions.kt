package com.example.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.core.data.Photo
import com.example.core.storage.PhotoStore

enum class ShareSet(val label: String) { JPG("JPG"), RAW("RAW"), BOTH("RAW + JPG") }

/** Share / open intents for a saved photo. Other apps get temporary read access to exactly these files. */
class PhotoActions(private val context: Context, private val store: PhotoStore) {

    fun availableSets(photo: Photo): List<ShareSet> = buildList {
        if (photo.jpgUri != null) add(ShareSet.JPG)
        if (photo.rawUri != null) add(ShareSet.RAW)
        if (photo.jpgUri != null && photo.rawUri != null) add(ShareSet.BOTH)
    }

    fun share(photo: Photo, set: ShareSet) {
        val intent = sendIntent(photo, set) ?: return
        context.startActivity(Intent.createChooser(intent, "Enviar foto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands the files to Lightroom Mobile ("Adicionar ao Lightroom"). Returns false if it isn't installed. */
    fun openInLightroom(photo: Photo, set: ShareSet): Boolean {
        val intent = sendIntent(photo, set)?.setPackage(LIGHTROOM) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Lightroom não está instalado. Escolha outro app.", Toast.LENGTH_LONG).show()
            share(photo, set)
            false
        }
    }

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

    private fun sendIntent(photo: Photo, set: ShareSet): Intent? {
        val files = when (set) {
            ShareSet.JPG -> listOfNotNull(photo.jpgUri)
            ShareSet.RAW -> listOfNotNull(photo.rawUri)
            ShareSet.BOTH -> listOfNotNull(photo.jpgUri, photo.rawUri)
        }.map(store::shareableUri)
        if (files.isEmpty()) return null

        val mime = when (set) {
            ShareSet.JPG -> JPEG
            ShareSet.RAW -> CR2
            ShareSet.BOTH -> "image/*"
        }
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, files.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(files))
        }
        intent.type = mime
        // ClipData carries the read grant to the receiving app for every file, not just the first.
        intent.clipData = ClipData.newRawUri(null, files.first()).apply { files.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    companion object {
        const val LIGHTROOM = "com.adobe.lrmobile"
        private const val JPEG = "image/jpeg"
        private const val CR2 = "image/x-canon-cr2"
    }
}
