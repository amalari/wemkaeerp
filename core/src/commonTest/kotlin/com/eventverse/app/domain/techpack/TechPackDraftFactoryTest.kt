package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.AdditionalProcess
import com.eventverse.app.domain.contracts.ApprovedSampleSpecification
import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.contracts.PanelYield
import com.eventverse.app.domain.contracts.SpecSizeMeasurement
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TechPackDraftFactoryTest {

    private val tenantId = TenantId("tenant-test")
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `toTechPackDraft preserves total panel weight and divides among yarns`() {
        val spec = ApprovedSampleSpecification(
            sourceOrderId = "order-123",
            tenantId = tenantId,
            spkNumber = "SPK-2026-001",
            styleName = "Cardigan Knit",
            clientName = "Brand A",
            approvedAt = now,
            sizeMode = "MULTI_SIZE",
            sizeCharts = listOf(
                SpecSizeMeasurement(sizeLabel = "S"),
                SpecSizeMeasurement(sizeLabel = "M"),
                SpecSizeMeasurement(sizeLabel = "L")
            ),
            panelYields = listOf(
                PanelYield(GarmentPanel.BODY_FRONT, Quantity(150_000L, UnitOfMeasure.GRAM), knittingMinutes = 20L),
                PanelYield(GarmentPanel.BODY_BACK, Quantity(150_000L, UnitOfMeasure.GRAM), knittingMinutes = 20L)
            ),
            yarns = listOf(
                MaterialRef.unresolved("Cotton Combed 30s Hitam"),
                MaterialRef.unresolved("Cotton Combed 30s Putih")
            ),
            isWashed = true,
            additionalProcesses = listOf(
                AdditionalProcess(name = "Kancing Batok", quantityPerGarment = Quantity(5_000L, UnitOfMeasure.PIECE))
            )
        )

        val draft = spec.toTechPackDraft(
            techPackId = TechPackId("tp-draft-1"),
            styleCode = StyleCode("STY-CARD-01"),
            defaultOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
            now = now
        )

        // Total weight was 300g (300,000 micros). Divided by 2 yarns -> 150g each
        val yarnBomLines = draft.bomLines.filter { it.category == MaterialCategory.YARN }
        assertEquals(2, yarnBomLines.size)
        val totalYarnGrams = yarnBomLines.sumOf { it.netQuantityPerGarment.micros }
        assertEquals(300_000L, totalYarnGrams)

        // Trim from additional process
        val trimLines = draft.bomLines.filter { it.category == MaterialCategory.TRIM }
        assertEquals(1, trimLines.size)
        assertEquals("Kancing Batok", trimLines.first().material.displayLabel)

        // Labor operations: 1 knitting (40 mins), 1 washing
        assertEquals(2, draft.laborOperations.size)
        val knitOp = draft.laborOperations.first { it.name == "Rajut Mesin" }
        assertEquals(40L, knitOp.samMinutes.numerator)
        val washOp = draft.laborOperations.first { it.name == "Washing" }
        assertTrue(washOp.isSubcontracted)

        // Sizes: S, M, L
        assertEquals(3, draft.sizeYieldFactors.size)
        assertEquals(listOf("S", "M", "L"), draft.sizeYieldFactors.map { it.sizeLabel })
    }
}
