package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.CartonReopened
import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * U4: Membuka kembali karton yang sudah disegel.
 *
 * Digunakan untuk koreksi jika ada salah input atau item kurang.
 * Karton yang dibuka kembali berstatus REOPENED dan bisa disegel ulang
 * dengan seal number baru.
 */
class ReopenCartonUseCase(
    private val cartonRepository: CartonRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        cartonId: CartonId,
        reason: String,
        actor: String
    ): Result<ReopenCartonResult> = runCatching {
        require(reason.isNotBlank()) { "Alasan reopen wajib diisi untuk audit" }
        require(actor.isNotBlank()) { "Nama actor wajib diisi" }

        val existing = cartonRepository.findById(tenantId, cartonId)
            ?: error("Karton tidak ditemukan: ${cartonId.value}")

        val now = Clock.System.now()
        val reopened = existing.reopen(reason, actor, now)
        val saved = cartonRepository.save(reopened)

        // Record domain event
        cartonRepository.recordEvent(
            tenantId = tenantId,
            cartonId = saved.id,
            eventType = "REOPENED",
            actor = actor,
            detail = "reason=$reason",
            occurredAt = now
        )

        ReopenCartonResult(
            carton = saved,
            event = CartonReopened(
                cartonId = saved.id,
                cartonNumber = saved.cartonNumber,
                reason = reason,
                reopenedBy = actor,
                occurredAt = now
            )
        )
    }
}

data class ReopenCartonResult(
    val carton: PackingCarton,
    val event: CartonReopened
)
