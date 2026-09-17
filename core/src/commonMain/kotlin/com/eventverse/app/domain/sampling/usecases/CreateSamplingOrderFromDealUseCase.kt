package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Menyiapkan (atau memperbarui) lembar sampling yang menempel pada satu deal CRM.
 *
 * Dua jalur di dalam satu use case, sesuai pola Command:
 * - [SamplingFromDealCommand.samplingOrderId == null] -> buat order sampling baru dari deal.
 * - [SamplingFromDealCommand.samplingOrderId != null] -> perbarui quantity, kurir, dan biaya
 *   milik order sampling yang sudah ada (form inputan cepat di Tab Sampling).
 */
data class SamplingFromDealCommand(
    val tenantId: TenantId,
    val dealId: String,
    val clientName: String,
    val styleName: String,
    val samplingOrderId: String? = null,
    val sampleQuantity: Int = 2,
    val courierTracking: String? = null,
    val samplingFeeIdr: Long = 0L,
    val notes: String = "",
    val sizeMatrix: List<SizeChartRow>? = null
)

class CreateSamplingOrderFromDealUseCase(
    private val samplingRepository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: SamplingFromDealCommand): Result<SamplingOrder> = runCatching {
        require(command.dealId.isNotBlank()) { "DealId tidak boleh kosong" }

        val existingId = command.samplingOrderId?.takeIf { it.isNotBlank() }
        if (existingId != null) {
            // Jalur update: styleName boleh kosong (berarti tidak di-rename) karena kartu desain
            // dibuat dengan kode autogenerate (DSG-01, …) yang bisa di-rename belakangan.
            val existing = samplingRepository.findById(SamplingOrderId(existingId))
                ?: error("Sampling order tidak ditemukan")
            require(existing.tenantId == command.tenantId) { "Sampling order bukan milik tenant ini" }
            require(existing.dealId == null || existing.dealId == command.dealId) {
                "Sampling order sudah menempel pada deal lain"
            }
            return@runCatching samplingRepository.save(
                existing
                    .linkToDeal(command.dealId, Clock.System.now())
                    .copy(
                        styleName = command.styleName.trim().ifBlank { existing.styleName },
                        sampleQuantity = command.sampleQuantity.coerceIn(1, 3),
                        samplingFeeIdr = command.samplingFeeIdr.coerceAtLeast(0L),
                        // Dikirim langsung tanpa fallback: UI selalu mengirim isi textarea terkini,
                        // jadi admin juga bisa mengosongkan catatan (fallback lama mencegah clear).
                        notes = command.notes,
                        sizeMatrix = command.sizeMatrix ?: existing.sizeMatrix,
                        updatedAt = Clock.System.now()
                    )
                    .updateCourierTracking(command.courierTracking ?: existing.courierTracking ?: "", Clock.System.now())
            )
        }

        require(command.styleName.isNotBlank()) { "Nama desain / style tidak boleh kosong" }

        val create = CreateSamplingOrderCommand(
            tenantId = command.tenantId,
            clientName = command.clientName,
            styleName = command.styleName,
            dealId = command.dealId,
            sampleQuantity = command.sampleQuantity,
            samplingFeeIdr = command.samplingFeeIdr,
            notes = command.notes,
            sizeMatrix = command.sizeMatrix ?: defaultSamplingSizeMatrix()
        )
        CreateSamplingOrderUseCase(samplingRepository)(create).getOrThrow()
    }
}
