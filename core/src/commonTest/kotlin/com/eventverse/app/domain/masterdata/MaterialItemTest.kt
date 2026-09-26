package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaterialItemTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val tenantId = TenantId("ten-demo-001")

    @Test
    fun packagingUnit_asBaseUom_shouldThrow() {
        assertFailsWith<IllegalArgumentException> {
            MaterialItem(
                id = MaterialId("mat-1"),
                tenantId = tenantId,
                code = MaterialCode("YRN-001"),
                name = "Benang 2/30",
                category = MaterialCategory.YARN,
                baseUom = UnitOfMeasure.CONE, // Invalid: cannot be packaging unit
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun convert_withItemSpecificPackagingConversion_shouldConvertAccurately() {
        // Yarn with base unit kg, and alternate packaging: 1 cone = 1.2 kg
        val yarn = MaterialItem(
            id = MaterialId("mat-yarn-1"),
            tenantId = tenantId,
            code = MaterialCode("YRN-001"),
            name = "Benang Katun Combed 30s",
            category = MaterialCategory.YARN,
            baseUom = UnitOfMeasure.KILOGRAM,
            alternateUoms = listOf(
                UomConversion(from = UnitOfMeasure.CONE, equivalent = Quantity.kilograms(1.2))
            ),
            createdAt = now,
            updatedAt = now
        )

        // 3 cones -> kg
        val threeCones = Quantity(3_000_000L, UnitOfMeasure.CONE)
        val inKg = yarn.convert(threeCones, UnitOfMeasure.KILOGRAM)
        assertEquals(3_600_000L, inKg.micros) // 3.6 kg
        assertEquals(UnitOfMeasure.KILOGRAM, inKg.uom)

        // 3.6 kg -> cones
        val inCones = yarn.convert(inKg, UnitOfMeasure.CONE)
        assertEquals(3_000_000L, inCones.micros) // 3.0 cones
        assertEquals(UnitOfMeasure.CONE, inCones.uom)
    }

    @Test
    fun reclassify_whenPriceHistoryExistsAndDimensionDiffers_shouldFail() {
        val yarn = MaterialItem(
            id = MaterialId("mat-1"),
            tenantId = tenantId,
            code = MaterialCode("YRN-001"),
            name = "Benang",
            category = MaterialCategory.YARN,
            baseUom = UnitOfMeasure.KILOGRAM,
            createdAt = now,
            updatedAt = now
        )

        // Reclassify from YARN (MASS) to TRIM (COUNT) when hasPriceHistory = true
        val result = yarn.reclassify(MaterialCategory.TRIM, now, hasPriceHistory = true)
        assertTrue(result.isFailure)
    }
}
