package com.eventverse.app.domain.transfer.usecases

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.transfer.FlowLegBoard
import com.eventverse.app.domain.transfer.FlowLegDerivation
import com.eventverse.app.domain.transfer.FlowLegStatusResolver
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TenantLocationConfigRepository

data class GetFlowTransferLegsQuery(
    val tenantId: String,
    /** SPK yang alurnya sedang dilihat — dipakai mencari dokumen yang sudah terbit untuknya. */
    val subjectId: String,
    val stages: List<SamplingPipelineStage>,
    val processes: List<TenantOptionalProcess>,
    /** Tahap yang dilompati rute sampling desain ini — lihat `SamplingRoute`. */
    val skippedStages: Set<SamplingPipelineStage> = emptySet(),
    /** Nama pembeli untuk leg ekor; `null` bila penyerahan ke buyer tidak relevan. */
    val customerName: String? = null
)

/**
 * Menggabungkan leg yang diturunkan dari alur dengan dokumen Surat Jalan yang sudah terbit,
 * menghasilkan papan status yang dipakai dua tempat: konektor di panel alur, dan gerbang
 * perpindahan tahap.
 *
 * Satu-satunya bagian ber-I/O dari seluruh rantai ini. Perhitungannya sendiri murni dan diuji
 * tanpa repository — lihat `FlowLegDerivation` dan `FlowLegStatusResolver`.
 */
class GetFlowTransferLegsUseCase(
    private val locationConfigRepository: TenantLocationConfigRepository,
    private val suratJalanRepository: SuratJalanRepository
) {
    suspend operator fun invoke(query: GetFlowTransferLegsQuery): Result<FlowLegBoard> = runCatching {
        val config = locationConfigRepository.findByTenantId(query.tenantId)
            ?: return@runCatching FlowLegBoard()

        val legs = FlowLegDerivation.deriveLegs(
            nodes = FlowLegDerivation.resolveNodes(query.stages, query.processes, query.skippedStages),
            processes = query.processes,
            config = config,
            customerName = query.customerName
        )
        if (legs.isEmpty()) return@runCatching FlowLegBoard()

        val manifests = suratJalanRepository.findBySubject(query.tenantId, query.subjectId)
        FlowLegStatusResolver.resolve(legs, manifests)
    }
}
