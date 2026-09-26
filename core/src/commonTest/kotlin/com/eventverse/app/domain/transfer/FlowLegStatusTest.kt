package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowLegStatusTest {

    private val gedungA = LocationId("loc-rajut-01")
    private val gedungB = LocationId("loc-finishing-01")
    private val gudangC = LocationId("loc-gudang-03")

    private val fromNode = FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING)
    private val toNode = FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY)

    private val leg = FlowTransferLeg(
        legKey = FlowTransferLeg.keyFor(fromNode, toNode, TransferType.INTERNAL_SITE_TRANSFER),
        fromNode = fromNode,
        toNode = toNode,
        origin = LegEndpoint.Site(gedungA, "Gedung A"),
        destination = LegEndpoint.Site(gedungB, "Gedung B"),
        transferType = TransferType.INTERNAL_SITE_TRANSFER
    )

    private val subject = WorkSubjectRef(
        kind = WorkSubjectKind.SAMPLING_ORDER,
        subjectId = "smp-001",
        orderNumber = "SPK-SMP-0002",
        articleName = "Cardigan Rajut"
    )

    private fun manifest(
        id: String,
        status: TransferStatus,
        legKey: String? = leg.legKey,
        dispatchedAt: Instant? = Instant.fromEpochMilliseconds(1_000),
        receivedAt: Instant? = null,
        destination: LocationId? = gedungB
    ) = SuratJalanManifest(
        id = SuratJalanId(id),
        tenantId = "demo-tenant",
        sjNumber = SuratJalanNumber(id.uppercase()),
        transferType = TransferType.INTERNAL_SITE_TRANSFER,
        subject = subject,
        originLocationId = gedungA,
        destinationLocationId = destination,
        status = status,
        legKey = legKey,
        items = listOf(
            SuratJalanItem(id = "$id-i1", bundleNo = 1, sizeLabel = "M", qtyPcs = 5)
        ),
        dispatchedAt = dispatchedAt,
        receivedAt = receivedAt
    )

    private fun resolveWith(vararg manifests: SuratJalanManifest) =
        FlowLegStatusResolver.resolve(listOf(leg), manifests.toList())

    @Test
    fun `leg status when no manifest exists should be not issued`() {
        val board = FlowLegStatusResolver.resolve(listOf(leg), emptyList())
        assertEquals(FlowLegStatus.BELUM_TERBIT, board.legs.single().status)
        assertTrue(board.hasPendingLeg)
    }

    @Test
    fun `leg status when manifest still draft should be not issued`() {
        val board = resolveWith(manifest("sj-1", TransferStatus.DRAFT, dispatchedAt = null))
        assertEquals(
            FlowLegStatus.BELUM_TERBIT,
            board.legs.single().status,
            "Draft adalah niat, bukan izin berangkat"
        )
    }

    @Test
    fun `leg status when dispatched should be in transit`() {
        val board = resolveWith(manifest("sj-1", TransferStatus.DISPATCHED))
        assertEquals(FlowLegStatus.DIKIRIM, board.legs.single().status)
    }

    @Test
    fun `leg status when in transit should be in transit`() {
        val board = resolveWith(manifest("sj-1", TransferStatus.IN_TRANSIT))
        assertEquals(FlowLegStatus.DIKIRIM, board.legs.single().status)
    }

    @Test
    fun `leg status when partially received should stay in transit`() {
        val board = resolveWith(manifest("sj-1", TransferStatus.PARTIAL_RECEIVED))
        assertEquals(
            FlowLegStatus.DIKIRIM,
            board.legs.single().status,
            "Gerbang tidak boleh terbuka untuk barang yang baru sampai sebagian"
        )
    }

    @Test
    fun `leg status when received should be received and leg no longer pending`() {
        val board = resolveWith(
            manifest("sj-1", TransferStatus.RECEIVED, receivedAt = Instant.fromEpochMilliseconds(2_000))
        )
        assertEquals(FlowLegStatus.DITERIMA, board.legs.single().status)
        assertFalse(board.hasPendingLeg)
    }

    @Test
    fun `leg status when manifest cancelled should fall back to not issued`() {
        val board = resolveWith(manifest("sj-1", TransferStatus.CANCELLED))
        assertEquals(FlowLegStatus.BELUM_TERBIT, board.legs.single().status)
        assertEquals(null, board.legs.single().manifest, "Dokumen batal tidak melayani leg")
    }

    @Test
    fun `leg status when cancelled and active both exist should ignore the cancelled one`() {
        val board = resolveWith(
            manifest("sj-cancel", TransferStatus.CANCELLED),
            manifest("sj-live", TransferStatus.DISPATCHED)
        )
        assertEquals(FlowLegStatus.DIKIRIM, board.legs.single().status)
        assertEquals(SuratJalanId("sj-live"), board.legs.single().manifest?.id)
    }

    @Test
    fun `leg status when leg travelled twice should follow the latest journey not the furthest`() {
        val board = resolveWith(
            manifest(
                "sj-old",
                TransferStatus.RECEIVED,
                dispatchedAt = Instant.fromEpochMilliseconds(1_000),
                receivedAt = Instant.fromEpochMilliseconds(2_000)
            ),
            manifest(
                "sj-new",
                TransferStatus.DISPATCHED,
                dispatchedAt = Instant.fromEpochMilliseconds(9_000)
            )
        )
        assertEquals(
            FlowLegStatus.DIKIRIM,
            board.legs.single().status,
            "Pengiriman ulang harus menutup kembali gerbang yang sudah pernah terbuka"
        )
    }

    @Test
    fun `leg status when manifest predates leg key should match heuristically and be flagged`() {
        val board = resolveWith(manifest("sj-legacy", TransferStatus.RECEIVED, legKey = null))
        val view = board.legs.single()

        assertEquals(FlowLegStatus.DITERIMA, view.status)
        assertTrue(view.isLegacyMatch, "Pasangan hasil tebakan wajib ditandai, bukan disamarkan")
    }

    @Test
    fun `leg status when legacy manifest targets another building should not match`() {
        val board = resolveWith(
            manifest("sj-legacy", TransferStatus.RECEIVED, legKey = null, destination = gudangC)
        )
        assertEquals(FlowLegStatus.BELUM_TERBIT, board.legs.single().status)
    }

    @Test
    fun `leg status when exact key exists should prefer it over the legacy guess`() {
        val board = resolveWith(
            manifest("sj-legacy", TransferStatus.RECEIVED, legKey = null),
            manifest("sj-exact", TransferStatus.DISPATCHED)
        )
        val view = board.legs.single()

        assertEquals(SuratJalanId("sj-exact"), view.manifest?.id)
        assertFalse(view.isLegacyMatch)
    }

    @Test
    fun `orphan manifest when its leg vanished from the flow should surface not disappear`() {
        val stray = manifest("sj-stray", TransferStatus.DISPATCHED, legKey = "STAGE:CUTTING>STAGE:OBRAS:X")
        val board = resolveWith(stray)

        assertEquals(listOf(SuratJalanId("sj-stray")), board.orphanManifests.map { it.id })
    }

    @Test
    fun `orphan manifest when cancelled should not be surfaced`() {
        val stray = manifest("sj-stray", TransferStatus.CANCELLED, legKey = "STAGE:CUTTING>STAGE:OBRAS:X")
        val board = resolveWith(stray)

        assertEquals(emptyList(), board.orphanManifests)
    }

    @Test
    fun `legs into node should return only legs arriving at that node`() {
        val board = resolveWith(manifest("sj-1", TransferStatus.DISPATCHED))

        assertEquals(1, board.legsInto(toNode).size)
        assertEquals(emptyList(), board.legsInto(fromNode))
    }
}
