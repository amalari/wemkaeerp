package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.presentation.tenant.InMemoryTenantSessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var sessionStorage: InMemoryTenantSessionStorage
    private lateinit var viewModel: AuthViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionStorage = InMemoryTenantSessionStorage()
        viewModel = AuthViewModel(scope = testScope, sessionStorage = sessionStorage)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selecting_tab_updates_selected_tab_state() {
        assertEquals(LoginTab.GOOGLE, viewModel.uiState.value.selectedTab)

        viewModel.onEvent(LoginUiEvent.SelectTab(LoginTab.WHATSAPP))
        assertEquals(LoginTab.WHATSAPP, viewModel.uiState.value.selectedTab)

        viewModel.onEvent(LoginUiEvent.SelectTab(LoginTab.GOOGLE))
        assertEquals(LoginTab.GOOGLE, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun updating_tenant_slug_normalizes_to_lowercase() {
        viewModel.onEvent(LoginUiEvent.UpdateTenantSlug("   BERKAH-JAYA   "))
        assertEquals("berkah-jaya", viewModel.uiState.value.tenantSlug)
    }

    @Test
    fun google_login_with_blank_slug_shows_error() = testScope.runTest {
        viewModel.onEvent(LoginUiEvent.UpdateTenantSlug(""))
        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin("some-token"))
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Subdomain perusahaan wajib diisi"))
    }

    @Test
    fun whatsapp_otp_should_refuse_instead_of_issuing_a_session() = testScope.runTest {
        // The OTP screen has no server flow behind it, so it used to hand out a session
        // carrying a locally fabricated token that every authenticated endpoint rejects.
        viewModel.onEvent(LoginUiEvent.UpdatePhoneNumber("81234567890"))
        viewModel.onEvent(LoginUiEvent.SendWhatsAppOtp)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOtpSent)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onEvent(LoginUiEvent.UpdateOtpCode("749102"))
        viewModel.onEvent(LoginUiEvent.VerifyWhatsAppOtp)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.authenticatedSession)
    }

    // logout_clears_active_session & demo_superadmin_login_...: dulu bergantung pada sesi OFFLINE
    // (server tak ada -> sesi palsu). Dipindah ke DemoLoginNoSilentFallbackTest dengan server palsu.
}
