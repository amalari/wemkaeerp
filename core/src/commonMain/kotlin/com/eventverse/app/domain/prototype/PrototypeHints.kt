package com.eventverse.app.domain.prototype

/**
 * Petunjuk perilaku papan dari pack: urutan kolom (termasuk yang kosong) dan transisi yang boleh.
 * Tanpa [transitions] kartu bebas pindah ke kolom mana pun.
 */
data class KanbanHints(
    val columns: List<String>,
    val transitions: Map<String, Set<String>> = emptyMap(),
    /** Nama kolom status di bahasa pack ("Status SPK"), dipakai di pesan penolakan. Null = "Kolom". */
    val groupLabel: String? = null
)

/**
 * Petunjuk perilaku tabel dari pack: kolom [statusColumn] bernilai salah satu [options] dan bisa
 * diubah di baris. Tanpa petunjuk, tabel hanya bisa disortir/difilter.
 */
data class TableHints(
    val statusColumn: String,
    val options: List<String>,
    val transitions: Map<String, Set<String>> = emptyMap()
)

/** Petunjuk dasbor dari pack: ubin berlabel [counts].key dihitung dari layar lain, bukan angka statis. */
data class DashboardHints(val counts: Map<String, CountSpec>)
