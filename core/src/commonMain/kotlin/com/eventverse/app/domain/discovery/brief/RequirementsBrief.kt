package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.prototype.SpecOp

/** Satu entri log perubahan dari sesi prototype (kontrak v1). [at] = waktu ISO-8601 dari pemanggil. */
data class CaptureEntry(val at: String, val op: SpecOp, val ok: Boolean, val message: String?)

data class BriefField(val label: String, val type: String, val required: Boolean, val options: List<String>)

data class BriefEntity(
    val id: String,
    val label: String,
    val fields: List<BriefField>,
    val statusField: String?,
    val transitions: Map<String, List<String>>
)

data class BriefScreen(val title: String, val widget: String, val entityId: String?)

data class BriefModule(val moduleId: String, val displayName: String, val screens: List<BriefScreen>, val entities: List<BriefEntity>)

/** Per modul: sudah ada di katalog ([covered], [monthlyIdr]) atau perlu dibangun (rentang bulanan). */
data class BriefCoverage(
    val moduleId: String,
    val displayName: String,
    val covered: Boolean,
    val monthlyIdr: Long?,
    val gapLowIdr: Long?,
    val gapHighIdr: Long?
)

/**
 * Ringkasan kebutuhan hasil sesi prototype — bahan kerja tim developer (kontrak v1). Data murni;
 * susunan teks ada di [BriefRenderer]. [customNeeds] = kebutuhan di luar blok standar (CUSTOM_EXTENSION).
 */
data class RequirementsBrief(
    val packCode: String,
    val modules: List<BriefModule>,
    val changes: List<CaptureEntry>,
    val coverage: List<BriefCoverage>,
    val customNeeds: List<String> = emptyList()
)
