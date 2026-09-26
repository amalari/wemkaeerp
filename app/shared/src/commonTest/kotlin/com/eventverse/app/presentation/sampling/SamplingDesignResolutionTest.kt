package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SpkNumber
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SamplingDesignResolutionTest {

    private val tenantId = TenantId("tenant-test")

    private fun createOrder(
        id: String,
        spk: String,
        dealId: String? = null,
        styleName: String = "Test Style",
        createdAt: Instant = Instant.fromEpochMilliseconds(1000L)
    ): SamplingOrder {
        return SamplingOrder(
            id = SamplingOrderId(id),
            tenantId = tenantId,
            spkNumber = SpkNumber(spk),
            clientName = "Client Test",
            styleName = styleName,
            dealId = dealId,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    @Test
    fun returnsNull_whenSingleOrderWithoutDealId_andNoDsgPrefix() {
        val order = createOrder(id = "smp-1", spk = "SPK-SMP-0001", styleName = "Cardigan Rajut Polos")
        val info = resolveDesignInfo(order, listOf(order))
        assertNull(info)
    }

    @Test
    fun returnsNull_whenSingleOrderInDeal_andNoDsgPrefix() {
        val order = createOrder(id = "smp-1", spk = "SPK-SMP-0001", dealId = "deal-1", styleName = "Cardigan Polos")
        val otherDealOrder = createOrder(id = "smp-2", spk = "SPK-SMP-0002", dealId = "deal-2", styleName = "Vest Polos")
        val info = resolveDesignInfo(order, listOf(order, otherDealOrder))
        assertNull(info)
    }

    @Test
    fun resolvesMultiDesignDeal_orderedByCreatedAtThenSpk() {
        val order1 = createOrder(
            id = "smp-1",
            spk = "SPK-SMP-0009",
            dealId = "deal-kanva",
            styleName = "Vest Rajut Rib Colorway Navy",
            createdAt = Instant.fromEpochMilliseconds(1000L)
        )
        val order2 = createOrder(
            id = "smp-2",
            spk = "SPK-SMP-0010",
            dealId = "deal-kanva",
            styleName = "Vest Rajut Rib Colorway Cream",
            createdAt = Instant.fromEpochMilliseconds(2000L)
        )
        val allOrders = listOf(order2, order1) // Out of order list

        val info1 = resolveDesignInfo(order1, allOrders)
        assertNotNull(info1)
        assertEquals("DSG-01", info1.code)
        assertEquals(1, info1.designNumber)
        assertEquals(2, info1.totalDesigns)

        val info2 = resolveDesignInfo(order2, allOrders)
        assertNotNull(info2)
        assertEquals("DSG-02", info2.code)
        assertEquals(2, info2.designNumber)
        assertEquals(2, info2.totalDesigns)
    }

    @Test
    fun resolvesMultiDesignDeal_breaksTiesBySpkNumber() {
        val sameTime = Instant.fromEpochMilliseconds(1000L)
        val orderA = createOrder(id = "smp-a", spk = "SPK-SMP-0001", dealId = "deal-multi", createdAt = sameTime)
        val orderB = createOrder(id = "smp-b", spk = "SPK-SMP-0002", dealId = "deal-multi", createdAt = sameTime)
        val orderC = createOrder(id = "smp-c", spk = "SPK-SMP-0003", dealId = "deal-multi", createdAt = sameTime)

        val all = listOf(orderC, orderA, orderB)

        assertEquals("DSG-01", resolveDesignInfo(orderA, all)?.code)
        assertEquals("DSG-02", resolveDesignInfo(orderB, all)?.code)
        assertEquals("DSG-03", resolveDesignInfo(orderC, all)?.code)
    }

    @Test
    fun resolvesExplicitDsgPrefixInStyleName() {
        val order = createOrder(id = "smp-x", spk = "SPK-SMP-0099", styleName = "DSG-04 Oversized Sweater")
        val info = resolveDesignInfo(order, listOf(order))
        assertNotNull(info)
        assertEquals("DSG-04", info.code)
        assertEquals(4, info.designNumber)
        assertEquals(1, info.totalDesigns)
    }
}
