package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition

/**
 * Jembatan sementara enum [SamplingPipelineStage] ↔ [StageCode] selama migrasi TRD-FLOW-001
 * Tahap 2: pembaca dipindah satu paket per PR, dan paket yang sudah pindah tetap perlu bicara
 * dengan paket yang belum.
 *
 * Arahnya satu: `sampling` bergantung pada `stageflow`, tidak sebaliknya. Dihapus bersama enum
 * di Tahap 3.
 */
fun SamplingPipelineStage.toStageCode(): StageCode = StageCode(name)

/**
 * `null` untuk tahap yang tidak punya padanan enum — tahap sisipan tenant atau tahap template
 * industri lain. Alias lama (`FINISHING_QC`) ikut diterjemahkan lewat parser tunggal enum.
 */
fun StageCode.toSamplingStageOrNull(): SamplingPipelineStage? = SamplingPipelineStage.parseOrNull(value)

/**
 * Membaca kode tahap tersimpan dengan aturan parser enum lama: alias (`FINISHING_QC`)
 * diterjemahkan, nama tak dikenal → `null`. Dipakai codec/route yang pindah ke [StageCode]
 * tanpa boleh mengubah apa yang diterima. Dilonggarkan di Tahap 3.
 */
fun parseLegacyStageCodeOrNull(raw: String?): StageCode? = SamplingPipelineStage.parseOrNull(raw)?.toStageCode()

/**
 * Jembatan baca untuk record yang menyimpan [StageCode] tetapi masih dibaca sebagai enum.
 * Gagal keras untuk kode non-rajut — fallback diam-diam akan menaruh kartu di tahap yang salah.
 * Template kedua tidak boleh aktif sebelum pembaca pindah (constraint TRD-FLOW-001).
 */
fun StageCode.requireSamplingStage(): SamplingPipelineStage =
    checkNotNull(toSamplingStageOrNull()) { "Tahap ${value} belum didukung jalur enum" }

/** Definisi template rajut untuk tahap enum ini — untuk pemanggil lama yang belum memegang kerangka SPK. */
fun SamplingPipelineStage.knitDefinition(): StageDefinition =
    IndustryStageTemplates.stagesOf(IndustryTemplateCode.KNIT_SWEATER).first { it.code.value == name }

/**
 * Membaca tahap tersimpan SPK terhadap **kerangka SPK itu sendiri**: alias lama diterjemahkan
 * dulu, lalu kode apa adanya — dan keduanya hanya diterima bila ada di [frame]. `null` berarti
 * nilai tersimpan tidak dikenali kerangkanya; pemanggil memakai fallback lamanya, sehingga baris
 * rusak diperlakukan persis seperti sebelum TRD-FLOW-001 (bukan diterima lalu meledak saat dibaca).
 */
fun resolveStoredStageCode(raw: String?, frame: List<StageDefinition>): StageCode? {
    fun inFrame(code: StageCode?) = code?.takeIf { c -> frame.any { it.code == c } }
    return inFrame(parseLegacyStageCodeOrNull(raw)) ?: inFrame(StageCode.parseOrNull(raw))
}
