package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class TraceContainerTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val tenant = TenantId("tnt-1")
    private val ref = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, "smp_1")

    /** Satu baju butuh depan 1, belakang 1, lengan 2 — inilah yang sering salah dibaca dari panelYields. */
    private val requirements = listOf(
        PanelRequirement(GarmentPanel.BODY_FRONT, 1),
        PanelRequirement(GarmentPanel.BODY_BACK, 1),
        PanelRequirement(GarmentPanel.SLEEVE_LEFT, 2)
    )

    private fun container(
        tier: TraceTier = TraceTier.BUNDLE,
        size: String = "L",
        colorway: String = "Navy",
        seq: Int = 0,
        state: TraceContainerState = TraceContainerState.OPENED,
        tallies: List<PanelTally> = emptyList(),
        pcs: Int = 0
    ) = TraceContainer(
        id = TraceContainerId("trc-$tier-$size-$seq"),
        tenantId = tenant,
        code = TraceCodec.encode(TraceWorkOrderKind.SAMPLING, tier, 1, 1, 0, seq),
        workOrder = ref,
        tier = tier,
        sizeLabel = size,
        colorway = colorway,
        state = state,
        panelTallies = tallies,
        declaredPcs = pcs,
        recordedAt = now,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `complete sets is limited by the scarcest panel not by the total`() {
        val bundle = container(
            tallies = listOf(
                PanelTally(GarmentPanel.BODY_FRONT, 5),
                PanelTally(GarmentPanel.BODY_BACK, 4),
                PanelTally(GarmentPanel.SLEEVE_LEFT, 10)
            )
        )
        // 19 lembar total, tapi hanya 4 baju yang bisa dirakit: belakang cuma 4.
        assertEquals(19, bundle.totalPanelPieces)
        assertEquals(4, bundle.completeSets(requirements))
    }

    @Test
    fun `sleeves count two per garment so ten sleeves are five sets not ten`() {
        val bundle = container(
            tallies = listOf(
                PanelTally(GarmentPanel.BODY_FRONT, 5),
                PanelTally(GarmentPanel.BODY_BACK, 5),
                PanelTally(GarmentPanel.SLEEVE_LEFT, 10)
            )
        )
        assertEquals(5, bundle.completeSets(requirements))
    }

    @Test
    fun `leftover panels carry the unpaired remainder`() {
        val bundle = container(
            tallies = listOf(
                PanelTally(GarmentPanel.BODY_FRONT, 5),
                PanelTally(GarmentPanel.BODY_BACK, 4),
                PanelTally(GarmentPanel.SLEEVE_LEFT, 10)
            )
        )
        val leftover = bundle.leftoverPanels(requirements).associate { it.panel to it.pieces }
        assertEquals(mapOf(GarmentPanel.BODY_FRONT to 1, GarmentPanel.SLEEVE_LEFT to 2), leftover)
    }

    @Test
    fun `bundle with a missing panel yields zero sets`() {
        val bundle = container(tallies = listOf(PanelTally(GarmentPanel.BODY_FRONT, 5)))
        assertEquals(0, bundle.completeSets(requirements))
        assertTrue(GarmentPanel.BODY_BACK in bundle.missingPanels(requirements))
    }

    @Test
    fun `tally cannot be recorded twice after the bundle is consumed`() {
        val bundle = container(state = TraceContainerState.CONSUMED)
        assertFailsWith<IllegalArgumentException> {
            bundle.recordTally(
                listOf(PanelTally(GarmentPanel.BODY_FRONT, 1)),
                "Sari", ShiftLabel("Malam"), now, now
            )
        }
    }

    @Test
    fun `sack rejects bundles of another size and says which sizes collide`() {
        val sack = container(tier = TraceTier.SACK, size = "L")
        val reasons = sack.missingUniformityReasons(
            listOf(
                container(size = "L", state = TraceContainerState.TALLIED, seq = 1),
                container(size = "XL", state = TraceContainerState.TALLIED, seq = 2)
            )
        )
        assertEquals(1, reasons.size)
        assertTrue(reasons.single().contains("L"), reasons.single())
        assertTrue(reasons.single().contains("XL"), reasons.single())
    }

    @Test
    fun `sack rejects mixed colorways`() {
        val sack = container(tier = TraceTier.SACK, colorway = "Navy")
        val reasons = sack.missingUniformityReasons(
            listOf(
                container(colorway = "Navy", state = TraceContainerState.TALLIED, seq = 1),
                container(colorway = "Cream", state = TraceContainerState.TALLIED, seq = 2)
            )
        )
        assertTrue(reasons.any { it.contains("warna") }, reasons.toString())
    }

    @Test
    fun `sack rejects bundles that were never tallied`() {
        val sack = container(tier = TraceTier.SACK)
        val reasons = sack.missingUniformityReasons(
            listOf(container(state = TraceContainerState.OPENED, seq = 1))
        )
        assertTrue(reasons.any { it.contains("belum dihitung") }, reasons.toString())
    }

    @Test
    fun `uniform sack of tallied bundles has no blockers`() {
        val sack = container(tier = TraceTier.SACK)
        val reasons = sack.missingUniformityReasons(
            listOf(
                container(state = TraceContainerState.TALLIED, seq = 1),
                container(state = TraceContainerState.TALLIED, seq = 2)
            )
        )
        assertTrue(reasons.isEmpty(), reasons.toString())
    }

    @Test
    fun `a bundle cannot be consumed before it is tallied`() {
        assertFailsWith<IllegalArgumentException> { container().markConsumed(now) }
    }

    @Test
    fun `duplicate panel rows are rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            container(
                tallies = listOf(
                    PanelTally(GarmentPanel.BODY_FRONT, 3),
                    PanelTally(GarmentPanel.BODY_FRONT, 4)
                )
            )
        }
    }
}
