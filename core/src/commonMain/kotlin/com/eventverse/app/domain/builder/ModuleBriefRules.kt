package com.eventverse.app.domain.builder

import com.eventverse.app.domain.builder.ModuleBriefLimits as L
import com.eventverse.app.domain.evidence.EvidenceContext
import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceRules

/**
 * Aturan FR-4 TRD-BLDR-001 + FR-3 TRD-BLDR-003 atas keluaran [ModuleBriefer]; angka dari [ModuleBriefLimits]. Murni,
 * tanpa I/O.
 *
 * **Keputusan features kosong**: `features` wajib 1–[L.FEATURES_MAX] untuk brief `source = AI` (model harus
 * menghasilkan sesuatu yang bermakna). Brief [BriefSource.DETERMINISTIC] boleh kosong: ia dirakit dari tautan peran
 * dan tidak boleh mengarang butir — modul tanpa tautan fitur tetap mendapat brief yang jujur. Batas atas, panjang,
 * dan duplikat tetap berlaku untuk keduanya.
 *
 * **Evidence (TRD-BLDR-003 FR-3.3)**: brief kontrak v2 wajib membawa bukti sah di setiap fitur dan tahap — butir
 * tanpa bukti hanya sah pada payload legacy v1. Kewajiban ditegakkan di sini (bukan konstruktor) supaya keluaran
 * agent bisa dikoreksi dengan umpan balik berpath; kecukupan referensinya divalidasi [EvidenceRules].
 *
 * `stages` lebih dari [L.STAGES_MAX] **ditolak** (minta dikelompokkan), tidak dipotong diam-diam.
 */
object ModuleBriefRules : ModuleBriefValidator {

    override fun validate(brief: ModuleBrief, evidence: EvidenceContext): List<BriefIssue> = buildList {
        summary(brief)
        features(brief, evidence)
        stages(brief, evidence)
        followUps(brief)
    }

    private fun MutableList<BriefIssue>.summary(b: ModuleBrief) {
        if (b.summary.isBlank()) add(BriefIssue("summary", "ringkasan tidak boleh kosong"))
        else if (b.summary.length > L.SUMMARY_MAX_CHARS) add(BriefIssue("summary", "maksimal ${L.SUMMARY_MAX_CHARS} karakter"))
    }

    private fun MutableList<BriefIssue>.features(b: ModuleBrief, evidence: EvidenceContext) {
        if (b.features.isEmpty() && b.source == BriefSource.AI) add(BriefIssue("features", "minimal 1 butir fitur"))
        if (b.features.size > L.FEATURES_MAX) add(BriefIssue("features", "maksimal ${L.FEATURES_MAX} butir"))
        val seen = mutableSetOf<String>()
        b.features.forEachIndexed { i, f ->
            if (f.text.isBlank()) add(BriefIssue("features[$i]", "butir kosong"))
            else if (f.text.length > L.FEATURE_MAX_CHARS) add(BriefIssue("features[$i]", "maksimal ${L.FEATURE_MAX_CHARS} karakter"))
            if (f.text.isNotBlank() && !seen.add(BriefKeys.normalize(f.text))) add(BriefIssue("features[$i]", "duplikat"))
            itemEvidence(b, "features[$i]", f.evidence, evidence)
        }
    }

    private fun MutableList<BriefIssue>.stages(b: ModuleBrief, evidence: EvidenceContext) {
        if (b.stages.size > L.STAGES_MAX) {
            add(BriefIssue("stages", "maksimal ${L.STAGES_MAX} tahap; kelompokkan tahap yang berdekatan"))
        }
        val earlier = mutableSetOf<String>()
        b.stages.forEachIndexed { i, s ->
            val key = BriefKeys.normalize(s.name)
            if (s.name.isBlank()) add(BriefIssue("stages[$i].name", "nama kosong"))
            else if (s.name.length > L.STAGE_NAME_MAX_CHARS) add(BriefIssue("stages[$i].name", "maksimal ${L.STAGE_NAME_MAX_CHARS} karakter"))
            if (s.name.isNotBlank() && key in earlier) add(BriefIssue("stages[$i].name", "nama tahap harus unik"))
            val target = s.reworkTo
            if (target != null && BriefKeys.normalize(target) !in earlier) {
                add(BriefIssue("stages[$i].reworkTo", "harus nama tahap sebelumnya, bukan tahap ini atau sesudahnya"))
            }
            itemEvidence(b, "stages[$i]", s.evidence, evidence)
            earlier += key
        }
    }

    /** Kontrak v2 wajib berevidence; v1 (legacy) dibaca tanpa bukti. Referensi yang ada selalu divalidasi bentuknya. */
    private fun MutableList<BriefIssue>.itemEvidence(
        b: ModuleBrief, path: String, ref: EvidenceRef?, evidence: EvidenceContext
    ) {
        if (ref == null) {
            if (b.contractVersion >= L.CONTRACT_VERSION_EVIDENCE) add(BriefIssue("$path.evidence", "wajib untuk kontrak v2"))
            return
        }
        EvidenceRules.validate(ref, evidence, "$path.evidence").forEach { add(BriefIssue(it.path, it.message)) }
    }

    private fun MutableList<BriefIssue>.followUps(b: ModuleBrief) {
        if (b.followUps.size > L.FOLLOW_UPS_MAX) add(BriefIssue("followUps", "maksimal ${L.FOLLOW_UPS_MAX} pertanyaan"))
        b.followUps.forEachIndexed { i, q ->
            if (q.isBlank()) add(BriefIssue("followUps[$i]", "pertanyaan kosong"))
            else {
                if (q.length > L.FOLLOW_UP_MAX_CHARS) add(BriefIssue("followUps[$i]", "maksimal ${L.FOLLOW_UP_MAX_CHARS} karakter"))
                if (!q.trim().endsWith("?")) add(BriefIssue("followUps[$i]", "harus berakhiran '?'"))
            }
        }
    }
}
