package com.eventverse.app.domain.discovery

/**
 * Kosakata **tertutup** widget prototype (plan §4, Fase C). Milik **sistem**, bukan data tenant —
 * lolos Uji Variabilitas: renderer harus bisa menggambar setiap kind di semua vertikal, dan
 * menambah kind = mengubah renderer, jadi ia memang harus jadi kode. Kontras dengan *isi* layar
 * (judul, kolom, label aksi) yang tetap data pack.
 */
enum class WidgetKind(val code: String, val displayName: String) {
    FORM("FORM", "Formulir"),
    TABLE("TABLE", "Tabel"),
    KANBAN("KANBAN", "Papan Kanban"),
    DASHBOARD("DASHBOARD", "Dasbor"),
    CHECKLIST("CHECKLIST", "Daftar Periksa"),
    PRINT("PRINT", "Cetak Dokumen"),
    CUSTOM_SCREEN("CUSTOM_SCREEN", "Layar Kustom");

    companion object {
        fun fromCode(code: String): WidgetKind? = entries.firstOrNull { it.code == code }
    }
}
