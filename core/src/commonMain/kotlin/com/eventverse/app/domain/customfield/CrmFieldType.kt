package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.MAX_NUMBER_DECIMALS
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.SelectOption
import com.eventverse.app.domain.prototype.SelectOptionId

/**
 * Tipe satu field kustom CRM = **kosakata tipe bersama** ([FieldType] prototype, registry tunggal —
 * diputuskan 2026-10-10, lihat `docs/plannings/PLAN-unify-field-vocabulary.md`) + muatan parameter
 * yang kini juga bersama: [SelectOption], [NumberFormat], `decimals`, `maxCount`, `maxSelections`
 * (nama & arti yang sama dengan `FieldSpec`, PLAN-unify §2a follow-up). Tinggal `targetResource`
 * (padanan `FieldSpec.target`, kunci wire berbeda) yang sengaja tetap khas CRM.
 *
 * Kode tersimpan (`custom_field_definitions.field_type`) dibaca lewat [CrmLegacyTypeCode] — kode legacy
 * `SINGLE_SELECT`/`CHECKBOX` tetap sah selamanya; tulisan baru memakai nama enum ([code]). Karena
 * registry-nya enum prototype, CRM otomatis mewarisi tipe baru (mis. `TIME`, `MULTI_SELECT`) tanpa
 * pendaftaran kedua.
 *
 * Invarian per jenis dijaga di [init]; parameter yang tidak relevan untuk [kind] diabaikan (bukan
 * ditafsirkan) supaya cell/seed tidak pernah "berubah bentuk" diam-diam. Aturan yang **beda** dari
 * `FieldSpec` (opsi boleh kosong saat dialog belum selesai, `maxSelections` tidak dibatasi jumlah
 * opsi, kode mata uang cukup 3 huruf apa pun kasusnya) tetap dipertahankan di sini agar data lama
 * tidak berubah bentuk diam-diam.
 */
data class CrmFieldType(
    val kind: FieldType,
    /** ENUM/MULTI_SELECT: opsi terpilih tenant (warna + arsip lembut); tipe bersama [SelectOption] — di discovery opsi lahir dari nama polos (`SelectOption(label)`, tanpa warna). */
    val options: List<SelectOption> = emptyList(),
    /** NUMBER: varian tampilan angka (polos/persen/mata uang) — enum bersama kosakata prototype. */
    val format: NumberFormat = NumberFormat.PLAIN,
    /** NUMBER: kode mata uang tiga huruf; wajib tepat bila [format] = [NumberFormat.CURRENCY], wajib `null` selain itu. */
    val currencyCode: String? = null,
    /** NUMBER: batas desimal masukan (0..[MAX_NUMBER_DECIMALS]); `null` = tidak dibatasi. */
    val decimals: Int? = null,
    /** DATE: `true` = tanggal berwaktu `TTTT-BB-HH'T'JJ:MM`. */
    val withTime: Boolean = false,
    /** USER_REF/RELATION: jumlah rujukan yang boleh disimpan. */
    val maxCount: Int = 1,
    /** RELATION: resource target yang sah dirujuk (aturan R1 `ModuleReferenceRules`). */
    val targetResource: String? = null,
    /** MULTI_SELECT: batas jumlah pilihan (1..jumlah opsi); `null` = tidak dibatasi. */
    val maxSelections: Int? = null
) {
    init {
        require(decimals == null || decimals in 0..MAX_NUMBER_DECIMALS) {
            "CrmFieldType.decimals must be within 0..$MAX_NUMBER_DECIMALS: $decimals"
        }
        require(maxCount >= 1) { "CrmFieldType.maxCount must be at least 1: $maxCount" }
        if (kind == FieldType.RELATION) {
            require(!targetResource.isNullOrBlank()) { "CrmFieldType RELATION requires targetResource" }
        }
        require(maxSelections == null || maxSelections >= 1) {
            "CrmFieldType.maxSelections must be at least 1: $maxSelections"
        }
        // Muatan bersama: aturan format/kode mata uang sama dengan FieldSpec; bentuk kode tetap
        // longgar (3 huruf apa pun kasusnya) supaya kode legacy tidak ditolak saat dibaca.
        if (format == NumberFormat.CURRENCY) {
            require(currencyCode != null && currencyCode.length == 3) {
                "CrmFieldType CURRENCY requires a 3-letter currency code, got '$currencyCode'"
            }
        } else {
            require(currencyCode == null) { "CrmFieldType format ${format.name} must not carry a currency code" }
        }
    }

    /** Kode tulis-baru: nama enum bersama. Pembacaan legacy lewat [CrmLegacyTypeCode]. */
    val code: String get() = CrmLegacyTypeCode.toCode(kind)

    /** Opsi yang belum diarsipkan — sel yang masih menunjuk opsi terarsip tetap dirender (redup). */
    val activeOptions: List<SelectOption> get() = options.filter { it.archivedAt == null }

    fun findOption(id: SelectOptionId): SelectOption? = options.firstOrNull { it.id == id }

    /** Butuh baris integritas rujukan (employees atau record modul lain). */
    val isReferential: Boolean get() = kind == FieldType.USER_REF || kind == FieldType.RELATION

    companion object {
        /** Batas desimal yang wajar untuk jumlah/rata-rata; alias registry bersama `FieldSpec.MAX_NUMBER_DECIMALS`. */
        const val MAX_DECIMALS: Int = MAX_NUMBER_DECIMALS
    }
}
