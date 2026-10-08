package com.eventverse.app.domain.pack

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.fulfillment.TenantHandoverRoutes
import com.eventverse.app.domain.fulfillment.toRouteCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.fulfillment.FulfillmentRouteConfigCodec
import com.eventverse.app.shared.fulfillment.HandoverRouteSettingsCodec
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test paritas Strangler Fig (TRD-FLOW-003 A2, Kontrak 8): tenant konveksi berperilaku identik dengan sebelum
 * rute menjadi data. Mengiterasi `SackRoute.entries`: entri enum baru tanpa padanan template = test gagal.
 */
class GarmentHandoverRoutesParityTest {

    private val tenant = TenantId("wemade-demo")
    private val template = GarmentHandoverRoutes.template

    @Test
    fun `setiap SackRoute punya rute template dengan kode, label, dan urutan identik`() {
        SackRoute.entries.forEach { route ->
            val padanan = template.firstOrNull { it.code == route.toRouteCode() }
            assertTrue(padanan != null, "SackRoute.${route.name} tidak punya padanan di GarmentHandoverRoutes.template")
            assertEquals(route.displayName, padanan.label)
            assertEquals(route.ordinal, padanan.sortOrder)
            assertTrue(padanan.active)
        }
    }

    @Test
    fun `template tidak punya rute di luar enum selama enum masih hidup`() {
        assertEquals(SackRoute.entries.map { it.name }, template.sortedBy { it.sortOrder }.map { it.code.value })
    }

    @Test
    fun `pack garment membawa template, dan tidak ada pack bawaan lain yang meminjamnya`() {
        assertEquals(template, GarmentDomainPack.pack.handoverRouteTemplate)
        DomainPackRegistry.shipped.filter { it.code != GarmentDomainPack.CODE }.forEach {
            assertTrue(it.handoverRouteTemplate.isEmpty(), "Pack ${it.code.value} tidak boleh mewarisi rute garment")
        }
    }

    @Test
    fun `mode efektif tiap rute identik dengan FulfillmentRouteConfig lama`() {
        val explicit = SackRoute.FINISHING_TO_QC_FINISHING
        val old = FulfillmentRouteConfig(tenant, modes = mapOf(explicit to HandoverMode.DIRECT))
        val view = HandoverRouteSettingsView.of(
            TenantHandoverRoutes.resolve(tenant, stored = null, template = template),
            old.modes
        )
        SackRoute.entries.forEach { route ->
            val setting = view.settings.single { it.route.code == route.toRouteCode() }
            assertEquals(old.modeFor(route), setting.mode, "mode ${route.name}")
            assertEquals(route.toRouteCode() in old.modes, setting.isExplicit, "isExplicit ${route.name}")
        }
        assertEquals(old.hasAdminHubRoute, view.hasAdminHubRoute)
    }

    @Test
    fun `JSON kontrak baru memuat semua kunci JSON lama dengan nilai sama untuk tenant konveksi`() {
        val old = FulfillmentRouteConfig(tenant, modes = mapOf(SackRoute.QC_RAJUT_TO_FINISHING to HandoverMode.DIRECT))
        val new = HandoverRouteSettingsView.of(
            TenantHandoverRoutes.resolve(tenant, null, template),
            old.modes
        )
        val oldJson = JsonParser.parseObject(FulfillmentRouteConfigCodec.encode(old).encode())
        val newJson = JsonParser.parseObject(HandoverRouteSettingsCodec.encode(new).encode())
        assertEquals(oldJson.string("tenantId"), newJson.string("tenantId"))
        assertEquals(oldJson.boolean("hasAdminHubRoute"), newJson.boolean("hasAdminHubRoute"))
        val oldRows = oldJson.objectArray("routes")
        val newRows = newJson.objectArray("routes")
        assertEquals(oldRows.size, newRows.size)
        oldRows.forEach { o ->
            val n = newRows.single { it.string("route") == o.string("route") }
            listOf("routeLabel", "mode", "modeLabel").forEach { k -> assertEquals(o.string(k), n.string(k), k) }
            assertEquals(o.boolean("isExplicit"), n.boolean("isExplicit"), "isExplicit ${o.string("route")}")
        }
    }
}
