package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Menandai sampel milik satu deal sebagai ACC buyer (atau memintanya revisi).
 *
 * Mengembalikan seluruh daftar sampling deal sehingga pemanggil (route layer) bisa
 * langsung menghitung gerbang Tab Produksi Massal: "semua desain aktif sudah ACC?"
 * tanpa round-trip query kedua.
 */
data class ApproveSamplingFromDealCommand(
    val tenantId: TenantId,
    val dealId: String,
    val samplingOrderId: String,
    val isApproved: Boolean,
    val notes: String = ""
)

class ApproveSamplingFromDealUseCase(
    private val samplingRepository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: ApproveSamplingFromDealCommand): Result<List<com.eventverse.app.domain.sampling.SamplingOrder>> =
        runCatching {
            require(command.dealId.isNotBlank()) { "DealId tidak boleh kosong" }

            val existing = samplingRepository.findById(SamplingOrderId(command.samplingOrderId))
                ?: error("SPK Sample tidak ditemukan")
            require(existing.tenantId == command.tenantId) { "Sampling order bukan milik tenant ini" }
            require(existing.dealId == command.dealId) {
                "Sampling order tidak menempel pada deal ini"
            }

            // Defense in depth: gerbang ACC juga dikunci di server, bukan hanya di UI.
            // Desain hanya boleh di-ACC buyer jika sudah memenuhi syarat wajib
            // (mockup depan, minimal 1 ukuran size chart lengkap, jumlah sampel >= 1 pcs).
            if (command.isApproved) {
                val issues = existing.missingApprovalRequirements()
                require(issues.isEmpty()) {
                    "Desain belum memenuhi syarat ACC: ${issues.joinToString(" ")}"
                }
            }

            ApproveSamplingOrderUseCase(samplingRepository)(
                ApproveSamplingOrderCommand(
                    orderId = existing.id,
                    isApproved = command.isApproved,
                    notes = command.notes
                )
            ).getOrThrow()

            samplingRepository.findByDealId(command.tenantId, command.dealId)
        }
}
