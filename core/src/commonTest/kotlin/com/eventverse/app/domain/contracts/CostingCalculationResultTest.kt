package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CostingCalculationResultTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun cmtServiceFeeOnly_shouldExcludeConsignedMaterialFromBillable() {
        val consignedFabric = CostBucket.material(
            label = "Kain Titipan Buyer",
            amountPerUnit = Money.zero(),
            ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
            behavior = CostingBehavior.SERVICE_FEE_ONLY
        )
        val threadTrim = CostBucket.material(
            label = "Benang Jahit & Kancing",
            amountPerUnit = Money.idr(5_000),
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
            behavior = CostingBehavior.SERVICE_FEE_ONLY
        )
        val labor = CostBucket(
            kind = CostBucketKind.LABOR,
            label = "Ongkos CMT / Jahit",
            amountPerUnit = Money.idr(25_000),
            ownership = StockOwnershipSemantics.NON_STOCK_SERVICE,
            isBillableToClient = true
        )
        val overhead = CostBucket(
            kind = CostBucketKind.OVERHEAD,
            label = "Listrik & Finishing",
            amountPerUnit = Money.idr(4_000),
            ownership = StockOwnershipSemantics.NON_STOCK_SERVICE,
            isBillableToClient = true
        )

        val result = CostingCalculationResult(
            costingId = "cst-001",
            tenantId = tenantId,
            techPackId = "tp-001",
            orderQuantity = 1000L,
            behavior = CostingBehavior.SERVICE_FEE_ONLY,
            buckets = listOf(consignedFabric, threadTrim, labor, overhead),
            marginRatio = Ratio.percent(10.0), // 10%
            consignedMaterialValueHandled = Money.idr(45_000), // Notional audit value
            calculatedAt = now
        )

        // Billable per unit = thread (0 because SERVICE_FEE_ONLY for owned raw material) + labor (25.000) + overhead (4.000) = 29.000
        // Wait, for threadTrim: ownership is OWNED_RAW_MATERIAL, behavior is SERVICE_FEE_ONLY -> isBillable = behavior != CostingBehavior.SERVICE_FEE_ONLY = false!
        assertEquals(29_000_00L, result.billablePerUnit.minorUnits)

        // Selling price with 10% margin: 29.000 + 2.900 = 31.900
        assertEquals(31_900_00L, result.sellingPricePerUnit.minorUnits)

        // Billable total for 1,000 pcs = Rp 29.000.000
        assertEquals(29_000_000_00L, result.billableTotal.minorUnits)
    }
}
