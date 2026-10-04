package com.eventverse.app.domain.prototype

/**
 * Tampilan tabel murni: filter teks lalu sortir. Tidak mengubah store — sortir/filter hanya cara
 * melihat. Angka diurutkan sebagai angka bila **semua** nilai kolom itu angka; selain itu sebagai teks
 * tanpa membedakan huruf besar/kecil.
 */
object TableView {

    fun apply(rows: List<PrototypeRow>, columns: List<String>, query: String, sortColumn: String?, ascending: Boolean): List<PrototypeRow> {
        val q = query.trim()
        val filtered = if (q.isEmpty()) rows else rows.filter { row -> columns.any { row[it].contains(q, ignoreCase = true) } }
        if (sortColumn == null || sortColumn !in columns) return filtered
        val numeric = filtered.isNotEmpty() && filtered.all { it[sortColumn].toDoubleOrNull() != null }
        val comparator: Comparator<PrototypeRow> =
            if (numeric) compareBy { it[sortColumn].toDouble() } else compareBy(String.CASE_INSENSITIVE_ORDER) { it[sortColumn] }
        return filtered.sortedWith(if (ascending) comparator else comparator.reversed())
    }
}
