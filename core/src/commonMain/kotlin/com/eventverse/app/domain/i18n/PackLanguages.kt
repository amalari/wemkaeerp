package com.eventverse.app.domain.i18n

/**
 * Bahasa yang **diklaim lengkap** oleh sebuah pack (TRD-I18N-002 F2). `id` selalu ada. Pack tanpa klaim `en`
 * tetap sah: UI memakai `id` dan menampilkan lencana "Belum diterjemahkan" ([ResolvedText.isFallback]).
 *
 * Klaim bukan niat — [PackLanguageValidator] membuktikannya: pack yang mengklaim `en` tetapi punya satu teks tanpa
 * `en` gagal validasi, sehingga "lengkap" bisa dipercaya mesin.
 */
data class PackLanguages(val languages: Set<LanguageCode>) {
    init { require(LanguageCode.ID in languages) { "PackLanguages wajib memuat 'id'" } }

    companion object {
        /** Bawaan pack lama: hanya Indonesia. */
        val ID_ONLY = PackLanguages(setOf(LanguageCode.ID))
    }
}

/** Satu teks yang melanggar klaim bahasa: [path] menunjuk jalurnya (mis. `phases[2].displayName`). */
data class MissingTranslation(val path: String, val language: LanguageCode)

object PackLanguageValidator {

    /**
     * Selisih antara klaim [claimed] dan isi [entries] (path → teks). Kosong = pack lengkap.
     * Urutan hasil mengikuti urutan [entries], lalu urutan bahasa klaim (id dilewati: wajib oleh konstruksi).
     */
    fun missing(claimed: PackLanguages, entries: List<Pair<String, LocalizedText>>): List<MissingTranslation> {
        val required = claimed.languages.filter { it != LanguageCode.ID }.sortedBy { it.code }
        return entries.flatMap { (path, text) ->
            required.filter { !text.has(it) }.map { MissingTranslation(path, it) }
        }
    }

    /** Pelaporan untuk UI/CI: jumlah teks yang belum diterjemahkan per bahasa. */
    fun countByLanguage(missing: List<MissingTranslation>): Map<LanguageCode, Int> =
        missing.groupingBy { it.language }.eachCount()
}
