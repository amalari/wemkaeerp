package com.eventverse.app.domain.prototype

/**
 * Fixture bersama tes paritas kosakata prototype (field-component-rules Kontrak 7). `when` di bawah
 * **tanpa `else`** (Kontrak 6): tipe baru di [FieldType] membuat tes paritas gagal kompilasi, bukan lolos diam-diam.
 */
object PrototypeFieldTypeSampleFields {

    /** Field sah untuk [type]; ENUM membawa opsi, tipe lain tanpa opsi. Kunci unik per tipe. */
    fun fieldFor(type: FieldType, required: Boolean = false): FieldSpec = when (type) {
        FieldType.TEXT -> FieldSpec("catatan_jahit", "Catatan", type, required = required)
        FieldType.LONG_TEXT -> FieldSpec("catatan_panjang", "Catatan panjang", type, required = required)
        FieldType.NUMBER -> FieldSpec("jumlah_titik", "Jumlah titik", type, required = required)
        FieldType.DATE -> FieldSpec("tanggal_desain", "Tanggal desain", type, required = required)
        FieldType.ENUM -> FieldSpec("tahap", "Tahap", type, listOf("Digitizing", "Hooping", "Selesai"), required)
        FieldType.BOOL -> FieldSpec("sudah_disetujui", "Disetujui", type, required = required)
        FieldType.RELATION -> FieldSpec("rujukan_po", "Rujukan PO", type, required = required, target = "pesanan")
    }

    /** Satu field per tipe, urutan [FieldType.entries]. */
    fun allFields(required: Boolean = false): List<FieldSpec> = FieldType.entries.map { fieldFor(it, required) }

    /** Nilai sah bagi [type] (dipakai sebagai baris contoh/seed). */
    fun validValue(type: FieldType): String = when (type) {
        FieldType.TEXT -> "Benang satin"
        FieldType.LONG_TEXT -> "Instruksi lengkap: jahit lapisan kain, benang polyester, lalu finishing."
        FieldType.NUMBER -> "12000"
        FieldType.DATE -> "2026-10-08"
        FieldType.ENUM -> "Hooping"
        FieldType.BOOL -> "ya"
        FieldType.RELATION -> "po-001"
    }
}
