package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.blueprint.Blueprint

/**
 * Aktivasi manual (PLAN A2): [CustomTenantPipeline.fromPreset] menghasilkan alur yang **mulai tanpa modul aktif** — preset
 * hanyalah saran. Fungsi ini menerapkan saran itu: bypass tiap node mengikuti snapshot [preset] (mis. CMT mem-bypass pengadaan
 * bahan baku). Dipakai untuk pabrik yang memang sudah berjalan (tenant demo/seed, "terapkan saran preset"), bukan untuk tenant baru.
 */
fun CustomTenantPipeline.withPresetActivation(preset: Blueprint): CustomTenantPipeline {
    val suggested = PipelinePresetFactory.createSnapshot(preset).nodes.associate { it.id to it.isBypassed }
    return copy(nodes = nodes.map { it.copy(isBypassed = suggested[it.nodeId] ?: false) })
}
