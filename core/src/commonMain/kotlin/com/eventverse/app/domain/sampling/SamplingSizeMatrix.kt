package com.eventverse.app.domain.sampling

/** Kolom size standar lama untuk backward compatibility bila data lama belum memiliki keys spesifik. */
val STANDARD_SAMPLING_SIZE_COLUMNS = listOf("ALL SIZE", "S", "M", "L", "XL", "XXL", "XXXL")

const val DEFAULT_PLACEHOLDER_SIZE_COLUMN = "ALL SIZE"
const val SAMPLING_QTY_ROW_ID = "sampling_qty_row"
const val SAMPLING_QTY_ROW_NAME = "Jumlah Sampel (pcs)"

/**
 * Satu baris pengukuran pada tabel size chart sampling (Point of Measurement - POM).
 *
 * Contoh:
 * - [pomName] = "Lebar Dada" atau "Lingkar Pinggang"
 * - [values] = mapOf("ALL SIZE" to "52", "28" to "48", "30" to "50", ...)
 */
data class SizeChartRow(
    val id: String,
    val pomName: String,
    val values: Map<String, String> = emptyMap()
)

val SizeChartRow.isQtyRow: Boolean
    get() = id == SAMPLING_QTY_ROW_ID || pomName.equals(SAMPLING_QTY_ROW_NAME, ignoreCase = true)

/**
 * Mengekstrak seluruh kolom ukuran yang ada di dalam matriks.
 * Urutan kolom dipertahankan sesuai urutan penambahan pertama kali.
 */
fun extractSizeColumns(matrix: List<SizeChartRow>): List<String> {
    val columns = LinkedHashSet<String>()
    for (row in matrix) {
        for (key in row.values.keys) {
            columns.add(key)
        }
    }
    return if (columns.isNotEmpty()) columns.toList() else listOf(DEFAULT_PLACEHOLDER_SIZE_COLUMN)
}

/**
 * Menambahkan kolom ukuran baru ke seluruh baris matriks (POM dan baris Qty).
 * Jika [newColumn] bernilai kosong / blank, kolom kosong baru akan disisipkan agar user
 * langsung melihat kolom dengan placeholder "Size" dan leluasa mengetikkan nama ukuran sendiri.
 */
fun addColumnToMatrix(matrix: List<SizeChartRow>, newColumn: String = ""): List<SizeChartRow> {
    val existing = extractSizeColumns(matrix)
    if (newColumn.isBlank()) {
        if (existing.any { it.isBlank() }) return matrix
        val blankKey = ""
        return matrix.map { row ->
            val newMap = LinkedHashMap(row.values)
            newMap[blankKey] = ""
            row.copy(values = newMap)
        }
    }
    val trimmed = newColumn.trim()
    if (existing.any { it.equals(trimmed, ignoreCase = true) }) return matrix
    return matrix.map { row ->
        val newMap = LinkedHashMap(row.values)
        newMap[trimmed] = ""
        row.copy(values = newMap)
    }
}

/**
 * Mengubah nama/label kolom ukuran di seluruh baris matriks, dengan mempertahankan urutan posisi.
 */
fun renameColumnInMatrix(matrix: List<SizeChartRow>, oldColumn: String, newColumn: String): List<SizeChartRow> {
    if (newColumn == oldColumn) return matrix
    val existing = extractSizeColumns(matrix)
    if (newColumn.isNotBlank() && existing.any { it != oldColumn && it.equals(newColumn.trim(), ignoreCase = true) }) {
        return matrix
    }
    if (newColumn.isBlank() && existing.any { it != oldColumn && it.isBlank() }) {
        return matrix
    }
    val targetKey = if (newColumn.isBlank()) "" else newColumn.trim()
    return matrix.map { row ->
        val newMap = LinkedHashMap<String, String>()
        for ((k, v) in row.values) {
            if (k == oldColumn) {
                newMap[targetKey] = v
            } else {
                newMap[k] = v
            }
        }
        row.copy(values = newMap)
    }
}

/**
 * Menghapus kolom ukuran dari seluruh baris matriks.
 * Minimal harus tersisa 1 kolom dalam matriks.
 */
fun deleteColumnFromMatrix(matrix: List<SizeChartRow>, column: String): List<SizeChartRow> {
    val columns = extractSizeColumns(matrix)
    if (columns.size <= 1) return matrix
    return matrix.map { row ->
        val newMap = LinkedHashMap(row.values)
        newMap.remove(column)
        row.copy(values = newMap)
    }
}

/**
 * Kolom ukuran [col] aktif dan dapat diisi kuantitas sampelnya jika dan hanya jika
 * SEMUA baris parameter fisik (POM) yang bernama tidak kosong telah terisi nilainya.
 */
fun isSizeColumnActive(matrix: List<SizeChartRow>, col: String): Boolean {
    if (col.isBlank()) return false
    val pomRows = matrix.filter { !it.isQtyRow && it.pomName.isNotBlank() }
    if (pomRows.isEmpty()) return false
    return pomRows.all { it.values[col]?.isNotBlank() == true }
}

/**
 * Menghitung total pcs sampel dari baris qty di matriks ukuran untuk kolom yang aktif.
 * Mengembalikan murni akumulasi kuantitas yang diisi pada kolom-kolom yang lengkap.
 */
