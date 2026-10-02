package com.example.ui.delivery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR codes made on the phone (ZXing), no internet. */
object QrCode {
    /** Black on white, [size] px square, with the quiet zone phone cameras need. */
    fun bitmap(text: String, size: Int, dark: Int = 0xFF000000.toInt(), light: Int = 0xFFFFFFFF.toInt()): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) dark else light }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

/** A QR code on a white tile (a dark background around it confuses some cameras). */
@Composable
fun QrImage(text: String, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap = remember(text) { QrCode.bitmap(text, 512).asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp)) {
        Image(bitmap, contentDescription, Modifier.size(size), filterQuality = FilterQuality.None)
    }
}
