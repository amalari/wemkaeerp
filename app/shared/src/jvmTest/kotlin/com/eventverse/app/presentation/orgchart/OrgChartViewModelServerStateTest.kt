package com.eventverse.app.presentation.orgchart

import com.eventverse.app.infrastructure.api.FixedSessionTokenProvider
import com.eventverse.app.infrastructure.api.OrgChartApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TRD-PLAT-010 T1 dengan klien ber-mock engine (tanpa server, tanpa DB). Fixture non-garment
 * (klinik) membuktikan bahwa tenant kosong tidak pernah melihat sampel konveksi.
 */
class OrgChartViewModelServerStateTest {

    private val deptJson =
        """{"id":"dept-poli","code":"POLI","displayName":"Poli Umum","shortName":"Poli","colorHex":4281545523,"isCustom":true}"""
    private val empJson =
        """{"id":"emp-dokter","name":"dr. Sari","email":"sari@klinik.id","level":"EXECUTIVE","roleTitle":"Direktur Klinik","department":null}"""

    /** Server palsu: jawaban dapat diganti per tes; mencatat setiap permintaan. */
    private class FakeServer {
        var departments: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "[]"
        var employees: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "[]"
        var restoreDepartments: Pair<HttpStatusCode, String> = HttpStatusCode.OK to ""
        var restoreEmployees: Pair<HttpStatusCode, String> = HttpStatusCode.OK to ""
        val requests = mutableListOf<String>()

        fun client(): OrgChartApiClient = OrgChartApiClient(
            httpClient = HttpClient(
                MockEngine { request ->
                    val path = request.url.encodedPath
                    requests += "${request.method.value} $path"
                    val (status, body) = when {
                        request.method == HttpMethod.Post && path.endsWith("/departments/restore-presets") -> restoreDepartments
                        request.method == HttpMethod.Post && path.endsWith("/employees/restore-presets") -> restoreEmployees
                        path.endsWith("/departments") -> departments
                        path.endsWith("/employees") -> employees
                        else -> HttpStatusCode.NotFound to ""
                    }
                    respond(body, status, headersOf("Content-Type", "application/json"))
                }
            ),
            tokenProvider = FixedSessionTokenProvider("t")
        )
    }

    private fun viewModel(server: FakeServer) = OrgChartViewModel(
        tenantSlug = "klinik-uji",
        apiClient = server.client(),
        coroutineScope = CoroutineScope(Dispatchers.Default)
    )

    private suspend fun OrgChartViewModel.awaitResolved(): OrgChartUiState =
        withTimeout(5_000) { uiState.first { it.loadState !is OrgChartLoadState.Loading && !it.isRestoringPresets } }

    @Test
    fun emptyResponse_onNonGarmentTenant_isEmptyWithNoSample() = runBlocking<Unit> {
        val vm = viewModel(FakeServer())
        // Keadaan awal sebelum server menjawab: Loading, bukan sampel.
        val first = vm.uiState.value
        assertTrue(first.employees.isEmpty() && first.departments.isEmpty())
        val state = vm.awaitResolved()
        assertEquals(OrgChartLoadState.Empty, state.loadState)
        assertTrue(state.departments.isEmpty())
        assertTrue(state.employees.isEmpty())
    }

    @Test
    fun networkFailure_isFailedAndShowsNoSample() = runBlocking<Unit> {
        val server = FakeServer().apply { departments = HttpStatusCode.InternalServerError to "boom" }
        val state = viewModel(server).awaitResolved()
        assertIs<OrgChartLoadState.Failed>(state.loadState)
        assertTrue(state.employees.isEmpty() && state.departments.isEmpty())
    }

    @Test
    fun unreadableBody_isFailedNotEmpty() = runBlocking<Unit> {
        val server = FakeServer().apply { departments = HttpStatusCode.OK to "<html>login</html>" }
        assertIs<OrgChartLoadState.Failed>(viewModel(server).awaitResolved().loadState)
    }

    @Test
    fun departmentsOkButEmployeesFail_isFailedNotHalfLoaded() = runBlocking<Unit> {
        val server = FakeServer().apply {
            departments = HttpStatusCode.OK to "[$deptJson]"
            employees = HttpStatusCode.Forbidden to "no"
        }
        val state = viewModel(server).awaitResolved()
        assertIs<OrgChartLoadState.Failed>(state.loadState)
    }

