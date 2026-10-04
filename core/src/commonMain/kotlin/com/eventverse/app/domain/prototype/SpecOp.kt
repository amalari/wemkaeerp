package com.eventverse.app.domain.prototype

/**
 * Operasi pada spec prototype (kontrak v1): satu-satunya cara klien/AI mengubah spec lewat percakapan.
 * Kosakata **tertutup** (sealed) — operasi di luar daftar ini tidak bisa dibentuk, jadi tidak ada
 * "operasi bebas" yang bisa diselundupkan LLM. Menambah jenis operasi = mengubah kode dan kontrak.
 */
sealed interface SpecOp {
    /** Tambah status/kolom (opsi ENUM); [after] null = di akhir. */
    data class AddEnumOption(val entityId: String, val field: String, val option: String, val after: String? = null) : SpecOp
    /** Ganti nama opsi ENUM; seed, mesin status, dan kolom kanban ikut ditulis ulang. */
    data class RenameEnumOption(val entityId: String, val field: String, val from: String, val to: String) : SpecOp
    /** Izinkan perpindahan status [from] → [to] (keduanya sudah ada). */
    data class AddTransition(val entityId: String, val field: String, val from: String, val to: String) : SpecOp
    data class AddField(val entityId: String, val field: FieldSpec) : SpecOp
    /** Hanya mengubah label tampil; kunci field tetap. */
    data class RenameFieldLabel(val entityId: String, val key: String, val label: String) : SpecOp
}
