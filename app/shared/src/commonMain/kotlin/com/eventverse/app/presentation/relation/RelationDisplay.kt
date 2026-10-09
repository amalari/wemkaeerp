package com.eventverse.app.presentation.relation

/**
 * Bentuk tampil nilai field rujukan (C7, TRD-FIELD-001 FR-3): label bila diketahui,
 * fallback id bila target belum diresolusi, atau "tidak ditemukan (id)" bila target hilang.
 *
 * [missing] = resolver otoritatif tersedia tapi tidak menemukan id → render abu di pemanggil.
 */
data class RelationDisplay(val text: String, val missing: Boolean)

/**
 * Hitung tampilan nilai `RELATION` tanpa memanggil jaringan. [labelFor] `null` berarti host
 * belum punya sumber label (mode demo) → tampilkan id apa adanya (fallback jujur, bukan
 * "tidak ditemukan" palsu). Bila [labelFor] ada tapi mengembalikan `null`, target memang hilang.
 */
fun relationDisplay(stored: String, labelFor: ((String) -> String?)?): RelationDisplay {
    val id = stored.trim()
    if (id.isEmpty()) return RelationDisplay(text = "—", missing = false)
    val label = labelFor?.invoke(id)
    return when {
        label != null -> RelationDisplay(text = label, missing = false)
        labelFor != null -> RelationDisplay(text = "Tidak ditemukan ($id)", missing = true)
        else -> RelationDisplay(text = id, missing = false)
    }
}

/**
 * Resolver label untuk tampilan baca dari sebuah cache lokal: label bila sudah diketahui, jika
 * tidak **id apa adanya**. Cache kosong/tak memuat id berarti "belum diresolusi" — bukan bukti
 * target hilang; status "tidak ditemukan" menuntut pemeriksaan otoritatif ke server, sedangkan
 * jalur baca lokal tidak punya informasi itu. Tanpa helper ini, resolver yang selalu `null`
 * membuat **setiap** nilai tampil "Tidak ditemukan (id)" meski targetnya ada.
 */
fun cachedRelationLabel(lookup: (String) -> String?): (String) -> String? = { id -> lookup(id) ?: id }
