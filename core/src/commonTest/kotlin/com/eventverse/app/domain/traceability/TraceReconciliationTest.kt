package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class TraceReconciliationTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val tenant = TenantId("tnt-1")
    private val ref = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, "smp_1")

    private val snapshot = TraceWorkOrderSnapshot(
        ref = ref,
        tenantId = tenant,
        tenantOrdinal = 1,
        ordinal = 9,
        spkNumber = "SPK-SMP-0009",
        styleName = "Cardigan Rajut",
        clientName = "PT Buyer",
        sizes = listOf(TraceSizeLine("L", 40), TraceSizeLine("XL", 20)),
        panelRequirements = listOf(
            PanelRequirement(GarmentPanel.BODY_FRONT, 1),
            PanelRequirement(GarmentPanel.BODY_BACK, 1),
            PanelRequirement(GarmentPanel.SLEEVE_LEFT, 2)
        )
    )

    private fun bundle(size: String, seq: Int, front: Int, back: Int, sleeve: Int, state: TraceContainerState) =
        TraceContainer(
            id = TraceContainerId("bdl-$size-$seq"),
            tenantId = tenant,
            code = TraceCodec.encode(TraceWorkOrderKind.SAMPLING, TraceTier.BUNDLE, 1, 9, 0, seq),
            workOrder = ref,
            tier = TraceTier.BUNDLE,
            sizeLabel = size,
            state = state,
            panelTallies = listOf(
                PanelTally(GarmentPanel.BODY_FRONT, front),
                PanelTally(GarmentPanel.BODY_BACK, back),
                PanelTally(GarmentPanel.SLEEVE_LEFT, sleeve)
            ),
            recordedAt = now, createdAt = now, updatedAt = now
        )

    private fun sack(size: String, seq: Int, pcs: Int) = TraceContainer(
        id = TraceContainerId("krg-$size-$seq"),
        tenantId = tenant,
        code = TraceCodec.encode(TraceWorkOrderKind.SAMPLING, TraceTier.SACK, 1, 9, 0, seq),
        workOrder = ref,
        tier = TraceTier.SACK,
        sizeLabel = size,
        state = TraceContainerState.CLOSED,
        declaredPcs = pcs,
        recordedAt = now, createdAt = now, updatedAt = now
    )

    @Test
    fun `shrinkage is the gap between what left the bundles and what reached the sack`() {
        val b1 = bundle("L", 1, 10, 10, 20, TraceContainerState.CONSUMED)   // 10 set
        val b2 = bundle("L", 2, 10, 10, 20, TraceContainerState.CONSUMED)   // 10 set
        val links = listOf(
            TraceContainerLink(tenant, TraceContainerId("krg-L-1"), b1.id, 10, now),
            TraceContainerLink(tenant, TraceContainerId("krg-L-1"), b2.id, 10, now)
        )
        val result = TraceReconciliation.build(snapshot, listOf(b1, b2, sack("L", 1, 18)), links)

        val l = result.perSize.single { it.sizeLabel == "L" }
        assertEquals(20, l.bundledSets)
        assertEquals(20, l.consumedSets)
        assertEquals(18, l.sackPcs)
        assertEquals(2, l.shrinkagePcs)
        assertTrue(l.hasUnexplainedGap)
        assertEquals(listOf("L"), result.sizesWithGap)
    }

    @Test
    fun `bundles not yet poured count as work in progress`() {
        val pending = bundle("XL", 1, 6, 6, 12, TraceContainerState.TALLIED)
        val result = TraceReconciliation.build(snapshot, listOf(pending), emptyList())

        val xl = result.perSize.single { it.sizeLabel == "XL" }
        assertEquals(6, xl.bundledSets)
        assertEquals(0, xl.consumedSets)
        assertEquals(6, xl.pendingSets)
        assertEquals(6, result.wipPieces)
    }

    @Test
    fun `a sack holding more than was recorded shows a negative gap rather than hiding it`() {
        val b1 = bundle("L", 1, 5, 5, 10, TraceContainerState.CONSUMED)
        val links = listOf(TraceContainerLink(tenant, TraceContainerId("krg-L-1"), b1.id, 5, now))
        val result = TraceReconciliation.build(snapshot, listOf(b1, sack("L", 1, 9)), links)

        // Bundel yang lupa di-scan tidak boleh disamarkan jadi nol.
        assertEquals(-4, result.perSize.single { it.sizeLabel == "L" }.shrinkagePcs)
    }

    @Test
    fun `leftover panels are aggregated across bundles of the same size`() {
        val b1 = bundle("L", 1, 5, 4, 10, TraceContainerState.TALLIED)  // sisa: 1 depan, 2 lengan
        val b2 = bundle("L", 2, 3, 2, 5, TraceContainerState.TALLIED)   // 2 set -> sisa 1 depan, 1 lengan
        val result = TraceReconciliation.build(snapshot, listOf(b1, b2), emptyList())

        val leftover = result.perSize.single { it.sizeLabel == "L" }
            .leftoverPanels.associate { it.panel to it.pieces }
        assertEquals(2, leftover[GarmentPanel.BODY_FRONT])
        assertEquals(3, leftover[GarmentPanel.SLEEVE_LEFT])
    }

    @Test
    fun `open sacks are ignored until they are closed`() {
        val open = sack("L", 1, 10).copy(state = TraceContainerState.OPENED)
        val result = TraceReconciliation.build(snapshot, listOf(open), emptyList())
        assertEquals(0, result.totalSackPcs)
    }
}
