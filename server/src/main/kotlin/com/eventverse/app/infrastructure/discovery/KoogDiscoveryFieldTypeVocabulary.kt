package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.prototype.FieldType

/**
 * Kosakata tipe field untuk model (kosakata **prototype**, `EntitySpec.kt`; terpisah dari CRM `FieldType` — D2).
 * Daftar tipe dibaca dari `FieldType.entries` dan catatan katalog dari `when` tanpa `else`, jadi tipe baru yang
 * tak punya catatan gagal kompilasi, dan prompt/katalog tidak bisa menyimpang dari enum (field-component-rules
 * Kontrak 6). Aturan TEXT vs LONG_TEXT hanya soal pilihan tipe: keduanya string bebas berkolom SQL `TEXT`
 * tanpa batasan format, dan validator usulan tidak membedakannya.
 */
internal object KoogDiscoveryFieldTypeVocabulary {

    /** Daftar nama tipe, mis. "TEXT|LONG_TEXT|…" — dibaca dari enum. */
    val names: String get() = FieldType.entries.joinToString("|") { it.name }

    /** Catatan per tipe untuk `screen_catalog.fieldTypes` (wajib ada tiap tipe). */
    fun note(type: FieldType): String = when (type) {
        FieldType.TEXT -> "teks satu baris: nama, kode, judul, nomor, alamat surel, telepon; parameter `validation` " +
            "(EMAIL/PHONE) untuk surel/telepon, lihat fieldParams"
        FieldType.LONG_TEXT -> "teks panjang multibaris (catatan, keluhan, deskripsi, instruksi, alamat lengkap); " +
            "pilih ini bila isinya biasanya sekalimat atau lebih; nama/kode/judul tetap TEXT; " +
            "di seed ditulis sebagai teks biasa"
        FieldType.NUMBER -> "angka; di seed ditulis sebagai teks \"5\""
        FieldType.DATE -> "tanggal ISO YYYY-MM-DD; parameter `withTime` true untuk waktu bermenit YYYY-MM-DDTHH:MM, lihat fieldParams"
        FieldType.ENUM -> "wajib options 2-${ProposalLimits.OPTIONS} pilihan; dipakai untuk status kerja"
        FieldType.BOOL -> "nilai \"ya\" atau \"tidak\""
        // C7 (TRD-FIELD-001): rujukan antar entitas; FR-6 — wajib target, seed kosong.
        FieldType.RELATION -> "rujukan antar entitas/modul; parameter `target` wajib (\"entityId\" atau " +
            "\"moduleId:entityId\", lihat fieldParams); di seed WAJIB kosong — nilai rujukan diisi data nyata, bukan contoh"
    }

    /** Aturan prompt: daftar tipe + pemilihan TEXT vs LONG_TEXT. Satu baris (disisipkan ke teks ber-indentasi). */
    val promptRule: String
        get() = "`fields` bertipe {$names} (ENUM wajib `options` 2–${ProposalLimits.OPTIONS} pilihan; tipe lain tanpa " +
            "`options`; ${FieldType.LONG_TEXT} untuk isi sekalimat atau lebih seperti catatan/keluhan/deskripsi, " +
            "${FieldType.TEXT} untuk nama/kode/judul satu baris); " +
            KoogDiscoveryNumberFormatVocabulary.promptRule + "; " + KoogDiscoveryDateTimeValidationVocabulary.promptRule
}