fun calculateTotalSampleQuantity(matrix: List<SizeChartRow>, fallback: Int = 0): Int {
    val qtyRow = matrix.firstOrNull { it.isQtyRow } ?: return fallback
    return qtyRow.values.entries
        .filter { (col, _) -> isSizeColumnActive(matrix, col) }
        .sumOf { (_, value) -> value.trim().toIntOrNull() ?: 0 }
}

/**
 * Memastikan baris khusus "Jumlah Sampel (pcs)" selalu berada di posisi teratas matriks.
 * Jika belum ada di [matrix], baris ini otomatis disisipkan.
 */
fun ensureSamplingQtyRow(matrix: List<SizeChartRow>): List<SizeChartRow> {
    val existingQty = matrix.firstOrNull { it.isQtyRow }
    val withoutQty = matrix.filter { !it.isQtyRow }
    val columns = extractSizeColumns(matrix)
    val qtyValues = LinkedHashMap<String, String>()
    for (col in columns) {
        qtyValues[col] = existingQty?.values?.get(col) ?: ""
    }
    val qtyRow = existingQty?.copy(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME, values = qtyValues)
        ?: SizeChartRow(
            id = SAMPLING_QTY_ROW_ID,
            pomName = SAMPLING_QTY_ROW_NAME,
            values = qtyValues
        )
    return listOf(qtyRow) + withoutQty
}

/**
 * Mengecek apakah minimal ada 1 kolom ukuran yang SELURUH baris POM-nya (selain baris Qty) terisi lengkap (tidak kosong).
 */
fun hasAtLeastOneCompleteMeasurementColumn(matrix: List<SizeChartRow>): Boolean {
    val pomRows = matrix.filter { !it.isQtyRow && it.pomName.isNotBlank() }
    if (pomRows.isEmpty()) return false
    val columns = extractSizeColumns(matrix)
    return columns.any { col ->
        isSizeColumnActive(matrix, col)
    }
}

/**
 * Mengembalikan kolom ukuran pertama yang "LENGKAP": seluruh baris pengukuran (POM) kolom itu
 * terisi non-kosong DAN alokasi jumlah sampelnya >= 1 pcs.
 */
fun firstCompleteSizeColumn(matrix: List<SizeChartRow>): String? {
    val qtyRow = matrix.firstOrNull { it.isQtyRow }
    val columns = extractSizeColumns(matrix)
    return columns.firstOrNull { col ->
        val qty = qtyRow?.values?.get(col)?.trim()?.toIntOrNull() ?: 0
        isSizeColumnActive(matrix, col) && qty >= 1
    }
}

/**
 * Mengembalikan daftar pasangan (ukuran, kuantitas) untuk setiap kolom ukuran yang aktif
 * (seluruh baris parameter POM terisi lengkap) dan memiliki alokasi jumlah sampel >= 1 pcs.
 * Digunakan untuk pemecahan SPK sampling per ukuran (1 SPK = 1 ukuran).
 */
fun activeSizesWithAllocatedQty(matrix: List<SizeChartRow>): List<Pair<String, Int>> {
    val qtyRow = matrix.firstOrNull { it.isQtyRow } ?: return emptyList()
    val columns = extractSizeColumns(matrix)
    return columns.mapNotNull { col ->
        val qty = qtyRow.values[col]?.trim()?.toIntOrNull() ?: 0
        if (qty >= 1 && isSizeColumnActive(matrix, col)) Pair(col, qty) else null
    }
}

/**
 * Membersihkan cell kuantitas untuk kolom yang seluruh parameter ukurannya kosong,
 * mencegah pemesanan sampel ukuran fiktif tanpa spesifikasi POM.
 */
fun sanitizeSamplingMatrix(matrix: List<SizeChartRow>): List<SizeChartRow> {
    val withQty = ensureSamplingQtyRow(matrix)
    val qtyRow = withQty.first { it.isQtyRow }
    val otherRows = withQty.filter { !it.isQtyRow }
    val columns = extractSizeColumns(matrix)

    val sanitizedQtyValues = qtyRow.values.toMutableMap()
    for (col in columns) {
        val hasPom = otherRows.any { it.pomName.isNotBlank() && it.values[col]?.isNotBlank() == true }
        if (!hasPom) {
            sanitizedQtyValues[col] = ""
        }
    }

    return listOf(qtyRow.copy(values = sanitizedQtyValues)) + otherRows
}

/**
 * Matriks sampling bawaan awal: hanya membuka 1 baris POM placeholder dan 1 kolom placeholder
 * tanpa teks kaku, sehingga sales/sampling leluasa mengisi ukuran apa pun (huruf maupun nomor).
 */
fun defaultSamplingSizeMatrix(): List<SizeChartRow> = listOf(
    SizeChartRow(
        id = SAMPLING_QTY_ROW_ID,
        pomName = SAMPLING_QTY_ROW_NAME,
        values = mapOf(DEFAULT_PLACEHOLDER_SIZE_COLUMN to "")
    ),
    SizeChartRow(
        id = "pom_1",
        pomName = "",
        values = mapOf(DEFAULT_PLACEHOLDER_SIZE_COLUMN to "")
    )
)

