package com.eventverse.app.domain.sampling

/** Kolom size standar pada matriks ukuran sampling konveksi/garmen. */
val STANDARD_SAMPLING_SIZE_COLUMNS = listOf("ALL SIZE", "S", "M", "L", "XL", "XXL", "XXXL")

const val SAMPLING_QTY_ROW_ID = "sampling_qty_row"
const val SAMPLING_QTY_ROW_NAME = "Jumlah Sampel (pcs)"

/**
 * Satu baris pengukuran pada tabel size chart sampling (Point of Measurement - POM).
 *
 * Contoh:
 * - [pomName] = "Lebar Dada"
 * - [values] = mapOf("ALL SIZE" to "52", "S" to "48", "M" to "50", "L" to "52", ...)
 */
data class SizeChartRow(
    val id: String,
    val pomName: String,
    val values: Map<String, String> = emptyMap()
)

val SizeChartRow.isQtyRow: Boolean
    get() = id == SAMPLING_QTY_ROW_ID || pomName.equals(SAMPLING_QTY_ROW_NAME, ignoreCase = true)

/**
 * Kolom ukuran [col] aktif dan dapat diisi kuantitas sampelnya jika dan hanya jika
 * SEMUA baris parameter fisik (POM) yang ada di tabel telah terisi nilainya secara lengkap (tidak kosong).
 */
fun isSizeColumnActive(matrix: List<SizeChartRow>, col: String): Boolean {
    val pomRows = matrix.filter { !it.isQtyRow }
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
    val qtyRow = existingQty?.copy(id = SAMPLING_QTY_ROW_ID, pomName = SAMPLING_QTY_ROW_NAME)
        ?: SizeChartRow(
            id = SAMPLING_QTY_ROW_ID,
            pomName = SAMPLING_QTY_ROW_NAME,
            values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { "" }
        )
    return listOf(qtyRow) + withoutQty
}

/**
 * Mengecek apakah minimal ada 1 kolom ukuran yang SELURUH baris POM-nya (selain baris Qty) terisi lengkap (tidak kosong).
 * Misal tabel memiliki POM Lebar Dada dan Panjang Baju, maka untuk ukuran "ALL SIZE", kedua baris tersebut harus terisi.
 */
fun hasAtLeastOneCompleteMeasurementColumn(matrix: List<SizeChartRow>): Boolean {
    val pomRows = matrix.filter { !it.isQtyRow }
    if (pomRows.isEmpty()) return false
    return STANDARD_SAMPLING_SIZE_COLUMNS.any { col ->
        isSizeColumnActive(matrix, col)
    }
}

/**
 * Mengembalikan kolom ukuran pertama yang "LENGKAP": seluruh baris pengukuran (POM) kolom itu
 * terisi non-kosong DAN alokasi jumlah sampelnya >= 1 pcs.
 *
 * Aturan bisnisnya: satu ukuran dianggap siap dipesan hanya jika datanya utuh — misal tabel punya
 * 2 baris POM (Lebar Dada + Panjang Baju), maka KEDUA baris itu wajib terisi untuk ukuran tersebut;
 * salah satu kosong berarti ukuran itu belum lengkap. `null` berarti belum ada satu pun ukuran lengkap.
 */
fun firstCompleteSizeColumn(matrix: List<SizeChartRow>): String? {
    val qtyRow = matrix.firstOrNull { it.isQtyRow }
    return STANDARD_SAMPLING_SIZE_COLUMNS.firstOrNull { col ->
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
    return STANDARD_SAMPLING_SIZE_COLUMNS.mapNotNull { col ->
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

    val sanitizedQtyValues = qtyRow.values.toMutableMap()
    for (col in STANDARD_SAMPLING_SIZE_COLUMNS) {
        val hasPom = otherRows.any { it.values[col]?.isNotBlank() == true }
        if (!hasPom) {
            sanitizedQtyValues[col] = ""
        }
    }

    return listOf(qtyRow.copy(values = sanitizedQtyValues)) + otherRows
}

fun defaultSamplingSizeMatrix(): List<SizeChartRow> = listOf(
    SizeChartRow(
        id = SAMPLING_QTY_ROW_ID,
        pomName = SAMPLING_QTY_ROW_NAME,
        values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { "" }
    ),
    SizeChartRow(
        id = "pom_lebar_dada",
        pomName = "Lebar Dada",
        values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { "" }
    ),
    SizeChartRow(
        id = "pom_panjang_baju",
        pomName = "Panjang Baju",
        values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { "" }
    )
)
