package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TechPackGrossRequirementTest {

    private val tenantId = TenantId("tenant-test")
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `grossRequirementFor falls back to line grossFor when no size factors`() {
        val line = BomLine(
            lineId = "line-1",
            material = MaterialRef.unresolved("Benang"),
            category = MaterialCategory.YARN,
            netQuantityPerGarment = Quantity(200_000L, UnitOfMeasure.GRAM), // 0.2 kg
            wasteAllowance = Ratio.percent(5.0),
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        )

        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Kaos All Size",
            bomLines = listOf(line),
            createdAt = now,
            updatedAt = now
        )

        // net: 0.2 kg + 5% waste = 0.21 kg per pcs. For 1000 pcs = 210 kg (210,000,000 micros)
        val gross = techPack.grossRequirementFor(line)
        assertEquals(210_000L, line.grossQuantityPerGarment.micros)
    }

    @Test
    fun `grossRequirementFor scales correctly per size with yield factor`() {
        val line = BomLine(
            lineId = "line-1",
            material = MaterialRef.unresolved("Benang"),
            category = MaterialCategory.YARN,
            netQuantityPerGarment = Quantity(200_000L, UnitOfMeasure.GRAM), // 200g
            wasteAllowance = Ratio.ZERO,
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        )

        // Size S: scale 1.0 (qty 10) -> 200g * 10 = 2000g
        // Size L: scale 1.1 (qty 10) -> 220g * 10 = 2200g
        // Total = 4200g
        val sizeFactors = listOf(
            SizeYieldFactor("S", Ratio.ONE, orderedQuantity = 10L),
            SizeYieldFactor("L", Ratio(11L, 10L), orderedQuantity = 10L)
        )

        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Polo Bertingkat",
            bomLines = listOf(line),
            sizeYieldFactors = sizeFactors,
            createdAt = now,
            updatedAt = now
        )

        val gross = techPack.grossRequirementFor(line)
        assertEquals(4_200_000L, gross.micros) // 4200g in micros
    }
}
