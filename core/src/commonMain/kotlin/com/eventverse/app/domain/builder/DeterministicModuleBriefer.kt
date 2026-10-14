package com.eventverse.app.domain.builder

import com.eventverse.app.domain.builder.ModuleBriefLimits as L

/**
 * Penyusun brief tanpa model (FR-6): ringkasan dirakit dari nama modul + peran/fitur tautan, `stages` **kosong**
 * (tidak mengarang alur), `followUps` kosong (celah dihitung use case lewat [ModuleGapAnalyzer]). Murni dan aman
 * bila [ModuleBriefRequest.roleFeatures] kosong.
 */
object DeterministicModuleBriefer : ModuleBriefer {
    override suspend fun brief(request: ModuleBriefRequest): Result<ModuleBrief> = Result.success(of(request))

    fun of(request: ModuleBriefRequest): ModuleBrief {
        val features = request.roleFeatures.map { it.trim().take(L.FEATURE_MAX_CHARS) }.filter { it.isNotEmpty() }
            .distinctBy(BriefKeys::normalize).take(L.FEATURES_MAX)
        val summary = if (features.isEmpty()) "Modul ${request.moduleName}: belum ada rincian fitur dari ceritamu."
        else "Modul ${request.moduleName} mencakup: ${features.joinToString("; ")}."
        return ModuleBrief(summary = summary.take(L.SUMMARY_MAX_CHARS), features = features, source = BriefSource.DETERMINISTIC)
    }
}
