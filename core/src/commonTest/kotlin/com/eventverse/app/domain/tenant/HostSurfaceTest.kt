package com.eventverse.app.domain.tenant

import kotlin.test.Test
import kotlin.test.assertEquals

class HostSurfaceTest {

    private val base = "wemakeerp.com"

    @Test
    fun `parse root and app host should be platform`() {
        assertEquals(HostSurface.Platform, HostSurface.parse("wemakeerp.com", base))
        assertEquals(HostSurface.Platform, HostSurface.parse("app.wemakeerp.com", base))
        assertEquals(HostSurface.Platform, HostSurface.parse("APP.WemakeErp.com:443", base))
    }

    @Test
    fun `parse non garment tenant subdomain should resolve its slug`() {
        assertEquals(HostSurface.Tenant(TenantSlug("bordir-uji")), HostSurface.parse("bordir-uji.wemakeerp.com", base))
        assertEquals(HostSurface.Tenant(TenantSlug("wemade-demo")), HostSurface.parse("wemade-demo.wemakeerp.com:8443", base))
    }

    @Test
    fun `parse reserved or malformed label should fall to platform not a tenant`() {
        assertEquals(HostSurface.Platform, HostSurface.parse("www.wemakeerp.com", base))
        assertEquals(HostSurface.Platform, HostSurface.parse("admin.wemakeerp.com", base))
        assertEquals(HostSurface.Platform, HostSurface.parse("ab.wemakeerp.com", base))
        assertEquals(HostSurface.Platform, HostSurface.parse("-x-.wemakeerp.com", base))
    }

    @Test
    fun `parse without base domain or outside it should be local`() {
        assertEquals(HostSurface.Local, HostSurface.parse("bordir.wemakeerp.com", null))
        assertEquals(HostSurface.Local, HostSurface.parse("bordir.wemakeerp.com", " "))
        assertEquals(HostSurface.Local, HostSurface.parse("localhost:3001", base))
        assertEquals(HostSurface.Local, HostSurface.parse(null, base))
        assertEquals(HostSurface.Local, HostSurface.parse("evilwemakeerp.com", base))
        assertEquals(HostSurface.Local, HostSurface.parse("a.b.wemakeerp.com", base))
    }

    @Test
    fun `tenantOrigin should build https origin`() {
        assertEquals("https://bordir-uji.wemakeerp.com", HostSurface.tenantOrigin(TenantSlug("bordir-uji"), "WeMakeERP.com."))
    }
}
