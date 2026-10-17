package com.eventverse.app.domain.builder

import com.eventverse.app.domain.builder.ModuleBriefLimits as L

/**
 * Penyusun brief tanpa model (FR-6): ringkasan dirakit dari nama modul + peran/fitur tautan, `stages` **kosong**
 * (tidak mengarang alur), `followUps` kosong (celah dihitung use case lewat [ModuleGapAnalyzer]). Murni dan aman
 * bila [ModuleBriefRequest.roleFeatures] kosong.
 *
 * Provenance (TRD-BLDR-003 FR-3.4/FR-3.5): bukti [RoleFeature.evidence] diwarisi apa adanya — tidak pernah dikarang.
 * Butir tanpa dasar **dihilangkan** (klaim lebih sedikit tapi jujur) — brief v2 `DETERMINISTIC` wajib berevidence.
 */
object DeterministicModuleBriefer : ModuleBriefer {
    override suspend fun brief(request: ModuleBriefRequest): Result<ModuleBrief> = Result.success(of(request))

    fun of(request: ModuleBriefRequest): ModuleBrief {
        // FR-3.5: butir tanpa dasar dihilangkan — ringkasan pun hanya merangkum butir berevidence (FR-3.6).
        val features = request.roleFeatures.filter { it.evidence != null }
            .map { BriefFeature(it.label.trim().take(L.FEATURE_MAX_CHARS), it.evidence) }
            .filter { it.text.isNotEmpty() }
            .distinctBy { BriefKeys.normalize(it.text) }.take(L.FEATURES_MAX)
        val summary = if (features.isEmpty()) "Modul ${request.moduleName}: belum ada rincian fitur dari ceritamu."
        else "Modul ${request.moduleName} mencakup: ${features.joinToString("; ") { it.text }}."
        return ModuleBrief(
            summary = summary.take(L.SUMMARY_MAX_CHARS),
            features = features,
            source = BriefSource.DETERMINISTIC,
            contractVersion = L.CONTRACT_VERSION_EVIDENCE,
        )
    }
}
