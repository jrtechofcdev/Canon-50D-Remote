package com.example.ui.delivery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import java.io.File

/**
 * The printed plate for the table or the photo booth (A6, 300 dpi): ① join the Wi-Fi, ② open the
 * photos, ③ type the code. White paper, little ink, the brand on top; ready for the print shop as
 * PNG or PDF.
 */
object PlateExport {

    const val WIDTH = 1240
    const val HEIGHT = 1748
    private const val INK = 0xFF1F1F1F.toInt()
    private const val MUTED = 0xFF5C5C5C.toInt()
    private const val MINT = 0xFF00C987.toInt()

    data class Plate(val ssid: String?, val password: String?, val url: String, val photographer: String)

    fun render(context: Context, plate: Plate): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val bold = ResourcesCompat.getFont(context, R.font.exo2_semibold) ?: Typeface.DEFAULT_BOLD
        val regular = ResourcesCompat.getFont(context, R.font.exo2_regular) ?: Typeface.DEFAULT
        val margin = 90f

        // Brand stripe and logo.
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 18f, Paint().apply { color = MINT })
        runCatching { context.assets.open("watermarks/horizontal_cores.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { logo ->
            val w = 560f
            val h = w * logo.height / logo.width
            canvas.drawBitmap(logo, null, RectF((WIDTH - w) / 2, 70f, (WIDTH + w) / 2, 70f + h), Paint(Paint.FILTER_BITMAP_FLAG))
            logo.recycle()
        }

        var y = 290f
        y = text(canvas, "Suas fotos na hora", bold, 76f, INK, y, center = true) + 14f
        y = text(canvas, "Direto do celular do fotógrafo, sem internet e sem instalar nada.", regular, 34f, MUTED, y, center = true) + 50f

        val qr = 400
        fun step(number: String, title: String, detail: String, qrText: String?) {
            val top = y
            val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MINT }
            canvas.drawCircle(margin + 34f, top + 34f, 34f, badge)
            text(canvas, number, bold, 40f, 0xFF00261A.toInt(), top + 12f, left = margin + 2f, width = 64)
            val textLeft = margin + 96f
            val textWidth = (if (qrText != null) WIDTH - margin - qr - 40f else WIDTH - margin) - textLeft
            var ty = text(canvas, title, bold, 46f, INK, top, left = textLeft, width = textWidth.toInt()) + 10f
            ty = text(canvas, detail, regular, 32f, MUTED, ty, left = textLeft, width = textWidth.toInt())
            qrText?.let {
                val code = QrCode.bitmap(it, qr)
                canvas.drawBitmap(code, WIDTH - margin - qr, top - 20f, null)
                code.recycle()
            }
            y = maxOf(ty, if (qrText != null) top + qr - 10f else ty) + 50f
        }

        if (plate.ssid != null && plate.password != null) {
            step("1", "Entre no Wi-Fi", "Aponte a câmera do celular para o código.\nRede: ${plate.ssid}\nSenha: ${plate.password}", com.example.core.delivery.DeliveryRules.wifiQr(plate.ssid, plate.password))
            step("2", "Abra as fotos", "Aponte a câmera para o código ou digite no navegador:\n${plate.url}", plate.url)
            step("3", "Digite seu código", "O fotógrafo te passa um código de 6 números. Toque em Salvar foto.", null)
        } else {
            step("1", "Abra as fotos", "Conectado no Wi-Fi do local, aponte a câmera para o código ou digite:\n${plate.url}", plate.url)
            step("2", "Digite seu código", "O fotógrafo te passa um código de 6 números. Toque em Salvar foto.", null)
        }

        text(canvas, "Se o celular avisar que o Wi-Fi não tem internet, toque em Manter conectado.", regular, 30f, MUTED, HEIGHT - 190f, center = true)
        text(canvas, plate.photographer, bold, 36f, INK, HEIGHT - 110f, center = true)
        return bitmap
    }

    /** Draws wrapped text; returns the y below it. */
    private fun text(canvas: Canvas, s: String, face: Typeface, size: Float, color: Int, top: Float, center: Boolean = false, left: Float = 90f, width: Int = WIDTH - 180): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = color }
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width)
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    /** Saves the plate as PNG and as an A6 PDF in the share folder and opens the share sheet. */
    fun share(context: Context, plate: Plate) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val bitmap = render(context, plate)
        val png = File(dir, "Placa_entrega.png").apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val pdf = File(dir, "Placa_entrega_A6.pdf")
        val doc = PdfDocument()
        // A6 in points (105 × 148 mm).
        val page = doc.startPage(PdfDocument.PageInfo.Builder(298, 420, 1).create())
        page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, 298f, 420f), Paint(Paint.FILTER_BITMAP_FLAG))
        doc.finishPage(page)
        pdf.outputStream().use { doc.writeTo(it) }
        doc.close()
        bitmap.recycle()
        val authority = "${context.packageName}.fileprovider"
        val uris = arrayListOf(FileProvider.getUriForFile(context, authority, png), FileProvider.getUriForFile(context, authority, pdf))
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Placa para imprimir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
