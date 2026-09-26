package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CostBucketTest {

    @Test
    fun consignedMaterial_mustHaveZeroAmountAndNotBillable() {
        // Safe factory creation
        val bucket = CostBucket.material(
            label = "Kain Titipan Klien",
            amountPerUnit = Money.idr(100_000), // even if passed 100k, factory forces Rp 0
            ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
            behavior = CostingBehavior.FULL_PACKAGE_COGS
        )

        assertEquals(0L, bucket.amountPerUnit.minorUnits)
        assertFalse(bucket.isBillableToClient)
    }

    @Test
    fun consignedMaterial_ifInstantiatedAsBillable_mustThrow() {
        assertFailsWith<IllegalArgumentException> {
            CostBucket(
                kind = CostBucketKind.MATERIAL,
                label = "Kain Titipan",
                amountPerUnit = Money.zero(),
                ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
                isBillableToClient = true // Violation of Contract 4
            )
        }
    }

    @Test
    fun consignedMaterial_ifInstantiatedWithNonZeroAmount_mustThrow() {
        assertFailsWith<IllegalArgumentException> {
            CostBucket(
                kind = CostBucketKind.MATERIAL,
                label = "Kain Titipan",
                amountPerUnit = Money.idr(50_000), // Violation of Contract 3
                ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
                isBillableToClient = false
            )
        }
    }
}
