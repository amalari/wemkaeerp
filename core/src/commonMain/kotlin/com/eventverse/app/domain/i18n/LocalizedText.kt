package com.eventverse.app.domain.i18n

/**
 * Teks data pack dalam beberapa bahasa (TRD-I18N-002). **Data, bukan resource**: beda per pack/industri/admin
 * (Uji Variabilitas), tersimpan di dokumen pack, dan bisa berubah tanpa rilis.
 *
 * Aturan:
 *  - Bahasa `id` **wajib** dan tak kosong (bahasa dasar; pack lama berbahasa Indonesia saja tetap sah).
 *  - Bahasa lain opsional; nilai kosong ditolak.
 *  - [resolve] mengembalikan [ResolvedText], bukan `String`: pemanggil tahu bila teks kembali ke `id` dan
 *    wajib menandainya (lencana "Belum diterjemahkan") — **tidak ada fallback senyap** (Kontrak 4).
 *  - Dokumen yang sudah **membeku** (SPK `frozenStageFlow`) menyimpan teks apa adanya; mengubah pack tidak
 *    mengubahnya (Kontrak 5).
 */
class LocalizedText private constructor(private val byLanguage: Map<LanguageCode, String>) {

    /** Bahasa yang punya terjemahan; selalu memuat [LanguageCode.ID]. */
    val languages: Set<LanguageCode> get() = byLanguage.keys

    /** Teks bahasa dasar. Jembatan Strangler Fig untuk pembaca lama yang masih `String` (hapus saat tahap ditutup). */
    val idText: String get() = byLanguage.getValue(LanguageCode.ID)

    fun has(language: LanguageCode): Boolean = language in byLanguage

    /** Teks persis di [language], atau `null` bila belum ada. Untuk validator/ekspor — bukan untuk tampilan. */
    fun textOrNull(language: LanguageCode): String? = byLanguage[language]

    fun resolve(requested: LanguageCode): ResolvedText {
        val exact = byLanguage[requested]
        return if (exact != null) ResolvedText(exact, requested, requested)
        else ResolvedText(idText, LanguageCode.ID, requested)
    }

    /** Salinan baru dengan terjemahan [language] ditambah/diganti. Mengganti `id` tetap wajib tak kosong. */
    fun with(language: LanguageCode, text: String): LocalizedText = fromMap(byLanguage + (language to text))

    /** Pasangan (bahasa, teks) terurut: `id` dahulu, lalu sisanya menurut kode — untuk penulisan yang stabil. */
    fun entries(): List<Pair<LanguageCode, String>> =
        byLanguage.entries.sortedBy { if (it.key == LanguageCode.ID) "" else it.key.code }.map { it.key to it.value }

    override fun equals(other: Any?): Boolean = other is LocalizedText && other.byLanguage == byLanguage
    override fun hashCode(): Int = byLanguage.hashCode()
    override fun toString(): String = "LocalizedText($byLanguage)"

    companion object {
        /** Batas panjang satu teks. Tutorial/wawancara panjang butuh ruang; muatan raksasa tetap ditolak. */
        const val MAX_LENGTH: Int = 4000

        fun of(id: String, en: String? = null): LocalizedText =
            fromMap(listOfNotNull(LanguageCode.ID to id, en?.let { LanguageCode.EN to it }).toMap())

        fun fromMap(map: Map<LanguageCode, String>): LocalizedText {
            require(LanguageCode.ID in map) { "LocalizedText wajib memuat bahasa 'id'" }
            map.forEach { (language, text) ->
                require(text.isNotBlank()) { "Teks bahasa '${language.code}' kosong" }
                require(text.length <= MAX_LENGTH) { "Teks bahasa '${language.code}' melebihi $MAX_LENGTH karakter" }
            }
            return LocalizedText(map.toMap())
        }
    }
}

/**
 * Hasil [LocalizedText.resolve]. [usedLanguage] ≠ [requested] berarti terjemahan belum ada dan teks `id` dipakai;
 * UI wajib menampilkan penandanya ([isFallback]).
 */
data class ResolvedText(val text: String, val usedLanguage: LanguageCode, val requested: LanguageCode) {
    val isFallback: Boolean get() = usedLanguage != requested
}
