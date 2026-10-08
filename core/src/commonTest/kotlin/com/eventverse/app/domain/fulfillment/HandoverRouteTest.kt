package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandoverRouteTest {

    private val tenant = TenantId("bordir-uji")

    /** Template non-default: rute bordir, bukan rute rajut (Kontrak 6). */
    private val bordirTemplate = listOf(
        HandoverRoute(HandoverRouteCode("DIGITIZING_TO_HOOPING"), "Digitizing ke Hooping", sortOrder = 1),
        HandoverRoute(
            HandoverRouteCode("HOOPING_TO_QC"), "Hooping ke QC",
            from = FlowNodeRef.Process("hooping"), to = FlowNodeRef.Process("qc-bordir"), sortOrder = 0
        )
    )

    @Test
    fun `kode rute - huruf kecil, kosong, null, dan terlalu panjang - ditolak`() {
        listOf("qc_rajut", "", " ", "1_RUTE", "RUTE-A", "A".repeat(41)).forEach {
            assertTrue(HandoverRouteCode.parse(it).isFailure, "seharusnya ditolak: '$it'")
        }
        assertTrue(HandoverRouteCode.parse(null).isFailure)
        assertEquals("A".repeat(40), HandoverRouteCode.parse("A".repeat(40)).getOrThrow().value)
    }

    @Test
    fun `jembatan - setiap SackRoute menjadi kode sah dengan nilai identik nama enum`() {
        SackRoute.entries.forEach { route ->
            assertEquals(route.name, route.toRouteCode().value)
        }
    }

    @Test
    fun `rute - from tanpa to, atau from sama dengan to - ditolak`() {
        val node = FlowNodeRef.Process("hooping")
        assertFailsWith<IllegalArgumentException> { HandoverRoute(HandoverRouteCode("A"), "A", from = node) }
        assertFailsWith<IllegalArgumentException> { HandoverRoute(HandoverRouteCode("A"), "A", from = node, to = node) }
        assertFailsWith<IllegalArgumentException> { HandoverRoute(HandoverRouteCode("A"), " ") }
    }

    @Test
    fun `daftar rute tenant - kode ganda ditolak`() {
        val r = bordirTemplate.first()
        assertFailsWith<IllegalStateException> { TenantHandoverRoutes(tenant, listOf(r, r)) }
    }

    @Test
    fun `rute aktif - nonaktif disaring dan diurutkan menurut sortOrder`() {
        val routes = TenantHandoverRoutes(
            tenant,
            bordirTemplate + HandoverRoute(HandoverRouteCode("LAMA"), "Rute lama", active = false)
        )
        assertEquals(listOf("HOOPING_TO_QC", "DIGITIZING_TO_HOOPING"), routes.active.map { it.code.value })
    }

    @Test
    fun `resolve - tanpa salinan tersimpan memakai template tanpa menyimpan apa pun`() {
        assertEquals(bordirTemplate, TenantHandoverRoutes.resolve(tenant, stored = null, template = bordirTemplate).routes)
        assertEquals(bordirTemplate, TenantHandoverRoutes.resolve(tenant, TenantHandoverRoutes(tenant), bordirTemplate).routes)
    }

    @Test
    fun `resolve - salinan tersimpan menang atas template`() {
        val own = TenantHandoverRoutes(tenant, listOf(HandoverRoute(HandoverRouteCode("RUTE_SENDIRI"), "Rute sendiri")))
        assertEquals(own, TenantHandoverRoutes.resolve(tenant, own, bordirTemplate))
    }

    @Test
    fun `tampilan pengaturan - rute dikenal tanpa entri jatuh ke ADMIN_HUB dan tidak eksplisit`() {
        val view = HandoverRouteSettingsView.of(TenantHandoverRoutes(tenant, bordirTemplate), emptyMap())
        assertTrue(view.settings.all { it.mode == HandoverMode.ADMIN_HUB && !it.isExplicit })
        assertTrue(view.hasAdminHubRoute)
    }

    @Test
    fun `tampilan pengaturan - mode untuk kode yang tidak dikenal tenant ditolak, bukan diabaikan`() {
        assertFailsWith<IllegalArgumentException> {
            HandoverRouteSettingsView.of(
                TenantHandoverRoutes(tenant, bordirTemplate),
                mapOf(SackRoute.QC_RAJUT_TO_FINISHING.toRouteCode() to HandoverMode.DIRECT)
            )
        }
    }

    @Test
    fun `tampilan pengaturan - rute nonaktif tidak dihitung pada hasAdminHubRoute`() {
        val routes = TenantHandoverRoutes(tenant, listOf(HandoverRoute(HandoverRouteCode("A"), "A", active = false)))
        assertFalse(HandoverRouteSettingsView.of(routes, emptyMap()).hasAdminHubRoute)
    }
}
