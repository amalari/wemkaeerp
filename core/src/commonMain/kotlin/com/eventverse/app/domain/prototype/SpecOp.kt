package com.eventverse.app.domain.prototype

/**
 * Operasi pada spec prototype (kontrak v1): satu-satunya cara klien/AI mengubah spec lewat percakapan.
 * Kosakata **tertutup** (sealed) — operasi di luar daftar ini tidak bisa dibentuk, jadi tidak ada
 * "operasi bebas" yang bisa diselundupkan LLM. Menambah jenis operasi = mengubah kode dan kontrak.
 * Kontrak v1 punya 5 jenis; v2 menambah [ShowFieldOnCard] dan [SetFieldRequired] (total 7, plan
 * induk §3.5).
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

    /** Tampilkan [field] pada kartu kanban dengan gaya [style]; mengganti elemen field yang sudah ada (kontrak v2). */
    data class ShowFieldOnCard(val entityId: String, val field: String, val style: CardStyle = CardStyle.TEXT) : SpecOp
    /** Ubah kewajiban isi [field]; ditegakkan reducer pada `Create` (kontrak v2). */
    data class SetFieldRequired(val entityId: String, val field: String, val required: Boolean) : SpecOp
}
