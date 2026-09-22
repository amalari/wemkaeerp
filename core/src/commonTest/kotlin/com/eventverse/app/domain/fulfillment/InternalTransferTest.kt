package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCode
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InternalTransferTest {

    private val tenant = TenantId("ten-1")
    private val sack = TraceCode("W1SK01A23B45C67D")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun aTransfer(
        status: SackTransferStatus = SackTransferStatus.MENUNGGU_ACC,
        declaredPcs: Int = 120
    ) = InternalTransfer(
        id = SackTransferId("trf-1"),
        tenantId = tenant,
        sackCode = sack,
        sizeLabel = "M",
        colorway = "Hitam",
        declaredPcs = declaredPcs,
        leg = SackRoute.QC_RAJUT_TO_FINISHING,
        status = status,
        dispatchWeightKg = WeightKg(8.40),
        dispatchScalePhotoKey = "uploads/timbang-dispatch.jpg",
        requestedBy = "Rian",
        requestedAt = now,
        approvedBy = if (status.sudahDisetujui) "Admin" else null,
        approvedAt = if (status.sudahDisetujui) now else null,
        approvalSignatureKey = if (status.sudahDisetujui) "{\"v\":1}" else null,
        rejectedBy = if (status == SackTransferStatus.DITOLAK) "Admin" else null,
        rejectReason = if (status == SackTransferStatus.DITOLAK) "isi tidak sesuai" else null,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `create transfer without dispatch photo should throw exception`() {
        val error = assertFailsWith<IllegalArgumentException> {
            aTransfer().copy(dispatchScalePhotoKey = " ")
        }
        assertTrue(error.message!!.contains("Foto timbangan"))
    }

    @Test
    fun `approve transfer when already approved should throw exception`() {
        val approved = aTransfer(status = SackTransferStatus.MENUNGGU_ACC)
            .approve("Admin", "{\"v\":1}", now)

        assertFailsWith<IllegalArgumentException> {
            approved.approve("Admin Lagi", "{\"v\":1}", now)
        }
    }

    @Test
    fun `reject transfer without reason should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            aTransfer().reject(" ", "Admin", now)
        }
    }

    @Test
    fun `resubmit transfer when rejected should return to pending`() {
        val rejected = aTransfer().reject("size campur", "Admin", now)
        val resubmitted = rejected.resubmit(WeightKg(8.40), "uploads/timbang-baru.jpg", "Rian", now)

        assertEquals(SackTransferStatus.MENUNGGU_ACC, resubmitted.status)
        assertEquals(null, resubmitted.rejectReason)
    }

    @Test
    fun `receive transfer when pcs shortage should mark discrepancy`() {
        val inTransit = aTransfer(status = SackTransferStatus.MENUNGGU_ACC).approve("Admin", "{\"v\":1}", now)
        val received = inTransit.receive(
            proof = HandoverProof.ReceiverHandover(
                receiverName = "Sugeng",
                signatureKey = "{\"v\":1}",
                evidencePhotoKey = "uploads/timbang-terima.jpg"
            ),
            receivedWeightKg = WeightKg(8.40),
            receivedPcs = 118,
            now = now
        )

        assertEquals(SackTransferStatus.DITERIMA_SELISIH, received.status)
    }

    @Test
    fun `receive transfer when everything matches should be accepted`() {
        val inTransit = aTransfer(status = SackTransferStatus.MENUNGGU_ACC).approve("Admin", "{\"v\":1}", now)
        val received = inTransit.receive(
            proof = HandoverProof.ReceiverHandover(
                receiverName = "Sugeng",
                signatureKey = "{\"v\":1}",
                evidencePhotoKey = "uploads/timbang-terima.jpg"
            ),
            receivedWeightKg = WeightKg(8.35),
            receivedPcs = 120,
            now = now
        )

        assertEquals(SackTransferStatus.DITERIMA, received.status)
    }

    @Test
    fun `receive transfer without receiver signature should throw exception`() {
        val inTransit = aTransfer(status = SackTransferStatus.MENUNGGU_ACC).approve("Admin", "{\"v\":1}", now)

        assertFailsWith<IllegalArgumentException> {
            inTransit.receive(
                proof = HandoverProof.ReceiverHandover("Sugeng", " ", "uploads/foto.jpg"),
                receivedWeightKg = null,
                receivedPcs = null,
                now = now
            )
        }
    }

    @Test
    fun `weight parse with comma input should keep two decimals`() {
        assertEquals(WeightKg(8.35), WeightKg.parse("8,35"))
        assertEquals(WeightKg(8.35), WeightKg.parse("8.35"))
        assertEquals("8.35 kg", WeightKg(8.35).formatted())
        assertEquals("8.50 kg", WeightKg(8.5).formatted())
        assertEquals(null, WeightKg.parse("berat"))
    }
}
