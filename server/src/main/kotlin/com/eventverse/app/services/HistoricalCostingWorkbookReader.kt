package com.eventverse.app.services

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Row
import java.io.File

/**
 * Isi satu berkas Excel HPP lama, sudah diratakan menjadi teks dan daftar gambar.
 *
 * Perantara ini ada supaya parser (AI maupun heuristik) tidak perlu tahu apa-apa soal POI —
 * dan supaya test parser bisa memberi teks grid langsung tanpa menyiapkan berkas `.xlsx`.
 */
data class WorkbookExtract(
    val fileName: String,
    /** Seluruh sheet diratakan jadi teks bertabulasi; baris kosong dibuang. */
    val gridText: String,
    val embeddedImages: List<EmbeddedImage> = emptyList()
) {
    data class EmbeddedImage(
        val suggestedFileName: String,
        val contentType: String,
        val bytes: ByteArray
    ) {
        // POI mengembalikan ByteArray; data class default-nya membandingkan referensi, yang membuat
        // dua gambar identik terlihat berbeda saat de-duplikasi.
        override fun equals(other: Any?): Boolean =
            this === other || (other is EmbeddedImage && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

/**
 * Membaca `.xlsx` menjadi [WorkbookExtract].
 *
 * ## Mengapa seluruh sheet diratakan jadi teks, bukan dipetakan per koordinat sel?
 * Seratus berkas HPP buatan tangan tidak pernah punya tata letak yang sama: ada yang menaruh
 * gramasi di `C7`, ada yang di `B12`, ada yang di sheet kedua. Memetakan koordinat berarti
 * menulis seratus aturan. Teks rata membiarkan parser mencari **labelnya** ("BERAT", "GRAMASI"),
 * yang justru stabil lintas berkas karena ditulis oleh orang yang sama.
 */
class HistoricalCostingWorkbookReader {

    fun read(file: File): WorkbookExtract {
        require(file.exists()) { "Berkas tidak ditemukan: ${file.absolutePath}" }

        WorkbookFactory.create(file, null, true).use { workbook ->
            val builder = StringBuilder()

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                builder.append("### SHEET: ").append(sheet.sheetName).append('\n')
                sheet.forEach { row ->
                    val line = row.toTabbedText()
                    if (line.isNotBlank()) builder.append(line).append('\n')
                }
            }

            val images = workbook.allPictures.mapIndexedNotNull { index, picture ->
                val extension = picture.suggestFileExtension().ifBlank { "png" }
                val data = picture.data
                if (data.isEmpty()) return@mapIndexedNotNull null
                WorkbookExtract.EmbeddedImage(
                    suggestedFileName = "${file.nameWithoutExtension}-mockup-$index.$extension",
                    contentType = "image/$extension",
                    bytes = data
                )
            }.distinct()

            return WorkbookExtract(
                fileName = file.name,
                gridText = builder.toString().trim(),
                embeddedImages = images
            )
        }
    }

    private fun Row.toTabbedText(): String = (0 until lastCellNum.coerceAtLeast(0))
        .joinToString("\t") { columnIndex ->
            val cell = getCell(columnIndex) ?: return@joinToString ""
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue.trim()
                CellType.NUMERIC -> formatNumeric(cell.numericCellValue)
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> runCatching { formatNumeric(cell.numericCellValue) }
                    .getOrElse { runCatching { cell.stringCellValue.trim() }.getOrDefault("") }
                else -> ""
            }
        }
        .trimEnd('\t')

    /** Angka bulat ditulis tanpa `.0`, supaya "7" tidak terbaca parser sebagai gauge "7.0". */
    private fun formatNumeric(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
