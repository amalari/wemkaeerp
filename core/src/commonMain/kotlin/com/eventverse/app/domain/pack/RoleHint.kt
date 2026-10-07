package com.eventverse.app.domain.pack

/**
 * Satu butir **kamus peran → modul** milik pack (PLAN-iv-B B2): kata/frasa [word] yang lazim muncul di cerita
 * pemilik usaha ("operator jahit", "admin gudang") → modul [moduleId] yang lazim dipegang peran itu, dengan
 * [label] nama peran tampil.
 *
 * **Kenapa data pack, bukan kode** (Uji Variabilitas): "perawat" → modul poli benar untuk klinik dan tak berarti
 * apa pun untuk konveksi; tiap industri punya kamusnya. Pola sama dengan [SlotDefinition.defaultWidget]:
 * **pack tanpa kamus ⇒ tidak ada tebakan** (pewawancara bertanya terbuka), tidak pernah meminjam kamus pack lain.
 *
 * Opsional & kompatibel mundur: pack lama tanpa kunci `roleHints` terbaca dengan daftar kosong.
 */
data class RoleHint(val word: String, val label: String, val moduleId: ModuleId) {
    init {
        require(word.isNotBlank() && word == word.trim().lowercase()) { "RoleHint.word '$word' wajib terisi, huruf kecil, tanpa spasi tepi" }
        require(label.isNotBlank()) { "RoleHint.label untuk '$word' kosong" }
    }
}

/**
 * Hint terbaik untuk [text] (mis. "Operator Jahit Senior"), atau null bila kamus pack tidak mengenalnya —
 * **termasuk** bila pack tak punya kamus. Pencocokan per kata/frasa utuh, tak peka huruf; frasa terpanjang menang
 * (deterministik), seri → urutan kamus.
 */
fun DomainPack.guessRoleHint(text: String): RoleHint? {
    val haystack = text.lowercase()
    return roleHints
        .filter { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it.word)}(?![\\p{L}\\p{N}])").containsMatchIn(haystack) }
        .maxByOrNull { it.word.length }
}
