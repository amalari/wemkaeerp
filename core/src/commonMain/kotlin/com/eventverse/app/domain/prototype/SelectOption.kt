package com.eventverse.app.domain.prototype

/**
 * Satu pilihan field ENUM/[FieldType.MULTI_SELECT] — tipe bersama kedua kosakata (registry tunggal,
 * PLAN-unify-field-vocabulary §2a follow-up): dipakai [FieldSpec.options] (prototype) dan
 * `CrmFieldType.options` (CRM).
 *
 * [colorHex] adalah **data tenant** (satu-satunya pengecualian sah literal warna di luar theme,
 * sama seperti `PhaseDefinition.colorHex`). `null` = belum diwarnai pemilik tenant — bawaan opsi
 * prototype yang lahir dari nama polos; UI memakai token netral, bukan menebak warna. Menghapus
 * opsi = arsip lembut ([archivedAt]), bukan membuangnya: sel yang masih menunjuk opsi terarsip
 * tetap dirender (redup), tidak pernah kehilangan data diam-diam.
 */
data class SelectOption(
    val id: SelectOptionId,
    val label: String,
    val colorHex: String? = null,
    val archivedAt: String? = null
) {
    init {
        require(label.isNotBlank()) { "SelectOption.label cannot be blank" }
        require(colorHex == null || COLOR_HEX_REGEX.matches(colorHex)) { "SelectOption.colorHex must be #RRGGBB: $colorHex" }
    }

    /**
     * Opsi dari **nama polos** (kosakata prototype: kawat opsi tetap larik string label — kompatibel
     * dokumen lama). Id diturunkan dari label dan tidak pernah mengalir ke kawat prototype: nilai sel
     * prototype = label, bukan id, jadi id di sini hanya identitas internal. Label >64 karakter
     * (sah menurut ProposalLimits.TEXT) menghasilkan id terpotong — dua label sepanjang itu boleh
     * ber-id kembar tanpa efek; keunikan opsi dijaga pada label (invarian [FieldSpec]).
     */
    constructor(label: String) : this(SelectOptionId(label.take(SelectOptionId.MAX_LENGTH)), label, null, null)

    companion object {
        private val COLOR_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}

/**
 * Identitas satu [SelectOption]. CRM menyimpan id di sel nilai (nilai sel = id opsi); prototype
 * menyimpan label. Kap 64 karakter — opsi berlabel lebih panjang memakai id terpotong (lihat
 * konstruktor sekunder [SelectOption]).
 */
@JvmInline
value class SelectOptionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SelectOptionId cannot be blank" }
        require(value.length <= MAX_LENGTH) { "SelectOptionId must be at most $MAX_LENGTH characters" }
    }

    companion object {
        const val MAX_LENGTH: Int = 64
    }
}

/** Pembantu fixture/generator: daftar opsi dari nama polos (bentuk kawat prototype). */
fun selectOptions(vararg labels: String): List<SelectOption> = labels.map { SelectOption(it) }