    @Test
    fun liveDataWinsFully_evenWhenOnlyDepartmentsExist() = runBlocking<Unit> {
        val server = FakeServer().apply { departments = HttpStatusCode.OK to "[$deptJson]" }
        val state = viewModel(server).awaitResolved()
        assertIs<OrgChartLoadState.Loaded>(state.loadState)
        assertEquals(listOf("Poli Umum"), state.departments.map { it.displayName })
        assertTrue(state.employees.isEmpty())
        assertEquals("Poli Umum", state.selectedDepartment?.displayName)
    }

    @Test
    fun liveDataLoaded_withEmployees() = runBlocking<Unit> {
        val server = FakeServer().apply {
            departments = HttpStatusCode.OK to "[$deptJson]"
            employees = HttpStatusCode.OK to "[$empJson]"
        }
        val state = viewModel(server).awaitResolved()
        assertIs<OrgChartLoadState.Loaded>(state.loadState)
        assertEquals(listOf("emp-dokter"), state.employees.map { it.id.value })
    }

    @Test
    fun loadSample_success_waitsForServerThenReloads() = runBlocking<Unit> {
        val server = FakeServer()
        val vm = viewModel(server)
        assertEquals(OrgChartLoadState.Empty, vm.awaitResolved().loadState)

        // Server menjawab restore lalu daftar berisi: klien membaca ulang, tidak merakit sampel sendiri.
        server.departments = HttpStatusCode.OK to "[$deptJson]"
        server.employees = HttpStatusCode.OK to "[$empJson]"
        vm.onEvent(OrgChartUiEvent.RestoreDefaultPresets)
        val state = vm.awaitResolved()

        assertIs<OrgChartLoadState.Loaded>(state.loadState)
        assertEquals(listOf("Poli Umum"), state.departments.map { it.displayName })
        assertTrue(server.requests.contains("POST /api/tenant/departments/restore-presets"))
        assertTrue(server.requests.contains("POST /api/tenant/employees/restore-presets"))
    }

    @Test
    fun loadSample_conflict409_showsServerMessageAndNoSample() = runBlocking<Unit> {
        val server = FakeServer().apply {
            restoreDepartments = HttpStatusCode.Conflict to "Jenis usaha ini tidak punya contoh."
        }
        val vm = viewModel(server)
        vm.awaitResolved()
        vm.onEvent(OrgChartUiEvent.RestoreDefaultPresets)
        val state = withTimeout(5_000) { vm.uiState.first { it.toastMessage != null } }

        assertEquals("Jenis usaha ini tidak punya contoh.", state.toastMessage)
        assertEquals(OrgChartLoadState.Empty, state.loadState)
        assertTrue(state.departments.isEmpty() && state.employees.isEmpty())
        assertFalse(server.requests.contains("POST /api/tenant/employees/restore-presets"))
    }

    @Test
    fun loadSample_forbidden403_showsMessageAndStateUnchanged() = runBlocking<Unit> {
        val server = FakeServer().apply { restoreDepartments = HttpStatusCode.Forbidden to "" }
        val vm = viewModel(server)
        vm.awaitResolved()
        vm.onEvent(OrgChartUiEvent.RestoreDefaultPresets)
        val state = withTimeout(5_000) { vm.uiState.first { it.toastMessage != null } }

        assertEquals("Anda tidak berwenang memuat contoh struktur organisasi.", state.toastMessage)
        assertEquals(OrgChartLoadState.Empty, state.loadState)
        assertTrue(state.departments.isEmpty() && state.employees.isEmpty())
    }

    @Test
    fun retryAfterFailure_transitionsLoadingThenEmptyThenLoaded() = runBlocking<Unit> {
        val server = FakeServer().apply { departments = HttpStatusCode.InternalServerError to "x" }
        val vm = viewModel(server)
        assertIs<OrgChartLoadState.Failed>(vm.awaitResolved().loadState)

        server.departments = HttpStatusCode.OK to "[]"
        vm.onEvent(OrgChartUiEvent.Reload)
        assertEquals(OrgChartLoadState.Loading, vm.uiState.value.loadState)
        assertEquals(OrgChartLoadState.Empty, vm.awaitResolved().loadState)

        server.departments = HttpStatusCode.OK to "[$deptJson]"
        vm.onEvent(OrgChartUiEvent.Reload)
        assertIs<OrgChartLoadState.Loaded>(vm.awaitResolved().loadState)
    }
}
