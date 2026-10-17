package com.eventverse.app.domain.evidence

/**
 * Alasan mengapa sistem perlu **bertanya** (TRD-BLDR-003 FR-4) — sengaja bukan [EvidenceRef]: follow-up lahir dari
 * informasi yang BELUM ada, jadi memaksa ia berevidence sama dengan memalsukan kutipan. Pertanyaan model wajib
 * dipetakan ke gap nyata; pertanyaan tanpa gap atau yang sudah terjawab ditolak/dinilai gagal.
 *
 * [code] dari registry platform (`ModuleGap` atau ekuivalen) — bukan teks bebas model. [inspectedInputs] = daftar
 * masukan yang sudah diperiksa sebelum menyimpulkan ada celah (bukti gap bukan kelalaian membaca).
 */
data class GapRef(
    val code: String,
    val moduleId: String? = null,
    val inspectedInputs: Set<String> = emptySet(),
) {
    init {
        require(code.isNotBlank()) { "GapRef.code kosong" }
        inspectedInputs.forEach { require(it.isNotBlank()) { "GapRef.inspectedInputs memuat entri kosong" } }
    }
}
