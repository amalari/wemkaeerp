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
    fun google_login_with_valid_slug_authenticates_and_stores_session() = testScope.runTest {
        viewModel.onEvent(LoginUiEvent.UpdateTenantSlug("konveksi-demo"))
        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin("mock-google-token:owner@demo.id"))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        val session = state.authenticatedSession
        assertNotNull(session)
        assertEquals(Role.TENANT_ADMIN, session.user.role)
        assertEquals("konveksi-demo", session.tenantSlug)

        // Verify session storage is updated
        assertNotNull(sessionStorage.currentSession.value)
        assertEquals("konveksi-demo", sessionStorage.currentSession.value!!.slug.value)
    }

    @Test
    fun whatsapp_otp_flow_sends_and_verifies_operator_session() = testScope.runTest {
        viewModel.onEvent(LoginUiEvent.SelectTab(LoginTab.WHATSAPP))
        viewModel.onEvent(LoginUiEvent.UpdatePhoneNumber("81234567890"))
        viewModel.onEvent(LoginUiEvent.SendWhatsAppOtp)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOtpSent)
        assertEquals(60, viewModel.uiState.value.otpCountdown)

        // Invalid OTP code (less than 6 digits)
        viewModel.onEvent(LoginUiEvent.UpdateOtpCode("123"))
        viewModel.onEvent(LoginUiEvent.VerifyWhatsAppOtp)
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Valid 6-digit OTP
        viewModel.onEvent(LoginUiEvent.UpdateOtpCode("749102"))
        viewModel.onEvent(LoginUiEvent.VerifyWhatsAppOtp)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val session = state.authenticatedSession
        assertNotNull(session)
        assertEquals(Role.OPERATOR, session.user.role)
    }

    @Test
    fun logout_clears_active_session() = testScope.runTest {
        viewModel.onEvent(LoginUiEvent.SubmitGoogleLogin("mock-google-token:owner@demo.id"))
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.authenticatedSession)

        viewModel.onEvent(LoginUiEvent.Logout)
        assertNull(viewModel.uiState.value.authenticatedSession)
        assertNull(sessionStorage.currentSession.value)
    }

    @Test
    fun demo_superadmin_login_authenticates_with_platform_superadmin_role() = testScope.runTest {
        viewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val session = state.authenticatedSession
        assertNotNull(session)
        assertEquals(Role.PLATFORM_SUPERADMIN, session.user.role)
        assertEquals("superadmin_apps", session.user.username.value)
        assertEquals("WeMade Platform Admin", sessionStorage.currentSession.value!!.name)
    }
}
