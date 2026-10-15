package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.prototype.FieldType

/**
 * Kompatibilitas tipe untuk [FieldCarry] (TRD-BLDR-002 FR-2/FR-6) — SATU sumber untuk validator ([ScenarioRules]) dan
 * pembawa nilai ([DefaultScenarioCarryRule]) supaya keduanya tidak bisa berbeda.
 *
 * **Keputusan (TRD §0 mencatat ini belum terverifikasi)**: `FieldTypeConversion` TIDAK dipakai ulang. Ia beroperasi pada
 * `CrmFieldType` (sealed, ber-parameter) dan sel `JsonValue`, bukan pada [FieldType] prototype yang bernilai string, dan
 * jalur "LOSSY + dry run" miliknya butuh konfirmasi pengguna — simulasi tidak punya langkah itu. Yang dipinjam hanya
 * prinsipnya: hanya konversi tanpa kehilangan (IDENTITY dan LOSSLESS) yang sah; RELATION/USER_REF/FILE tidak pernah
 * dikoersi lintas jenis.
 *
 * Aturan tingkat tipe (nilai konkret tetap diperiksa `FieldSpec.accepts` saat dibawa):
 * - jenis sama: sah, kecuali FILE (ref terikat record asal) — selalu ditolak;
 * - ke TEXT/LONG_TEXT dari NUMBER, DATE, TIME, ENUM, BOOL, USER_REF, serta TEXT dan LONG_TEXT satu sama lain: sah;
 * - selain itu (mis. TEXT ke ENUM, DATE ke NUMBER, apa pun lintas jenis dengan RELATION/MULTI_SELECT): ditolak.
 */
object ScenarioCarryTypes {

    private val textual = setOf(FieldType.TEXT, FieldType.LONG_TEXT)
    private val readableAsText = setOf(
        FieldType.NUMBER, FieldType.DATE, FieldType.TIME, FieldType.ENUM, FieldType.BOOL, FieldType.USER_REF
    )

    fun compatible(from: FieldType, to: FieldType): Boolean = when {
        from == FieldType.FILE || to == FieldType.FILE -> false
        from == to -> true
        to in textual -> from in textual || from in readableAsText
        else -> false
    }

    /** Alasan tak kompatibel untuk pesan galat, atau `null` bila sah. */
    fun problem(from: FieldType, to: FieldType): String? =
        if (compatible(from, to)) null
        else "tipe ${from.name} tidak dapat dibawa ke ${to.name} tanpa kehilangan"
}
