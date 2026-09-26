package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.contracts.CostBucketKind
import com.eventverse.app.domain.contracts.CostingCalculationResult
import com.eventverse.app.domain.costing.strategies.FullPackageCogsStrategy
import com.eventverse.app.domain.costing.strategies.RetailValuationWithFeesStrategy
import com.eventverse.app.domain.costing.strategies.ServiceFeeOnlyStrategy
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CostingEngineTest {

    private val tenantId = TenantId("ten-costing-test")
    private val now = Clock.System.now()

    @Test
    fun strategyResolver_defaultStrategiesCoverAllBehaviors() {
        val strategies = CostingStrategyResolver.defaultStrategies()

        assertEquals(4, strategies.size)
        assertTrue(strategies[CostingBehavior.FULL_PACKAGE_COGS] is FullPackageCogsStrategy)
        assertTrue(strategies[CostingBehavior.SERVICE_FEE_ONLY] is ServiceFeeOnlyStrategy)
        assertTrue(strategies[CostingBehavior.RETAIL_VALUATION_WITH_FEES] is RetailValuationWithFeesStrategy)
    }

    @Test
    fun samMinutesCalculator_calculatesDirectAndSubcontractSeparately() {
        val operations = listOf(
            LaborOperation("op-1", "Potong Pola", Ratio.of(25, 10), "CUTTING", isSubcontracted = false),   // 2.5 min
            LaborOperation("op-2", "Jahit Kerah", Ratio.of(40, 10), "SEWING", isSubcontracted = false),    // 4.0 min
            LaborOperation("op-3", "Bordir Komputer", Ratio.of(50, 10), "EMBROIDERY", isSubcontracted = true), // 5.0 min subkon
            LaborOperation("op-4", "Pasang Kancing", Ratio.of(15, 10), "FINISHING", isSubcontracted = false) // 1.5 min
        )
        val breakdown = SamMinutesCalculator.calculate(operations)

        // Direct = 2.5 + 4.0 + 1.5 = 8.0 min = 8_000_000 micros
        assertEquals(8_000_000L, breakdown.directMicros)
        // Subcon = 5.0 min = 5_000_000 micros
        assertEquals(5_000_000L, breakdown.subcontractMicros)
        assertEquals(13_000_000L, breakdown.totalMicros)
    }

    @Test
    fun fullPackageCogsStrategy_calculatesDirectCostsLaborOverhead() {
        val techPack = TechPack(
            id = TechPackId("tp-polo-01"),
            tenantId = tenantId,
            styleCode = StyleCode("POLO-001"),
            styleName = "Polo Shirt Classic",
            createdAt = now,
            updatedAt = now
        )

        val bomPreview = BomCostPreview(
            techPackId = TechPackId("tp-polo-01"),
            orderQuantity = 1000L,
            at = now,
            currency = CurrencyCode.IDR,
            lines = listOf(
                BomLineCost(
                    lineId = "line-1",
                    material = MaterialRef.unresolved("Kain Katun Pique"),
                    category = MaterialCategory.FABRIC,
                    grossQuantityPerGarment = Quantity(120, UnitOfMeasure.METER),
                    grossQuantityTotal = Quantity(1200, UnitOfMeasure.METER),
                    resolvedPrice = null,
                    costPerGarment = Money.idr(50_000L),
                    costTotal = Money.idr(50_000_000L),
                    ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
                ),
                BomLineCost(
                    lineId = "line-2",
                    material = MaterialRef.unresolved("Kancing Polos"),
                    category = MaterialCategory.TRIM,
                    grossQuantityPerGarment = Quantity(3, UnitOfMeasure.PIECE),
                    grossQuantityTotal = Quantity(3000, UnitOfMeasure.PIECE),
                    resolvedPrice = null,
                    costPerGarment = Money.idr(1_500L),
                    costTotal = Money.idr(1_500_000L),
                    ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
                )
            )
        )

        val samBreakdown = SamMinutesCalculator.SamMinutesBreakdown(
            directMicros = 10_000_000L, // 10 menit SAM langsung
            subcontractMicros = 2_000_000L // 2 menit SAM subkon
        )

        val params = ResolvedCostingParameters.defaultsFor(CostingBehavior.FULL_PACKAGE_COGS).copy(
            laborRatePerSamMinute = Money.idr(1_000L),       // 10 min * 1000 = 10,000
            subcontractRatePerSamMinute = Money.idr(1_500L), // 2 min * 1500 = 3,000
            overheadPerUnit = Money.idr(5_000L),             // 5,000
            packingCostPerUnit = Money.idr(2_000L),          // 2,000
            marginRatio = Ratio.percent(20.0)
        )

        val input = CostingFormulaInput(
            techPack = techPack,
            bomCostPreview = bomPreview,
            orderQuantity = 1000L,
            samBreakdown = samBreakdown,
            parameters = params,
            pricingAsOf = now,
            currency = CurrencyCode.IDR
        )

        val result = FullPackageCogsStrategy.calculate(input).getOrThrow()

        // Verify buckets:
        // Materials = 50,000 + 1,500 = 51,500
        // Labor = 10,000
        // Subcontract = 3,000
        // Overhead = 5,000
        // Packaging = 2,000
        // Direct COGS = 71,500
        val materialSum = Money.sum(result.buckets.filter { it.kind == CostBucketKind.MATERIAL }.map { it.amountPerUnit }, CurrencyCode.IDR)
        assertEquals(51_500_00L, materialSum.minorUnits)

        val laborBucket = result.buckets.first { it.kind == CostBucketKind.LABOR }
        assertEquals(10_000_00L, laborBucket.amountPerUnit.minorUnits)

        val subconBucket = result.buckets.first { it.kind == CostBucketKind.SUBCONTRACT }
        assertEquals(3_000_00L, subconBucket.amountPerUnit.minorUnits)

        val overheadBucket = result.buckets.first { it.kind == CostBucketKind.OVERHEAD }
        assertEquals(5_000_00L, overheadBucket.amountPerUnit.minorUnits)

        val packagingBucket = result.buckets.first { it.kind == CostBucketKind.PACKAGING }
        assertEquals(2_000_00L, packagingBucket.amountPerUnit.minorUnits)
    }

    @Test
    fun serviceFeeOnlyStrategy_excludesConsignedMaterialFromBillable() {
        val techPack = TechPack(
            id = TechPackId("tp-cmt-01"),
            tenantId = tenantId,
            styleCode = StyleCode("CMT-001"),
            styleName = "Jasa Jahit Kaos",
            createdAt = now,
            updatedAt = now
        )

        val bomPreview = BomCostPreview(
            techPackId = TechPackId("tp-cmt-01"),
            orderQuantity = 500L,
            at = now,
            currency = CurrencyCode.IDR,
            lines = listOf(
                BomLineCost(
                    lineId = "line-consigned",
                    material = MaterialRef.unresolved("Kain Titipan Buyer"),
                    category = MaterialCategory.FABRIC,
                    grossQuantityPerGarment = Quantity(150, UnitOfMeasure.METER),
                    grossQuantityTotal = Quantity(750, UnitOfMeasure.METER),
                    resolvedPrice = null,
                    costPerGarment = Money.idr(35_000L), // Notional audit value
                    costTotal = Money.idr(17_500_000L),
                    ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
                )
            )
        )

        val samBreakdown = SamMinutesCalculator.SamMinutesBreakdown(
            directMicros = 15_000_000L, // 15 min SAM
            subcontractMicros = 0L
        )

        val params = ResolvedCostingParameters.defaultsFor(CostingBehavior.SERVICE_FEE_ONLY).copy(
            serviceFeePerUnit = Money.idr(25_000L), // Fixed CMT fee
            laborRatePerSamMinute = Money.idr(800L),
            overheadPerUnit = Money.idr(1_000L),
            packingCostPerUnit = Money.idr(500L),
            includeFabricCost = false
        )

        val input = CostingFormulaInput(
            techPack = techPack,
            bomCostPreview = bomPreview,
            orderQuantity = 500L,
            samBreakdown = samBreakdown,
            parameters = params,
            pricingAsOf = now,
            currency = CurrencyCode.IDR
        )

        val result = ServiceFeeOnlyStrategy.calculate(input).getOrThrow()

        // Consigned fabric is captured for audit reconciliation (total order = 17,500,000), but its billable bucket amount is ZERO
        assertEquals(17_500_000_00L, result.consignedMaterialValueHandled.minorUnits)

        val materialBucket = result.buckets.first { it.kind == CostBucketKind.MATERIAL }
        assertEquals(0L, materialBucket.amountPerUnit.minorUnits)
        assertFalse(materialBucket.isBillableToClient)

        val laborBucket = result.buckets.first { it.kind == CostBucketKind.LABOR }
        assertEquals(25_000_00L, laborBucket.amountPerUnit.minorUnits)
        assertTrue(laborBucket.isBillableToClient)
    }

    @Test
    fun costingParameterCodec_mergesAndEncodesOverrides() {
        val base = ResolvedCostingParameters.defaultsFor(CostingBehavior.FULL_PACKAGE_COGS)
        val overrides = mapOf(
            "marginPercent" to "22.5",
            "overheadPerPcsIdr" to "3500",
            "packingCostPerPcsIdr" to "1200"
        )

        val merged = CostingParameterCodec.merge(
            base = base,
            sheetOverrides = overrides
        )

        assertEquals(22.5, merged.marginRatio.toDouble() * 100.0, 0.01)
        assertEquals(3_500L, merged.overheadPerUnit.toWholeUnits())
        assertEquals(1_200L, merged.packingCostPerUnit.toWholeUnits())
        assertEquals(CostingParameterSource.SHEET_OVERRIDE, merged.provenance["marginRatio"])

        val encoded = CostingParameterCodec.encodeOverrides(merged)
        assertEquals("22.5", encoded["marginPercent"])
        assertEquals("3500", encoded["overheadPerPcsIdr"])
    }

    @Test
    fun costingSheetDrift_detectsPerUnitDelta() {
        val baselineResult = CostingCalculationResult(
            costingId = "cst-baseline",
            tenantId = tenantId,
            techPackId = "tp-1",
            orderQuantity = 100L,
            behavior = CostingBehavior.FULL_PACKAGE_COGS,
            buckets = listOf(
                com.eventverse.app.domain.contracts.CostBucket(
                    kind = CostBucketKind.MATERIAL,
                    label = "Kain",
                    amountPerUnit = Money.idr(50_000L),
                    isBillableToClient = true
                )
            ),
            calculatedAt = now
        )

        val snapshot = CostingSnapshot(
            snapshotId = "snap-1",
            approvedAt = now,
            approvedByUserId = "u-1",
            result = baselineResult,
            inputFingerprint = "fp-1"
        )

        val sheet = CostingSheet(
            id = CostingSheetId("sheet-1"),
            tenantId = tenantId,
            number = CostingNumber("HPP-2026-0001"),
            techPackId = "tp-1",
            orderQuantity = 100L,
            behavior = CostingBehavior.FULL_PACKAGE_COGS,
            status = CostingSheetStatus.APPROVED,
            pricingAsOf = now,
            approvedSnapshot = snapshot,
            createdAt = now,
            updatedAt = now
        )

        // Case 1: No change
        val driftNone = sheet.driftAgainst(baselineResult)
        assertTrue(driftNone is CostingDrift.None)

        // Case 2: Material price increases to 55,000 (10% increase)
        val driftedResult = baselineResult.copy(
            buckets = listOf(
                com.eventverse.app.domain.contracts.CostBucket(
                    kind = CostBucketKind.MATERIAL,
                    label = "Kain",
                    amountPerUnit = Money.idr(55_000L),
                    isBillableToClient = true
                )
            )
        )

        val driftDetected = sheet.driftAgainst(driftedResult)
        assertTrue(driftDetected is CostingDrift.Detected)
        assertEquals(5_000_00L, driftDetected.deltaPerUnit.minorUnits)
        assertEquals(10.0, driftDetected.deltaPercent, 0.01)
        assertTrue(driftDetected.isSignificant)
    }
}
