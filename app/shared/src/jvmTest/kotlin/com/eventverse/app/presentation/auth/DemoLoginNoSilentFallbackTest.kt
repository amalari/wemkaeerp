package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.tenant.InMemoryTenantSessionStorage
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.AuthApiClient
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import com.eventverse.app.presentation.common.FriendlyErrors
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Login demo/persona tidak boleh punya fallback senyap: penolakan server (403/404) dan jaringan
 * mati menjadi pesan di UI, TANPA sesi dan tanpa tulis storage. Slug non-default (`bordir-uji`).
 */
class DemoLoginNoSilentFallbackTest {

    private val sessionJson = """
        {"token":"server.signed.jwt",
         "user":{"id":"usr-1","tenantId":"ten-bordir-uji","username":"owner",
                 "email":"o@bordir-uji.id","role":"TENANT_ADMIN"},
         "tenantSlug":"bordir-uji"}
    """.trimIndent()

    private val forbiddenBody = "Tenant 'bordir-uji' bukan tenant demo; login demo ditolak."

    private val persona = TestingPersona(
        userId = "usr-persona-1", name = "Budi", tenantId = TenantId("ten-bordir-uji"),
        tenantSlug = "bordir-uji", departmentId = null, departmentName = "Tanpa Divisi",
        roleId = null, roleTitle = "Staf"
    )

    @BeforeTest fun setUp() = PlatformLocalStorage.clear()
    @AfterTest fun tearDown() = PlatformLocalStorage.clear()

    private class Fixture(val vm: AuthViewModel, val requests: MutableList<String>)

    private val sessionStorage = InMemoryTenantSessionStorage()

