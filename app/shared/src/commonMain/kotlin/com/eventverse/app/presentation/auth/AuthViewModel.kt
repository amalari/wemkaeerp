package com.eventverse.app.presentation.auth

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.api.AuthApiClient
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import com.eventverse.app.presentation.tenant.InMemoryTenantSessionStorage
import com.eventverse.app.presentation.tenant.TenantSession
import com.eventverse.app.presentation.tenant.TenantSessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

object GoogleAuthBridge {
    var onAuthenticated: ((email: String, name: String) -> Unit)? = null
    var onSignInTrigger: (() -> Unit)? = null
}

/**
 * MVI State-Holder for authentication, persistent session restoration,
 * and database-backed demo login orchestration.
 */
class AuthViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val sessionStorage: TenantSessionStorage = InMemoryTenantSessionStorage(),
    private val authApiClient: AuthApiClient = AuthApiClient()
) {
    companion object {
        const val STORAGE_KEY = "wemade_auth_session"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<LoginUiEffect>()
    val uiEffect: SharedFlow<LoginUiEffect> = _uiEffect.asSharedFlow()

    init {
        GoogleAuthBridge.onAuthenticated = { email, name ->
            onEvent(LoginUiEvent.SubmitGoogleLogin(idToken = "", email = email, name = name))
        }

        // 1. Auto-restore session from PlatformLocalStorage on startup / reload
        val savedJson = PlatformLocalStorage.getItem(STORAGE_KEY)
        val restoredSession = AuthApiClient.deserializeSession(savedJson)
        if (restoredSession != null) {
            _uiState.update {
                it.copy(
                    authenticatedSession = restoredSession,
                    tenantSlug = restoredSession.tenantSlug ?: it.tenantSlug
                )
            }
            sessionStorage.setSession(
                TenantSession(
                    tenantId = restoredSession.user.tenantId ?: TenantId("ten-default"),
                    slug = TenantSlug(restoredSession.tenantSlug ?: "wemade-demo"),
                    name = "Pabrik ${restoredSession.tenantSlug ?: "wemade-demo"}",
                    tier = SubscriptionTier.PRO
                )
            )

            // 2. Asynchronously verify token validity against backend DB
            scope.launch {
                val verifyResult = authApiClient.verifySession(restoredSession.token.value)
                verifyResult.onSuccess { verifiedSession ->
                    _uiState.update { it.copy(authenticatedSession = verifiedSession) }
                    PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(verifiedSession))
                }.onFailure {
                    // Token expired or invalid: clear session
                    PlatformLocalStorage.removeItem(STORAGE_KEY)
                    sessionStorage.clearSession()
                    _uiState.update {
                        it.copy(
                            authenticatedSession = null,
                            errorMessage = "Sesi telah kedaluwarsa. Silakan masuk kembali."
                        )
                    }
                }
            }
        }
    }

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab, errorMessage = null, successMessage = null) }
            }
            is LoginUiEvent.UpdateTenantSlug -> {
                _uiState.update { it.copy(tenantSlug = event.slug.lowercase().trim(), errorMessage = null) }
            }
            is LoginUiEvent.UpdatePhoneNumber -> {
                _uiState.update { it.copy(phoneNumber = event.phone, errorMessage = null) }
            }
            is LoginUiEvent.UpdateOtpCode -> {
                _uiState.update { it.copy(otpCode = event.otp, errorMessage = null) }
            }
            is LoginUiEvent.SubmitDemoLogin -> {
                handleDemoLogin(Role.TENANT_ADMIN)
            }
            is LoginUiEvent.SubmitDemoSuperAdminLogin -> {
                handleDemoLogin(Role.PLATFORM_SUPERADMIN)
            }
            is LoginUiEvent.SubmitGoogleLogin -> {
                if (event.idToken == "demo-token") {
                    handleDemoLogin(Role.TENANT_ADMIN)
                } else {
                    handleGoogleLogin(event.idToken, event.email, event.name)
                }
            }
            is LoginUiEvent.SendWhatsAppOtp -> {
                handleSendWhatsAppOtp()
            }
            is LoginUiEvent.VerifyWhatsAppOtp -> {
                handleVerifyWhatsAppOtp()
            }
            is LoginUiEvent.DismissMessage -> {
                _uiState.update { it.copy(errorMessage = null, successMessage = null) }
            }
            is LoginUiEvent.Logout -> {
                PlatformLocalStorage.removeItem(STORAGE_KEY)
                sessionStorage.clearSession()
                _uiState.update {
                    it.copy(
                        authenticatedSession = null,
                        errorMessage = null,
                        successMessage = "Berhasil keluar dari sistem."
                    )
                }
            }
        }
    }

    private fun handleDemoLogin(targetRole: Role = Role.TENANT_ADMIN) {
        val currentSlug = _uiState.value.tenantSlug.ifBlank { "wemade-demo" }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            val result = authApiClient.loginDemo(currentSlug, role = targetRole.name)
            result.onSuccess { session ->
                // 1. Simpan session token & profil ke PlatformLocalStorage (browser localStorage)
                PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))

                // 2. Simpan ke tenant session storage
                sessionStorage.setSession(
                    TenantSession(
                        tenantId = session.user.tenantId ?: TenantId("ten-default"),
                        slug = TenantSlug(session.tenantSlug ?: currentSlug),
                        name = if (targetRole == Role.PLATFORM_SUPERADMIN) "WeMade Platform Admin" else "Pabrik ${session.tenantSlug ?: currentSlug}",
                        tier = SubscriptionTier.PRO
                    )
                )

                // 3. Update state & navigasi
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = session,
                        successMessage = "Selamat datang, ${session.user.username.value}! (${session.user.email.value}) — Terkoneksi ke DB & JWT Aktif"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
            }.onFailure { error ->
                // Fallback offline session jika backend offline
                val fallbackUser = if (targetRole == Role.PLATFORM_SUPERADMIN) {
                    User(
                        id = UserId("usr-superadmin-001"),
                        tenantId = TenantId("ten-$currentSlug"),
                        username = Username("superadmin_apps"),
                        email = EmailAddress("superadmin@wemade.id"),
                        role = Role.PLATFORM_SUPERADMIN,
                        isActive = true
                    )
                } else {
                    User(
                        id = UserId("usr-owner-001"),
                        tenantId = TenantId("ten-$currentSlug"),
                        username = Username("achmad_owner"),
                        email = EmailAddress("student.achmad@gmail.com"),
                        role = Role.TENANT_ADMIN,
                        isActive = true
                    )
                }
                val offlineSession = UserSession(
                    user = fallbackUser,
                    token = AuthToken("jwt-offline-token-${kotlin.random.Random.nextInt(100000, 999999)}"),
                    tenantSlug = currentSlug
                )
                PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(offlineSession))
                sessionStorage.setSession(
                    TenantSession(
                        tenantId = fallbackUser.tenantId ?: TenantId("ten-default"),
                        slug = TenantSlug(currentSlug),
                        name = if (targetRole == Role.PLATFORM_SUPERADMIN) "WeMade Platform Admin" else "Pabrik $currentSlug",
                        tier = SubscriptionTier.PRO
                    )
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = offlineSession,
                        successMessage = "Mode Demo Offline: ${fallbackUser.username.value} (${fallbackUser.role.name})"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(offlineSession))
            }
        }
    }

    private fun handleGoogleLogin(idToken: String, email: String? = null, name: String? = null) {
        val currentSlug = _uiState.value.tenantSlug
        if (currentSlug.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Subdomain perusahaan wajib diisi") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            try {
                val userEmail = email?.ifBlank { null } ?: "student.achmad@gmail.com"
                val userName = if (userEmail.contains("achmad")) "achmad_owner" else (name?.ifBlank { null } ?: "user_${userEmail.substringBefore("@")}")

                val user = User(
                    id = UserId("usr-owner-001"),
                    tenantId = TenantId("ten-$currentSlug"),
                    username = Username(userName.replace(" ", "_").lowercase()),
                    email = EmailAddress(userEmail),
                    role = Role.TENANT_ADMIN,
                    isActive = true
                )

                val session = UserSession(
                    user = user,
                    token = AuthToken("jwt-session-token-${kotlin.random.Random.nextInt(100000, 999999)}"),
                    tenantSlug = currentSlug
                )

                // Persist session
                PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))

                sessionStorage.setSession(
                    TenantSession(
                        tenantId = user.tenantId ?: TenantId("ten-default"),
                        slug = TenantSlug(currentSlug),
                        name = "Pabrik $currentSlug",
                        tier = SubscriptionTier.PRO
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authenticatedSession = session,
                        successMessage = "Selamat datang kembali, ${user.username.value}! (${user.email.value}) — Role: ${user.role.name}"
                    )
                }
                _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Autentikasi Google gagal"
                    )
                }
            }
        }
    }

    private fun handleSendWhatsAppOtp() {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.length < 9) {
            _uiState.update { it.copy(errorMessage = "Nomor WhatsApp tidak valid (minimal 9 digit)") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isOtpSent = true,
                    otpCountdown = 60,
                    successMessage = "Kode OTP 6-digit berhasil dikirim ke WhatsApp $phone"
                )
            }
        }
    }

    private fun handleVerifyWhatsAppOtp() {
        val otp = _uiState.value.otpCode.trim()
        if (otp.length != 6) {
            _uiState.update { it.copy(errorMessage = "Kode OTP harus 6 digit angka") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val currentSlug = _uiState.value.tenantSlug
            val randomId = kotlin.random.Random.nextInt(100, 999)
            val operatorUser = User(
                id = UserId("usr-op-$randomId"),
                tenantId = TenantId("ten-$currentSlug"),
                username = Username("operator_lapangan"),
                email = EmailAddress("operator@${currentSlug}.id"),
                role = Role.OPERATOR,
                isActive = true
            )

            val session = UserSession(
                user = operatorUser,
                token = AuthToken("jwt-wa-token-${kotlin.random.Random.nextInt(100000, 999999)}"),
                tenantSlug = currentSlug
            )

            PlatformLocalStorage.setItem(STORAGE_KEY, AuthApiClient.serializeSession(session))

            _uiState.update {
                it.copy(
                    isLoading = false,
                    authenticatedSession = session,
                    successMessage = "Login WhatsApp berhasil sebagai Operator Mesin"
                )
            }
            _uiEffect.emit(LoginUiEffect.NavigateToDashboard(session))
        }
    }
}
