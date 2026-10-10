package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.AuthRejectionReason
import com.eventverse.app.infrastructure.api.AuthApiClient
import com.eventverse.app.infrastructure.api.AuthApiError
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.tenant.InMemoryTenantSessionStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Sesi tersimpan yang ditolak saat diverifikasi: pesan dibedakan lewat kode alasan bertipe dari server
 * ([AuthRejectionReason]), bukan lewat teks pesan. Slug non-default `bordir-uji`.
 */
class SessionRestoreRejectionTest {
    @BeforeTest fun setUp() = PlatformLocalStorage.clear()
    @AfterTest fun tearDown() = PlatformLocalStorage.clear()

    private val saved = """{"token":"old.jwt","user":{"id":"usr-1","tenantId":"ten-bordir-uji","username":"owner",""" +
        """"email":"o@bordir-uji.id","role":"TENANT_ADMIN"},"tenantSlug":"bordir-uji"}"""

    private fun restoreWith(status: HttpStatusCode, body: String, reason: String?): LoginUiState {
        PlatformLocalStorage.setItem(AuthApiClient.SESSION_STORAGE_KEY, saved)
        val engine = MockEngine {
            val headers = buildList {
                if (reason != null) add(AuthRejectionReason.HEADER to listOf(reason))
            }.toTypedArray()
            respond(body, status, headersOf(*headers))
        }
        val scope = CoroutineScope(Dispatchers.Default)
        val vm = AuthViewModel(
            scope = scope,
            sessionStorage = InMemoryTenantSessionStorage(),
            authApiClient = AuthApiClient(httpClient = HttpClient(engine)),
            policyRepository = RbacAccessPolicyRepository(apiClientProvider = { null }, scope = scope)
        )
        return runBlocking { withTimeout(5_000) { vm.uiState.first { it.authenticatedSession == null && it.errorMessage != null } } }
    }

    @Test
    fun restore_tokenWithoutTenant401_showsStaleSessionMessage() {
        val state = restoreWith(HttpStatusCode.Unauthorized, "Token tidak memuat tenant", AuthRejectionReason.TOKEN_WITHOUT_TENANT)
        assertEquals("Sesi lama tidak lagi berlaku. Silakan masuk ulang.", state.errorMessage)
        assertNull(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
    }

    @Test
    fun restore_otherUnauthorized_keepsExpiredMessageEvenWithSimilarText() {
        // Teks mirip tanpa kode alasan tidak boleh dikenali sebagai sesi lama.
        val state = restoreWith(HttpStatusCode.Unauthorized, "Token tidak memuat tenant", null)
        assertEquals(AuthViewModel.MSG_SESSION_EXPIRED, state.errorMessage)
        val expired = restoreWith(HttpStatusCode.Unauthorized, "Token expired or invalid", null)
        assertEquals(AuthViewModel.MSG_SESSION_EXPIRED, expired.errorMessage)
    }

    @Test
    fun sessionRejectedMessage_mapsByTypedReasonOnly() {
        val stale = AuthApiError.Rejected(401, "x", "x", AuthRejectionReason.TOKEN_WITHOUT_TENANT)
        assertEquals(AuthViewModel.MSG_SESSION_STALE, AuthViewModel.sessionRejectedMessage(stale))
        assertEquals(AuthViewModel.MSG_SESSION_EXPIRED, AuthViewModel.sessionRejectedMessage(AuthApiError.Rejected(401, "x", "x")))
        assertEquals(AuthViewModel.MSG_SESSION_EXPIRED, AuthViewModel.sessionRejectedMessage(IllegalStateException("x")))
    }
}
