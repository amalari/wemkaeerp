package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.FixedSessionTokenProvider
import com.eventverse.app.infrastructure.api.RbacApiClient
import com.eventverse.app.shared.rbac.AccessDecisionCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Persona tanpa akses Bagan Organisasi tidak boleh memicu GET divisi/karyawan (gerbang server menolaknya dengan 403
 * yang sia-sia, mis. saat membuka Factory Flow). Slug non-default `bordir-uji`.
 */
class RbacAccessPolicyOrgGateTest {
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private fun decision(level: AccessLevel) = AccessDecision(
        config = ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA),
        source = if (level == AccessLevel.NONE) AccessSource.NONE else AccessSource.ROLE,
        fromRole = ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA),
        fromDepartment = ModuleAccessConfig()
    )

    private fun load(orgLevel: AccessLevel): List<String> {
        val paths = mutableListOf<String>()
        val access = AccessDecisionCodec.encode(
            mapOf(
                GarmentModules.ORG_CHART to decision(orgLevel),
                GarmentModules.DYNAMIC_RBAC to decision(AccessLevel.NONE)
            )
        ).encode()
        val client = RbacApiClient(
            httpClient = HttpClient(MockEngine { request ->
                paths += request.url.encodedPath
                val body = if (request.url.encodedPath == "/api/tenant/me/access") access else "[]"
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            tokenProvider = FixedSessionTokenProvider("t")
        )
        val repo = RbacAccessPolicyRepository(apiClientProvider = { client }, scope = scope)
        repo.load(TenantId("ten-bordir-uji"), "bordir-uji")
        runBlocking { withTimeout(10_000) { repo.isLoading.first { !it } } }
        return paths
    }

    @Test
    fun `server menutup Bagan Organisasi - divisi dan karyawan tidak diminta`() {
        val paths = load(AccessLevel.NONE)
        assertTrue("/api/tenant/me/access" in paths)
        assertFalse("/api/tenant/departments" in paths, "tanpa GET divisi: $paths")
        assertFalse("/api/tenant/employees" in paths, "tanpa GET karyawan: $paths")
    }

    @Test
    fun `server membuka Bagan Organisasi - divisi dan karyawan tetap dimuat`() {
        val paths = load(AccessLevel.VIEW)
        assertEquals(1, paths.count { it == "/api/tenant/departments" })
        assertEquals(1, paths.count { it == "/api/tenant/employees" })
    }
}
