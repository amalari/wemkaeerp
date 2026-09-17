package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Menempelkan foto mockup desain ke satu order sampling.
 *
 * [storageKey] adalah key object storage (bukan presigned URL). Kenapa key dan bukan URL:
 * presigned URL kedaluwarsa dalam hitungan menit, jadi menyimpannya di DB berarti foto
 * hilang dari UI keesokan harinya. URL segar dibuat di route saat data dibaca.
 */
data class AttachSamplingMockupCommand(
    val tenantId: TenantId,
    val samplingOrderId: SamplingOrderId,
    val storageKey: String,
    val slot: String = "front"
)

class AttachSamplingMockupUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: AttachSamplingMockupCommand): Result<SamplingOrder> = runCatching {
        require(command.storageKey.isNotBlank()) { "Key mockup tidak boleh kosong" }

        val existing = repository.findById(command.samplingOrderId)
            ?: error("SPK Sample tidak ditemukan")
        require(existing.tenantId == command.tenantId) { "Sampling order bukan milik tenant ini" }

        repository.save(existing.attachMockup(command.storageKey, command.slot, Clock.System.now()))
    }
}
