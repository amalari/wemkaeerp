package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.fulfillment.InternalTransferCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.domain.traceability.TraceCode
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-FLOW-003 A3–A5: config berkunci kode, entitas `route`, codec tanpa fallback senyap — dengan tenant bordir. */
class HandoverRouteConfigTest {

    private val tenant = TenantId("bordir-uji")
    private val digitizing = HandoverRouteCode("DIGITIZING_TO_HOOPING")
    private val hooping = HandoverRouteCode("HOOPING_TO_QC")
    private val bordir = TenantHandoverRoutes(
        tenant,
        listOf(
            HandoverRoute(digitizing, "Digitizing ke Hooping", sortOrder = 0),
            HandoverRoute(hooping, "Hooping ke QC", sortOrder = 1),
            HandoverRoute(HandoverRouteCode("LAMA"), "Rute lama", sortOrder = 2, active = false)
        )
    )

    @Test
    fun `mode rute bordir - tanpa entri ADMIN_HUB, disetel DIRECT dibaca per kode`() {
        val config = FulfillmentRouteConfig(tenant, mapOf(hooping to HandoverMode.DIRECT))
        assertEquals(HandoverMode.ADMIN_HUB, config.modeFor(digitizing))
        assertEquals(HandoverMode.DIRECT, config.modeFor(hooping))
    }

    @Test
    fun `rute yang ditawarkan - hanya rute aktif tenant, dan bundel belum ditutup hanya rute DIRECT`() {
        val config = FulfillmentRouteConfig(tenant, mapOf(hooping to HandoverMode.DIRECT))
        assertEquals(listOf(hooping), config.routesAccepting(bordir, isClosedSack = false).map { it.code })
        assertEquals(listOf(digitizing, hooping), config.routesAccepting(bordir, isClosedSack = true).map { it.code })
    }

    @Test
    fun `antrean ACC - rute ADMIN_HUB yang nonaktif tidak dihitung`() {
        val semuaDirect = FulfillmentRouteConfig(tenant, mapOf(digitizing to HandoverMode.DIRECT, hooping to HandoverMode.DIRECT))
        assertFalse(semuaDirect.hasAdminHubRoute(bordir), "rute LAMA nonaktif tidak boleh memunculkan antrean ACC")
        assertTrue(FulfillmentRouteConfig(tenant).hasAdminHubRoute(bordir))
    }

    @Test
    fun `validatedAgainst - kode di luar rute tenant ditolak, bukan diabaikan`() {
        val salah = FulfillmentRouteConfig(tenant, mapOf(SackRoute.QC_RAJUT_TO_FINISHING.toRouteCode() to HandoverMode.DIRECT))
        assertFailsWith<IllegalArgumentException> { salah.validatedAgainst(bordir) }
        FulfillmentRouteConfig(tenant, mapOf(hooping to HandoverMode.DIRECT)).validatedAgainst(bordir)
    }

    @Test
    fun `jembatan - config lama berkunci SackRoute tetap bekerja dan setara`() {
        val lama = FulfillmentRouteConfig(tenant, mapOf(SackRoute.FINISHING_TO_QC_FINISHING to HandoverMode.DIRECT))
        assertEquals(HandoverMode.DIRECT, lama.modeFor(SackRoute.FINISHING_TO_QC_FINISHING))
        assertEquals(HandoverMode.ADMIN_HUB, lama.modeFor(SackRoute.QC_RAJUT_TO_FINISHING))
        assertEquals(listOf(SackRoute.FINISHING_TO_QC_FINISHING), lama.routesAccepting(isClosedSack = false))
    }

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private fun transfer(route: HandoverRouteCode) = InternalTransfer(
        id = SackTransferId("trf-1"), tenantId = tenant, sackCode = TraceCode("W1SK01A23B45C67D"),
        sizeLabel = "M", declaredPcs = 10, route = route, handoverMode = HandoverMode.DIRECT,
        status = SackTransferStatus.DIANTAR, requestedBy = "Rian", requestedAt = now, createdAt = now, updatedAt = now
    )

    @Test
    fun `perjalanan di rute bordir - round trip codec, label jatuh ke kode, dan pembacaan lama gagal keras`() {
        val t = transfer(hooping)
        val json = InternalTransferCodec.encode(t).encode()
        assertEquals(hooping, InternalTransferCodec.decode(JsonParser.parseObject(json)).route)
        assertEquals("HOOPING_TO_QC", JsonParser.parseObject(json).string("legLabel"))
        assertFailsWith<IllegalStateException> { t.leg }
        assertEquals(SackRoute.QC_RAJUT_TO_FINISHING, transfer(SackRoute.QC_RAJUT_TO_FINISHING.toRouteCode()).leg)
    }

    @Test
    fun `perjalanan rajut - legLabel tetap nama baku seperti sebelum migrasi`() {
        val json = InternalTransferCodec.encode(transfer(SackRoute.QC_RAJUT_TO_FINISHING.toRouteCode())).encode()
        assertEquals("QC Rajut ke Finishing", JsonParser.parseObject(json).string("legLabel"))
    }

    @Test
    fun `decode perjalanan - rute tak sah atau hilang ditolak, tidak jatuh ke QC_RAJUT_TO_FINISHING`() {
        val ok = JsonParser.parseObject(InternalTransferCodec.encode(transfer(hooping)).encode())
        listOf("huruf kecil", "", "RUTE-X").forEach { buruk ->
            val rusak = JsonParser.parseObject(InternalTransferCodec.encode(transfer(hooping)).encode().replace("\"leg\":\"HOOPING_TO_QC\"", "\"leg\":\"$buruk\""))
            assertFailsWith<IllegalArgumentException>("seharusnya ditolak: '$buruk'") { InternalTransferCodec.decode(rusak) }
        }
        val tanpaLeg = JsonParser.parseObject(InternalTransferCodec.encode(transfer(hooping)).encode().replace("\"leg\":\"HOOPING_TO_QC\",", ""))
        assertFailsWith<IllegalArgumentException> { InternalTransferCodec.decode(tanpaLeg) }
        assertEquals(hooping, InternalTransferCodec.decode(ok).route)
    }
}
