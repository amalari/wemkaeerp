package com.eventverse.app.presentation.auth

import com.eventverse.app.infrastructure.api.AuthApiClient
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google sign-in must go through the server, which verifies the ID token against Google and
 * issues the signed session token every authenticated endpoint requires.
 *
 * This previously fabricated both the user and the token in the browser, so the "logged in"
 * identity was whatever the client claimed, and the token was rejected by the API.
 *
 * Ktor does its work on its own dispatchers, so these cases run on real dispatchers and wait
 * for the state to settle rather than advancing virtual time — a `TestDispatcher` reports
 * "idle" before the request has even been issued.
 */
class GoogleLoginTest {

    private companion object {
        const val SERVER_ISSUED_TOKEN = "server.signed.jwt"
        const val TIMEOUT_MILLIS = 5_000L
    }

    @BeforeTest
    fun setUp() = PlatformLocalStorage.clear()

    @AfterTest
    fun tearDown() = PlatformLocalStorage.clear()

    private fun sessionResponse(
        email: String = "owner@pabrik-alpha.id",
        role: String = "TENANT_ADMIN",
        tenantSlug: String = "pabrik-alpha"
    ) = """
        {"token":"$SERVER_ISSUED_TOKEN",
         "user":{"id":"usr-google-1","tenantId":"ten-alpha-001","username":"owner",
                 "email":"$email","role":"$role","permissions":[]},
         "tenantSlug":"$tenantSlug"}
    """.trimIndent()

    /** Records what the client actually sent. */
    private class Recorder {
        val requests = mutableListOf<Pair<HttpRequestData, String>>()
        val single: Pair<HttpRequestData, String> get() = requests.single()
    }

    private fun viewModelWith(
        recorder: Recorder,
        body: String = "",
        status: HttpStatusCode = HttpStatusCode.OK
    ): AuthViewModel {
        val engine = MockEngine { request ->
            val requestBody = (request.body as? OutgoingContent.ByteArrayContent)
                ?.let { String(it.bytes()) }
                ?: ""
            recorder.requests += request to requestBody
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return AuthViewModel(
            scope = CoroutineScope(Dispatchers.Default),
            authApiClient = AuthApiClient(httpClient = HttpClient(engine))
        )
    }

    /** Suspends until the login attempt has either produced a session or failed. */
    private suspend fun AuthViewModel.awaitSettled(): LoginUiState =
        withTimeout(TIMEOUT_MILLIS) {
            uiState.first {
                !it.isLoading && (it.authenticatedSession != null || it.errorMessage != null)
            }
        }

    @Test
    fun googleLogin_shouldExchangeIdTokenForAServerIssuedSession() = runBlocking<Unit> {
        val recorder = Recorder()
        val viewModel = viewModelWith(recorder, body = sessionResponse())

        viewModel.onEvent(LoginUiEvent.UpdateTenantSlug("pabrik-alpha"))
        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin("google.id.token"))
        val state = viewModel.awaitSettled()

        // 1. The ID token went to the server rather than being trusted locally.
        val (request, body) = recorder.single
        assertTrue(
            request.url.encodedPath.endsWith("/api/public/auth/google"),
            "Diminta ke: ${request.url}"
        )
        assertTrue(body.contains("idToken=google.id.token"), "Body: $body")
        assertTrue(body.contains("tenantSlug=pabrik-alpha"), "Body: $body")

        // 2. The session carries the server's token, not a locally generated one.
        val session = state.authenticatedSession
        assertNotNull(session, "Error: ${state.errorMessage}")
        assertEquals(SERVER_ISSUED_TOKEN, session.token.value)
        assertEquals("owner@pabrik-alpha.id", session.user.email.value)
    }

    @Test
    fun googleLogin_shouldPersistTheSessionSoApiClientsCanReadTheToken() = runBlocking<Unit> {
        val recorder = Recorder()
        val viewModel = viewModelWith(recorder, body = sessionResponse())

        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin("google.id.token"))
        viewModel.awaitSettled()

        val stored = AuthApiClient.deserializeSession(
            PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY)
        )
        assertNotNull(stored, "Sesi harus tersimpan agar API client bisa mengirim token")
        assertEquals(SERVER_ISSUED_TOKEN, stored.token.value)
    }

    @Test
    fun googleLogin_whenServerRejectsTheToken_shouldFailWithoutCreatingASession() =
        runBlocking<Unit> {
            val recorder = Recorder()
            val viewModel = viewModelWith(
                recorder,
                body = "Google token verification failed",
                status = HttpStatusCode.Unauthorized
            )

            viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin("tampered.token"))
            val state = viewModel.awaitSettled()

            // No local fallback: a session the API would reject is worse than a clear
            // failure, because it fails later and somewhere else.
            assertNull(state.authenticatedSession)
            assertNotNull(state.errorMessage)
            assertNull(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
        }

    @Test
    fun googleLogin_withoutAnIdToken_shouldNotCallTheServer() = runBlocking<Unit> {
        // Desktop has no Google bridge wired up and submits a blank token.
        val recorder = Recorder()
        val viewModel = viewModelWith(recorder, body = sessionResponse())

        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin(""))
        val state = viewModel.awaitSettled()

        assertTrue(recorder.requests.isEmpty(), "Token kosong tidak boleh dikirim ke server")
        assertNull(state.authenticatedSession)
        assertTrue(
            state.errorMessage?.contains("belum tersedia") == true,
            "Pesan: ${state.errorMessage}"
        )
    }

    @Test
    fun googleBridge_shouldForwardTheIdTokenItReceives() = runBlocking<Unit> {
        val recorder = Recorder()
        val viewModel = viewModelWith(recorder, body = sessionResponse())
        viewModel.onEvent(LoginUiEvent.UpdateTenantSlug("pabrik-alpha"))

        // The platform bridge hands over the token exactly as the browser produced it.
        GoogleAuthBridge.onAuthenticated?.invoke("token.from.browser")
        viewModel.awaitSettled()

        val (_, body) = recorder.single
        assertTrue(body.contains("idToken=token.from.browser"), "Body: $body")
    }

    @Test
    fun whatsAppOtp_shouldRefuseInsteadOfIssuingAnUnusableSession() = runBlocking<Unit> {
        val recorder = Recorder()
        val viewModel = viewModelWith(recorder, body = sessionResponse())

        viewModel.onEvent(LoginUiEvent.UpdatePhoneNumber("081234567890"))
        viewModel.onEvent(LoginUiEvent.SendWhatsAppOtp)

        assertTrue(viewModel.uiState.value.errorMessage?.contains("belum tersedia") == true)
        assertFalse(viewModel.uiState.value.isOtpSent, "Jangan mengaku OTP terkirim")

        viewModel.onEvent(LoginUiEvent.UpdateOtpCode("123456"))
        viewModel.onEvent(LoginUiEvent.VerifyWhatsAppOtp)

        assertNull(
            viewModel.uiState.value.authenticatedSession,
            "OTP mock tidak boleh menghasilkan sesi apa pun"
        )
        assertNull(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun whatsAppTab_shouldBeHiddenFromTheLoginScreen() {
        assertEquals(listOf(LoginTab.GOOGLE), LoginTab.available)
    }
}
