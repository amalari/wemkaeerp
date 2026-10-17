package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource

/**
 * Adaptor kompatibilitas `BasisRef` wawancara → [EvidenceRef] (TRD-BLDR-003 FR-1.5): dokumen wawancara lama
 * **tidak diubah** (bentuk JSON `basis`/`quote`/`answerId` tetap); peminjam buktinya (brief, skenario) menerima
 * kosakata evidence lewat sini. Pemetaan jujur:
 *
 * - NARASI → [EvidenceSource.NARRATIVE] (kutipan dibawa apa adanya; validator narasi tujuan yang membuktikan substring).
 * - JAWABAN → [EvidenceSource.INTERVIEW_ANSWER] (`answerId` = questionId).
 * - SARAN_DITERIMA → [EvidenceSource.ACCEPTED_SUGGESTION] (`answerId` menunjuk giliran penerimaan).
 * - SARAN_BELUM_DIJAWAB → `null` — bukan bukti (validator wawancara pun menolaknya); peminjam wajib menghapus
 *   butir tanpa dasar, bukan mengarang bukti.
 */
fun BasisRef?.toEvidenceRef(): EvidenceRef? = when (this?.basis) {
    Basis.NARASI -> EvidenceRef(EvidenceSource.NARRATIVE, quote = quote)
    Basis.JAWABAN -> EvidenceRef(EvidenceSource.INTERVIEW_ANSWER, referenceId = answerId)
    Basis.SARAN_DITERIMA -> EvidenceRef(EvidenceSource.ACCEPTED_SUGGESTION, referenceId = answerId)
    else -> null
}