    private fun fixture(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Fixture {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.toString()
            handler(request)
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val vm = AuthViewModel(
            scope = scope,
            sessionStorage = sessionStorage,
            authApiClient = AuthApiClient(httpClient = HttpClient(engine)),
            // Repo kebijakan terisolasi: instance bersama memakai Dispatchers.Main (tak ada di test).
            policyRepository = RbacAccessPolicyRepository(apiClientProvider = { null }, scope = scope)
        )
        return Fixture(vm, requests)
    }

    @Test
    fun logout_afterServerDemoLogin_clearsSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.OK, sessionJson) }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoLogin)
        f.vm.settled()
        assertNotNull(sessionStorage.currentSession.value)

        f.vm.onEvent(LoginUiEvent.Logout)
        assertNull(f.vm.uiState.value.authenticatedSession)
        assertNull(sessionStorage.currentSession.value)
    }

    @Test
    fun demoSuperadminLogin_success_usesServerRoleAndPlatformAdminTenantName() = runBlocking<Unit> {
        val superJson = sessionJson.replace("TENANT_ADMIN", "PLATFORM_SUPERADMIN").replace("\"owner\"", "\"superadmin_apps\"")
        val f = fixture { reply(HttpStatusCode.OK, superJson) }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
        val session = assertNotNull(f.vm.settled().authenticatedSession)
        assertEquals(Role.PLATFORM_SUPERADMIN, session.user.role)
        assertEquals("superadmin_apps", session.user.username.value)
        assertEquals("WeMade Platform Admin", sessionStorage.currentSession.value?.name)
    }

    private fun MockRequestHandleScope.reply(status: HttpStatusCode, body: String = "") =
        respond(body, status, headersOf(HttpHeaders.ContentType, "text/plain"))

    private suspend fun AuthViewModel.settled(): LoginUiState = withTimeout(5_000) {
        uiState.first { !it.isLoading && (it.authenticatedSession != null || it.errorMessage != null) }
    }

    private fun assertNoSession(state: LoginUiState) {
        assertNull(state.authenticatedSession)
        assertNull(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
    }

    @Test
    fun demoLogin_serverForbids403_showsServerMessageAndNoSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.Forbidden, forbiddenBody) }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoLogin)
        val state = f.vm.settled()
        assertEquals(forbiddenBody, state.errorMessage)
        assertNoSession(state)
    }

    @Test
    fun demoLogin_demoDisabled404_showsDemoOffMessageAndNoSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.NotFound) }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoLogin)
        val state = f.vm.settled()
        assertEquals("Login demo dimatikan di server ini", state.errorMessage)
        assertNoSession(state)
    }

    @Test
    fun demoSuperadminLogin_serverForbids_noSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.Forbidden, "ditolak") }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
        val state = f.vm.settled()
        assertEquals("ditolak", state.errorMessage)
        assertNoSession(state)
    }

    @Test
    fun demoLogin_blankSlug_sendsNoRequestAndAsksForFactoryCode() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.OK, sessionJson) }
        delay(200) // biarkan init (resolveHostSurface) selesai sebelum menghitung request
        f.requests.clear()
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("   "))
        f.vm.onEvent(LoginUiEvent.SubmitDemoLogin)
        val state = f.vm.uiState.value
        assertEquals(AuthViewModel.MSG_SLUG_REQUIRED, state.errorMessage)
        delay(200)
        assertTrue(f.requests.none { it.contains("/auth/demo") }, "Request: ${f.requests}")
        assertNoSession(state)
    }

    @Test
    fun demoLogin_networkDown_showsUnreachableAndNoOfflineSession() = runBlocking<Unit> {
        val f = fixture { throw IOException("connection refused") }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoLogin)
        val state = f.vm.settled()
        assertEquals(FriendlyErrors.UNREACHABLE, state.errorMessage)
        assertNoSession(state)
    }

    @Test
    fun demoLogin_success_storesServerSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.OK, sessionJson) }
        f.vm.onEvent(LoginUiEvent.UpdateTenantSlug("bordir-uji"))
        f.vm.onEvent(LoginUiEvent.SubmitDemoLogin)
        val state = f.vm.settled()
        val session = assertNotNull(state.authenticatedSession, "Error: ${state.errorMessage}")
        assertEquals("server.signed.jwt", session.token.value)
        assertEquals("bordir-uji", session.tenantSlug)
        assertNotNull(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
    }

    @Test
    fun personaLogin_serverForbids403_showsMessageAndNoSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.Forbidden, forbiddenBody) }
        f.vm.onEvent(LoginUiEvent.SubmitPersonaLogin(persona))
        val state = f.vm.settled()
        assertEquals(forbiddenBody, state.errorMessage)
        assertNoSession(state)
    }

    @Test
    fun personaLogin_demoDisabled404_showsDemoOffMessage() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.NotFound) }
        f.vm.onEvent(LoginUiEvent.SubmitPersonaLogin(persona))
        val state = f.vm.settled()
        assertEquals("Login demo dimatikan di server ini", state.errorMessage)
        assertNoSession(state)
    }

    @Test
    fun personaLogin_networkDown_showsUnreachableAndNoSession() = runBlocking<Unit> {
        val f = fixture { throw IOException("connection refused") }
        f.vm.onEvent(LoginUiEvent.SubmitPersonaLogin(persona))
        val state = f.vm.settled()
        assertEquals(FriendlyErrors.UNREACHABLE, state.errorMessage)
        assertNoSession(state)
    }

    // --- Pemulihan sesi tersimpan: tanpa slug tidak boleh menebak wemade-demo ---

    private fun saved(role: String, slug: String?): String {
        val slugPart = slug?.let { ""","tenantSlug":"$it"""" } ?: ""
        return """{"token":"saved.jwt","user":{"id":"usr-1","tenantId":"ten-bordir-uji","username":"owner",""" +
            """"email":"o@bordir-uji.id","role":"$role"}$slugPart}"""
    }

    @Test
    fun restore_savedTenantSessionWithoutSlug_isRejectedAndStorageCleared() = runBlocking<Unit> {
        PlatformLocalStorage.setItem(AuthApiClient.SESSION_STORAGE_KEY, saved("TENANT_ADMIN", null))
        val f = fixture { reply(HttpStatusCode.OK, sessionJson) }
        val state = f.vm.uiState.value
        assertNull(state.authenticatedSession)
        assertEquals(AuthViewModel.MSG_SESSION_NO_TENANT, state.errorMessage)
        assertNull(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
        assertNull(sessionStorage.currentSession.value)
        assertNull(f.vm.uiState.value.authenticatedSession?.tenantSlug) // tidak pernah menjadi wemade-demo
    }

    @Test
    fun restore_savedSessionWithNonDefaultSlug_keepsThatTenant() = runBlocking<Unit> {
        PlatformLocalStorage.setItem(AuthApiClient.SESSION_STORAGE_KEY, saved("TENANT_ADMIN", "bordir-uji"))
        val f = fixture { reply(HttpStatusCode.OK, sessionJson) }
        assertEquals("bordir-uji", f.vm.uiState.value.authenticatedSession?.tenantSlug)
        assertEquals("bordir-uji", sessionStorage.currentSession.value?.slug?.value)
        delay(300) // verifikasi sesi di latar menulis storage; tunggu agar tidak bocor ke test berikutnya
    }

    @Test
    fun restore_savedSuperadminWithoutSlug_staysSignedInWithoutTenant() = runBlocking<Unit> {
        PlatformLocalStorage.setItem(AuthApiClient.SESSION_STORAGE_KEY, saved("PLATFORM_SUPERADMIN", null))
        val f = fixture { reply(HttpStatusCode.OK, saved("PLATFORM_SUPERADMIN", null)) }
        assertNotNull(f.vm.uiState.value.authenticatedSession)
        assertNull(sessionStorage.currentSession.value, "tanpa tenant: tidak ada TenantSession tebakan")
        delay(300)
    }

    @Test
    fun personaLogin_success_storesServerSession() = runBlocking<Unit> {
        val f = fixture { reply(HttpStatusCode.OK, sessionJson) }
        f.vm.onEvent(LoginUiEvent.SubmitPersonaLogin(persona))
        val state = f.vm.settled()
        assertEquals("server.signed.jwt", assertNotNull(state.authenticatedSession).token.value)
    }
}
