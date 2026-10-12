package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.CartonSealed
import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.fulfillment.carton.SealNumber
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * U3: Menyegel karton packing.
 *
 * Menutup karton dengan seal number dan mencatat berat kotor.
 * Karton yang sudah disegel tidak bisa ditambahkan item lagi.
 */
class SealCartonUseCase(
    private val cartonRepository: CartonRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        cartonId: CartonId,
        sealNumber: SealNumber,
        grossWeightKg: Double,
        actor: String
    ): Result<SealCartonResult> = runCatching {
        require(grossWeightKg > 0.0) { "Berat kotor wajib lebih dari 0" }
        require(actor.isNotBlank()) { "Nama actor wajib diisi" }

        val existing = cartonRepository.findById(tenantId, cartonId)
            ?: error("Karton tidak ditemukan: ${cartonId.value}")

        val sealed = existing.seal(sealNumber, grossWeightKg, actor)
        val saved = cartonRepository.save(sealed)

        // Record domain event
        val now = Clock.System.now()
        cartonRepository.recordEvent(
            tenantId = tenantId,
            cartonId = saved.id,
            eventType = "SEALED",
            actor = actor,
            detail = "sealNumber=${sealNumber.value},grossWeightKg=$grossWeightKg,itemCount=${saved.quantityPcs}",
            occurredAt = now
        )

        SealCartonResult(
            carton = saved,
            event = CartonSealed(
                cartonId = saved.id,
                cartonNumber = saved.cartonNumber,
                sealNumber = sealNumber.value,
                grossWeightKg = grossWeightKg,
                itemCount = saved.quantityPcs,
                sealedBy = actor,
                occurredAt = now
            )
        )
    }
}

data class SealCartonResult(
    val carton: PackingCarton,
    val event: CartonSealed
)
