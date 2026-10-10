package com.eventverse.app.domain.prototype

/**
 * Hitungan baris dari layar modul lain: jumlah baris layar [moduleId] yang cocok ([field] sama dengan
 * [equals] dan/atau tidak sama dengan [notEquals]); tanpa filter = semua baris. [suffix] ditempel di
 * angka ("3 PO"). Kosakata agregat tertutup — v1 hanya hitungan.
 */
data class CountSpec(
    val moduleId: String,
    val field: String? = null,
    val equals: String? = null,
    val notEquals: String? = null,
    val suffix: String = ""
) {
    init {
        require(moduleId.isNotBlank()) { "CountSpec.moduleId kosong" }
        require((equals == null && notEquals == null) || field != null) { "CountSpec: filter butuh field" }
    }

    fun matches(row: PrototypeRow): Boolean =
        (equals == null || row[field.orEmpty()] == equals) && (notEquals == null || row[field.orEmpty()] != notEquals)
}

/**
 * Satu ubin dasbor. [value] statis; bila [count] ada, angkanya dihitung dari layar sumber dan [value]
 * hanya cadangan saat layar sumber tidak bisa dimainkan.
 */
data class TileSpec(val label: String, val value: String? = null, val count: CountSpec? = null) {
    init {
        require(label.isNotBlank()) { "TileSpec.label kosong" }
        require(value != null || count != null) { "Ubin '$label' tanpa nilai maupun hitungan" }
    }
}

data class DashboardConfig(val tiles: List<TileSpec>) {
    init { require(tiles.isNotEmpty()) { "Dasbor tanpa ubin" } }
}

/** Menghitung nilai ubin dari baris layar lain. Murni; sumber baris disuntikkan ([rowsOf] null = tak ada). */
object DashboardEvaluator {
    fun valueOf(tile: TileSpec, rowsOf: (moduleId: String) -> List<PrototypeRow>?): String {
        val count = tile.count ?: return tile.value.orEmpty()
        val rows = rowsOf(count.moduleId) ?: return tile.value ?: "-"
        return "${rows.count(count::matches)}${count.suffix}"
    }
}
