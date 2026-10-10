package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType

/**
 * SATU tempat parser kompatibilitas kode tipe field **legacy CRM** (field-component-rules Kontrak 4;
 * PLAN-unify-field-vocabulary §3, keputusan D2): kode yang tersimpan di `custom_field_definitions.field_type`
 * memakai kosakata lama (`SINGLE_SELECT`, `CHECKBOX`, …) sementara kosakata baru adalah enum
 * [com.eventverse.app.domain.prototype.FieldType] kosakata prototype. **Tanpa migrasi data** — parser
 * inilah yang menyesuaikan; kode lama tetap terbaca selamanya, tulisan baru memakai nama enum ([toCode]).
 *
 * Aturan (Kontrak 4 variability): nilai `null`/kosong/tak dikenal **ditolak** dengan
 * [IllegalArgumentException] eksplisit — **tidak** jatuh senyap ke `TEXT` (fallback senyap = data berubah
 * tanpa jejak). Masukan yang sah: kode legacy ATAU nama enum (baris baru yang memakai nama enum, mis.
 * `TIME`, tetap terbaca). Pencocokan literal (tanpa trim): spasi di sekitar kode adalah korupsi data
 * yang harus terdengar, bukan dinormalisasi diam-diam.
 *
 * Slice A (CRM core) dan slice C (CRM UI) memakai objek ini sebagai satu-satunya jembatan; jangan menulis
 * peta `SINGLE_SELECT`/`CHECKBOX` di tempat lain.
 */
object CrmLegacyTypeCode {

    /** Peta kode legacy CRM (dan nama enum, yang identitasnya sama) -> anggota kosakata prototype. */
    private val legacyCodes: Map<String, FieldType> = mapOf(
        "TEXT" to FieldType.TEXT,
        "LONG_TEXT" to FieldType.LONG_TEXT,
        "NUMBER" to FieldType.NUMBER,
        "DATE" to FieldType.DATE,
        // Pasangan legacy -> enum yang jadi alasan parser ini ada (kode tersimpan ≠ nama enum).
        "SINGLE_SELECT" to FieldType.ENUM,
        "MULTI_SELECT" to FieldType.MULTI_SELECT,
        "CHECKBOX" to FieldType.BOOL,
        "RELATION" to FieldType.RELATION,
        "FILE" to FieldType.FILE,
        "USER_REF" to FieldType.USER_REF,
    )

    /**
     * Kode legacy CRM **atau** nama enum saat ini -> [FieldType]. Baris yang ditulis dengan nama enum baru
     * (mis. `TIME`, yang tak pernah punya kode legacy) tetap terbaca. Kosong/tak dikenal ->
     * [IllegalArgumentException] berpesan kosakata — fail-closed, bukan fallback.
     */
    fun toFieldType(code: String): FieldType {
        require(code.isNotEmpty()) { "Kode tipe field kosong; tidak dapat dibaca sebagai FieldType." }
        return legacyCodes[code]
            ?: FieldType.entries.firstOrNull { it.name == code }
            ?: throw IllegalArgumentException(
                "Kode tipe field '$code' tidak dikenal (kosakata: ${legacyCodes.keys.sorted().joinToString()})."
            )
    }

    /**
     * [FieldType] -> tulisan kode **baru** = nama enum, untuk SEMUA tipe (termasuk `TIME` yang tidak punya
     * kode legacy — tulisan baru lahir langsung memakai nama enum).
     */
    fun toCode(type: FieldType): String = type.name
}
