package com.eventverse.app.presentation.tenant

import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import kotlin.test.*

class TenantSessionStorageTest {

    @Test
    fun session_storage_updates_current_session_flow() {
        val storage = InMemoryTenantSessionStorage()
        assertNull(storage.currentSession.value)

        val session = TenantSession(
            tenantId = TenantId("ten-1"),
            slug = TenantSlug("berkah-konveksi"),
            name = "Berkah Konveksi",
            tier = SubscriptionTier.PRO
        )

        storage.setSession(session)
        assertEquals(session, storage.currentSession.value)

        storage.clearSession()
        assertNull(storage.currentSession.value)
    }

    @Test
    fun endpoint_resolver_generates_production_subdomain_url() {
        val resolver = TenantApiEndpointResolver(rootDomain = "wemade.id", isDevEnvironment = false)
        val url = resolver.resolveBaseUrl(TenantSlug("garmen-jaya"))
        assertEquals("https://garmen-jaya.wemade.id", url)
    }

    @Test
    fun endpoint_resolver_generates_dev_localhost_url() {
        val resolver = TenantApiEndpointResolver(devPort = 8080, isDevEnvironment = true)
        val url = resolver.resolveBaseUrl(TenantSlug("garmen-jaya"))
        assertEquals("http://localhost:8080", url)
    }

    @Test
    fun endpoint_resolver_builds_correct_headers() {
        val resolver = TenantApiEndpointResolver()
        val session = TenantSession(
            tenantId = TenantId("ten-abc"),
            slug = TenantSlug("konveksi-demo"),
            name = "Demo Konveksi",
            tier = SubscriptionTier.STARTER
        )

        val headers = resolver.buildTenantHeaders(session)
        assertEquals("konveksi-demo", headers["X-Tenant-Slug"])
        assertEquals("ten-abc", headers["X-Tenant-ID"])
    }
}
