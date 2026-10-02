package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Bootstrap **draf kerja tenant** dari keadaan tenant yang sudah berjalan (PLAN-builder-console:
 * "migrasi impor" M0). Dipanggil `GET /api/builder/draft` saat tenant belum punya draf, supaya pane
 * Modules/Data Flow/Prototype menampilkan modul tenant yang nyata — bukan kanvas kosong.
 *
 * Sumber data, mengikuti tangga keputusan tenant-variability:
 * - **Pack** dari [DomainPackRegistry] sesuai `domain_pack` tenant (shipped garment dulu; pack data
 *   mengikuti begitu terdaftar).
 * - **Blueprint** starter pertama milik pack itu — hanya kerangkanya; aktif/non-aktif ditentukan data.
 * - **Modul aktif** dari pipeline tenant ([TenantPipelineRepository]): node non-bypass, non-plugin.
 *   Invarian [DiscoveryDraft] menuntut blueprint hanya menyebut modul milik pack-nya sendiri, jadi
 *   irisan dengan blueprint yang dipakai; modul plugin/luar blueprint tidak dipaksa masuk.
 *   Tanpa pipeline (tenant belum tersusun) → aktif default blueprint.
 *
 * Idempoten: tenant yang sudah punya draf **tidak pernah** disentuh. Id mengikuti konvensi
 * [ApplyDraftPatchUseCase] (`draft-<tenantId>`) supaya bootstrap dan "Terapkan" mengerjakan baris
 * yang sama. Fail-soft: kondisi yang tidak bisa dibangun (pack tak dikenal, blueprint tak ada,
 * validator menolak, penyimpanan gagal) mengembalikan `null` — route menjawab tanpa draf seperti
 * sebelum fitur ini ada; bootstrap tidak pernah mengubah perilaku gagal jadi error baru.
 */
class EnsureTenantWorkingDraftUseCase(
    private val drafts: DiscoveryDraftRepository,
    private val pipelines: TenantPipelineRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        domainPack: DomainPackCode,
        ownerUserId: UserId
    ): StoredDiscoveryDraft? {
        drafts.findByTenant(tenantId)?.let { return it }

        val pack = DomainPackRegistry.find(domainPack) ?: return null
        val blueprint = GarmentBlueprints.all.firstOrNull { it.pack == pack.code } ?: return null

        val pipelineCodes = pipelines.findByTenantId(tenantId)
            ?.activeNodes
            ?.filterNot { it.isCustomPlugin }
            ?.map { it.moduleId }
            ?.toSet()
            .orEmpty()
        val effectiveActive = when {
            pipelineCodes.isEmpty() -> blueprint.activeModuleCodes
            else -> blueprint.activeModuleCodes.intersect(pipelineCodes)
                .ifEmpty { blueprint.activeModuleCodes }
        }

        val draft = DiscoveryDraft(
            pack = pack,
            blueprint = blueprint.copy(
                modules = blueprint.modules.map { it.copy(active = it.moduleCode in effectiveActive) }
            ),
            screens = emptyList()
        )
        if (DiscoveryDraftValidator.validate(draft).isNotEmpty()) return null

        return runCatching {
            drafts.save(
                StoredDiscoveryDraft(
                    id = DiscoveryDraftId("draft-${tenantId.value}"),
                    ownerUserId = ownerUserId,
                    draft = draft,
                    tenantId = tenantId,
                    createdAt = clock.now(),
                    updatedAt = clock.now()
                )
            )
        }.getOrNull()
    }
}