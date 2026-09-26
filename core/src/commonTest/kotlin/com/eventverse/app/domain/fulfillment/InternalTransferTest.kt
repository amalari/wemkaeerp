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
        declaredPcs: Int = 120,
        handoverMode: HandoverMode = HandoverMode.ADMIN_HUB
    ): InternalTransfer {
        val lewatMejaAdmin = handoverMode == HandoverMode.ADMIN_HUB
        return InternalTransfer(
            id = SackTransferId("trf-1"),
            tenantId = tenant,
            sackCode = sack,
            sizeLabel = "M",
            colorway = "Hitam",
            declaredPcs = declaredPcs,
            leg = SackRoute.QC_RAJUT_TO_FINISHING,
            handoverMode = handoverMode,
            status = status,
            dispatchWeightKg = if (lewatMejaAdmin) WeightKg(8.40) else null,
            dispatchScalePhotoKey = if (lewatMejaAdmin) "uploads/timbang-dispatch.jpg" else null,
            requestedBy = "Rian",
            requestedAt = now,
            approvedBy = if (lewatMejaAdmin && status.sedangBerjalan) "Admin" else null,
            approvedAt = if (lewatMejaAdmin && status.sedangBerjalan) now else null,
            approvalSignatureKey = if (lewatMejaAdmin && status.sedangBerjalan) "{\"v\":1}" else null,
            rejectedBy = if (status == SackTransferStatus.DITOLAK) "Admin" else null,
            rejectReason = if (status == SackTransferStatus.DITOLAK) "isi tidak sesuai" else null,
            createdAt = now,
            updatedAt = now
        )
    }

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

    // ── HandoverMode.DIRECT ─────────────────────────────────────────────────────────────────

    @Test
    fun `create direct transfer without weight and photo should be valid`() {
        val direct = aTransfer(status = SackTransferStatus.DIANTAR, handoverMode = HandoverMode.DIRECT)

        assertEquals(null, direct.dispatchWeightKg)
        assertEquals(null, direct.approvedBy, "Antar langsung tidak melewati siapa pun untuk di-ACC")
        assertEquals(SackTransferStatus.DIANTAR, direct.status)
    }

    @Test
    fun `create direct transfer carrying approval data should throw exception`() {
        val error = assertFailsWith<IllegalArgumentException> {
            aTransfer(status = SackTransferStatus.DIANTAR, handoverMode = HandoverMode.DIRECT)
                .copy(approvedBy = "Admin", approvalSignatureKey = "{\"v\":1}")
        }
        assertTrue(error.message!!.contains("tidak mengenal ACC admin"))
    }

    @Test
    fun `create direct transfer with rejected status should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            aTransfer(status = SackTransferStatus.DITOLAK, handoverMode = HandoverMode.DIRECT)
        }
    }

    @Test
    fun `approve direct transfer should throw exception`() {
        val direct = aTransfer(status = SackTransferStatus.DIANTAR, handoverMode = HandoverMode.DIRECT)

        val error = assertFailsWith<IllegalArgumentException> {
            direct.approve("Admin", "{\"v\":1}", now)
        }
        assertTrue(error.message!!.contains("diantar langsung"))
    }

    @Test
    fun `receive direct transfer without signature should be accepted`() {
        val direct = aTransfer(status = SackTransferStatus.DIANTAR, handoverMode = HandoverMode.DIRECT)

        val received = direct.receive(
            proof = HandoverProof.ReceiverHandover(
                receiverName = "Sugeng",
                signatureKey = null,
                evidencePhotoKey = "uploads/serah-terima.jpg"
            ),
            receivedWeightKg = null,
            receivedPcs = 120,
            now = now
        )

        assertEquals(SackTransferStatus.DITERIMA, received.status)
    }

    @Test
    fun `receive direct transfer when pcs shortage should mark discrepancy`() {
        // Tanpa berat berangkat, perbandingan pcs memikul seluruh deteksi selisih di mode ini.
        val direct = aTransfer(status = SackTransferStatus.DIANTAR, handoverMode = HandoverMode.DIRECT)

        val received = direct.receive(
            proof = HandoverProof.ReceiverHandover("Sugeng", null, "uploads/serah-terima.jpg"),
            receivedWeightKg = null,
            receivedPcs = 118,
            now = now
        )

        assertEquals(SackTransferStatus.DITERIMA_SELISIH, received.status)
    }

    @Test
    fun `receive admin hub transfer without signature should still throw exception`() {
        // Regresi: pelonggaran TTD hanya berlaku untuk DIRECT, bukan untuk semua.
        val inTransit = aTransfer(status = SackTransferStatus.MENUNGGU_ACC).approve("Admin", "{\"v\":1}", now)

        assertFailsWith<IllegalArgumentException> {
            inTransit.receive(
                proof = HandoverProof.ReceiverHandover("Sugeng", null, "uploads/foto.jpg"),
                receivedWeightKg = null,
                receivedPcs = null,
                now = now
            )
        }
    }

    @Test
    fun `route config without entries should default every route to admin hub`() {
        // Regresi utama seluruh perubahan ini: tenant yang tidak menyetel apa pun harus
        // berperilaku persis seperti sebelum mode DIRECT ada.
        val config = FulfillmentRouteConfig(tenantId = tenant)

        SackRoute.entries.forEach { route ->
            assertEquals(HandoverMode.ADMIN_HUB, config.modeFor(route), "Rute $route berubah diam-diam")
        }
        assertTrue(config.hasAdminHubRoute)
    }

    @Test
    fun `route config should only offer direct routes for an unclosed bundle`() {
        val config = FulfillmentRouteConfig(
            tenantId = tenant,
            modes = mapOf(SackRoute.FINISHING_TO_QC_FINISHING to HandoverMode.DIRECT)
        )

        assertEquals(listOf(SackRoute.FINISHING_TO_QC_FINISHING), config.routesAccepting(isClosedSack = false))
        assertEquals(SackRoute.entries.toList(), config.routesAccepting(isClosedSack = true))
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
