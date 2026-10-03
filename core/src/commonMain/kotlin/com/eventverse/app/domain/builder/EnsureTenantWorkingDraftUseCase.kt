package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.ScreenSuggestion
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
 * **Layar prototype dari data pack**: bootstrap mengisi `screens` dari usulan layar pack
 * (`DomainPack.screenSuggestions`) yang modulnya aktif — draf baru langsung punya mock
 * `/builder/prototype` tanpa menunggu agent LLM. Draf lama yang belum punya layar di-backfill
 * sekali dengan cara yang sama; draf yang sudah punya layar **tidak pernah** disentuh.
 *
 * Id mengikuti konvensi
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
        val pack = DomainPackRegistry.find(domainPack) ?: return null
        val blueprint = GarmentBlueprints.all.firstOrNull { it.pack == pack.code } ?: return null
        val effectiveActive = effectiveActiveCodes(tenantId, blueprint)

        drafts.findByTenant(tenantId)?.let { existing ->
            // Draf yang sudah punya layar milik tenant/agent — tidak pernah ditimpa bootstrap.
            if (existing.draft.screens.isNotEmpty()) return existing
            // Draf lama dari sebelum usulan layar pack ada: backfill sekali, lalu idempoten lagi.
            // Snapshot pack dalam dokumen bisa lebih tua dari usulan terkini, maka sumbernya
            // diambil dari pack registry dengan gerbang modul tetap milik snapshot draf.
            val screens = backfillScreensFor(existing) ?: return existing
            val backfilled = existing.copy(
                draft = existing.draft.copy(screens = screens),
                updatedAt = clock.now()
            )
            return runCatching { drafts.save(backfilled) }.getOrNull() ?: existing
        }

        val draft = DiscoveryDraft(
            pack = pack,
            blueprint = blueprint.copy(
                modules = blueprint.modules.map { it.copy(active = it.moduleCode in effectiveActive) }
            ),
            screens = screenSuggestionsFor(pack, effectiveActive)
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

    /** Modul aktif blueprint, diiriskan node pipeline tenant yang benar-benar jalan (fail-soft tanpa pipeline). */
    private suspend fun effectiveActiveCodes(tenantId: TenantId, blueprint: Blueprint): Set<String> {
        val pipelineCodes = pipelines.findByTenantId(tenantId)
            ?.activeNodes
            ?.filterNot { it.isCustomPlugin }
            ?.map { it.moduleId }
            ?.toSet()
            .orEmpty()
        return when {
            pipelineCodes.isEmpty() -> blueprint.activeModuleCodes
            else -> blueprint.activeModuleCodes.intersect(pipelineCodes)
                .ifEmpty { blueprint.activeModuleCodes }
        }
    }

    /**
     * Usulan layar pack yang modulnya aktif, menjadi deskriptor [PrototypeScreen]. Id layar
     * `default-<moduleId>` menandai asalnya dari data pack — bukan dari agent maupun Studio.
     */
    private fun screenSuggestionsFor(pack: DomainPack, activeModuleCodes: Set<String>): List<PrototypeScreen> =
        pack.screenSuggestions
            .filter { it.moduleId.value in activeModuleCodes }
            .map { s ->
                PrototypeScreen(
                    screenId = "default-${s.moduleId.value}",
                    moduleId = s.moduleId,
                    title = s.title,
                    widget = s.widget.code
                )
            }

    /**
     * Layar backfill untuk draf lama yang belum berlayar. Usulan diambil dari **pack registry
     * terkini** (draf dulu menyimpan snapshot pack sebelum field ini ada), tetapi hanya usulan
     * yang modulnya benar-benar ada di snapshot draf yang lolos — dokumen tidak pernah diubah
     * pack-nya. `null` = tidak ada yang bisa di-backfill, draf dibiarkan apa adanya.
     */
    private suspend fun backfillScreensFor(existing: StoredDiscoveryDraft): List<PrototypeScreen>? {
        val storedPack = existing.draft.pack
        val active = effectiveActiveCodes(tenantId(existing), existing.draft.blueprint)
        val source = DomainPackRegistry.find(storedPack.code) ?: storedPack
        val screens = (source.screenSuggestions + storedPack.screenSuggestions)
            .distinctBy { it.moduleId }
            .filter { storedPack.module(it.moduleId) != null && it.moduleId.value in active }
            .let { screenSuggestionsOf(it) }
        return screens.ifEmpty { null }
    }

    private fun tenantId(existing: StoredDiscoveryDraft): TenantId =
        requireNotNull(existing.tenantId) { "Draf kerja tanpa tenant tidak bisa di-backfill" }

    private fun screenSuggestionsOf(suggestions: List<ScreenSuggestion>): List<PrototypeScreen> =
        suggestions.map { s ->
            PrototypeScreen(
                screenId = "default-${s.moduleId.value}",
                moduleId = s.moduleId,
                title = s.title,
                widget = s.widget.code
            )
        }
}