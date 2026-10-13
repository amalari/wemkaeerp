package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.pipeline.usecases.GetTenantPipelineUseCase
import com.eventverse.app.domain.tenant.TenantId

/**
 * Aktivasi manual (PLAN A2): pipeline hasil preset kini **mulai tanpa modul aktif** — preset hanyalah saran. Tes yang
 * memeriksa perilaku pipeline yang sudah berjalan memakai helper ini untuk meniru tenant yang sudah mengaktifkan modul
 * sesuai saran preset (bypass per node mengikuti snapshot preset, mis. CMT mem-bypass pengadaan bahan baku).
 */
fun CustomTenantPipeline.activatedPerPreset(preset: Blueprint): CustomTenantPipeline {
    val suggested = PipelinePresetFactory.createSnapshot(preset).nodes.associate { it.id to it.isBypassed }
    return copy(nodes = nodes.map { it.copy(isBypassed = suggested[it.nodeId] ?: false) })
}

/** Memprovisikan pipeline tenant dari [preset] lalu menyimpannya dalam keadaan "sudah diaktifkan sesuai saran preset". */
suspend fun provisionActivated(
    getPipeline: GetTenantPipelineUseCase,
    repository: TenantPipelineRepository,
    tenantId: TenantId,
    preset: Blueprint
): CustomTenantPipeline =
    repository.save(getPipeline(tenantId, preset).getOrThrow().activatedPerPreset(preset)).getOrThrow()
