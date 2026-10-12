package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.CartonCreated
import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonNumber
import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * U2: Membuat karton packing baru.
 *
 * Membuat karton kosong dengan status OPEN untuk menampung item dari packing queue.
 * Setiap karton mendapat nomor urut unik dengan format CTN-TAHUN-urutan.
 */
class CreatePackingCartonUseCase(
    private val cartonRepository: CartonRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        productionRef: String,
        createdBy: String
    ): Result<CreatePackingCartonResult> = runCatching {
        require(productionRef.isNotBlank()) { "Referensi produksi wajib diisi" }
        require(createdBy.isNotBlank()) { "Nama creator wajib diisi" }

        val now = Clock.System.now()
        val year = now.toLocalDateTime(TimeZone.currentSystemDefault()).year
        val sequence = cartonRepository.getNextCartonSequence(tenantId, year)
        val cartonNumber = CartonNumber.generate(year, sequence)

        val carton = PackingCarton(
            id = CartonId("ctn-${now.toEpochMilliseconds()}"),
            tenantId = tenantId,
            productionRef = productionRef,
            cartonNumber = cartonNumber,
            status = com.eventverse.app.domain.fulfillment.carton.CartonStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )

        val saved = cartonRepository.save(carton)

        // Record domain event
        cartonRepository.recordEvent(
            tenantId = tenantId,
            cartonId = saved.id,
            eventType = "CREATED",
            actor = createdBy,
            detail = "cartonNumber=${saved.cartonNumber.value},productionRef=$productionRef",
            occurredAt = now
        )

        CreatePackingCartonResult(
            carton = saved,
            event = CartonCreated(
                cartonId = saved.id,
                cartonNumber = saved.cartonNumber,
                tenantId = tenantId,
                productionRef = productionRef,
                createdBy = createdBy,
                occurredAt = now
            )
        )
    }
}

data class CreatePackingCartonResult(
    val carton: PackingCarton,
    val event: CartonCreated
)
