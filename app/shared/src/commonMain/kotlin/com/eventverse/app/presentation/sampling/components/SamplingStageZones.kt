package com.eventverse.app.presentation.sampling.components

import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait

/** Peran kolom papan — sumbu yang sama untuk template industri apa pun. */
enum class SamplingZoneKind { INTAKE, FLOW_REVIEW, PREP, RND, STORAGE, DONE }

/**
 * Zona drop di papan Kanban, diturunkan dari kerangka tahap pabrik (TRD-FLOW-001), bukan enum.
 *
 * Kolom tahap tunggal memetakan 1:1 ke tahapnya; kolom grup memetakan ke tahap PERTAMA di
 * dalamnya — itulah target sah drag lintas divisi (mis. rajut: Program CAM → R&D).
 */
data class SamplingStageZone(
    val kind: SamplingZoneKind,
    val title: String,
    val subtitle: String,
    val stages: List<StageCode>,
    val dropStage: StageCode,
    val showActions: Boolean,
    /** Warna identitas tahap tujuan drop — data tenant (`StageDefinition.colorHex`). */
    val tintHex: Long
)

/**
 * Menyusun kolom papan dari [frame]:
 * 1. SPK Masuk · 2. Penentuan Alur — jangkar masuk
 * 3. Persiapan — tahap kerja tanpa meja operator (rajut: Program CAM); judulnya nama tahap bila tunggal
 * 4. R&D — tahap bermeja operator (rajut: Rajut hingga Kemas)
 * 5. Penyimpanan · 6. Selesai — jangkar keluar
 *
 * Kolom tanpa tahap tidak ditampilkan, dan nomornya ikut merapat.
 */
fun samplingStageZones(frame: List<StageDefinition>): List<SamplingStageZone> {
    fun find(code: StageCode) = frame.firstOrNull { it.code == code }
    val intake = find(INTAKE)
    val review = find(FLOW_REVIEW)
    val prep = frame.filter { it.kind == StageKind.WORK && !it.has(StageTrait.OPERATOR_DESK) }
    val desks = frame.filter { it.has(StageTrait.OPERATOR_DESK) }
    val storage = find(ExitStages.STORAGE)
    val done = listOfNotNull(find(ExitStages.DELIVERY), find(ExitStages.APPROVED))

    val drafts = buildList {
        intake?.let { add(Draft(SamplingZoneKind.INTAKE, "SPK Masuk", "Order baru dari deal/klien", listOf(it), true)) }
        review?.let { add(Draft(SamplingZoneKind.FLOW_REVIEW, "Penentuan Alur", "Setup bordir/sablon per desain", listOf(it), true)) }
        if (prep.isNotEmpty()) {
            val title = prep.singleOrNull()?.displayName ?: "Persiapan"
            add(Draft(SamplingZoneKind.PREP, title, "Persiapan sebelum lantai produksi", prep, true))
        }
        // Tahap bermeja operator digabung jadi satu kolom R&D: kolom ini hanya memantau — posisi
        // persis dibaca dari jejak progres di kartu + chip saring di kepala kolom.
        if (desks.isNotEmpty()) add(Draft(SamplingZoneKind.RND, "R&D", joinLabels(desks), desks, false))
        // Barang selesai kemas ditaruh dulu (rak packing / gudang) sampai SPK sedeal lengkap.
        storage?.let { add(Draft(SamplingZoneKind.STORAGE, "Penyimpanan", "Disimpan, tunggu deal lengkap", listOf(it), true)) }
        if (done.isNotEmpty()) add(Draft(SamplingZoneKind.DONE, "Selesai", "Terkirim, tunggu ACC buyer", done, true))
    }
    return drafts.mapIndexed { index, draft ->
        SamplingStageZone(
            kind = draft.kind,
            title = "${index + 1}. ${draft.title}",
            subtitle = draft.subtitle,
            stages = draft.stages.map { it.code },
            dropStage = draft.stages.first().code,
            showActions = draft.showActions,
            tintHex = draft.stages.first().colorHex
        )
    }
}

private data class Draft(
    val kind: SamplingZoneKind,
    val title: String,
    val subtitle: String,
    val stages: List<StageDefinition>,
    val showActions: Boolean
)

/** "Rajut, Jahit, Cuci & Kemas" — label ringkas tahap, bukan teks yang ditulis per template. */
private fun joinLabels(stages: List<StageDefinition>): String {
    val labels = stages.map { it.shortLabel }.distinct()
    return if (labels.size <= 1) labels.joinToString() else labels.dropLast(1).joinToString(", ") + " & " + labels.last()
}

private val INTAKE = SamplingPipelineStage.NEW_INTAKE.toStageCode()
private val FLOW_REVIEW = SamplingPipelineStage.FLOW_REVIEW.toStageCode()
