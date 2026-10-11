package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.SelectOption
import com.eventverse.app.domain.prototype.SelectOptionId

/**
 * Tipe satu field kustom CRM = **kosakata tipe bersama** ([FieldType] prototype, registry tunggal —
 * diputuskan 2026-10-10, lihat `docs/plannings/PLAN-unify-field-vocabulary.md`) + parameter khas CRM
 * yang tidak dikenal kosakata discovery (opsi berwarna dengan arsip, `maxCount` rujukan).
 *
 * Kode tersimpan (`custom_field_definitions.field_type`) dibaca lewat [CrmLegacyTypeCode] — kode legacy
 * `SINGLE_SELECT`/`CHECKBOX` tetap sah selamanya; tulisan baru memakai nama enum ([code]). Karena
 * registry-nya enum prototype, CRM otomatis mewarisi tipe baru (mis. `TIME`, `MULTI_SELECT`) tanpa
 * pendaftaran kedua.
 *
 * Invarian per jenis dijaga di [init]; parameter yang tidak relevan untuk [kind] diabaikan (bukan
 * ditafsirkan) supaya cell/seed tidak pernah "berubah bentuk" diam-diam.
 */
data class CrmFieldType(
    val kind: FieldType,
    /** ENUM/MULTI_SELECT: opsi terpilih tenant (warna + arsip lembut); tipe bersama [SelectOption] — di discovery opsi lahir dari nama polos (`SelectOption(label)`, tanpa warna). */
    val options: List<SelectOption> = emptyList(),
    /** NUMBER: varian tampilan angka (polos/persen/mata uang). */
    val format: NumberFormat = NumberFormat.Plain,
    /** NUMBER: batas desimal masukan (0..6); `null` = tidak dibatasi. */
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
        require(decimals == null || decimals in 0..MAX_DECIMALS) {
            "CrmFieldType.decimals must be within 0..$MAX_DECIMALS: $decimals"
        }
        require(maxCount >= 1) { "CrmFieldType.maxCount must be at least 1: $maxCount" }
        if (kind == FieldType.RELATION) {
            require(!targetResource.isNullOrBlank()) { "CrmFieldType RELATION requires targetResource" }
        }
        require(maxSelections == null || maxSelections >= 1) {
            "CrmFieldType.maxSelections must be at least 1: $maxSelections"
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
        /** Batas desimal yang wajar untuk jumlah/rata-rata; sinkron dengan `FieldSpec.MAX_NUMBER_DECIMALS`. */
        const val MAX_DECIMALS: Int = 6
    }
}

sealed interface NumberFormat {
    data object Plain : NumberFormat
    data object Percent : NumberFormat
    data class Currency(val currencyCode: String) : NumberFormat {
        init {
            require(currencyCode.length == 3) { "Currency code must be ISO-4217 (3 letters): $currencyCode" }
        }
    }
}
