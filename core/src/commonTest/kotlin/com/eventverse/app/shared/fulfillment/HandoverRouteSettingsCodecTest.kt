package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.domain.fulfillment.TenantHandoverRoutes
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.matchesShipped
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.DomainPackCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandoverRouteSettingsCodecTest {

    private val tenant = TenantId("bordir-uji")
    private val routes = listOf(
        HandoverRoute(HandoverRouteCode("DIGITIZING_TO_HOOPING"), "Digitizing ke Hooping", sortOrder = 1),
        HandoverRoute(
            HandoverRouteCode("HOOPING_TO_QC"), "Hooping ke QC",
            from = FlowNodeRef.Process("hooping"), to = FlowNodeRef.Process("qc-bordir"), sortOrder = 0
        )
    )

    private fun body(json: String) = JsonParser.parseObject(json)

    @Test
    fun `encode - bentuk kontrak terkunci, kunci lama tetap ada`() {
        val view = HandoverRouteSettingsView.of(
            TenantHandoverRoutes(tenant, routes),
            mapOf(HandoverRouteCode("HOOPING_TO_QC") to HandoverMode.DIRECT)
        )
        assertEquals(
            """{"tenantId":"bordir-uji","hasAdminHubRoute":true,"routes":[""" +
                """{"route":"HOOPING_TO_QC","routeLabel":"Hooping ke QC","mode":"DIRECT","modeLabel":"Operator Antar Langsung","isExplicit":true,"active":true,"sortOrder":0},""" +
                """{"route":"DIGITIZING_TO_HOOPING","routeLabel":"Digitizing ke Hooping","mode":"ADMIN_HUB","modeLabel":"Lewat Meja Admin","isExplicit":false,"active":true,"sortOrder":1}]}""",
            HandoverRouteSettingsCodec.encode(view).encode()
        )
    }

    @Test
    fun `decodeModes - payload sah dibaca per kode`() {
        val modes = HandoverRouteSettingsCodec.decodeModes(body("""{"routes":[{"route":"HOOPING_TO_QC","mode":"DIRECT"}]}"""))
        assertEquals(mapOf(HandoverRouteCode("HOOPING_TO_QC") to HandoverMode.DIRECT), modes)
    }

    @Test
    fun `decodeModes - mode tak dikenal, kode tak sah, kode ganda, dan baris bukan objek ditolak`() {
        listOf(
            """{"routes":[{"route":"A","mode":"HYBRID"}]}""",
            """{"routes":[{"route":"a-b","mode":"DIRECT"}]}""",
            """{"routes":[{"route":"A","mode":"DIRECT"},{"route":"A","mode":"ADMIN_HUB"}]}""",
            """{"routes":["A"]}""",
            """{"bukanRoutes":[]}"""
        ).forEach { json ->
            assertFailsWith<IllegalArgumentException>("seharusnya ditolak: $json") {
                HandoverRouteSettingsCodec.decodeModes(body(json))
            }
        }
    }

    @Test
    fun `rute - round trip tenant non-default termasuk from dan to`() {
        val encoded = HandoverRouteSettingsCodec.encodeRoutes(routes).encode()
        assertEquals(routes, HandoverRouteSettingsCodec.decodeRoutes(body(encoded)))
    }

    @Test
    fun `decodeRoutes - simpul alur tak dikenal dan kode tak sah ditolak, bukan dilewati`() {
        listOf(
            """{"routes":[{"code":"A","label":"A","from":"NGAWUR:x","to":"PROC:y"}]}""",
            """{"routes":[{"code":"huruf kecil","label":"A"}]}""",
            """{"routes":[{"code":"A"}]}"""
        ).forEach { json ->
            assertFailsWith<IllegalArgumentException>("seharusnya ditolak: $json") {
                HandoverRouteSettingsCodec.decodeRoutes(body(json))
            }
        }
    }

    @Test
    fun `pack - template rute round trip, dan pack tanpa template tidak menambah kunci`() {
        val withTemplate = GarmentDomainPack.pack.copy(handoverRouteTemplate = routes)
        val raw = DomainPackCodec.encodeToString(withTemplate)
        assertEquals(routes, DomainPackCodec.decode(raw).handoverRouteTemplate)
        assertFalse("handoverRouteTemplate" in DomainPackCodec.encodeToString(GarmentDomainPack.pack))
    }

    @Test
    fun `pack - kolom aditif, draf lama tanpa template tetap cocok, template lain ditolak`() {
        val shipped = GarmentDomainPack.pack.copy(handoverRouteTemplate = routes)
        assertTrue(GarmentDomainPack.pack.copy(handoverRouteTemplate = emptyList()).matchesShipped(shipped))
        assertTrue(shipped.matchesShipped(shipped))
        assertFalse(GarmentDomainPack.pack.copy(handoverRouteTemplate = routes.take(1)).matchesShipped(shipped))
    }

    @Test
    fun `pack - template dengan kode ganda ditolak`() {
        assertFailsWith<IllegalStateException> {
            GarmentDomainPack.pack.copy(handoverRouteTemplate = listOf(routes.first(), routes.first()))
        }
    }

    @Test
    fun `pack - dokumen dengan template rusak ditolak saat decode`() {
        val raw = DomainPackCodec.encodeToString(GarmentDomainPack.pack).removeSuffix("}") +
            ""","handoverRouteTemplate":[{"code":"huruf kecil","label":"x"}]}"""
        assertFailsWith<IllegalArgumentException> { DomainPackCodec.decode(raw) }
    }
}
