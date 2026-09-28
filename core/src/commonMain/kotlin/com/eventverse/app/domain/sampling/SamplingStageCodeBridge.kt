package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.stageflow.StageCode

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
