package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.FixedSessionTokenProvider
import com.eventverse.app.infrastructure.api.RbacApiClient
import com.eventverse.app.presentation.common.FriendlyErrors
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Temuan cek visual /rbac: chip "9 Total Karyawan" pada tenant tanpa jabatan. Akarnya data nyata (seed V98 memberi
 * tiap tenant demo 9 karyawan, jabatan hanya untuk wemade-demo), BUKAN kebocoran. Tes ini mengunci bahwa klien
 * memisahkan tenant per slug (tidak ada state yang terbawa) dan keadaan "karyawan ada, jabatan nol" terwakili jujur.
 */
class RbacTenantStateTest {
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private fun emp(i: Int, tenant: String) =
        """{"id":"emp-$i","name":"N$i","email":"n$i@x.id","level":"STAFF","roleTitle":"Staf","tenantId":"$tenant"}"""

    private val roleJson =
        """{"id":"role-o","tenantId":"ten-a","name":"Owner","description":"d","isSystemDefault":true,"userCount":1,"modulePermissions":{}}"""

    /** Server palsu yang membedakan tenant lewat X-Tenant-Slug seperti server asli. */
    private fun client() = RbacApiClient(
        httpClient = HttpClient(MockEngine { request ->
            val slug = request.headers["X-Tenant-Slug"]
            val body = when (request.url.encodedPath) {
                "/api/tenant/roles" -> if (slug == "tenant-a") "[$roleJson]" else "[]"
                "/api/tenant/employees" ->
                    if (slug == "tenant-a") "[${emp(1, "ten-a")}]" else "[${(1..9).joinToString(",") { emp(it, "ten-b") }}]"
                "/api/tenant/module-assignments" -> "{}"
                else -> "[]"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        tokenProvider = FixedSessionTokenProvider("t")
    )

    private fun vm(slug: String) = DynamicRbacViewModel(
        tenantId = TenantId("ten-$slug"),
        tenantSlug = slug,
        apiClient = client(),
        scope = scope,
        policyRepository = RbacAccessPolicyRepository(apiClientProvider = { null }, scope = scope)
    )

    private fun DynamicRbacViewModel.settled(): DynamicRbacUiState = runBlocking {
        withTimeout(10_000) { uiState.first { it.loadState != RbacLoadState.Loading } }
    }

    @Test
    fun `dua tenant berurutan - jumlah karyawan dan jabatan tidak terbawa antar tenant`() {
        val a = vm("tenant-a").settled()
        val b = vm("tenant-b").settled()
        assertEquals(1, a.totalUsers)
        assertEquals(1, a.roles.size)
        assertEquals(9, b.totalUsers)
        assertTrue(b.roles.isEmpty())
        assertEquals(1, vm("tenant-a").settled().totalUsers) // kembali ke A: tetap milik A
    }

    @Test
    fun `tenant tanpa jabatan tetapi ada karyawan - Per Jabatan menampilkan NoRoles`() {
        val b = vm("tenant-b").settled()
        assertEquals(RbacRoleListState.NoRoles, RbacRoleListState.of(b.roles, ""))
    }

    @Test
    fun `daftar jabatan - kosong beda dari tak cocok pencarian`() {
        val role = (vm("tenant-a").settled().roles).first()
        assertEquals(RbacRoleListState.NoRoles, RbacRoleListState.of(emptyList<CustomRole>(), "x"))
        assertEquals(RbacRoleListState.NoMatch, RbacRoleListState.of(listOf(role), "zzz-tak-ada"))
        assertIs<RbacRoleListState.Showing>(RbacRoleListState.of(listOf(role), "own"))
        assertIs<RbacRoleListState.Showing>(RbacRoleListState.of(listOf(role), ""))
    }

    @Test
    fun `galat proxy mentah - pesan Failed ramah bukan teks proxy`() {
        val raw = IllegalStateException("Gagal memuat jabatan (HTTP 502): Error occurred while trying to proxy: localhost:3001/api")
        val state = RbacLoadState.from(Result.failure(raw), Result.success(emptyList()), Result.success(emptyMap()))
        assertEquals(RbacLoadState.Failed(FriendlyErrors.UNREACHABLE), state)
    }

    @Test
    fun `galat bisnis server - pesan aslinya dipertahankan`() {
        val state = RbacLoadState.from(
            Result.failure(IllegalStateException("Gagal memuat jabatan (HTTP 403): akses ditolak")),
            Result.success(emptyList()), Result.success(emptyMap())
        )
        assertEquals(RbacLoadState.Failed("Gagal memuat jabatan (HTTP 403): akses ditolak"), state)
    }
}
