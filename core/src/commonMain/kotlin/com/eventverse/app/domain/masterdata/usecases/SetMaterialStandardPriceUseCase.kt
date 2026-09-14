package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPrice
import com.eventverse.app.domain.masterdata.MaterialPriceId
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.masterdata.PriceSource
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class SetMaterialStandardPriceCommand(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val unitPrice: UnitPrice,
    val effectiveFrom: Instant,
    val note: String = "",
    val recordedByUserId: String = "",
    val idGenerator: () -> String = { "prc-${Clock.System.now().toEpochMilliseconds()}" }
)

class SetMaterialStandardPriceUseCase(
    private val materialRepository: MaterialItemRepository,
    private val priceRepository: MaterialPriceRepository
) {
    suspend operator fun invoke(command: SetMaterialStandardPriceCommand): Result<MaterialPrice> = runCatching {
        val item = materialRepository.findById(command.tenantId, command.materialId)
            ?: error("Material '${command.materialId.value}' tidak ditemukan")

        // Currency check (guardrail: phase 1 supports IDR)
        require(command.unitPrice.amount.currency == CurrencyCode.IDR) {
            "Mata uang acuan saat ini harus IDR (${command.unitPrice.amount.currency.code} tidak didukung pada fase ini)"
        }

        // Unit compatibility check
        val uom = command.unitPrice.per.uom
        val isConvertible = uom.canConvertTo(item.baseUom) || item.alternateUoms.any { it.from == uom }
        require(isConvertible) {
            "Satuan harga (${uom.displayName}) tidak kompatibel dengan satuan dasar material (${item.baseUom.displayName}) maupun konversi alternatifnya"
        }

        val price = MaterialPrice(
            id = MaterialPriceId(command.idGenerator()),
            tenantId = command.tenantId,
            materialId = command.materialId,
            unitPrice = command.unitPrice,
            source = PriceSource.STANDARD,
            effectiveFrom = command.effectiveFrom,
            note = command.note.trim(),
            recordedByUserId = command.recordedByUserId,
            recordedAt = Clock.System.now()
        )

        priceRepository.append(price)
    }
}
