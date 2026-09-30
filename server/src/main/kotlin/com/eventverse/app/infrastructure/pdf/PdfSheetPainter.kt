package com.eventverse.app.infrastructure.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.util.Matrix

/**
 * Primitif menggambar lembar dokumen **platform** ke PDF.
 *
 * Diangkat dari [BlueprintPdfRenderer] begitu dokumen platform kedua muncul (tagihan langganan). Yang
 * dipindahkan bukan sekadar penghematan baris: [encodeSafe] menyimpan **pengetahuan** — daftar glyph
 * yang terbukti absen dari font cetak yang dibundel. Kalau daftar itu disalin ke tiap renderer, suatu
 * saat hanya satu salinan yang diperbaiki, dan dokumen yang satu lagi gagal terbit karena satu
 * karakter aneh di nama modul tenant.
 *
 * Kelas ini sengaja **buta terhadap domain**: ia tidak tahu peran baris, status invoice, atau warna
 * yang pantas. Ia menerima angka (mm10, pt, derajat) dan skala abu-abu; pemetaan peran → rupa tetap
 * milik renderer masing-masing dokumen.
 *
 * Koordinat yang dipakai pemanggil selalu **asal kiri-atas** (satuan 1/10 mm, sama dengan model lembar
 * di domain); konversi ke koordinat PDF (asal kiri-bawah) terjadi di sini, sekali.
 */
