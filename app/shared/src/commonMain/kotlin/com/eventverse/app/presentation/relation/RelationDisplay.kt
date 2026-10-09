package com.eventverse.app.presentation.relation

/**
 * Bentuk tampil nilai field rujukan (C7, TRD-FIELD-001 FR-3): label bila diketahui,
 * fallback id bila target belum diresolusi, atau "tidak ditemukan (id)" bila target hilang.
 *
 * [resolved] = label diketahui (atau nilai kosong, tak ada yang perlu dicari).
 * [missing] = resolver tersedia tapi tidak menemukan id → render abu di pemanggil.
 */
data class RelationDisplay(val text: String, val resolved: Boolean, val missing: Boolean)

/**
 * Hitung tampilan nilai `RELATION` tanpa memanggil jaringan. [labelFor] `null` berarti host
 * belum punya cache label (mode demo) → tampilkan id apa adanya (fallback jujur, bukan
 * "tidak ditemukan" palsu). Bila [labelFor] ada tapi mengembalikan `null`, target memang hilang.
 */
fun relationDisplay(stored: String, labelFor: ((String) -> String?)?): RelationDisplay {
    val id = stored.trim()
    if (id.isEmpty()) return RelationDisplay(text = "—", resolved = true, missing = false)
    val label = labelFor?.invoke(id)
    return when {
        label != null -> RelationDisplay(text = label, resolved = true, missing = false)
        labelFor != null -> RelationDisplay(text = "Tidak ditemukan ($id)", resolved = false, missing = true)
        else -> RelationDisplay(text = id, resolved = false, missing = false)
    }
}
