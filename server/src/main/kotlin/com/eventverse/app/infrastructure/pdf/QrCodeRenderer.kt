package com.eventverse.app.infrastructure.pdf

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.apache.pdfbox.pdmodel.PDPageContentStream

/**
 * Menggambar QR sebagai kotak vektor, bukan gambar raster.
 *
 * Alasannya praktis, bukan estetis: modul vektor tetap tajam pada resolusi printer mana pun, sedangkan
 * PNG yang di-resample bisa membuat tepi modul berbayang dan menurunkan keberhasilan pemindaian pada
 * kartu berukuran 25 mm. Selain itu ini menghindari `BufferedImage`/AWT, yang merepotkan di server
 * headless.
 */
object QrCodeRenderer {

    /**
     * Kartu bundel diikat ke bundel benang, dipegang tangan berdebu serat, dan kusut di meja QC.
     * Level Q memulihkan 25% modul yang rusak — satu tingkat lebih mahal daripada M, jauh lebih murah
     * daripada satu bundel yang tidak bisa dipindai dan harus dilacak manual.
     */
    private val DEFAULT_ECC = ErrorCorrectionLevel.Q

    fun draw(
        content: PDPageContentStream,
        payload: String,
        xPt: Float,
        topPt: Float,
        sizePt: Float,
        ecc: ErrorCorrectionLevel = DEFAULT_ECC
    ) {
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            1,
            1,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ecc,
                // Quiet zone digambar sendiri oleh tata letak kartu (padding), jadi ZXing tidak perlu
                // menambahkan margin yang akan mengecilkan modulnya tanpa terlihat.
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )

        val modules = matrix.width
        if (modules <= 0) return
        val moduleSize = sizePt / modules

        content.setNonStrokingColor(0f, 0f, 0f)
        for (row in 0 until matrix.height) {
            var column = 0
            while (column < modules) {
                if (!matrix.get(column, row)) {
                    column++
                    continue
                }
                // Modul gelap yang berdampingan digabung jadi satu persegi panjang. Satu kartu bisa
                // memuat ratusan modul; menggambarnya satu per satu memperbesar berkas PDF tanpa
                // mengubah hasil cetaknya sedikit pun.
                var runEnd = column
                while (runEnd + 1 < modules && matrix.get(runEnd + 1, row)) runEnd++

                content.addRect(
                    xPt + column * moduleSize,
                    topPt - (row + 1) * moduleSize,
                    moduleSize * (runEnd - column + 1),
                    moduleSize
                )
                column = runEnd + 1
            }
        }
        content.fill()
    }
}