class PdfSheetPainter(
    doc: PDDocument,
    private val pageHeightPt: Float = PDRectangle.A4.height
) {
    private val regular: PDFont = PdfFonts.bodyRegular(doc)
    private val bold: PDFont = PdfFonts.bodyBold(doc)

    /** Teks satu baris. `xMm10` dari kiri kertas, `topMm10` dari atas kertas. */
    fun text(
        cs: PDPageContentStream,
        value: String,
        xMm10: Int,
        topMm10: Int,
        sizePt: Float,
        isBold: Boolean = false,
        gray: Float = BODY_GRAY
    ) {
        if (value.isBlank()) return
        val font = fontFor(isBold)
        cs.setNonStrokingColor(gray, gray, gray)
        cs.beginText()
        cs.setFont(font, sizePt)
        cs.newLineAtOffset(PdfUnits.toPoints(xMm10), topMm10ToBaseline(topMm10, sizePt))
        cs.showText(encodeSafe(font, value))
        cs.endText()
    }

    /**
     * Kotak tipis di sekeliling teks: menandai "ini labelnya", bukan isi paragraf. Dipakai peran
     * `BADGE` pada lembar blueprint.
     */
    fun box(
        cs: PDPageContentStream,
        value: String,
        xMm10: Int,
        topMm10: Int,
        sizePt: Float,
        isBold: Boolean = false,
        gray: Float = RULE_GRAY
    ) {
        if (value.isBlank()) return
        val widthPt = textWidthPt(value, sizePt, isBold)
        val xPt = PdfUnits.toPoints(xMm10)
        val topPt = pageHeightPt - PdfUnits.toPoints(topMm10)
        cs.setStrokingColor(gray, gray, gray)
        cs.setLineWidth(0.7f)
        cs.addRect(
            xPt - BOX_PADDING_PT,
            topPt - sizePt - BOX_PADDING_PT,
            widthPt + 2 * BOX_PADDING_PT,
            sizePt + 2 * BOX_PADDING_PT
        )
        cs.stroke()
    }

    /**
     * Garis horizontal dari `leftMm10` ke `rightMm10` pada ketinggian `topMm10`.
     *
     * [offsetPt] menggeser garis dalam titik: pemanggil yang menaruh garis di atas blok footer
     * memerlukannya agar garis tidak menempel ke huruf pertamanya.
     */
    fun rule(
        cs: PDPageContentStream,
        leftMm10: Int,
        rightMm10: Int,
        topMm10: Int,
        gray: Float = RULE_GRAY,
        offsetPt: Float = 0f
    ) {
        val y = pageHeightPt - PdfUnits.toPoints(topMm10) + offsetPt
        cs.setStrokingColor(gray, gray, gray)
        cs.setLineWidth(0.5f)
        cs.moveTo(PdfUnits.toPoints(leftMm10), y)
        cs.lineTo(PdfUnits.toPoints(rightMm10), y)
        cs.stroke()
    }

    /**
     * Penanda diagonal besar di tengah kertas.
     *
     * Titik asal gambar diputar lebih dulu, lalu teks digeser setengah lebar & setengah tinggi supaya
     * pusatnya jatuh di pusat kertas — bukan sudut kirinya. Tinggi kertas dipakai apa adanya karena
     * pemanggil menyerahkan **pusat** (bukan tepi atas).
     */
    fun watermark(
        cs: PDPageContentStream,
        value: String,
        centerXMm10: Int,
        centerYMm10: Int,
        sizePt: Int,
        angleDeg: Float,
        gray: Float = WATERMARK_GRAY
    ) {
        if (value.isBlank()) return
        val font = bold
        val printable = encodeSafe(font, value)
        val centerXPt = PdfUnits.toPoints(centerXMm10)
        val centerYPt = pageHeightPt - PdfUnits.toPoints(centerYMm10)
        val size = sizePt.toFloat()
        val widthPt = textWidthPt(printable, size, isBold = true)

        cs.saveGraphicsState()
        cs.setNonStrokingColor(gray, gray, gray)
        cs.transform(Matrix.getRotateInstance(Math.toRadians(angleDeg.toDouble()), centerXPt, centerYPt))
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(-widthPt / 2f, -size / 2f)
        cs.showText(printable)
        cs.endText()
        cs.restoreGraphicsState()
    }

    /** Lebar teks dalam titik; teks yang tak bisa diukur fontnya jatuh ke 0, bukan melempar. */
    fun textWidthPt(value: String, sizePt: Float, isBold: Boolean): Float =
        runCatching { fontFor(isBold).getStringWidth(value) / 1000f * sizePt }.getOrDefault(0f)

    /**
     * Teks yang dijamin bisa dicetak font ini.
     *
     * Dipanggil untuk **setiap** teks, termasuk watermark: `PDFBox.showText` melempar untuk glyph yang
     * absen, dan dokumen yang gagal digambar berarti penerimanya tidak menerima apa pun — jauh lebih
     * buruk daripada satu tanda `?` di nama modul.
     */
    fun encodeSafe(font: PDFont, value: String): String = buildString {
        value.forEach { ch ->
            val candidate = ASCII_FALLBACK[ch] ?: ch.toString()
            append(if (canEncode(font, candidate)) candidate else "?")
        }
    }

    private fun fontFor(isBold: Boolean): PDFont = if (isBold) bold else regular

    private fun topMm10ToBaseline(topMm10: Int, sizePt: Float): Float =
        pageHeightPt - PdfUnits.toPoints(topMm10) - sizePt

    private fun canEncode(font: PDFont, value: String): Boolean =
        runCatching { font.getStringWidth(value) }.isSuccess

    companion object {
        /** Abu-abu sangat muda: terbaca sebagai penanda, tapi tidak mengganggu teks di atasnya. */
        const val WATERMARK_GRAY = 0.88f
        const val RULE_GRAY = 0.70f
        const val BODY_GRAY = 0.15f
        const val MUTED_GRAY = 0.45f

        const val BOX_PADDING_PT = 4f

        /**
         * Padanan ASCII untuk karakter yang **terbukti** tidak ada di font cetak yang dibundel.
         *
         * Daftarnya sengaja pendek. Memetakan karakter yang **ada** juga merugikan: lebar teks yang
         * dihitung domain (lewat `InvoiceTextLayout`) memakai karakter aslinya, sedangkan yang tercetak
         * jadi lebih pendek — baris bisa terlihat berbeda dari rencananya.
         *
         * Hasil penyelidikan `nunito_regular.ttf`/`nunito_bold.ttf`: panah `→` dan centang `✓` absen;
         * em dash, en dash, titik tengah, bullet, kutip lengkung, elipsis, `×`, dan huruf beraksen
         * (é, ü) semua ada. Karena itu hanya keduanya yang dipetakan; sisanya diserahkan ke `?`.
         */
        val ASCII_FALLBACK: Map<Char, String> = mapOf('→' to "->", '✓' to "v")
    }
}
