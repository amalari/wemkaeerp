package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.FixedSessionTokenProvider
import com.eventverse.app.infrastructure.api.RbacApiClient
import com.eventverse.app.presentation.pack.ActiveTenantPack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRD-PLAT-010 T3. Tenant uji: klinik (non-garment), sengaja dengan `ActiveTenantPack` masih garment saat VM
 * dibuat (keadaan balapan yang dibuktikan menghasilkan "6 jabatan" sebelum perubahan ini).
 */
class DynamicRbacViewModelTest {
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val slug = "klinik-uji"

    private val ownerJson = """{"id":"role-owner","tenantId":"ten-klinik-uji","name":"Owner Klinik","description":"d","isSystemDefault":true,"userCount":5,"modulePermissions":{}}"""
    private val deptJson = """{"id":"dept-poli","code":"POLI","displayName":"Poli Umum","shortName":"Poli","colorHex":4280572883,"isCustom":true,"tenantId":"ten-klinik-uji"}"""
    private val empJson = """{"id":"emp-1","name":"Dr. Rina","email":"rina@klinik.id","level":"HEAD_OF_DEPARTMENT","roleTitle":"Dokter","tenantId":"ten-klinik-uji"}"""

    private class Server(
        var roles: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "[]",
        var depts: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "[]",
        var employees: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "[]",
        var assignments: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "{}"
    ) {
        val requests = mutableListOf<HttpRequestData>()
        fun client() = RbacApiClient(
            httpClient = HttpClient(MockEngine { request ->
                requests += request
                val (status, body) = when (request.url.encodedPath) {
                    "/api/tenant/roles" -> roles
                    "/api/tenant/departments" -> depts
                    "/api/tenant/employees" -> employees
                    "/api/tenant/module-assignments" -> assignments
                    else -> HttpStatusCode.NotFound to "{}"
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            tokenProvider = FixedSessionTokenProvider("t")
        )
    }

    /** Menunggu sampai pemuatan selesai (MockEngine berjalan di dispatcher lain). */
    private fun DynamicRbacViewModel.settled(): DynamicRbacUiState = runBlocking {
        withTimeout(10_000) { uiState.first { it.loadState != RbacLoadState.Loading } }
    }

    private fun vm(server: Server) = DynamicRbacViewModel(
        tenantId = TenantId("ten-klinik-uji"),
        tenantSlug = slug,
        apiClient = server.client(),
        scope = scope,
        policyRepository = RbacAccessPolicyRepository(apiClientProvider = { null }, scope = scope)
    )

    @Test
    fun `init - memanggil klien dengan slug yang diberikan pada setiap permintaan`() {
        val server = Server()
        vm(server).settled()
        assertTrue(server.requests.isNotEmpty())
        server.requests.forEach { assertEquals(slug, it.headers["X-Tenant-Slug"]) }
    }

    @Test
    fun `server kosong pada tenant non-garment - Empty tanpa jabatan atau divisi garment`() {
        check(ActiveTenantPack.current.code == GarmentDomainPack.CODE) // balapan: pack masih garment
        val state = vm(Server()).settled()
        assertEquals(RbacLoadState.Empty, state.loadState)
        assertTrue(state.roles.isEmpty())
        assertTrue(state.departments.isEmpty())
        assertTrue(state.moduleAssignments.isEmpty())
    }

    @Test
    fun `server memberi satu jabatan Owner - hanya itu yang tampil walau pack aktif masih garment`() {
        val state = vm(Server(roles = HttpStatusCode.OK to "[$ownerJson]")).settled()
        assertEquals(RbacLoadState.Loaded, state.loadState)
        assertEquals(listOf("Owner Klinik"), state.roles.map { it.name })
        assertEquals("role-owner", state.selectedRoleId)
    }

    @Test
    fun `galat server pada jabatan - Failed tanpa data contoh`() {
        val state = vm(Server(roles = HttpStatusCode.InternalServerError to "boom")).settled()
        val failed = state.loadState as RbacLoadState.Failed
        assertTrue(failed.message.isNotBlank())
        assertTrue(state.roles.isEmpty())
        assertTrue(state.departments.isEmpty())
    }

    @Test
    fun `403 pada penugasan - Failed bukan setengah data`() {
        val server = Server(
            roles = HttpStatusCode.OK to "[$ownerJson]",
            assignments = HttpStatusCode.Forbidden to "{}"
        )
        val state = vm(server).settled()
        assertTrue(state.loadState is RbacLoadState.Failed)
        assertTrue(state.roles.isEmpty())
    }

    @Test
    fun `Loaded memakai data server penuh dan total karyawan dari karyawan nyata`() {
        val server = Server(
            roles = HttpStatusCode.OK to "[$ownerJson]",
            depts = HttpStatusCode.OK to "[$deptJson]",
            employees = HttpStatusCode.OK to "[$empJson]"
        )
        val state = vm(server).settled()
        assertEquals(RbacLoadState.Loaded, state.loadState)
        assertEquals(listOf("Poli Umum"), state.departments.map { it.displayName })
        assertEquals(1, state.totalUsers) // bukan userCount preset jabatan (5)
    }

    @Test
    fun `karyawan tak terbaca - total karyawan null (chip disembunyikan) dan state tetap Loaded`() {
        val server = Server(
            roles = HttpStatusCode.OK to "[$ownerJson]",
            employees = HttpStatusCode.Forbidden to "{}"
        )
        val state = vm(server).settled()
        assertEquals(RbacLoadState.Loaded, state.loadState)
        assertNull(state.totalUsers)
    }

    @Test
    fun `Reload setelah Failed memuat ulang dari server`() {
        val server = Server(roles = HttpStatusCode.InternalServerError to "boom")
        val viewModel = vm(server)
        assertTrue(viewModel.settled().loadState is RbacLoadState.Failed)
        server.roles = HttpStatusCode.OK to "[$ownerJson]"
        viewModel.onEvent(DynamicRbacUiEvent.Reload)
        val state = viewModel.settled()
        assertEquals(RbacLoadState.Loaded, state.loadState)
        assertEquals(1, state.roles.size)
    }

    @Test
    fun `tanpa klien - Empty tanpa data`() {
        val viewModel = DynamicRbacViewModel(
            tenantId = TenantId("ten-klinik-uji"),
            tenantSlug = slug,
            apiClient = null,
            scope = scope,
            policyRepository = RbacAccessPolicyRepository(apiClientProvider = { null }, scope = scope)
        )
        assertEquals(RbacLoadState.Empty, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.roles.isEmpty())
    }
}
