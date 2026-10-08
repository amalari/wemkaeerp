package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Operasi pada spec prototype (kontrak v1): satu-satunya cara klien/AI mengubah spec lewat percakapan.
 * Kosakata **tertutup** (sealed) — operasi di luar daftar ini tidak bisa dibentuk, jadi tidak ada
 * "operasi bebas" yang bisa diselundupkan LLM. Menambah jenis operasi = mengubah kode dan kontrak.
 * Kontrak v1 punya 5 jenis; v2 menambah [ShowFieldOnCard] dan [SetFieldRequired] (total 7, plan
 * induk §3.5); SP-B5 menambah [ChangeWidget] (total 8).
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

    /**
     * Ubah varian tampilan angka [field] (C4 Irisan 2): hanya field NUMBER; [currencyCode] wajib tepat bila
     * [format] = CURRENCY. Penyimpanan dan isi baris tidak berubah (kolom tetap `NUMERIC(18,4)`).
     */
    data class SetFieldFormat(val entityId: String, val field: String, val format: NumberFormat, val currencyCode: String? = null) : SpecOp

    /**
     * Ubah [field] DATE menjadi tanggal-saja (`withTime=false`) atau tanggal+jam (`withTime=true`) (A0(C6) Irisan 2).
     * Berbeda dari [SetFieldFormat], ini mengubah **bentuk nilai tersimpan** (`DATE` ↔ `TIMESTAMP`), jadi operasi
     * **ditolak** bila ada nilai baris contoh yang tak lolos bentuk baru — tidak ada konversi diam-diam.
     */
    data class SetFieldWithTime(val entityId: String, val field: String, val withTime: Boolean) : SpecOp

    /**
     * Ubah validasi bentuk [field] TEXT (A0(C9) Irisan 2). Menyempitkan aturan bisa membuat nilai lama tak sah, jadi
     * operasi **ditolak** bila ada nilai baris contoh yang tak lolos aturan baru; melonggarkan (mis. ke NONE) selalu lolos.
     */
    data class SetFieldValidation(val entityId: String, val field: String, val validation: TextValidation) : SpecOp

    /**
     * Ganti jenis tampilan layar [screenId] menjadi [widget] — hanya **TABLE** dan **KANBAN** (papan) yang dikenal;
     * jenis lain ditolak dengan pesan. Tabel selalu mungkin untuk layar data; papan butuh field pilihan status
     * (ENUM). Aturan kelayakan lengkap di `ChangeWidgetOp`.
     */
    data class ChangeWidget(val screenId: String, val widget: WidgetKind) : SpecOp
}
