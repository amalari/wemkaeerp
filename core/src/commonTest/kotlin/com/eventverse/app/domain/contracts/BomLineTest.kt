package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlin.test.Test
import kotlin.test.assertEquals

class BomLineTest {

    @Test
    fun bomLine_grossCalculationWithWasteAllowance_shouldBeExact() {
        val bomLine = BomLine(
            lineId = "line-1",
            material = MaterialRef.unresolved("Benang Katun Combed 30s"),
            category = MaterialCategory.YARN,
            netQuantityPerGarment = Quantity.grams(280.0),
            wasteAllowance = Ratio.percent(5.0), // 5% allowance
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        )

        // Gross per garment = 280g + 14g = 294g
        val grossPerGarment = bomLine.grossQuantityPerGarment
        assertEquals(294_000_000L, grossPerGarment.micros)
        assertEquals(UnitOfMeasure.GRAM, grossPerGarment.uom)

        // For 1,000 garments = 294,000g = 294 kg
        val grossForOrder = bomLine.grossFor(1000)
        assertEquals(294_000_000_000L, grossForOrder.micros)
        val inKg = grossForOrder.convertTo(UnitOfMeasure.KILOGRAM)
        assertEquals(294_000_000L, inKg.micros) // 294 kg
    }
}
